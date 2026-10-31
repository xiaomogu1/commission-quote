package com.bendigo.lendingquote.quote.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bendigo.lendingquote.LendingQuoteServiceApplication;
import com.bendigo.lendingquote.quote.exception.VendorTimeoutException;
import com.bendigo.lendingquote.quote.exception.VendorUnavailableException;
import com.bendigo.lendingquote.quote.exception.VendorRejectedException;
import com.bendigo.lendingquote.common.CorrelationIdFilter;
import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import com.bendigo.lendingquote.quote.domain.RiskBand;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientException;

/**
 * Component test of the vendor integration against a WireMock server: the real RestClient configuration
 * (timeouts, interceptors, status handlers) and method validation, over real HTTP.
 * Each test stubs one vendor behaviour and checks how we translate it.
 */
@SpringBootTest(classes = LendingQuoteServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VendorClientComponentTest {

    private static final String PATH = "/v1/commission-quotes";
    private static final String API_KEY = "test-key";
    private static final LoanDetails LOAN = new LoanDetails(new BigDecimal("500000.00"), 360, RiskBand.MEDIUM);

    @RegisterExtension
    static WireMockExtension vendor = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void vendorProperties(DynamicPropertyRegistry registry) {
        registry.add("vendor.base-url", vendor::baseUrl);
        registry.add("vendor.api-key", () -> API_KEY);
        // Generous enough for the first (cold) call of the test run; the timeout test waits longer than this.
        registry.add("vendor.read-timeout", () -> "2s");
    }

    @Autowired
    VendorClient client;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    /** Tests share one application context; start each one with the vendor circuit closed. */
    @BeforeEach
    void closeCircuit() {
        circuitBreakers.circuitBreaker("vendor").reset();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ---- 2xx ----------------------------------------------------------------------------------

    @Test
    void requestQuote_vendorReturns200_returnsQuote() {
        vendor.stubFor(post(PATH).willReturn(okJson("""
                {"quoteId": "Q-123", "commissionRate": 0.006, "totalCommission": 3000.00}""")));

        CommissionQuote quote = client.requestQuote(LOAN);

        assertThat(quote.getQuoteId()).isEqualTo("Q-123");
        assertThat(quote.getCommissionRate()).isEqualByComparingTo("0.006");
        assertThat(quote.getTotalCommission()).isEqualByComparingTo("3000.00");
    }

    @Test
    void requestQuote_anyRequest_sendsApiKeyCorrelationIdAndContractBody() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-12345678");
        vendor.stubFor(post(PATH).willReturn(okJson("""
                {"quoteId": "Q-1", "commissionRate": 0.006, "totalCommission": 3000.00}""")));

        client.requestQuote(LOAN);

        vendor.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("api-key", equalTo(API_KEY))
                .withHeader("X-Correlation-Id", equalTo("corr-12345678"))
                .withRequestBody(equalToJson("""
                        {"loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM"}""")));
    }

    @Test
    void requestQuote_responseHasUnknownFields_returnsQuote() {
        vendor.stubFor(post(PATH).willReturn(okJson("""
                {"quoteId": "Q-1", "commissionRate": 0.006, "totalCommission": 3000.00, "newField": "x"}""")));

        assertThat(client.requestQuote(LOAN).getQuoteId()).isEqualTo("Q-1");
    }

    @Test
    void requestQuote_responseMissingFields_throwsConstraintViolationException() {
        vendor.stubFor(post(PATH).willReturn(okJson("""
                {"quoteId": "Q-1"}""")));

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void requestQuote_negativeCommission_throwsConstraintViolationException() {
        vendor.stubFor(post(PATH).willReturn(okJson("""
                {"quoteId": "Q-1", "commissionRate": -0.006, "totalCommission": 3000.00}""")));

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void requestQuote_unparseableBody_throwsRestClientException() {
        vendor.stubFor(post(PATH).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("not json")));

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(RestClientException.class);
    }

    // ---- 4xx / 5xx ----------------------------------------------------------------------------

    @ParameterizedTest(name = "HTTP {0} -> unavailable (retryable)")
    @ValueSource(ints = {429, 500, 502, 503})
    void requestQuote_vendorReturns5xxOr429_throwsVendorUnavailableException(int status) {
        stubStatus(status);

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(VendorUnavailableException.class);
    }

    @ParameterizedTest(name = "HTTP {0} -> rejected (not retryable)")
    @ValueSource(ints = {400, 401, 403, 422})
    void requestQuote_vendorReturns4xx_throwsVendorRejectedException(int status) {
        stubStatus(status);

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(VendorRejectedException.class);
    }

    // ---- network ------------------------------------------------------------------------------

    @Test
    void requestQuote_vendorSlowerThanReadTimeout_throwsVendorTimeoutException() {
        vendor.stubFor(post(PATH).willReturn(okJson("""
                {"quoteId": "Q-1", "commissionRate": 0.006, "totalCommission": 3000.00}""").withFixedDelay(3000)));

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(VendorTimeoutException.class);
    }

    @Test
    void requestQuote_connectionReset_throwsVendorUnavailableException() {
        vendor.stubFor(post(PATH).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(VendorUnavailableException.class);
    }

    // ---- no automatic retry: creating a quote is not idempotent ------------------------------

    @Test
    void requestQuote_vendorTimesOut_callsVendorOnlyOnce() {
        vendor.stubFor(post(PATH).willReturn(okJson("{}").withFixedDelay(3000)));

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(VendorTimeoutException.class);

        // The vendor may have created a quote before timing out; retrying could create a duplicate.
        vendor.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void requestQuote_vendorReturns500_callsVendorOnlyOnce() {
        stubStatus(500);

        assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(VendorUnavailableException.class);

        vendor.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    private void stubStatus(int status) {
        vendor.stubFor(post(PATH).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\": " + status + "}")));
    }
}

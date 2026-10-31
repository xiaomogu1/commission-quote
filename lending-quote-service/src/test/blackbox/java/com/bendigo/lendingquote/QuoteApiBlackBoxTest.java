package com.bendigo.lendingquote;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Black-box test: the service runs on a real port and is called over HTTP, exactly like the browser does.
 * Responses are read as raw JSON (not our DTOs) so the test checks the actual contract the UI depends on.
 * The vendor is replaced by WireMock, so we can drive success, vendor errors and timeouts end to end.
 */
@SpringBootTest(classes = LendingQuoteServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuoteApiBlackBoxTest {

    private static final String API_KEY = "black-box-test-key";
    private static final String VENDOR_PATH = "/v1/commission-quotes";
    private static final String VALID_BODY = """
            {"loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM"}""";

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

    @LocalServerPort
    int port;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    /** Tests share one application context; start each one with the vendor circuit closed. */
    @BeforeEach
    void closeCircuit() {
        circuitBreakers.circuitBreaker("vendor").reset();
    }

    @Test
    void createQuote_validRequest_returns200AndSendsApiKeyToVendor() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(okJson("""
                {"quoteId": "Q-1", "commissionRate": 0.006, "totalCommission": 3000.00}""")));

        ResponseEntity<String> response = postQuote(VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
        assertThat(JsonPath.<String>read(response.getBody(), "$.quoteId")).isEqualTo("Q-1");
        assertThat(JsonPath.<Double>read(response.getBody(), "$.commissionRate")).isEqualTo(0.006);
        assertThat(JsonPath.<Double>read(response.getBody(), "$.totalCommission")).isEqualTo(3000.00);
        vendor.verify(postRequestedFor(urlEqualTo(VENDOR_PATH)).withHeader("api-key", equalTo(API_KEY)));
    }

    @Test
    void createQuote_invalidInput_returns400WithFieldErrorsWithoutCallingVendor() {
        ResponseEntity<String> response = postQuote("""
                {"loanAmount": 1, "loanTermInMonths": 360, "riskBand": "MEDIUM"}""");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(JsonPath.<String>read(response.getBody(), "$.errors.loanAmount")).isEqualTo("must be at least $1,000");
        assertThat(JsonPath.<String>read(response.getBody(), "$.correlationId")).isNotBlank();
        vendor.verify(0, postRequestedFor(urlEqualTo(VENDOR_PATH)));
    }

    @Test
    void createQuote_vendorReturns500_returns502Retryable() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> response = postQuote(VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertRetryable(response, true);
    }

    @Test
    void createQuote_vendorUnreachable_returns502Retryable() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ResponseEntity<String> response = postQuote(VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertRetryable(response, true);
    }

    @Test
    void createQuote_vendorTimesOut_returns504Retryable() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(okJson("{}").withFixedDelay(3000)));

        ResponseEntity<String> response = postQuote(VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertRetryable(response, true);
    }

    @Test
    void createQuote_vendorRejectsKey_returns502NotRetryableWithoutExposingKey() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(401)
                .withBody("{\"detail\": \"api-key " + API_KEY + " is not valid\"}")));

        ResponseEntity<String> response = postQuote(VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertRetryable(response, false);
        assertThat(response.getBody()).doesNotContain(API_KEY).doesNotContain("api-key");
        assertThat(response.getHeaders().toString()).doesNotContain(API_KEY);
    }

    @Test
    void createQuote_vendorReturnsIncompleteBody_returns502NotRetryable() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(okJson("""
                {"quoteId": "Q-1"}""")));

        ResponseEntity<String> response = postQuote(VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertRetryable(response, false);
    }

    private static void assertRetryable(ResponseEntity<String> response, boolean expected) {
        assertThat(JsonPath.<Boolean>read(response.getBody(), "$.retryable")).isEqualTo(expected);
        assertThat(JsonPath.<String>read(response.getBody(), "$.correlationId")).isNotBlank();
    }

    private ResponseEntity<String> postQuote(String body) {
        return RestClient.create("http://localhost:" + port)
                .post().uri("/api/commission-quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (request, response) -> { /* assert on the status instead of throwing */ })
                .toEntity(String.class);
    }
}

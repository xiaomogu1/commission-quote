package com.bendigo.lendingquote.quote.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bendigo.lendingquote.LendingQuoteServiceApplication;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import com.bendigo.lendingquote.quote.domain.RiskBand;
import com.bendigo.lendingquote.quote.exception.VendorRejectedException;
import com.bendigo.lendingquote.quote.exception.VendorTimeoutException;
import com.bendigo.lendingquote.quote.exception.VendorUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The vendor circuit breaker against a WireMock vendor. A small window (4 calls) keeps the tests short;
 * the production values live in application.yml. State changes are checked through the registry, and
 * "no call reached the vendor" through WireMock's request log.
 */
@SpringBootTest(classes = LendingQuoteServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VendorCircuitBreakerComponentTest {

    private static final String PATH = "/v1/commission-quotes";
    private static final int WINDOW = 4;
    private static final LoanDetails LOAN = new LoanDetails(new BigDecimal("500000.00"), 360, RiskBand.MEDIUM);

    @RegisterExtension
    static WireMockExtension vendor = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vendor.base-url", vendor::baseUrl);
        registry.add("vendor.api-key", () -> "test-key");
        registry.add("vendor.read-timeout", () -> "1s");
        registry.add("resilience4j.circuitbreaker.instances.vendor.sliding-window-size", () -> WINDOW);
        registry.add("resilience4j.circuitbreaker.instances.vendor.minimum-number-of-calls", () -> WINDOW);
        registry.add("resilience4j.circuitbreaker.instances.vendor.permitted-number-of-calls-in-half-open-state", () -> 2);
        registry.add("resilience4j.circuitbreaker.instances.vendor.wait-duration-in-open-state", () -> "1h");
    }

    @Autowired
    VendorClient client;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    private CircuitBreaker circuit;

    @BeforeEach
    void closeCircuit() {
        circuit = circuitBreakers.circuitBreaker("vendor");
        circuit.reset();
    }

    @Test
    void requestQuote_vendorKeepsReturning500_opensCircuitAndStopsCallingVendor() {
        vendor.stubFor(post(PATH).willReturn(aResponse().withStatus(500)));

        failTimes(WINDOW, VendorUnavailableException.class);
        assertThat(circuit.getState()).isEqualTo(State.OPEN);

        assertThatThrownBy(() -> client.requestQuote(LOAN))
                .isInstanceOf(VendorUnavailableException.class)
                .hasMessageContaining("circuit is open");
        vendor.verify(WINDOW, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void requestQuote_vendorKeepsTimingOut_opensCircuit() {
        vendor.stubFor(post(PATH).willReturn(okJson("{}").withFixedDelay(2000)));

        failTimes(WINDOW, VendorTimeoutException.class);

        assertThat(circuit.getState()).isEqualTo(State.OPEN);
    }

    @Test
    void requestQuote_vendorKeepsRejectingRequest_keepsCircuitClosed() {
        vendor.stubFor(post(PATH).willReturn(aResponse().withStatus(401)));

        failTimes(WINDOW * 2, VendorRejectedException.class);

        // A 4xx is our configuration or contract problem; failing fast would only hide it.
        assertThat(circuit.getState()).isEqualTo(State.CLOSED);
        vendor.verify(WINDOW * 2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void requestQuote_vendorRecoversWhileHalfOpen_closesCircuit() {
        vendor.stubFor(post(PATH).willReturn(aResponse().withStatus(500)));
        failTimes(WINDOW, VendorUnavailableException.class);
        circuit.transitionToHalfOpenState(); // what happens on its own after wait-duration-in-open-state

        vendor.stubFor(post(PATH).willReturn(okJson("""
                {"quoteId": "Q-1", "commissionRate": 0.006, "totalCommission": 3000.00}""")));
        client.requestQuote(LOAN);
        client.requestQuote(LOAN);

        assertThat(circuit.getState()).isEqualTo(State.CLOSED);
    }

    private void failTimes(int times, Class<? extends Exception> expected) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> client.requestQuote(LOAN)).isInstanceOf(expected);
        }
    }
}

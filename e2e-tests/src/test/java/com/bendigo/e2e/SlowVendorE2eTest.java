package com.bendigo.e2e;

import static com.bendigo.e2e.E2eStack.VALID_QUOTE;
import static com.bendigo.e2e.E2eStack.postQuote;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The whole system with a vendor slower than the service's 3s read timeout. */
@Testcontainers
class SlowVendorE2eTest {

    @Container
    static final ComposeContainer stack = E2eStack.start(Map.of(
            "VENDOR_FAILURE_RATE", "0",
            "VENDOR_MIN_LATENCY_MS", "5000",
            "VENDOR_MAX_LATENCY_MS", "5000"));

    @Test
    void createQuote_vendorSlowerThanTimeout_returnsRetryable504() {
        ResponseEntity<String> response = postQuote(stack, VALID_QUOTE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(JsonPath.<Boolean>read(response.getBody(), "$.retryable")).isTrue();
    }
}

package com.bendigo.e2e;

import static com.bendigo.e2e.E2eStack.VALID_QUOTE;
import static com.bendigo.e2e.E2eStack.postQuote;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The whole system with a vendor that randomly fails half the time, as the real one occasionally will.
 * With 20 calls the chance of never seeing both outcomes is about 2 in a million.
 */
@Testcontainers
class UnreliableVendorE2eTest {

    private static final int CALLS = 20;

    @Container
    static final ComposeContainer stack = E2eStack.start(Map.of("VENDOR_FAILURE_RATE", "0.5"));

    @Test
    void createQuote_vendorFailsRandomly_returnsQuoteOrRetryable502() {
        List<HttpStatusCode> statuses = new ArrayList<>();

        for (int i = 0; i < CALLS; i++) {
            ResponseEntity<String> response = postQuote(stack, VALID_QUOTE);
            statuses.add(response.getStatusCode());
            assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_GATEWAY);
            if (response.getStatusCode() == HttpStatus.BAD_GATEWAY) {
                assertThat(JsonPath.<Boolean>read(response.getBody(), "$.retryable")).isTrue();
            }
        }

        assertThat(statuses).contains(HttpStatus.OK, HttpStatus.BAD_GATEWAY);
    }
}

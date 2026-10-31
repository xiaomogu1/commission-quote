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

/**
 * The whole system with a vendor that never fails. Fails (rather than skipping) when Docker isn't available:
 * this project exists only to run these tests, so a silent skip would be a false green.
 */
@Testcontainers
class QuoteApiE2eTest {

    @Container
    static final ComposeContainer stack = E2eStack.start(Map.of("VENDOR_FAILURE_RATE", "0"));

    @Test
    void createQuote_validRequest_returnsQuoteCalculatedByVendor() {
        ResponseEntity<String> response = postQuote(stack, VALID_QUOTE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
        assertThat(JsonPath.<String>read(response.getBody(), "$.quoteId")).startsWith("Q-");
        assertThat(JsonPath.<Double>read(response.getBody(), "$.commissionRate")).isEqualTo(0.006);
        assertThat(JsonPath.<Double>read(response.getBody(), "$.totalCommission")).isEqualTo(3000.00);
    }

    @Test
    void createQuote_invalidInput_returns400() {
        ResponseEntity<String> response = postQuote(stack, """
                {"loanAmount": 1, "loanTermInMonths": 360, "riskBand": "MEDIUM"}""");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(JsonPath.<String>read(response.getBody(), "$.errors.loanAmount")).isNotBlank();
    }

    @Test
    void vendor_calledWithoutApiKey_returns401() {
        ResponseEntity<String> response = E2eStack.post(stack, E2eStack.VENDOR, 8081, "/v1/commission-quotes", VALID_QUOTE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

package com.bendigo.lendingquote.quote.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bendigo.lendingquote.LendingQuoteServiceApplication;
import com.bendigo.lendingquote.quote.client.VendorClient;
import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.exception.VendorRejectedException;
import com.bendigo.lendingquote.quote.exception.VendorTimeoutException;
import com.bendigo.lendingquote.quote.exception.VendorUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClientException;

/**
 * Component test of the quote API: the whole application context is real (filter, controller, validation,
 * mappers, service, exception handler); only the vendor boundary (VendorClient) is mocked so every
 * vendor outcome can be forced.
 */
@SpringBootTest(classes = LendingQuoteServiceApplication.class, properties = "vendor.api-key=test-key")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class QuoteApiComponentTest {

    private static final String URL = "/api/commission-quotes";
    private static final String VALID_BODY = """
            {"loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM"}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    VendorClient vendorClient;

    // ---- 2xx ----------------------------------------------------------------------------------

    @Test
    void createQuote_validRequest_returns200WithQuote() throws Exception {
        when(vendorClient.requestQuote(any())).thenReturn(
                new CommissionQuote("Q-123", new BigDecimal("0.006"), new BigDecimal("3000.00")));

        postQuote(VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.quoteId").value("Q-123"))
                .andExpect(jsonPath("$.commissionRate").value(0.006))
                .andExpect(jsonPath("$.totalCommission").value(3000.00));
    }

    @Test
    void createQuote_callerSendsCorrelationId_echoesSameId() throws Exception {
        when(vendorClient.requestQuote(any())).thenThrow(new VendorUnavailableException("down", null));

        mvc.perform(post(URL).header("X-Correlation-Id", "abc-12345678")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(header().string("X-Correlation-Id", "abc-12345678"))
                .andExpect(jsonPath("$.correlationId").value("abc-12345678"));
    }

    // ---- 4xx: rejected before the vendor is called --------------------------------------------

    @Test
    void createQuote_invalidFields_returns400WithFieldErrors() throws Exception {
        postQuote("""
                {"loanAmount": 500.123, "loanTermInMonths": 400}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.loanAmount").exists())
                .andExpect(jsonPath("$.errors.loanTermInMonths").value("must be at most 360 months"))
                .andExpect(jsonPath("$.errors.riskBand").value("is required"))
                .andExpect(jsonPath("$.correlationId").exists());

        verifyNoInteractions(vendorClient);
    }

    @ParameterizedTest(name = "loanAmount={0}, loanTermInMonths={1} -> 200")
    @CsvSource({
            "1000,       1",    // lowest amount, shortest term
            "5000000,    360",  // highest amount, longest term
            "1000.5,     12",   // one decimal place
            "250000.99,  12",   // two decimal places
    })
    void createQuote_boundaryValuesInsideRange_returns200(String loanAmount, String loanTermInMonths) throws Exception {
        when(vendorClient.requestQuote(any())).thenReturn(
                new CommissionQuote("Q-1", new BigDecimal("0.006"), new BigDecimal("6.00")));

        postQuote(body(loanAmount, loanTermInMonths, "\"LOW\""))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "loanAmount={0}, loanTermInMonths={1}, riskBand={2} -> 400 on {3}")
    @CsvSource({
            "999.99,     12,  '\"LOW\"', loanAmount",        // just below minimum
            "5000000.01, 12,  '\"LOW\"', loanAmount",        // just above maximum
            "0,          12,  '\"LOW\"', loanAmount",
            "-1000,      12,  '\"LOW\"', loanAmount",
            "1000.123,   12,  '\"LOW\"', loanAmount",        // more than 2 decimal places
            "null,       12,  '\"LOW\"', loanAmount",
            "1000,       0,   '\"LOW\"', loanTermInMonths",  // just below minimum
            "1000,       361, '\"LOW\"', loanTermInMonths",  // just above maximum
            "1000,       -12, '\"LOW\"', loanTermInMonths",
            "1000,       null,'\"LOW\"', loanTermInMonths",
            "1000,       12,  null,      riskBand",
    })
    void createQuote_invalidValue_returns400OnThatField(String loanAmount, String loanTermInMonths, String riskBand,
                                                        String field) throws Exception {
        postQuote(body(loanAmount, loanTermInMonths, riskBand))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());

        verifyNoInteractions(vendorClient);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "{\"loanAmount\": \"abc\", \"loanTermInMonths\": 12, \"riskBand\": \"LOW\"}",   // not a number
            "{\"loanAmount\": 1000, \"loanTermInMonths\": 12.5, \"riskBand\": \"LOW\"}",      // must not be truncated to 12
    })
    void createQuote_wrongType_returns400(String requestBody) throws Exception {
        postQuote(requestBody)
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vendorClient);
    }

    @Test
    void createQuote_success_auditsQuoteWithoutLoanAmount(CapturedOutput output) throws Exception {
        when(vendorClient.requestQuote(any())).thenReturn(
                new CommissionQuote("Q-AUDIT-1", new BigDecimal("0.006"), new BigDecimal("2964.42")));

        postQuote(body("494070.13", "360", "\"MEDIUM\"")).andExpect(status().isOk());

        assertThat(output.getOut()).contains("quote.generated quoteId=Q-AUDIT-1 riskBand=MEDIUM loanTermInMonths=360");
        assertThat(output.getOut()).doesNotContain("494070.13");
    }

    @Test
    void createQuote_unknownRiskBand_returns400() throws Exception {
        postQuote("""
                {"loanAmount": 500000, "loanTermInMonths": 12, "riskBand": "EXTREME"}""")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vendorClient);
    }

    @Test
    void createQuote_malformedJson_returns400() throws Exception {
        postQuote("{not json")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vendorClient);
    }

    // ---- vendor failures -> 5xx ---------------------------------------------------------------

    static Stream<Arguments> vendorFailures() {
        String internal = "vendor said: HTTP 401 internal detail";
        return Stream.of(
                Arguments.of(new VendorTimeoutException(new HttpTimeoutException(internal)), 504, true),
                Arguments.of(new VendorUnavailableException(internal, null), 502, true),
                Arguments.of(new VendorRejectedException(internal), 502, false),
                Arguments.of(new ConstraintViolationException(internal, Set.of()), 502, false),
                Arguments.of(new RestClientException(internal), 502, false));
    }

    @ParameterizedTest(name = "{0} -> HTTP {1}, retryable={2}")
    @MethodSource("vendorFailures")
    void createQuote_vendorFails_returnsMappedStatus(RuntimeException failure, int expectedStatus, boolean retryable) throws Exception {
        when(vendorClient.requestQuote(any())).thenThrow(failure);

        postQuote(VALID_BODY)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.retryable").value(retryable))
                .andExpect(jsonPath("$.correlationId").exists())
                // vendor internals must not leak to the browser
                .andExpect(content().string(not(containsString("internal detail"))));
    }

    @Test
    void createQuote_unexpectedError_returns500WithoutDetails() throws Exception {
        when(vendorClient.requestQuote(any())).thenThrow(new IllegalStateException("secret internal state"));

        postQuote(VALID_BODY)
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("secret internal state"))));
    }

    private static String body(String loanAmount, String loanTermInMonths, String riskBand) {
        return "{\"loanAmount\": " + loanAmount + ", \"loanTermInMonths\": " + loanTermInMonths
                + ", \"riskBand\": " + riskBand + "}";
    }

    private ResultActions postQuote(String body) throws Exception {
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

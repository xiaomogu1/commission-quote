package com.bendigo.mockvendor.api;

import com.bendigo.mockvendor.simulation.FailureSimulator;
import com.bendigo.mockvendor.simulation.SimulatedVendorFailureException;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Component test of the mock vendor API: full Spring context and real filter chain, driven over HTTP
 * with MockMvc. Random failures and latency are switched off so results are deterministic.
 */
@SpringBootTest(properties = {
        "mock-vendor.api-key=test-key",
        "mock-vendor.simulation.failure-rate=0",
        "mock-vendor.simulation.min-latency-ms=0",
        "mock-vendor.simulation.max-latency-ms=0"
})
@AutoConfigureMockMvc
class MockVendorApiTest {

    private static final String URL = "/v1/commission-quotes";
    private static final String VALID_BODY = """
            {"loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM"}""";

    @Autowired
    MockMvc mvc;

    @MockitoSpyBean
    FailureSimulator failureSimulator;

    @Test
    void createQuote_validRequest_returns200WithQuote() throws Exception {
        mvc.perform(post(URL).header("api-key", "test-key").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId", startsWith("Q-")))
                .andExpect(jsonPath("$.commissionRate").value(0.006))
                .andExpect(jsonPath("$.totalCommission").value(3000.00));
    }

    @Test
    void createQuote_missingApiKey_returns401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createQuote_wrongApiKey_returns401() throws Exception {
        mvc.perform(post(URL).header("api-key", "wrong").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createQuote_outOfRangeValues_returns400WithFieldErrors() throws Exception {
        mvc.perform(post(URL).header("api-key", "test-key").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loanAmount": -5, "loanTermInMonths": 0, "riskBand": "LOW"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.loanAmount").exists())
                .andExpect(jsonPath("$.errors.loanTermInMonths").exists());
    }

    @Test
    void createQuote_unknownRiskBand_returns400() throws Exception {
        mvc.perform(post(URL).header("api-key", "test-key").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loanAmount": 500000, "loanTermInMonths": 12, "riskBand": "EXTREME"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createQuote_simulatedFailure_returns500() throws Exception {
        doThrow(new SimulatedVendorFailureException()).when(failureSimulator).maybeFail();

        mvc.perform(post(URL).header("api-key", "test-key").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError());
    }
}

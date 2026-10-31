package com.bendigo.lendingquote.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bendigo.lendingquote.LendingQuoteServiceApplication;
import com.bendigo.lendingquote.quote.client.VendorClient;
import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The quote API with staff authentication switched on. Requests carry a real "Authorization: Bearer" header;
 * only signature verification (the JwtDecoder, which would call Keycloak) is replaced. Role mapping,
 * the username used for auditing and the access rules all run as in production.
 */
@SpringBootTest(classes = LendingQuoteServiceApplication.class,
        properties = {
                "app.security.enabled=true",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://sso.test/realms/lending",
                "vendor.api-key=test-key"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecuredQuoteApiComponentTest {

    private static final String URL = "/api/commission-quotes";
    private static final String VALID_BODY = """
            {"loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM"}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    VendorClient vendorClient;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void createQuote_noToken_returns401() throws Exception {
        mvc.perform(quoteRequest())
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(vendorClient);
    }

    @Test
    void createQuote_tokenWithoutStaffRole_returns403() throws Exception {
        String token = token("bob", Map.of("realm_access", Map.of("roles", List.of("CUSTOMER"))));

        mvc.perform(quoteRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vendorClient);
    }

    @Test
    void createQuote_staffRoleOnlyInScope_returns403() throws Exception {
        // Spring's default would read "scope"; we only trust Keycloak realm roles.
        String token = token("mallory", Map.of("scope", SecurityConfig.STAFF_ROLE));

        mvc.perform(quoteRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vendorClient);
    }

    @Test
    void createQuote_staffRole_returns200AndAuditsActor(CapturedOutput output) throws Exception {
        when(vendorClient.requestQuote(any())).thenReturn(
                new CommissionQuote("Q-SEC-1", new BigDecimal("0.006"), new BigDecimal("3000.00")));
        String token = token("alice", Map.of("realm_access", Map.of("roles", List.of(SecurityConfig.STAFF_ROLE))));

        mvc.perform(quoteRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value("Q-SEC-1"));

        assertThat(output.getOut()).contains("quote.generated quoteId=Q-SEC-1").contains("actor=alice");
    }

    @Test
    void health_noToken_returns200() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder quoteRequest() {
        return post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY);
    }

    /** Registers a Keycloak-shaped token with the decoder and returns its value for the Authorization header. */
    private String token(String username, Map<String, Object> claims) {
        String value = username + "-token";
        Jwt jwt = Jwt.withTokenValue(value)
                .header("alg", "RS256")
                .subject("user-id-" + username)
                .claim("preferred_username", username)
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode(value)).thenReturn(jwt);
        return value;
    }
}

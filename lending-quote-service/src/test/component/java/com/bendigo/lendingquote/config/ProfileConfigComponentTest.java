package com.bendigo.lendingquote.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bendigo.lendingquote.LendingQuoteServiceApplication;
import com.bendigo.lendingquote.quote.client.VendorProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Configuration is code too: each environment profile is started for real, with the values the platform
 * would provide, to prove it wires up as intended and refuses to start when a required value is missing.
 */
class ProfileConfigComponentTest {

    private static final String VENDOR_URL = "--VENDOR_BASE_URL=https://vendor.example.com";
    private static final String VENDOR_KEY = "--VENDOR_API_KEY=key-from-secrets-manager";
    private static final String ISSUER = "--OIDC_ISSUER_URI=https://sso.example.com/realms/lending";

    @Test
    void devProfile_noEnvironmentVariables_startsAgainstLocalMockWithSecurityOff() {
        try (var app = start("dev", "--VENDOR_API_KEY=local-dev-only-key")) {
            assertThat(app.getBean(VendorProperties.class).getBaseUrl()).isEqualTo("http://localhost:8081");
            assertThat(app.getEnvironment().getProperty("app.security.enabled")).isEqualTo("false");
        }
    }

    @Test
    void stgProfile_allRequiredValues_startsWithSecurityOn() {
        try (var app = start("stg", VENDOR_URL, VENDOR_KEY, ISSUER)) {
            assertThat(app.getBean(VendorProperties.class).getBaseUrl()).isEqualTo("https://vendor.example.com");
            assertThat(app.getEnvironment().getProperty("app.security.enabled")).isEqualTo("true");
            assertThat(app.getBean(JwtDecoder.class)).isNotNull();
        }
    }

    @Test
    void stgProfile_missingVendorUrl_failsToStart() {
        assertThatThrownBy(() -> start("stg", VENDOR_KEY, ISSUER)).hasStackTraceContaining("VENDOR_BASE_URL");
    }

    @Test
    void stgProfile_missingVendorApiKey_failsToStart() {
        assertThatThrownBy(() -> start("stg", VENDOR_URL, ISSUER)).hasStackTraceContaining("VENDOR_API_KEY");
    }

    @Test
    void stgProfile_missingTokenIssuer_failsToStart() {
        assertThatThrownBy(() -> start("stg", VENDOR_URL, VENDOR_KEY)).hasStackTraceContaining("OIDC_ISSUER_URI");
    }

    @Test
    void prdProfile_missingVendorApiKey_failsToStart() {
        assertThatThrownBy(() -> start("prd", VENDOR_URL, ISSUER)).hasStackTraceContaining("VENDOR_API_KEY");
    }

    @Test
    void prdProfile_allRequiredValues_startsWithQuietLogsButFullAudit() {
        try (var app = start("prd", VENDOR_URL, VENDOR_KEY, ISSUER)) {
            Environment env = app.getEnvironment();
            assertThat(env.getProperty("app.security.enabled")).isEqualTo("true");
            assertThat(env.getProperty("logging.level.root")).isEqualTo("WARN");
            assertThat(env.getProperty("logging.level.audit")).isEqualTo("INFO");
        }
    }

    /**
     * Starts the real application with one profile. The arguments stand in for the environment variables the
     * platform would set; like those, they take precedence over application*.yml. Port 0 = any free port.
     */
    private static ConfigurableApplicationContext start(String profile, String... environment) {
        List<String> args = new ArrayList<>(List.of(environment));
        args.add("--server.port=0");
        return new SpringApplicationBuilder(LendingQuoteServiceApplication.class)
                .profiles(profile)
                .run(args.toArray(new String[0]));
    }
}

package com.bendigo.lendingquote.quote.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The app must refuse to start without a vendor api-key rather than fail on the first user request. */
class VendorPropertiesComponentTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("vendor.base-url=http://localhost:8081",
                    "vendor.connect-timeout=1s",
                    "vendor.read-timeout=3s");

    @Test
    void bind_missingApiKey_failsStartup() {
        context.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void bind_blankApiKey_failsStartup() {
        context.withPropertyValues("vendor.api-key= ").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void bind_allSettingsPresent_startsWithParsedTimeouts() {
        context.withPropertyValues("vendor.api-key=k").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(VendorProperties.class).getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
        });
    }

    @EnableConfigurationProperties(VendorProperties.class)
    static class Config {
    }
}

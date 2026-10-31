package com.bendigo.lendingquote.quote.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class VendorPropertiesTest {

    @Test
    void toString_anyProperties_doesNotContainApiKey() {
        var properties = new VendorProperties("http://vendor", "super-secret-key", Duration.ofSeconds(1), Duration.ofSeconds(3));

        assertThat(properties.toString()).contains("http://vendor").doesNotContain("super-secret-key");
    }
}

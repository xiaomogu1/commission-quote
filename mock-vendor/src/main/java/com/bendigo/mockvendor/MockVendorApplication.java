package com.bendigo.mockvendor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Stand-in for the third-party Commission Quote API, which the vendor is still building.
 *
 * Implements the agreed contract, enforces the api-key and randomly fails/slows down like a real
 * network dependency. It is a separate application on purpose: the BFF must treat it exactly as it
 * will treat the real vendor, so switching over is a base-url change.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MockVendorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockVendorApplication.class, args);
    }
}

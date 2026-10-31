package com.bendigo.mockvendor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.ToString;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Runtime configuration of the mock vendor. */
@Value
@Validated
@ConfigurationProperties(prefix = "mock-vendor")
public class MockVendorProperties {

    /** The single API key the mock accepts. */
    @NotBlank
    @ToString.Exclude
    String apiKey;

    /** Knobs that make the mock behave like an unreliable network dependency. */
    @NotNull
    @Valid
    Simulation simulation;

    @Value
    public static class Simulation {

        /** Probability (0..1) that a request fails with a simulated 500. */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        double failureRate;

        /** Lower bound of artificial latency added to each request. */
        @PositiveOrZero
        long minLatencyMs;

        /** Upper bound of artificial latency added to each request. */
        @PositiveOrZero
        long maxLatencyMs;
    }
}

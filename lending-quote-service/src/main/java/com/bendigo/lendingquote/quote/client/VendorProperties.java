package com.bendigo.lendingquote.quote.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.ToString;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for calling the vendor. Validated at startup: a missing api-key stops the app from starting.
 *
 * Where apiKey comes from is a configuration concern, not a code one: it is always the VENDOR_API_KEY
 * environment variable. In dev that comes from .env; in stg/prd the platform injects it from
 * AWS Secrets Manager (see application-stg.yml / application-prd.yml).
 * The key is only ever sent to the vendor: never to the browser, never logged.
 */
@Value
@Validated
@ConfigurationProperties(prefix = "vendor")
public class VendorProperties {

    @NotBlank(message = "is required: set VENDOR_BASE_URL")
    String baseUrl;

    /** Excluded from toString so the key can never end up in a log line. */
    @NotBlank(message = "is required: set VENDOR_API_KEY")
    @ToString.Exclude
    String apiKey;

    @NotNull
    Duration connectTimeout;

    @NotNull
    Duration readTimeout;
}

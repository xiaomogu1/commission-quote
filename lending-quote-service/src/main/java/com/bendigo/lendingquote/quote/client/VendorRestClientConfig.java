package com.bendigo.lendingquote.quote.client;

import com.bendigo.lendingquote.quote.exception.VendorTimeoutException;
import com.bendigo.lendingquote.quote.exception.VendorUnavailableException;
import com.bendigo.lendingquote.quote.exception.VendorRejectedException;
import com.bendigo.lendingquote.common.CorrelationIdInterceptor;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * How we talk to the vendor: where, with which credentials, how long we wait, and how each
 * kind of HTTP failure is translated. VendorClient only decides what to call.
 *
 * <pre>
 *   no response in time          -> VendorTimeoutException      (interceptor)
 *   connection error             -> VendorUnavailableException  (interceptor)
 *   5xx / 429                    -> VendorUnavailableException  (status handler)
 *   other 4xx                    -> VendorRejectedException     (status handler)
 * </pre>
 *
 * No automatic retry: creating a quote is a POST with no idempotency key in the vendor contract,
 * so retrying after a timeout could create a duplicate quote. The user retries explicitly instead.
 */
@Configuration
class VendorRestClientConfig {

    static final String API_KEY_HEADER = "api-key";

    private static final Logger log = LoggerFactory.getLogger(VendorRestClientConfig.class);

    @Bean
    RestClient vendorRestClient(RestClient.Builder builder, VendorProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(API_KEY_HEADER, properties.getApiKey())
                .requestFactory(requestFactory(properties))
                .requestInterceptor(new CorrelationIdInterceptor())
                .requestInterceptor(new VendorNetworkErrorInterceptor())
                .defaultStatusHandler(VendorRestClientConfig::isTransient, (request, response) -> {
                    String message = "Vendor unavailable (HTTP %d)".formatted(response.getStatusCode().value());
                    log.warn(message);
                    throw new VendorUnavailableException(message, null);
                })
                // Not logged here: QuoteExceptionHandler logs it once, at ERROR, with the stack trace.
                .defaultStatusHandler(VendorRestClientConfig::isRejected, (request, response) -> {
                    throw new VendorRejectedException(
                            "Vendor rejected the request (HTTP %d). 401/403: check the api-key; other 4xx: contract mismatch."
                                    .formatted(response.getStatusCode().value()));
                })
                .build();
    }

    /** Worth retrying: the vendor is down or busy. Mutually exclusive with {@link #isRejected}, so handler order doesn't matter. */
    static boolean isTransient(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value();
    }

    /** Not worth retrying: the vendor refused our request (401/403: our key; other 4xx: contract mismatch). */
    static boolean isRejected(HttpStatusCode status) {
        return status.is4xxClientError() && status.value() != HttpStatus.TOO_MANY_REQUESTS.value();
    }

    private static JdkClientHttpRequestFactory requestFactory(VendorProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                // The JDK client otherwise tries a h2c upgrade on plain http, which some servers handle badly.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }
}

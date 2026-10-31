package com.bendigo.e2e;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

/**
 * Starts the whole system from the root docker-compose.yml (real Docker images) with the given mock
 * vendor behaviour, and calls it over HTTP the way a client would. Nothing is mocked or stubbed.
 */
final class E2eStack {

    static final String UI = "quote-ui";
    static final String SERVICE = "lending-quote-service";
    static final String VENDOR = "mock-vendor";
    static final String QUOTE_PATH = "/api/commission-quotes";
    static final String VALID_QUOTE = """
            {"loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM"}""";

    private E2eStack() {
    }

    /** @param vendorSettings overrides for the mock vendor, e.g. VENDOR_FAILURE_RATE */
    static ComposeContainer start(Map<String, String> vendorSettings) {
        ComposeContainer stack = new ComposeContainer(new File("../docker-compose.yml"))
                .withBuild(true)
                .withEnv("VENDOR_API_KEY", "e2e-test-key")
                .withEnv("VENDOR_MIN_LATENCY_MS", "0")
                .withEnv("VENDOR_MAX_LATENCY_MS", "0")
                .withEnv(vendorSettings)
                .withExposedService(UI, 8080, Wait.forHttp("/").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(10)))
                .withExposedService(SERVICE, 8080, healthy())
                .withExposedService(VENDOR, 8081, healthy());
        return stack;
    }

    static ResponseEntity<String> post(ComposeContainer stack, String service, int port, String path, String body) {
        String baseUrl = "http://" + stack.getServiceHost(service, port) + ":" + stack.getServicePort(service, port);
        return RestClient.create(baseUrl)
                .post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (request, response) -> { /* assert on the status instead of throwing */ })
                .toEntity(String.class);
    }

    static ResponseEntity<String> get(ComposeContainer stack, String service, int port, String path) {
        String baseUrl = "http://" + stack.getServiceHost(service, port) + ":" + stack.getServicePort(service, port);
        return RestClient.create(baseUrl)
                .get().uri(path)
                .retrieve()
                .onStatus(status -> true, (request, response) -> { /* assert on the status instead of throwing */ })
                .toEntity(String.class);
    }

    static ResponseEntity<String> postQuote(ComposeContainer stack, String body) {
        return post(stack, SERVICE, 8080, QUOTE_PATH, body);
    }

    private static WaitStrategy healthy() {
        // The first run builds both images, which can take a few minutes.
        return Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(10));
    }
}

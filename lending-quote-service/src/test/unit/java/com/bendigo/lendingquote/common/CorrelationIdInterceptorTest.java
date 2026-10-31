package com.bendigo.lendingquote.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class CorrelationIdInterceptorTest {

    private final CorrelationIdInterceptor interceptor = new CorrelationIdInterceptor();
    private final MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://vendor"));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void intercept_correlationIdInMdc_setsHeader() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "abc-12345678");

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(CorrelationIdFilter.HEADER)).isEqualTo("abc-12345678");
    }

    @Test
    void intercept_noCorrelationId_addsNoHeader() throws Exception {
        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().containsKey(CorrelationIdFilter.HEADER)).isFalse();
    }
}

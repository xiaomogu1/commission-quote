package com.bendigo.lendingquote.common;

import java.io.IOException;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Propagates the current request's correlation id to downstream calls. */
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Optional.ofNullable(MDC.get(CorrelationIdFilter.MDC_KEY))
                .ifPresent(id -> request.getHeaders().set(CorrelationIdFilter.HEADER, id));
        return execution.execute(request, body);
    }
}

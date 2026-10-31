package com.bendigo.lendingquote.quote.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bendigo.lendingquote.quote.exception.VendorTimeoutException;
import com.bendigo.lendingquote.quote.exception.VendorUnavailableException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class VendorNetworkErrorInterceptorTest {

    private final VendorNetworkErrorInterceptor interceptor = new VendorNetworkErrorInterceptor();
    private final MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://vendor"));

    @Test
    void intercept_responseReceived_returnsResponse() {
        var response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);

        assertThat(interceptor.intercept(request, new byte[0], (req, body) -> response)).isSameAs(response);
    }

    @Test
    void intercept_readTimeout_throwsVendorTimeoutException() {
        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], failingWith(new HttpTimeoutException("slow"))))
                .isInstanceOf(VendorTimeoutException.class);
    }

    @Test
    void intercept_connectTimeout_throwsVendorTimeoutException() {
        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], failingWith(new HttpConnectTimeoutException("slow"))))
                .isInstanceOf(VendorTimeoutException.class);
    }

    @Test
    void intercept_connectionRefused_throwsVendorUnavailableException() {
        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], failingWith(new ConnectException("refused"))))
                .isInstanceOf(VendorUnavailableException.class);
    }

    private static ClientHttpRequestExecution failingWith(IOException e) {
        return (req, body) -> {
            throw e;
        };
    }
}

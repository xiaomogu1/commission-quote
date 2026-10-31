package com.bendigo.lendingquote.quote.client;

import com.bendigo.lendingquote.quote.exception.VendorTimeoutException;
import com.bendigo.lendingquote.quote.exception.VendorUnavailableException;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Translates network-level failures of a vendor call into our exceptions, by exception type. */
class VendorNetworkErrorInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(VendorNetworkErrorInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        try {
            return execution.execute(request, body);
        } catch (HttpTimeoutException e) {
            log.warn("Vendor call timed out");
            throw new VendorTimeoutException(e);
        } catch (IOException e) {
            log.warn("Vendor unreachable: {}", e.getMessage());
            throw new VendorUnavailableException("Vendor could not be reached", e);
        }
    }
}

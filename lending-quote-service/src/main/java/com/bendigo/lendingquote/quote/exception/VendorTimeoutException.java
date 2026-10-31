package com.bendigo.lendingquote.quote.exception;

/** The vendor did not respond within the configured timeout. Worth retrying. */
public class VendorTimeoutException extends VendorException {

    public VendorTimeoutException(Throwable cause) {
        super("Vendor did not respond in time", cause);
    }
}

package com.bendigo.lendingquote.quote.exception;

/** The vendor returned 5xx/429 or could not be reached. Usually transient, worth retrying. */
public class VendorUnavailableException extends VendorException {

    public VendorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

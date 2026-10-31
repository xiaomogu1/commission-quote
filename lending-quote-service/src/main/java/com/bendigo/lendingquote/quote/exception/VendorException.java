package com.bendigo.lendingquote.quote.exception;

/**
 * Base type for failures talking to the vendor. Subclasses say what the caller can do about it,
 * so the API layer can respond without knowing anything about the vendor's HTTP details.
 */
public abstract class VendorException extends RuntimeException {

    protected VendorException(String message, Throwable cause) {
        super(message, cause);
    }
}

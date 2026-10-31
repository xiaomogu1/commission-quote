package com.bendigo.lendingquote.quote.exception;

/**
 * The vendor returned a 4xx (other than 429). Retrying won't help: either our api-key is wrong (401/403)
 * or our request and their contract disagree (we validate input before calling). Needs investigation.
 */
public class VendorRejectedException extends VendorException {

    public VendorRejectedException(String message) {
        super(message, null);
    }
}

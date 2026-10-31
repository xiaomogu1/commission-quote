package com.bendigo.lendingquote.quote.api;

import com.bendigo.lendingquote.common.CorrelationIdFilter;
import com.bendigo.lendingquote.quote.exception.VendorRejectedException;
import com.bendigo.lendingquote.quote.exception.VendorTimeoutException;
import com.bendigo.lendingquote.quote.exception.VendorUnavailableException;
import com.bendigo.lendingquote.security.CurrentUser;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Turns failures into RFC 9457 problem+json for the React app, and audits every failed quote request.
 *
 * <pre>
 *   invalid input                                -> 400 with per-field errors (vendor is never called)
 *   VendorTimeoutException                       -> 504  retryable
 *   VendorUnavailableException                   -> 502  retryable
 *   VendorRejectedException, unreadable or
 *   invalid vendor body                          -> 502  not retryable (our config or contract problem)
 *   anything unexpected                          -> 500
 * </pre>
 *
 * Vendor details (status codes, messages) are logged, never returned: the browser only gets a safe
 * message plus the correlation id that support can use to find the logs.
 */
@RestControllerAdvice(assignableTypes = QuoteController.class)
class QuoteExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteExceptionHandler.class);
    private static final Logger audit = LoggerFactory.getLogger("audit");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid loan details", "Please correct the highlighted fields.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        // Malformed JSON or a value of the wrong type (e.g. riskBand "EXTREME"). The UI can't produce these, so
        // this is most likely a direct API call: log why, but keep the parser's details out of the response.
        log.warn("Unreadable quote request: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request body could not be read.");
    }

    @ExceptionHandler(VendorTimeoutException.class)
    ProblemDetail handleVendorTimeout(VendorTimeoutException ex) {
        return vendorFailure(ex, HttpStatus.GATEWAY_TIMEOUT, true, "Quote service timed out",
                "The quote service took too long to respond. Please try again.");
    }

    @ExceptionHandler(VendorUnavailableException.class)
    ProblemDetail handleVendorUnavailable(VendorUnavailableException ex) {
        return vendorFailure(ex, HttpStatus.BAD_GATEWAY, true, "Quote service unavailable",
                "We couldn't get a quote right now. Please try again.");
    }

    /**
     * Not retryable. ConstraintViolationException comes from method validation of the vendor's quote
     * (VendorClient is the only @Validated bean); RestClientException from a body that couldn't be parsed.
     */
    @ExceptionHandler({VendorRejectedException.class, ConstraintViolationException.class, RestClientException.class})
    ProblemDetail handleVendorFailed(RuntimeException ex) {
        log.error("Vendor call failed", ex);
        return vendorFailure(ex, HttpStatus.BAD_GATEWAY, false, "Quote service error",
                "We couldn't get a quote. Please contact support and quote the reference below.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error while creating a quote", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "Something went wrong. Please contact support and quote the reference below.");
    }

    private static ProblemDetail vendorFailure(RuntimeException ex, HttpStatus status, boolean retryable,
                                               String title, String detail) {
        audit.info("quote.failed reason={} actor={}", ex.getClass().getSimpleName(), CurrentUser.name());
        ProblemDetail problem = problem(status, title, detail);
        problem.setProperty("retryable", retryable);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }
}

package com.bendigo.lendingquote.quote.client;

import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import com.bendigo.lendingquote.quote.exception.VendorUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;

/**
 * Requests a commission quote from the vendor.
 *
 * Failures are handled outside this class: HTTP and network errors by the RestClient configured in
 * {@link VendorRestClientConfig}; an unusable 2xx body by Spring method validation of the returned quote
 * (ConstraintViolationException) or by Jackson (RestClientException); a vendor that keeps failing by the
 * "vendor" circuit breaker (configured in application.yml).
 */
@Component
@Validated
public class VendorClient {

    static final String QUOTES_PATH = "/v1/commission-quotes";

    private final RestClient vendorRestClient;
    private final VendorQuoteMapper mapper;

    public VendorClient(RestClient vendorRestClient, VendorQuoteMapper mapper) {
        this.vendorRestClient = vendorRestClient;
        this.mapper = mapper;
    }

    @CircuitBreaker(name = "vendor", fallbackMethod = "circuitOpen")
    public @NotNull @Valid CommissionQuote requestQuote(LoanDetails loan) {
        VendorResponse response = vendorRestClient.post()
                .uri(QUOTES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.toVendorRequest(loan))
                .retrieve()
                .body(VendorResponse.class);
        return mapper.toDomain(response);
    }

    /** Called instead of the vendor while the circuit is open: fail fast with the same error as a vendor outage. */
    private CommissionQuote circuitOpen(LoanDetails loan, CallNotPermittedException e) {
        throw new VendorUnavailableException("Vendor circuit is open; not calling the vendor", e);
    }
}

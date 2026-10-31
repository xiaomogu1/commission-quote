package com.bendigo.lendingquote.quote.service;

import com.bendigo.lendingquote.quote.client.VendorClient;
import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import com.bendigo.lendingquote.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Requests a quote and records it in the audit log. Failures are audited by the API exception handler,
 * which every failure passes through.
 *
 * The audit log records who asked (the staff member from the JWT) and deliberately excludes the loan
 * amount (customer financial data).
 */
@Service
public class QuoteService {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final VendorClient client;

    public QuoteService(VendorClient client) {
        this.client = client;
    }

    public CommissionQuote requestQuote(LoanDetails loan) {
        CommissionQuote quote = client.requestQuote(loan);
        audit.info("quote.generated quoteId={} riskBand={} loanTermInMonths={} actor={}",
                quote.getQuoteId(), loan.getRiskBand(), loan.getLoanTermInMonths(), CurrentUser.name());
        return quote;
    }
}

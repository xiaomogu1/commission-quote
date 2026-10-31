package com.bendigo.lendingquote.quote.service;

import com.bendigo.lendingquote.quote.client.VendorClient;
import com.bendigo.lendingquote.quote.exception.VendorTimeoutException;
import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import com.bendigo.lendingquote.quote.domain.RiskBand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuoteServiceTest {

    private static final LoanDetails LOAN = new LoanDetails(new BigDecimal("500000"), 360, RiskBand.MEDIUM);

    private final VendorClient client = mock(VendorClient.class);
    private final QuoteService service = new QuoteService(client);

    @Test
    void requestQuote_vendorReturnsQuote_returnsQuote() {
        var quote = new CommissionQuote("Q-1", new BigDecimal("0.006"), new BigDecimal("3000.00"));
        when(client.requestQuote(LOAN)).thenReturn(quote);

        assertThat(service.requestQuote(LOAN)).isEqualTo(quote);
    }

    @Test
    void requestQuote_vendorTimesOut_throwsVendorTimeoutException() {
        when(client.requestQuote(LOAN)).thenThrow(new VendorTimeoutException(null));

        assertThatThrownBy(() -> service.requestQuote(LOAN))
                .isInstanceOf(VendorTimeoutException.class);
    }
}

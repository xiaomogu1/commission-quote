package com.bendigo.lendingquote.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import com.bendigo.lendingquote.quote.domain.RiskBand;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class QuoteApiMapperTest {

    private final QuoteApiMapper mapper = Mappers.getMapper(QuoteApiMapper.class);

    @Test
    void toLoanDetails_validRequest_returnsLoanDetails() {
        var loan = mapper.toLoanDetails(new QuoteRequest(new BigDecimal("250000.50"), 120, RiskBand.LOW));

        assertThat(loan).isEqualTo(new LoanDetails(new BigDecimal("250000.50"), 120, RiskBand.LOW));
    }

    @Test
    void toResponse_commissionQuote_returnsQuoteResponse() {
        var response = mapper.toResponse(new CommissionQuote("Q-9", new BigDecimal("0.007"), new BigDecimal("1750.00")));

        assertThat(response).isEqualTo(new QuoteResponse("Q-9", new BigDecimal("0.007"), new BigDecimal("1750.00")));
    }
}

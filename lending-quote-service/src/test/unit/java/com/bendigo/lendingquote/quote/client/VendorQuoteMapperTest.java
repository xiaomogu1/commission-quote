package com.bendigo.lendingquote.quote.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import com.bendigo.lendingquote.quote.domain.RiskBand;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class VendorQuoteMapperTest {

    private final VendorQuoteMapper mapper = Mappers.getMapper(VendorQuoteMapper.class);

    @Test
    void toVendorRequest_loanDetails_returnsRequestWithRiskBandName() {
        var request = mapper.toVendorRequest(new LoanDetails(new BigDecimal("500000.00"), 360, RiskBand.HIGH));

        assertThat(request).isEqualTo(new VendorRequest(new BigDecimal("500000.00"), 360, "HIGH"));
    }

    @Test
    void toDomain_vendorResponse_returnsCommissionQuote() {
        var quote = mapper.toDomain(new VendorResponse("Q-1", new BigDecimal("0.006"), new BigDecimal("3000.00")));

        assertThat(quote).isEqualTo(new CommissionQuote("Q-1", new BigDecimal("0.006"), new BigDecimal("3000.00")));
    }
}

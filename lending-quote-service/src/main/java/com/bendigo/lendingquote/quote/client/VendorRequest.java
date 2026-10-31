package com.bendigo.lendingquote.quote.client;

import java.math.BigDecimal;
import lombok.Value;

/** Vendor wire format (request). Only used inside this package, so contract changes stay here. */
@Value
class VendorRequest {
    BigDecimal loanAmount;
    int loanTermInMonths;
    String riskBand;
}

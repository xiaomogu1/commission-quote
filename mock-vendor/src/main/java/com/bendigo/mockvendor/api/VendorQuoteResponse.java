package com.bendigo.mockvendor.api;

import java.math.BigDecimal;
import lombok.Value;

/** Response body of the vendor contract. commissionRate is a fraction: 0.006 = 0.60%. */
@Value
public class VendorQuoteResponse {
    String quoteId;
    BigDecimal commissionRate;
    BigDecimal totalCommission;
}

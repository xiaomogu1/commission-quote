package com.bendigo.lendingquote.quote.client;

import java.math.BigDecimal;
import lombok.Value;

/** Vendor wire format (response). Unknown extra fields from the vendor are ignored. */
@Value
class VendorResponse {
    String quoteId;
    BigDecimal commissionRate;
    BigDecimal totalCommission;
}

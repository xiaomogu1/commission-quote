package com.bendigo.lendingquote.quote.api;

import java.math.BigDecimal;
import lombok.Value;

/** Response of POST /api/commission-quotes. commissionRate is a fraction: 0.006 = 0.60%. */
@Value
public class QuoteResponse {
    String quoteId;
    BigDecimal commissionRate;
    BigDecimal totalCommission;
}

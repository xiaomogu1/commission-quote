package com.bendigo.lendingquote.quote.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Value;

/**
 * A commission quote in our own model, independent of the vendor's wire format.
 * The constraints are the invariants of a quote we are willing to show to staff;
 * a vendor response that breaks them is rejected.
 */
@Value
public class CommissionQuote {

    @NotBlank
    String quoteId;

    /** Fraction, e.g. 0.006 = 0.60%. */
    @NotNull
    @PositiveOrZero
    BigDecimal commissionRate;

    @NotNull
    @PositiveOrZero
    BigDecimal totalCommission;
}

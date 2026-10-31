package com.bendigo.lendingquote.quote.api;

import com.bendigo.lendingquote.quote.domain.RiskBand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Value;

/**
 * Body of POST /api/commission-quotes.
 *
 * Validated here so bad input is rejected with a 400 before we ever call the vendor.
 * Messages are written to be shown next to the form field.
 */
@Value
public class QuoteRequest {

    @NotNull(message = "is required")
    @DecimalMin(value = "1000.00", message = "must be at least $1,000")
    @DecimalMax(value = "5000000.00", message = "must be at most $5,000,000")
    @Digits(integer = 7, fraction = 2, message = "must have at most 2 decimal places")
    BigDecimal loanAmount;

    @NotNull(message = "is required")
    @Min(value = 1, message = "must be at least 1 month")
    @Max(value = 360, message = "must be at most 360 months")
    Integer loanTermInMonths;

    @NotNull(message = "is required")
    RiskBand riskBand;
}

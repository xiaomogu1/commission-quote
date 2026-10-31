package com.bendigo.mockvendor.api;

import com.bendigo.mockvendor.domain.RiskBand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Value;

/** Request body of the vendor contract. Ranges are our assumptions (see README "Assumptions"). */
@Value
public class VendorQuoteRequest {

    @NotNull
    @DecimalMin("1000.00")
    @DecimalMax("5000000.00")
    @Digits(integer = 7, fraction = 2)
    BigDecimal loanAmount;

    @NotNull
    @Min(1)
    @Max(360)
    Integer loanTermInMonths;

    @NotNull
    RiskBand riskBand;
}

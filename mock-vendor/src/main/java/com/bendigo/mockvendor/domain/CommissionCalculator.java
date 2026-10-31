package com.bendigo.mockvendor.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Value;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the vendor's pricing engine. The real rules belong to the vendor, so this is
 * deliberately simple: the risk band's rate applied to the loan amount.
 *
 * Money is BigDecimal end to end; the total is rounded half-up to cents.
 */
@Component
public class CommissionCalculator {

    public Commission calculate(BigDecimal loanAmount, RiskBand riskBand) {
        BigDecimal rate = riskBand.getCommissionRate();
        BigDecimal total = loanAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new Commission(rate, total);
    }

    @Value
    public static class Commission {
        BigDecimal rate;
        BigDecimal total;
    }
}

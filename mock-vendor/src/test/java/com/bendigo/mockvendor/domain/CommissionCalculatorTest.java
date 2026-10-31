package com.bendigo.mockvendor.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommissionCalculatorTest {

    private final CommissionCalculator calculator = new CommissionCalculator();

    @ParameterizedTest(name = "{0} at {1} -> rate {2}, total {3}")
    @CsvSource({
            "500000.00, LOW,    0.0070, 3500.00",
            "500000.00, MEDIUM, 0.0060, 3000.00",
            "500000.00, HIGH,   0.0050, 2500.00",
    })
    void calculate_eachRiskBand_returnsBandRateAndTotal(BigDecimal amount, RiskBand band, BigDecimal expectedRate, BigDecimal expectedTotal) {
        var result = calculator.calculate(amount, band);

        assertThat(result.getRate()).isEqualByComparingTo(expectedRate);
        assertThat(result.getTotal()).isEqualByComparingTo(expectedTotal);
    }

    @ParameterizedTest(name = "{0} at {1} -> {2}")
    @CsvSource({
            "1234.56, MEDIUM, 7.41",  // 7.40736 -> rounds up
            "1000.75, MEDIUM, 6.00",  // 6.0045  -> rounds down
            "1035.00, LOW,    7.25",  // 7.245   -> exact half rounds UP (banker's rounding would give 7.24)
    })
    void calculate_fractionalCents_roundsHalfUpToCents(BigDecimal amount, RiskBand band, BigDecimal expectedTotal) {
        var result = calculator.calculate(amount, band);

        assertThat(result.getTotal()).isEqualByComparingTo(expectedTotal);
        assertThat(result.getTotal().scale()).isEqualTo(2);
    }
}

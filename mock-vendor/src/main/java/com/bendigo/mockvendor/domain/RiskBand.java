package com.bendigo.mockvendor.domain;

import java.math.BigDecimal;

/**
 * The vendor's risk bands and the (assumed) commission rate for each, as a fraction (0.006 = 0.60%).
 * Higher risk earns a lower commission.
 */
public enum RiskBand {
    LOW("0.0070"),
    MEDIUM("0.0060"),
    HIGH("0.0050");

    private final BigDecimal commissionRate;

    RiskBand(String commissionRate) {
        this.commissionRate = new BigDecimal(commissionRate);
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }
}

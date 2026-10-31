package com.bendigo.lendingquote.quote.domain;

import java.math.BigDecimal;
import lombok.Value;

/** What we ask a quote for. Already validated at the API boundary. */
@Value
public class LoanDetails {
    BigDecimal loanAmount;
    int loanTermInMonths;
    RiskBand riskBand;
}

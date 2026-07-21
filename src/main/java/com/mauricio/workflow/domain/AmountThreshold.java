package com.mauricio.workflow.domain;

import java.math.BigDecimal;
import java.util.Currency;

public record AmountThreshold(
        BigDecimal max,
        Currency currency
) implements Rule {

    public AmountThreshold {
        if (max == null || max.signum() <= 0){
            throw new IllegalArgumentException(
                    "max must be positive");
        }
        if (currency == null){
            throw new IllegalArgumentException("currency is required");
        }
    }
}

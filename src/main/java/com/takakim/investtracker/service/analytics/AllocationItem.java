package com.takakim.investtracker.service.analytics;

import java.math.BigDecimal;
import java.util.Objects;

public record AllocationItem(
        String category,
        BigDecimal marketValue,
        BigDecimal percentage,
        BigDecimal costBasis,
        BigDecimal unrealizedGainLoss
) {
    public AllocationItem {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(marketValue, "marketValue must not be null");
        Objects.requireNonNull(percentage, "percentage must not be null");
        Objects.requireNonNull(costBasis, "costBasis must not be null");
        Objects.requireNonNull(unrealizedGainLoss, "unrealizedGainLoss must not be null");
    }
}

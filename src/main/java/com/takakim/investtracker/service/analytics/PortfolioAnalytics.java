package com.takakim.investtracker.service.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PortfolioAnalytics(
        UUID portfolioId,
        Instant asOf,
        String baseCurrency,
        BigDecimal totalCurrentValue,
        BigDecimal totalCostBasis,
        BigDecimal totalUnrealizedGainLoss,
        BigDecimal totalUnrealizedReturnPercentage,
        BigDecimal totalRealizedGainLoss,
        BigDecimal totalCashValue,
        List<AllocationItem> byAssetClass,
        List<AllocationItem> byCurrency,
        List<AllocationItem> byAccount,
        List<HoldingExposure> topHoldings,
        List<String> warnings
) {
    public PortfolioAnalytics {
        Objects.requireNonNull(portfolioId, "portfolioId must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(totalCurrentValue, "totalCurrentValue must not be null");
        Objects.requireNonNull(totalCostBasis, "totalCostBasis must not be null");
        Objects.requireNonNull(totalUnrealizedGainLoss, "totalUnrealizedGainLoss must not be null");
        Objects.requireNonNull(totalUnrealizedReturnPercentage, "totalUnrealizedReturnPercentage must not be null");
        Objects.requireNonNull(totalRealizedGainLoss, "totalRealizedGainLoss must not be null");
        Objects.requireNonNull(totalCashValue, "totalCashValue must not be null");
        byAssetClass = byAssetClass != null ? List.copyOf(byAssetClass) : List.of();
        byCurrency = byCurrency != null ? List.copyOf(byCurrency) : List.of();
        byAccount = byAccount != null ? List.copyOf(byAccount) : List.of();
        topHoldings = topHoldings != null ? List.copyOf(topHoldings) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}

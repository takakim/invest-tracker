package com.takakim.investtracker.service.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate portfolio performance result produced by PerformanceEngine.
 * All monetary values are expressed in the portfolio's base currency.
 * When market prices are unavailable (Phase 6), cost-basis is used as the
 * portfolio valuation proxy for TWR/MWR; the {@code valuationBasis} field
 * indicates this explicitly.
 */
public record PerformanceResult(
        UUID portfolioId,
        Instant asOf,
        String returnMethod,
        BigDecimal twrReturn,
        BigDecimal twrAnnualized,
        BigDecimal mwrReturn,
        BigDecimal totalRealizedGainLoss,
        BigDecimal totalDividendIncome,
        BigDecimal totalInterestIncome,
        BigDecimal totalFees,
        BigDecimal totalTaxes,
        BigDecimal totalNetIncome,
        BigDecimal totalCostBasis,
        BigDecimal totalNetDeposits,
        String currency,
        String valuationBasis,
        List<AccountPerformanceSummary> byAccount
) {
    public PerformanceResult {
        Objects.requireNonNull(portfolioId, "Portfolio ID must not be null");
        Objects.requireNonNull(asOf, "As-of date must not be null");
        Objects.requireNonNull(returnMethod, "Return method must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        totalNetDeposits = totalNetDeposits != null ? totalNetDeposits : BigDecimal.ZERO;
        byAccount = byAccount != null ? List.copyOf(byAccount) : List.of();
    }

    public PerformanceResult(
            UUID portfolioId,
            Instant asOf,
            String returnMethod,
            BigDecimal twrReturn,
            BigDecimal twrAnnualized,
            BigDecimal mwrReturn,
            BigDecimal totalRealizedGainLoss,
            BigDecimal totalDividendIncome,
            BigDecimal totalInterestIncome,
            BigDecimal totalFees,
            BigDecimal totalTaxes,
            BigDecimal totalNetIncome,
            BigDecimal totalCostBasis,
            String currency,
            String valuationBasis,
            List<AccountPerformanceSummary> byAccount
    ) {
        this(
                portfolioId,
                asOf,
                returnMethod,
                twrReturn,
                twrAnnualized,
                mwrReturn,
                totalRealizedGainLoss,
                totalDividendIncome,
                totalInterestIncome,
                totalFees,
                totalTaxes,
                totalNetIncome,
                totalCostBasis,
                BigDecimal.ZERO,
                currency,
                valuationBasis,
                byAccount
        );
    }
}

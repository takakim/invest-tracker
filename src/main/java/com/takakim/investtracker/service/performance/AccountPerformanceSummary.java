package com.takakim.investtracker.service.performance;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-account performance breakdown within a portfolio performance result.
 */
public record AccountPerformanceSummary(
        UUID accountId,
        String accountName,
        BigDecimal realizedGainLoss,
        BigDecimal dividendIncome,
        BigDecimal interestIncome,
        BigDecimal fees,
        BigDecimal taxes,
        BigDecimal costBasis,
        String currency
) {}

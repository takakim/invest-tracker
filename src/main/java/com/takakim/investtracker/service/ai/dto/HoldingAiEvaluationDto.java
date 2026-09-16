package com.takakim.investtracker.service.ai.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public record HoldingAiEvaluationDto(
        UUID instrumentId,
        String symbol,
        String name,
        String assetClass,
        BigDecimal quantity,
        BigDecimal currentPrice,
        BigDecimal averageCostBasis,
        BigDecimal unrealizedGainLoss,
        Double unrealizedGainLossPercentage,
        Double portfolioWeightPercentage,
        AiStance stance,
        int riskScore,
        AiRiskLevel riskLevel,
        String executiveSummary,
        List<String> strengths,
        List<String> risks,
        String holdingVsSellingTradeoff,
        HoldingFinancialMetricsDto fundamentalMetrics,
        String modelUsed,
        Instant evaluatedAt
) {
    public boolean isStale() {
        return evaluatedAt != null && evaluatedAt.isBefore(Instant.now().minus(7, ChronoUnit.DAYS));
    }
}

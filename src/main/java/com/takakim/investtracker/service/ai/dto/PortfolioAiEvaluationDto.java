package com.takakim.investtracker.service.ai.dto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public record PortfolioAiEvaluationDto(
        UUID portfolioId,
        String portfolioName,
        String baseCurrency,
        int overallRiskScore,
        AiRiskLevel overallRiskLevel,
        String executiveSummary,
        String diversificationAssessment,
        List<String> concentrationRisks,
        List<String> taxAndLocationOptimization,
        List<String> topRecommendations,
        List<String> macroStressScenarios,
        List<HoldingAiEvaluationDto> topHoldingEvaluations,
        String modelUsed,
        Instant evaluatedAt
) {
    public boolean isStale() {
        return evaluatedAt != null && evaluatedAt.isBefore(Instant.now().minus(7, ChronoUnit.DAYS));
    }
}

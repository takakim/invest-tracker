package com.takakim.investtracker.service.ai.dto;

public record HoldingFinancialMetricsDto(
        Double peRatio,
        Double forwardPe,
        Double pegRatio,
        Double priceToBook,
        Double dividendYield,
        Double debtToEquity,
        Double returnOnEquity,
        Double fiftyTwoWeekHigh,
        Double fiftyTwoWeekLow,
        Double marketCap,
        Double expenseRatio,
        String assetClass,
        String currency
) {
}

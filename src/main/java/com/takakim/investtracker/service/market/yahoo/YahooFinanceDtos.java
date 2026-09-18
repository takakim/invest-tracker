package com.takakim.investtracker.service.market.yahoo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

public final class YahooFinanceDtos {

    private YahooFinanceDtos() {}

    // ── /v7/finance/quote endpoint DTOs ─────────────────────────────────────────

    /**
     * Yahoo Finance wraps every numeric value as {"raw": 1.23, "fmt": "1.23"}.
     * We only need the raw numeric value.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YahooValue(Double raw) {}

    /**
     * Top-level response from {@code /v7/finance/quote}.
     * This endpoint does not require a crumb/cookie and returns
     * rich fundamental data for equities and ETFs.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteResponse(QuoteWrapper quoteResponse) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteWrapper(
            List<QuoteResult> result,
            Object error
    ) {}

    /**
     * Per-symbol fundamental data returned by {@code /v7/finance/quote}.
     * Fields present depend on the instrument type (EQUITY, ETF, etc.).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteResult(
            // Valuation multiples
            Double trailingPE,
            Double forwardPE,
            Double priceToBook,
            Double trailingAnnualDividendYield,  // as a fraction (e.g. 0.005 = 0.5%)
            // Growth / quality
            Double epsTrailingTwelveMonths,
            Double epsForward,
            Double bookValue,
            // Balance sheet / risk
            // (debtToEquity and returnOnEquity are not in /v7/quote; sourced from AI fallback)
            // Market data
            Double marketCap,
            Double fiftyTwoWeekHigh,
            Double fiftyTwoWeekLow,
            Double trailingAnnualDividendRate
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartResponse(
            ChartWrapper chart
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartWrapper(
            List<ChartEntry> result,
            ChartError error
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartEntry(
            ChartMeta meta,
            List<Long> timestamp,
            ChartIndicators indicators,
            ChartEvents events
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartEvents(
            java.util.Map<String, DividendEvent> dividends,
            java.util.Map<String, SplitEvent> splits
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DividendEvent(
            BigDecimal amount,
            Long date
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SplitEvent(
            Long date,
            BigDecimal numerator,
            BigDecimal denominator,
            String splitRatio
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartMeta(
            String currency,
            String symbol,
            String exchangeName,
            String fullExchangeName,
            String instrumentType,
            BigDecimal regularMarketPrice,
            BigDecimal chartPreviousClose,
            BigDecimal previousClose,
            Long regularMarketTime
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartIndicators(
            List<QuoteIndicator> quote
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteIndicator(
            List<BigDecimal> open,
            List<BigDecimal> high,
            List<BigDecimal> low,
            List<BigDecimal> close,
            List<Long> volume
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChartError(
            String code,
            String description
    ) {}
}

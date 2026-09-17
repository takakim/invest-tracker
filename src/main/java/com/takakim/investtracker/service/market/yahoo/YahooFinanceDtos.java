package com.takakim.investtracker.service.market.yahoo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

public final class YahooFinanceDtos {

    private YahooFinanceDtos() {}

    // ── QuoteSummary endpoint DTOs ────────────────────────────────────────────

    /**
     * Yahoo Finance wraps every numeric value as {"raw": 1.23, "fmt": "1.23"}.
     * We only need the raw numeric value.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YahooValue(Double raw) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteSummaryResponse(QuoteSummaryWrapper quoteSummary) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteSummaryWrapper(
            List<QuoteSummaryResult> result,
            ChartError error
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteSummaryResult(
            DefaultKeyStatistics defaultKeyStatistics,
            SummaryDetail summaryDetail
    ) {}

    /**
     * Populated from Yahoo's {@code defaultKeyStatistics} module.
     * Key multiples: forwardPE, pegRatio, priceToBook, debtToEquity, returnOnEquity,
     * fiftyTwoWeekHigh, fiftyTwoWeekLow, enterpriseValue, trailingEps, forwardEps.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DefaultKeyStatistics(
            YahooValue forwardPE,
            YahooValue pegRatio,
            YahooValue priceToBook,
            YahooValue returnOnEquity,
            YahooValue enterpriseValue,
            YahooValue trailingEps,
            YahooValue forwardEps,
            YahooValue enterpriseToRevenue,
            YahooValue enterpriseToEbitda
    ) {}

    /**
     * Populated from Yahoo's {@code summaryDetail} module.
     * Key multiples: trailingPE, dividendYield, fiftyTwoWeekHigh, fiftyTwoWeekLow,
     * marketCap, beta, debtToEquity.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SummaryDetail(
            YahooValue trailingPE,
            YahooValue dividendYield,
            YahooValue fiftyTwoWeekHigh,
            YahooValue fiftyTwoWeekLow,
            YahooValue marketCap,
            YahooValue beta,
            YahooValue debtToEquity
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

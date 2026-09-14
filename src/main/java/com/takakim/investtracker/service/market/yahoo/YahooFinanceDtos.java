package com.takakim.investtracker.service.market.yahoo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

public final class YahooFinanceDtos {

    private YahooFinanceDtos() {}

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

package com.takakim.investtracker.service.benchmark;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BenchmarkComparisonResult(
        UUID portfolioId,
        UUID benchmarkInstrumentId,
        String benchmarkName,
        String benchmarkTicker,
        Instant periodStart,
        Instant periodEnd,
        BigDecimal portfolioReturn,
        BigDecimal benchmarkReturn,
        BigDecimal excessReturn,
        BigDecimal annualizedPortfolioReturn,
        BigDecimal annualizedBenchmarkReturn,
        BigDecimal annualizedExcessReturn,
        boolean outperforming,
        String baseCurrency,
        List<String> warnings
) {
    public BenchmarkComparisonResult {
        Objects.requireNonNull(portfolioId, "portfolioId must not be null");
        Objects.requireNonNull(benchmarkInstrumentId, "benchmarkInstrumentId must not be null");
        Objects.requireNonNull(benchmarkName, "benchmarkName must not be null");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        Objects.requireNonNull(portfolioReturn, "portfolioReturn must not be null");
        Objects.requireNonNull(benchmarkReturn, "benchmarkReturn must not be null");
        Objects.requireNonNull(excessReturn, "excessReturn must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}

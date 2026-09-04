package com.takakim.investtracker.service.benchmark;

import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.performance.PerformanceEngine;
import com.takakim.investtracker.service.performance.PerformanceResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(noRollbackFor = {ResourceNotFoundException.class})
public class BenchmarkEngine {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final PortfolioRepository portfolioRepository;
    private final InstrumentRepository instrumentRepository;
    private final TransactionRepository transactionRepository;
    private final MarketDataService marketDataService;
    private final MarketObservationRepository marketObservationRepository;
    private final FxRateService fxRateService;
    private final PerformanceEngine performanceEngine;

    public BenchmarkEngine(
            PortfolioRepository portfolioRepository,
            InstrumentRepository instrumentRepository,
            TransactionRepository transactionRepository,
            MarketDataService marketDataService,
            MarketObservationRepository marketObservationRepository,
            FxRateService fxRateService,
            PerformanceEngine performanceEngine) {
        this.portfolioRepository = portfolioRepository;
        this.instrumentRepository = instrumentRepository;
        this.transactionRepository = transactionRepository;
        this.marketDataService = marketDataService;
        this.marketObservationRepository = marketObservationRepository;
        this.fxRateService = fxRateService;
        this.performanceEngine = performanceEngine;
    }

    public List<Instrument> getAvailableBenchmarks() {
        return instrumentRepository.findAll();
    }

    public BenchmarkComparisonResult comparePortfolioToBenchmark(
            UUID portfolioId,
            UUID benchmarkInstrumentId,
            String period,
            Instant asOf) {

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        Instrument benchmark = instrumentRepository.findById(benchmarkInstrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Benchmark instrument not found: " + benchmarkInstrumentId));

        Currency baseCurrency = portfolio.getBaseCurrency();
        Instant periodEnd = asOf != null ? asOf : Instant.now();
        List<String> warnings = new ArrayList<>();

        // 1. Determine period window
        List<Transaction> txs = transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)
                .stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .sorted((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()))
                .toList();

        Instant earliestTxDate = txs.isEmpty() ? periodEnd.minus(30, ChronoUnit.DAYS) : txs.get(0).getTradeDate();
        Instant periodStart = calculatePeriodStart(period, periodEnd, earliestTxDate);

        long days = ChronoUnit.DAYS.between(periodStart, periodEnd);
        if (days <= 0) {
            days = 1;
        }

        // 2. Fetch portfolio performance
        PerformanceResult performance = performanceEngine.calculate(portfolioId);
        BigDecimal pRet = performance.twrReturn() != null ? performance.twrReturn() : performance.mwrReturn();
        BigDecimal portfolioReturn = pRet != null
                ? pRet.setScale(SCALE, ROUNDING)
                : BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal annualizedPortfolioReturn = performance.twrAnnualized() != null
                ? performance.twrAnnualized().setScale(SCALE, ROUNDING)
                : portfolioReturn;

        // 3. Fetch benchmark prices at periodStart and periodEnd
        if (marketDataService.getObservationCount(benchmarkInstrumentId) < 10) {
            try {
                marketDataService.backfillHistoricalPrices(benchmarkInstrumentId, periodStart, periodEnd);
            } catch (Exception ignored) {
            }
        }

        PriceQuote endQuote;
        try {
            endQuote = marketDataService.getLatestPrice(benchmarkInstrumentId, periodEnd);
        } catch (ResourceNotFoundException e) {
            endQuote = resolveHistoricalQuote(benchmark, periodEnd);
        }
        if (endQuote.warning() != null) {
            warnings.add("Benchmark end price: " + endQuote.warning());
        }

        PriceQuote startQuote = resolveHistoricalQuote(benchmark, periodStart);
        if (startQuote.warning() != null) {
            warnings.add("Benchmark start price: " + startQuote.warning());
        }

        // 4. Multi-currency conversion to portfolio base currency
        Money startMoney = new Money(startQuote.price(), benchmark.getCurrency());
        Money startInBase = fxRateService.convert(startMoney, baseCurrency, periodStart);

        Money endMoney = new Money(endQuote.price(), benchmark.getCurrency());
        Money endInBase = fxRateService.convert(endMoney, baseCurrency, periodEnd);

        // 5. Benchmark return calculation
        BigDecimal startPriceBase = startInBase.amount();
        BigDecimal endPriceBase = endInBase.amount();

        BigDecimal benchmarkReturn;
        if (startPriceBase.compareTo(BigDecimal.ZERO) > 0) {
            benchmarkReturn = endPriceBase.subtract(startPriceBase)
                    .divide(startPriceBase, 6, ROUNDING)
                    .setScale(SCALE, ROUNDING);
        } else {
            benchmarkReturn = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        // Annualized benchmark return
        BigDecimal annualizedBenchmarkReturn;
        if (days >= 365 && startPriceBase.compareTo(BigDecimal.ZERO) > 0) {
            double rDouble = benchmarkReturn.doubleValue();
            double ann = Math.pow(1.0 + rDouble, 365.0 / days) - 1.0;
            annualizedBenchmarkReturn = BigDecimal.valueOf(ann).setScale(SCALE, ROUNDING);
        } else {
            annualizedBenchmarkReturn = benchmarkReturn;
        }

        // 6. Excess Return (Alpha)
        BigDecimal excessReturn = portfolioReturn.subtract(benchmarkReturn).setScale(SCALE, ROUNDING);
        BigDecimal annualizedExcessReturn = annualizedPortfolioReturn.subtract(annualizedBenchmarkReturn).setScale(SCALE, ROUNDING);
        boolean outperforming = excessReturn.compareTo(BigDecimal.ZERO) >= 0;

        return new BenchmarkComparisonResult(
                portfolioId,
                benchmarkInstrumentId,
                benchmark.getName(),
                benchmark.getTicker() != null ? benchmark.getTicker() : "—",
                periodStart,
                periodEnd,
                portfolioReturn,
                benchmarkReturn,
                excessReturn,
                annualizedPortfolioReturn,
                annualizedBenchmarkReturn,
                annualizedExcessReturn,
                outperforming,
                baseCurrency.code(),
                warnings
        );
    }

    private Instant calculatePeriodStart(String period, Instant periodEnd, Instant earliestTxDate) {
        if (period == null || period.equalsIgnoreCase("ALL")) {
            return earliestTxDate;
        }
        return switch (period.toUpperCase()) {
            case "1M" -> periodEnd.minus(30, ChronoUnit.DAYS);
            case "3M" -> periodEnd.minus(90, ChronoUnit.DAYS);
            case "6M" -> periodEnd.minus(180, ChronoUnit.DAYS);
            case "1Y" -> periodEnd.minus(365, ChronoUnit.DAYS);
            case "YTD" -> periodEnd.atZone(ZoneOffset.UTC).withDayOfYear(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            default -> earliestTxDate;
        };
    }

    private PriceQuote resolveHistoricalQuote(Instrument instrument, Instant targetTime) {
        List<MarketObservation> obs = marketObservationRepository
                .findByInstrumentIdAndObservedAtBetweenOrderByObservedAtAsc(
                        instrument.getId(),
                        targetTime.minus(7, ChronoUnit.DAYS),
                        targetTime.plus(7, ChronoUnit.DAYS)
                );

        if (!obs.isEmpty()) {
            MarketObservation closest = obs.get(0);
            long minDiff = Math.abs(ChronoUnit.SECONDS.between(closest.getObservedAt(), targetTime));
            for (MarketObservation o : obs) {
                long diff = Math.abs(ChronoUnit.SECONDS.between(o.getObservedAt(), targetTime));
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = o;
                }
            }
            return new PriceQuote(
                    instrument.getId(),
                    closest.getPrice(),
                    closest.getCurrency(),
                    closest.getObservedAt(),
                    closest.getSourceType(),
                    closest.getSourceReference(),
                    false,
                    null
            );
        }

        try {
            return marketDataService.getLatestPrice(instrument.getId(), targetTime);
        } catch (ResourceNotFoundException e) {
            return new PriceQuote(
                    instrument.getId(),
                    BigDecimal.ONE,
                    instrument.getCurrency().code(),
                    targetTime,
                    com.takakim.investtracker.domain.ObservationSourceType.PROVIDER,
                    "FALLBACK_PROXY",
                    true,
                    "Historical benchmark quote unavailable at " + targetTime
            );
        }
    }
}

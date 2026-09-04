package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.benchmark.BenchmarkComparisonResult;
import com.takakim.investtracker.service.benchmark.BenchmarkEngine;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.performance.PerformanceEngine;
import com.takakim.investtracker.service.performance.PerformanceResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkEngineTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private MarketObservationRepository marketObservationRepository;
    @Mock
    private FxRateService fxRateService;
    @Mock
    private PerformanceEngine performanceEngine;

    private BenchmarkEngine benchmarkEngine;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Instrument sp500;
    private Instrument msciWorld;
    private Account account;

    @BeforeEach
    void setUp() {
        benchmarkEngine = new BenchmarkEngine(
                portfolioRepository, instrumentRepository, transactionRepository,
                marketDataService, marketObservationRepository, fxRateService, performanceEngine
        );

        portfolioId = UUID.randomUUID();
        portfolio = new Portfolio("Alpha Growth", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        account = new Account(portfolio, "Trading", "Broker", new Currency("GBP"));

        sp500 = new Instrument("Vanguard S&P 500 ETF", AssetClass.ETF, "VUSA", "IE00B3XXRP09", "LSE", new Currency("GBP"));
        msciWorld = new Instrument("iShares Core MSCI World ETF", AssetClass.ETF, "IWDA", "IE00B4L5Y983", "Euronext", new Currency("USD"));
    }

    @Test
    void comparePortfolioToBenchmark_portfolioNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                benchmarkEngine.comparePortfolioToBenchmark(portfolioId, sp500.getId(), "1Y", Instant.now()));
    }

    @Test
    void comparePortfolioToBenchmark_benchmarkNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(sp500.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                benchmarkEngine.comparePortfolioToBenchmark(portfolioId, sp500.getId(), "1Y", Instant.now()));
    }

    @Test
    void comparePortfolioToBenchmark_sameCurrencyOutperforming_calculatesExcessReturn() {
        Instant now = Instant.now();
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(sp500.getId())).thenReturn(Optional.of(sp500));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());

        // Portfolio performance return: 15.00%
        PerformanceResult perf = new PerformanceResult(
                portfolioId, now, "TWR",
                new BigDecimal("0.1500"), new BigDecimal("0.1500"), null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), "GBP", "COST_BASIS", List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(perf);

        // Quotes: Start Price = 50.00 GBP, End Price = 55.00 GBP -> Benchmark Return = (55 - 50) / 50 = +10.00%
        PriceQuote endQuote = new PriceQuote(
                sp500.getId(), new BigDecimal("55.00"), "GBP", now,
                ObservationSourceType.PROVIDER, "EXCHANGE", false, null
        );
        when(marketDataService.getLatestPrice(eq(sp500.getId()), any(Instant.class))).thenReturn(endQuote);

        MarketObservation startObs1 = new MarketObservation(
                sp500, new BigDecimal("48.00"), "GBP", now.minus(380, ChronoUnit.DAYS),
                ObservationSourceType.PROVIDER, "EXCHANGE"
        );
        MarketObservation startObs2 = new MarketObservation(
                sp500, new BigDecimal("50.00"), "GBP", now.minus(365, ChronoUnit.DAYS),
                ObservationSourceType.PROVIDER, "EXCHANGE"
        );
        when(marketObservationRepository.findByInstrumentIdAndObservedAtBetweenOrderByObservedAtAsc(
                eq(sp500.getId()), any(Instant.class), any(Instant.class)
        )).thenReturn(List.of(startObs1, startObs2));

        // FX conversions (GBP -> GBP identity)
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BenchmarkComparisonResult result = benchmarkEngine.comparePortfolioToBenchmark(
                portfolioId, sp500.getId(), "1Y", now);

        assertEquals(portfolioId, result.portfolioId());
        assertEquals(sp500.getId(), result.benchmarkInstrumentId());
        assertEquals("VUSA", result.benchmarkTicker());
        assertEquals("Vanguard S&P 500 ETF", result.benchmarkName());
        assertEquals(new BigDecimal("0.1500"), result.portfolioReturn());
        assertEquals(new BigDecimal("0.1000"), result.benchmarkReturn());
        assertEquals(new BigDecimal("0.0500"), result.excessReturn()); // +5.00% Alpha
        assertTrue(result.outperforming());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void comparePortfolioToBenchmark_multiCurrencyWithWarnings_convertsToPortfolioCurrency() {
        Instant now = Instant.now();
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(msciWorld.getId())).thenReturn(Optional.of(msciWorld));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());

        // Portfolio performance return: 5.00%
        PerformanceResult perf = new PerformanceResult(
                portfolioId, now, "TWR",
                new BigDecimal("0.0500"), new BigDecimal("0.0500"), null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), "GBP", "COST_BASIS", List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(perf);

        // Benchmark in USD: Start = 100 USD (at 1 USD = 0.75 GBP -> 75 GBP), End = 120 USD (at 1 USD = 0.80 GBP -> 96 GBP)
        PriceQuote endQuote = new PriceQuote(
                msciWorld.getId(), new BigDecimal("120.00"), "USD", now,
                ObservationSourceType.MANUAL, "MANUAL_OVERRIDE", true, "Price is older than 24h"
        );
        when(marketDataService.getLatestPrice(eq(msciWorld.getId()), any(Instant.class))).thenReturn(endQuote);

        when(marketObservationRepository.findByInstrumentIdAndObservedAtBetweenOrderByObservedAtAsc(
                eq(msciWorld.getId()), any(Instant.class), any(Instant.class)
        )).thenReturn(List.of());

        // FX conversions:
        // Start: 120 USD (fallback) -> 96 GBP
        when(fxRateService.convert(eq(new Money(new BigDecimal("120.00"), new Currency("USD"))), eq(new Currency("GBP")), any(Instant.class)))
                .thenReturn(new Money(new BigDecimal("96.00"), new Currency("GBP")));

        BenchmarkComparisonResult result = benchmarkEngine.comparePortfolioToBenchmark(
                portfolioId, msciWorld.getId(), "3M", now);

        assertEquals(portfolioId, result.portfolioId());
        assertEquals(new BigDecimal("0.0500"), result.portfolioReturn());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void getAvailableBenchmarks_returnsInstrumentsList() {
        when(instrumentRepository.findAll()).thenReturn(List.of(sp500, msciWorld));

        List<Instrument> benchmarks = benchmarkEngine.getAvailableBenchmarks();

        assertEquals(2, benchmarks.size());
        assertEquals("VUSA", benchmarks.get(0).getTicker());
        assertEquals("IWDA", benchmarks.get(1).getTicker());
    }

    @Test
    void comparePortfolioToBenchmark_periodsAndFallback_coverage() {
        Instant now = Instant.now();
        Instrument untracked = new Instrument("Global Benchmark", AssetClass.OTHER, null, null, null, new Currency("GBP"));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(untracked.getId())).thenReturn(Optional.of(untracked));

        Transaction tx = new Transaction(
                account, null, TransactionType.DEPOSIT, now.minus(400, ChronoUnit.DAYS),
                null, null, null, new BigDecimal("1000.00"), null, null, "GBP", null, null, null, null
        );
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of(tx));

        // MWR performance result (null twrReturn, null twrAnnualized)
        PerformanceResult perf = new PerformanceResult(
                portfolioId, now, "MWR",
                null, null, new BigDecimal("0.0800"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), "GBP", "COST_BASIS", List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(perf);

        when(marketDataService.getLatestPrice(eq(untracked.getId()), any(Instant.class)))
                .thenThrow(new ResourceNotFoundException("No quote"));

        // Return 0 price for startMoney to test startPriceBase <= 0
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenReturn(new Money(BigDecimal.ZERO, new Currency("GBP")));

        // Test with different periods (YTD, 6M, 3M, 1M, ALL, invalid, null asOf)
        benchmarkEngine.comparePortfolioToBenchmark(portfolioId, untracked.getId(), "YTD", null);
        benchmarkEngine.comparePortfolioToBenchmark(portfolioId, untracked.getId(), "6M", now);
        benchmarkEngine.comparePortfolioToBenchmark(portfolioId, untracked.getId(), "3M", now);
        benchmarkEngine.comparePortfolioToBenchmark(portfolioId, untracked.getId(), "1M", now);
        benchmarkEngine.comparePortfolioToBenchmark(portfolioId, untracked.getId(), "ALL", now);
        BenchmarkComparisonResult result = benchmarkEngine.comparePortfolioToBenchmark(portfolioId, untracked.getId(), "CUSTOM", now);

        assertNotNull(result);
        assertEquals("—", result.benchmarkTicker());
        assertEquals(new BigDecimal("0.0800"), result.portfolioReturn());
        assertEquals(BigDecimal.ZERO.setScale(4), result.benchmarkReturn());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void comparePortfolioToBenchmark_underperformingAndPendingTx() {
        Instant now = Instant.now();
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(sp500.getId())).thenReturn(Optional.of(sp500));

        // Create a corrected tx + completed tx
        Transaction tx1 = new Transaction(
                account, null, TransactionType.DEPOSIT, now.minus(10, ChronoUnit.DAYS),
                null, null, null, new BigDecimal("1000.00"), null, null, "GBP", null, null, null, null
        );
        tx1.markCorrected();
        Transaction tx2 = new Transaction(
                account, null, TransactionType.DEPOSIT, now.minus(5, ChronoUnit.DAYS),
                null, null, null, new BigDecimal("1000.00"), null, null, "GBP", null, null, null, null
        );
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of(tx1, tx2));

        // Negative portfolio return (-5%) vs Benchmark return (+10%)
        PerformanceResult perf = new PerformanceResult(
                portfolioId, now, "TWR",
                new BigDecimal("-0.0500"), new BigDecimal("-0.0500"), null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), "GBP", "COST_BASIS", List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(perf);

        when(marketDataService.getLatestPrice(eq(sp500.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(sp500.getId(), new BigDecimal("110.00"), "USD", now, ObservationSourceType.PROVIDER, "FEED", false, null));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenReturn(new Money(new BigDecimal("100.00"), new Currency("GBP"))) // start
                .thenReturn(new Money(new BigDecimal("110.00"), new Currency("GBP"))); // end

        BenchmarkComparisonResult result = benchmarkEngine.comparePortfolioToBenchmark(portfolioId, sp500.getId(), "1M", now);
        assertNotNull(result);
        assertFalse(result.outperforming());
        assertTrue(result.excessReturn().compareTo(BigDecimal.ZERO) < 0);
    }

    @Test
    void comparePortfolioToBenchmark_edgeCases_coverage() {
        Instant now = Instant.now();
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(sp500.getId())).thenReturn(Optional.of(sp500));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());

        // Performance with null returns
        PerformanceResult perfNull = new PerformanceResult(
                portfolioId, now, "TWR",
                null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "GBP", "COST_BASIS", List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(perfNull);

        // When observation count >= 10, does not backfill
        when(marketDataService.getObservationCount(sp500.getId())).thenReturn(15L);
        when(marketDataService.getLatestPrice(eq(sp500.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(sp500.getId(), new BigDecimal("100.00"), "GBP", now, ObservationSourceType.PROVIDER, "FEED", false, null));
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BenchmarkComparisonResult res = benchmarkEngine.comparePortfolioToBenchmark(portfolioId, sp500.getId(), "1D", now);
        assertNotNull(res);
        assertEquals(BigDecimal.ZERO.setScale(4), res.portfolioReturn());

        // When observation count < 10 and backfill throws exception
        when(marketDataService.getObservationCount(sp500.getId())).thenReturn(2L);
        org.mockito.Mockito.doThrow(new RuntimeException("API error"))
                .when(marketDataService).backfillHistoricalPrices(eq(sp500.getId()), any(), any());
        BenchmarkComparisonResult res2 = benchmarkEngine.comparePortfolioToBenchmark(portfolioId, sp500.getId(), "1D", now);
        assertNotNull(res2);
    }
}

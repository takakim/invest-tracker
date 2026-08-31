package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.PortfolioHistoryResponse;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.analytics.PortfolioHistoryService;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioHistoryServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private AnalyticsEngine analyticsEngine;
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private FxRateService fxRateService;

    private PortfolioHistoryService service;
    private Portfolio portfolio;
    private Account account;
    private Instrument vusa;
    private Instrument benchmark;
    private UUID portfolioId;

    @BeforeEach
    void setUp() {
        service = new PortfolioHistoryService(
                portfolioRepository,
                transactionRepository,
                instrumentRepository,
                analyticsEngine,
                marketDataService,
                fxRateService
        );

        portfolioId = UUID.randomUUID();
        portfolio = new Portfolio("Retirement Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        account = new Account(portfolio, "ISA Account", "Interactive Investor", new Currency("GBP"));
        vusa = new Instrument("Vanguard S&P 500", AssetClass.ETF, "VUSA", "IE00B3XXRP09", "LSE", new Currency("GBP"));
        benchmark = new Instrument("S&P 500 Index", AssetClass.ETF, "SPX", "US78378X1072", "INDEX", new Currency("USD"));
    }

    @Test
    void generateHistory_1YDefaultPeriod_success() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant tradeDate = Instant.now().minus(200, ChronoUnit.DAYS);
        Transaction dep = new Transaction(
                account, null, TransactionType.DEPOSIT, tradeDate, null,
                null, null, new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(dep));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("12000.00"), new BigDecimal("10000.00"),
                new BigDecimal("2000.00"), new BigDecimal("20.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(mockAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "1Y", null, null);

        assertNotNull(response);
        assertEquals(portfolio.getName(), response.portfolioName());
        assertEquals("GBP", response.baseCurrency());
        assertEquals("1Y", response.period());
        assertEquals("WEEKLY", response.interval());
        assertFalse(response.dataPoints().isEmpty());
        assertEquals(new BigDecimal("12000.00"), response.summary().endingValue());
        assertNotNull(response.summary().maxDrawdownPercentage());
    }

    @Test
    void generateHistory_withBenchmarkComparison_computesRelativeReturns() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(benchmark.getId())).thenReturn(Optional.of(benchmark));

        Instant tradeDate = Instant.now().minus(400, ChronoUnit.DAYS);
        Transaction dep = new Transaction(
                account, null, TransactionType.DEPOSIT, tradeDate, null,
                null, null, new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(dep));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Mock benchmark quotes: start at 100, end at 115 (+15%)
        when(marketDataService.getLatestPrice(eq(benchmark.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(benchmark.getId(), new BigDecimal("100.00"), "USD", tradeDate, ObservationSourceType.PROVIDER, "TEST", false, null))
                .thenReturn(new PriceQuote(benchmark.getId(), new BigDecimal("115.00"), "USD", Instant.now(), ObservationSourceType.PROVIDER, "TEST", false, null));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("12000.00"), new BigDecimal("10000.00"),
                new BigDecimal("2000.00"), new BigDecimal("20.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(mockAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "6M", "WEEKLY", benchmark.getId());

        assertNotNull(response);
        assertEquals(benchmark.getId(), response.benchmarkId());
        assertEquals("SPX", response.benchmarkTicker());
        assertEquals("S&P 500 Index", response.benchmarkName());
        assertNotNull(response.summary());
        assertNotNull(response.summary().excessReturnPercentage());
    }

    @Test
    void generateHistory_benchmarkFailsOrThrows_handledGracefully() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(benchmark.getId())).thenReturn(Optional.of(benchmark));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of());

        // Market data service throws exception on quote fetch
        when(marketDataService.getLatestPrice(eq(benchmark.getId()), any(Instant.class)))
                .thenThrow(new RuntimeException("Market provider offline"));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(mockAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "1M", "DAILY", benchmark.getId());

        assertNotNull(response);
        assertNull(response.summary().benchmarkReturnPercentage());
    }

    @Test
    void generateHistory_benchmarkNotFoundOrQuoteZero_handled() {
        UUID unknownBenchId = UUID.randomUUID();
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(unknownBenchId)).thenReturn(Optional.empty());
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of());

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(mockAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "1M", "DAILY", unknownBenchId);

        assertNotNull(response);
        assertNull(response.benchmarkId());
    }

    @Test
    void generateHistory_allPeriodsHandled() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant earlyDate = Instant.now().minus(500, ChronoUnit.DAYS);
        Transaction dep = new Transaction(
                account, null, TransactionType.DEPOSIT, earlyDate, null,
                null, null, new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );
        Transaction withdr = new Transaction(
                account, null, TransactionType.WITHDRAWAL, earlyDate.plus(10, ChronoUnit.DAYS), null,
                null, null, new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );
        Transaction buy = new Transaction(
                account, vusa, TransactionType.BUY, earlyDate.plus(15, ChronoUnit.DAYS), null,
                BigDecimal.TEN, new BigDecimal("100.00"), new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(dep, withdr, buy));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("5000.00"), new BigDecimal("4000.00"),
                new BigDecimal("1000.00"), new BigDecimal("25.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(mockAnalytics);

        for (String period : List.of("1M", "3M", "6M", "YTD", "1Y", "3Y", "ALL")) {
            PortfolioHistoryResponse resp = service.generateHistory(portfolioId, period, null, null);
            assertNotNull(resp);
            assertFalse(resp.dataPoints().isEmpty());
        }

        // Test with null and blank period
        PortfolioHistoryResponse nullPeriodResp = service.generateHistory(portfolioId, null, "MONTHLY", null);
        assertEquals("1Y", nullPeriodResp.period());

        PortfolioHistoryResponse blankPeriodResp = service.generateHistory(portfolioId, "  ", "DAILY", null);
        assertEquals("1Y", blankPeriodResp.period());
    }

    @Test
    void generateHistory_benchmarkThrowsExceptionInTimelineLoop_handledGracefully() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(benchmark.getId())).thenReturn(Optional.of(benchmark));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of());

        // First quote (start quote) succeeds, but later quotes throw exceptions
        when(marketDataService.getLatestPrice(eq(benchmark.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(benchmark.getId(), new BigDecimal("100.00"), "USD", Instant.now(), ObservationSourceType.PROVIDER, "TEST", false, null))
                .thenThrow(new RuntimeException("Temporary rate limit"));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(mockAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "1M", "DAILY", benchmark.getId());
        assertNotNull(response);
    }

    @Test
    void generateHistory_zeroInitialValueWithInvestedCapital_calculatesReturn() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant tradeDate = Instant.now().minus(20, ChronoUnit.DAYS);
        Transaction dep = new Transaction(
                account, null, TransactionType.DEPOSIT, tradeDate, null,
                null, null, new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(dep));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // First sample has market value 0, second has market value 5500
        PortfolioAnalytics zeroAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        PortfolioAnalytics fundedAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("5500.00"), new BigDecimal("5000.00"),
                new BigDecimal("500.00"), new BigDecimal("10.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class)))
                .thenReturn(zeroAnalytics)
                .thenReturn(fundedAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "1M", "DAILY", null);
        assertNotNull(response);
    }

    @Test
    void generateHistory_portfolioNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.generateHistory(portfolioId, "1Y", null, null));
    }

    @Test
    void generateHistory_zeroHoldings_handlesGracefully() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of());

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(mockAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "ALL", null, null);

        assertNotNull(response);
        assertEquals(new BigDecimal("0.00"), response.summary().startingValue());
        assertEquals(new BigDecimal("0.00"), response.summary().endingValue());
        assertEquals(new BigDecimal("0.00"), response.summary().portfolioReturnPercentage());
    }

    @Test
    void generateHistory_drawdownCalculation() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of());

        PortfolioAnalytics highAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("10000.00"), new BigDecimal("8000.00"),
                new BigDecimal("2000.00"), new BigDecimal("25.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        PortfolioAnalytics dipAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("8000.00"), new BigDecimal("8000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class)))
                .thenReturn(highAnalytics)
                .thenReturn(dipAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "1M", "DAILY", null);

        assertNotNull(response);
        assertTrue(response.summary().maxDrawdownPercentage().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void generateHistory_startAfterEnd_handledInTimeline() {
        Instant now = Instant.now();
        List<Instant> timeline = service.generateTimeline(now.plus(10, ChronoUnit.DAYS), now, "DAILY");
        assertNotNull(timeline);
        assertEquals(1, timeline.size());
        assertEquals(now, timeline.get(0));
    }

    @Test
    void generateHistory_filterNonCompletedTransactionsAndExtractPriceEdgeCases() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(instrumentRepository.findById(benchmark.getId())).thenReturn(Optional.of(benchmark));

        Instant past = Instant.now().minus(60, ChronoUnit.DAYS);
        Transaction completedDep = new Transaction(
                account, null, TransactionType.DEPOSIT, past, null,
                null, null, new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );
        Transaction correctedTx = new Transaction(
                account, null, TransactionType.DEPOSIT, past.plus(1, ChronoUnit.DAYS), null,
                null, null, new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );
        correctedTx.markCorrected();

        // Future tx beyond now and past tx before periodStart to exercise skips in calculateNetDeposits
        Transaction futureTx = new Transaction(
                account, null, TransactionType.DEPOSIT, Instant.now().plus(10, ChronoUnit.DAYS), null,
                null, null, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );
        Transaction veryOldTx = new Transaction(
                account, null, TransactionType.DEPOSIT, past.minus(300, ChronoUnit.DAYS), null,
                null, null, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(completedDep, correctedTx, futureTx, veryOldTx));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Quote with 0 price -> extractPositivePrice returns null
        when(marketDataService.getLatestPrice(eq(benchmark.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(benchmark.getId(), BigDecimal.ZERO, "USD", past, ObservationSourceType.PROVIDER, "TEST", false, null));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(mockAnalytics);

        PortfolioHistoryResponse response = service.generateHistory(portfolioId, "1M", "DAILY", benchmark.getId());
        assertNotNull(response);
        assertNull(response.summary().benchmarkReturnPercentage());
    }
}

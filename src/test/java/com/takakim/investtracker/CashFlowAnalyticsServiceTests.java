package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.CashFlowAnalyticsResponse;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.CashFlowAnalyticsService;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.currency.FxRateService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
class CashFlowAnalyticsServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AnalyticsEngine analyticsEngine;
    @Mock
    private FxRateService fxRateService;

    private CashFlowAnalyticsService service;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Account accountGbp;
    private Account accountUsd;
    private com.takakim.investtracker.domain.Instrument instrument;

    @BeforeEach
    void setUp() {
        service = new CashFlowAnalyticsService(
                portfolioRepository,
                accountRepository,
                transactionRepository,
                analyticsEngine,
                fxRateService
        );

        portfolio = new Portfolio("Master Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        portfolioId = portfolio.getId();
        accountGbp = new Account(portfolio, "Freetrade ISA", "Freetrade", new Currency("GBP"));
        accountUsd = new Account(portfolio, "Interactive Brokers GIA", "IBKR", new Currency("USD"));
        instrument = new com.takakim.investtracker.domain.Instrument("Apple", com.takakim.investtracker.domain.AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
    }

    private Transaction createTx(Account account, TransactionType type, Instant tradeDate, BigDecimal amount, String currency) {
        return createTx(account, null, type, tradeDate, amount, currency);
    }

    private Transaction createTx(Account account, com.takakim.investtracker.domain.Instrument inst, TransactionType type, Instant tradeDate, BigDecimal amount, String currency) {
        return new Transaction(
                account, inst, type, tradeDate, null,
                null, null, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                currency, null, null, null, null
        );
    }

    @Test
    void calculateCashFlows_portfolioNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.calculateCashFlows(portfolioId, "ALL", "MONTH")
        );
    }

    @Test
    void calculateCashFlows_emptyTransactions_returnsZeroSummary() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "ALL", "MONTH");

        assertNotNull(response);
        assertEquals(portfolioId, response.portfolioId());
        assertEquals("GBP", response.baseCurrency());
        assertEquals(new BigDecimal("0.00"), response.summary().totalDeposits());
        assertEquals(new BigDecimal("0.00"), response.summary().totalWithdrawals());
        assertEquals(new BigDecimal("0.00"), response.summary().netContributions());
        assertEquals(0, response.summary().activeContributionMonths());
        assertEquals(new BigDecimal("0.00"), response.summary().capitalContributionsPercentage());
        assertEquals(new BigDecimal("0.00"), response.summary().marketGrowthPercentage());
        assertEquals(1, response.accountBreakdown().size());
        assertEquals(new BigDecimal("0.00"), response.accountBreakdown().get(0).currentCashBalance());
    }

    @Test
    void calculateCashFlows_depositsAndWithdrawals_calculatesSavingsRateAndAttribution() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant t1 = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant t2 = ZonedDateTime.of(2026, 2, 20, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant t3 = ZonedDateTime.of(2026, 2, 25, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();

        Transaction tx1 = createTx(accountGbp, TransactionType.DEPOSIT, t1, new BigDecimal("5000.00"), "GBP");
        Transaction tx2 = createTx(accountGbp, TransactionType.DEPOSIT, t2, new BigDecimal("3000.00"), "GBP");
        Transaction tx3 = createTx(accountGbp, TransactionType.WITHDRAWAL, t3, new BigDecimal("1000.00"), "GBP");

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx3, tx2, tx1));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));

        // Mock 1:1 GBP FX conversions
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Current valuation: 10,000 GBP (Net contributions = 7,000 GBP -> Growth = 3,000 GBP (30%), Capital = 70%)
        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", new BigDecimal("10000.00"), new BigDecimal("7000.00"),
                new BigDecimal("3000.00"), new BigDecimal("42.86"), BigDecimal.ZERO, new BigDecimal("7000.00"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "ALL", "MONTH");

        assertNotNull(response);
        var summary = response.summary();
        assertEquals(new BigDecimal("8000.00"), summary.totalDeposits());
        assertEquals(new BigDecimal("1000.00"), summary.totalWithdrawals());
        assertEquals(new BigDecimal("7000.00"), summary.netContributions());
        assertEquals(new BigDecimal("7000.00"), summary.netCashFlow());
        assertEquals(2, summary.activeContributionMonths()); // Jan and Feb
        assertEquals(new BigDecimal("3500.00"), summary.avgMonthlyContribution()); // 7000 / 2
        assertEquals(new BigDecimal("7000.00"), summary.cumulativeContributions());
        assertEquals(new BigDecimal("10000.00"), summary.currentPortfolioValue());
        assertEquals(new BigDecimal("70.00"), summary.capitalContributionsPercentage());
        assertEquals(new BigDecimal("30.00"), summary.marketGrowthPercentage());

        // Verify periodic breakdown contains data points
        assertFalse(response.periods().isEmpty());

        // Verify account breakdown
        assertEquals(1, response.accountBreakdown().size());
        var accSummary = response.accountBreakdown().get(0);
        assertEquals(accountGbp.getId(), accSummary.accountId());
        assertEquals(new BigDecimal("7000.00"), accSummary.currentCashBalance());
        assertEquals(new BigDecimal("7000.00"), accSummary.netContributions());
    }

    @Test
    void calculateCashFlows_multiCurrencyAndInternalIncome_convertsProperly() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant t1 = ZonedDateTime.of(2026, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant t2 = ZonedDateTime.of(2026, 1, 20, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant t3 = ZonedDateTime.of(2026, 1, 22, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();

        // USD Deposit of 1000 USD (rate 0.80 -> 800 GBP)
        Transaction txUsdDeposit = createTx(accountUsd, TransactionType.DEPOSIT, t1, new BigDecimal("1000.00"), "USD");

        // Dividend of 100 GBP
        Transaction txDividend = createTx(accountGbp, instrument, TransactionType.DIVIDEND, t2, new BigDecimal("100.00"), "GBP");

        // Fee of 10 GBP
        Transaction txFee = createTx(accountGbp, TransactionType.FEE, t3, new BigDecimal("10.00"), "GBP");

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(txFee, txDividend, txUsdDeposit));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp, accountUsd));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> {
                    Money m = inv.getArgument(0);
                    if ("USD".equals(m.currency().code())) {
                        return new Money(m.amount().multiply(new BigDecimal("0.80")), new Currency("GBP"));
                    }
                    return m;
                });

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", new BigDecimal("890.00"), new BigDecimal("800.00"),
                new BigDecimal("90.00"), new BigDecimal("11.25"), BigDecimal.ZERO, new BigDecimal("890.00"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "ALL", "MONTH");

        assertNotNull(response);
        var summary = response.summary();
        assertEquals(new BigDecimal("800.00"), summary.totalDeposits());
        assertEquals(new BigDecimal("100.00"), summary.totalDividends());
        assertEquals(new BigDecimal("10.00"), summary.totalFees());
        assertEquals(new BigDecimal("800.00"), summary.netContributions());
        assertEquals(new BigDecimal("890.00"), summary.netCashFlow()); // 800 net contributions + 100 div - 10 fee
    }

    @Test
    void calculateCashFlows_groupingQuarterAndYear_createsCorrectBuckets() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant t1 = ZonedDateTime.of(2025, 3, 10, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant t2 = ZonedDateTime.of(2025, 7, 20, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant t3 = ZonedDateTime.of(2026, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();

        Transaction tx1 = createTx(accountGbp, TransactionType.DEPOSIT, t1, new BigDecimal("1000.00"), "GBP");
        Transaction tx2 = createTx(accountGbp, TransactionType.DEPOSIT, t2, new BigDecimal("2000.00"), "GBP");
        Transaction tx3 = createTx(accountGbp, TransactionType.DEPOSIT, t3, new BigDecimal("3000.00"), "GBP");

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx3, tx2, tx1));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Test QUARTER grouping
        CashFlowAnalyticsResponse qResponse = service.calculateCashFlows(portfolioId, "ALL", "QUARTER");
        assertNotNull(qResponse);
        assertEquals("QUARTER", qResponse.groupBy());
        assertTrue(qResponse.periods().stream().anyMatch(p -> p.periodLabel().contains("2025-Q1")));
        assertTrue(qResponse.periods().stream().anyMatch(p -> p.periodLabel().contains("2025-Q3")));

        // Test YEAR grouping
        CashFlowAnalyticsResponse yResponse = service.calculateCashFlows(portfolioId, "ALL", "YEAR");
        assertNotNull(yResponse);
        assertEquals("YEAR", yResponse.groupBy());
        assertTrue(yResponse.periods().stream().anyMatch(p -> p.periodLabel().equals("2025")));
        assertTrue(yResponse.periods().stream().anyMatch(p -> p.periodLabel().equals("2026")));
    }

    @Test
    void calculateCashFlows_periodFilters_1M_3M_6M_YTD_1Y() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of());
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of());

        for (String p : List.of("1M", "3M", "6M", "YTD", "1Y")) {
            CashFlowAnalyticsResponse res = service.calculateCashFlows(portfolioId, p, "MONTH");
            assertNotNull(res);
            assertEquals(p, res.period());
        }
    }

    @Test
    void calculateCashFlows_capitalOverTotalValue_capsAtOneHundredPercent() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant t1 = ZonedDateTime.of(2026, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Transaction txDeposit = createTx(accountGbp, TransactionType.DEPOSIT, t1, new BigDecimal("5000.00"), "GBP");

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(txDeposit));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Portfolio market value fell to 4,000 GBP (contributions = 5,000 GBP) -> Capital = 100%, Growth = 0%
        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", new BigDecimal("4000.00"), new BigDecimal("5000.00"),
                new BigDecimal("-1000.00"), new BigDecimal("-20.00"), BigDecimal.ZERO, new BigDecimal("4000.00"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "ALL", "MONTH");
        assertEquals(new BigDecimal("100.00"), response.summary().capitalContributionsPercentage());
        assertEquals(new BigDecimal("0.00"), response.summary().marketGrowthPercentage());
    }

    @Test
    void calculateCashFlows_filtersOutNonCompletedTransactions() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant t1 = ZonedDateTime.of(2026, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Transaction txCompleted = createTx(accountGbp, TransactionType.DEPOSIT, t1, new BigDecimal("1000.00"), "GBP");
        Transaction txCorrected = createTx(accountGbp, TransactionType.DEPOSIT, t1, new BigDecimal("500.00"), "GBP");
        txCorrected.markCorrected();

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(txCompleted, txCorrected));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "ALL", "MONTH");
        assertEquals(new BigDecimal("1000.00"), response.summary().totalDeposits());
    }

    @Test
    void calculateCashFlows_handlesInterestFeeBuySellAndNonCashTransactions() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant now = Instant.now();
        Instant t0 = now.minusSeconds(86400 * 45); // outside 1M
        Instant t1 = now.minusSeconds(86400 * 25); // inside 1M
        Instant t2 = now.minusSeconds(86400 * 20);
        Instant t3 = now.minusSeconds(86400 * 15);
        Instant t4 = now.minusSeconds(86400 * 10);
        Instant t5 = now.minusSeconds(86400 * 5);
        Instant t6 = now.minusSeconds(86400 * 2);

        // Transaction before period start to test boundary
        Transaction txOldDeposit = createTx(accountGbp, TransactionType.DEPOSIT, t0, new BigDecimal("2000.00"), "GBP");

        // Transactions in period: DEPOSIT, WITHDRAWAL, INTEREST, FEE, BUY, SELL, SPLIT
        Transaction txDeposit = createTx(accountGbp, TransactionType.DEPOSIT, t1, new BigDecimal("1000.00"), "GBP");
        Transaction txWithdrawal = createTx(accountGbp, TransactionType.WITHDRAWAL, t2, new BigDecimal("200.00"), "GBP");
        Transaction txInterest = createTx(accountGbp, TransactionType.INTEREST, t3, new BigDecimal("50.00"), "GBP");
        Transaction txFee = createTx(accountGbp, TransactionType.FEE, t4, new BigDecimal("15.00"), "GBP");
        Transaction txBuy = new Transaction(
                accountGbp, instrument, TransactionType.BUY, t5, null,
                new BigDecimal("5.00"), new BigDecimal("100.00"), new BigDecimal("500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", null, null, null, null
        );
        Transaction txSell = new Transaction(
                accountGbp, instrument, TransactionType.SELL, t6, null,
                new BigDecimal("2.00"), new BigDecimal("110.00"), new BigDecimal("220.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", null, null, null, null
        );
        Transaction txSplit = new Transaction(
                accountGbp, instrument, TransactionType.STOCK_SPLIT, t6, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(txSplit, txSell, txBuy, txFee, txInterest, txWithdrawal, txDeposit, txOldDeposit));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", new BigDecimal("3000.00"), new BigDecimal("2800.00"),
                new BigDecimal("200.00"), new BigDecimal("7.14"), BigDecimal.ZERO, new BigDecimal("3000.00"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        // Period 1M so txOldDeposit is before periodStart
        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "1M", "MONTH");
        assertNotNull(response);

        var summary = response.summary();
        assertEquals(new BigDecimal("1000.00"), summary.totalDeposits());
        assertEquals(new BigDecimal("200.00"), summary.totalWithdrawals());
        assertEquals(new BigDecimal("800.00"), summary.netContributions());
        assertEquals(new BigDecimal("50.00"), summary.totalInterest());
        assertEquals(new BigDecimal("15.00"), summary.totalFees());
        // netCashFlow = 800 + 50 - 15 = 835.00
        assertEquals(new BigDecimal("835.00"), summary.netCashFlow());

        // Account cash balance: 2000 (old) + 1000 (dep) - 200 (with) + 50 (int) - 15 (fee) - 500 (buy) + 220 (sell) = 2555.00
        assertEquals(new BigDecimal("2555.00"), response.accountBreakdown().get(0).currentCashBalance());
        assertEquals(new BigDecimal("800.00"), response.accountBreakdown().get(0).netContributions());
    }

    @Test
    void calculateCashFlows_analyticsEngineFailure_approximatesValuationAndSetsWarning() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE)).thenReturn(List.of());
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenThrow(new RuntimeException("Valuation timeout"));

        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "ALL", "MONTH");
        assertNotNull(response);
        assertEquals(new BigDecimal("0.00"), response.summary().currentPortfolioValue());
        assertTrue(response.warnings().contains("Portfolio live valuation was approximated: Valuation timeout"));
    }

    @Test
    void calculateCashFlows_analyticsEngineReturnsWarnings_propagatesWarnings() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE)).thenReturn(List.of());

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of("Market FX quote delayed")
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "ALL", "MONTH");
        assertNotNull(response);
        assertEquals(new BigDecimal("0.00"), response.summary().currentPortfolioValue());
        assertTrue(response.warnings().contains("Market FX quote delayed"));
    }

    @Test
    void calculateCashFlows_capitalAndGrowthEdgeCases() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant t1 = ZonedDateTime.of(2026, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        // Negative net contributions (withdrawal > deposit)
        Transaction txWithdrawal = createTx(accountGbp, TransactionType.WITHDRAWAL, t1, new BigDecimal("1000.00"), "GBP");

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(txWithdrawal));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Case 1: Portfolio value positive, contributions negative -> capital = 0%, growth = 100%
        PortfolioAnalytics analyticsPos = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", new BigDecimal("500.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analyticsPos);

        CashFlowAnalyticsResponse res1 = service.calculateCashFlows(portfolioId, "ALL", "MONTH");
        assertEquals(new BigDecimal("0.00"), res1.summary().capitalContributionsPercentage());
        assertEquals(new BigDecimal("100.00"), res1.summary().marketGrowthPercentage());

        // Case 2: Portfolio value <= 0, contributions negative -> capital = 0%, growth = 0%
        PortfolioAnalytics analyticsZero = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analyticsZero);

        CashFlowAnalyticsResponse res2 = service.calculateCashFlows(portfolioId, "ALL", "MONTH");
        assertEquals(new BigDecimal("0.00"), res2.summary().capitalContributionsPercentage());
        assertEquals(new BigDecimal("0.00"), res2.summary().marketGrowthPercentage());
    }

    @Test
    void calculateCashFlows_normalizesBlankAndInvalidPeriodAndGroupBy() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE)).thenReturn(List.of());

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        // Null and blank strings
        CashFlowAnalyticsResponse resNull = service.calculateCashFlows(portfolioId, null, null);
        assertEquals("ALL", resNull.period());
        assertEquals("MONTH", resNull.groupBy());

        CashFlowAnalyticsResponse resBlank = service.calculateCashFlows(portfolioId, "   ", "   ");
        assertEquals("ALL", resBlank.period());
        assertEquals("MONTH", resBlank.groupBy());

        // Invalid fallback
        CashFlowAnalyticsResponse resInvalid = service.calculateCashFlows(portfolioId, "INVALID_PERIOD", "INVALID_GROUPBY");
        assertEquals("ALL", resInvalid.period());
        assertEquals("MONTH", resInvalid.groupBy());
    }

    @Test
    void calculateCashFlows_futureTransactionGeneratesCurrentBucketFallback() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Transaction 30 days in the future so periodStart for "ALL" is after now
        Instant futureDate = Instant.now().plusSeconds(86400 * 30);
        Transaction txFuture = createTx(accountGbp, TransactionType.DEPOSIT, futureDate, new BigDecimal("100.00"), "GBP");

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(txFuture));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        CashFlowAnalyticsResponse response = service.calculateCashFlows(portfolioId, "ALL", "MONTH");
        assertNotNull(response);
        assertTrue(response.periods().stream().anyMatch(p -> "Current".equals(p.periodLabel())));
    }
}

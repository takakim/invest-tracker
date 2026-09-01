package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.DividendAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.HoldingDividendMetricResponse;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.DividendAnalyticsService;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
class DividendAnalyticsServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private FxRateService fxRateService;

    private DividendAnalyticsService dividendAnalyticsService;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Account account;
    private Instrument vusa;
    private Instrument aapl;

    @BeforeEach
    void setUp() {
        dividendAnalyticsService = new DividendAnalyticsService(
                portfolioRepository, positionRepository, transactionRepository,
                marketDataService, fxRateService
        );

        portfolioId = UUID.randomUUID();
        portfolio = new Portfolio("Income Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        account = new Account(portfolio, "ISA Account", "Freetrade", new Currency("GBP"));

        vusa = new Instrument("Vanguard S&P 500 ETF", AssetClass.ETF, "VUSA", "IE00B3XXRP09", "LSE", new Currency("GBP"));
        aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
    }

    @Test
    void calculate_portfolioNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> dividendAnalyticsService.calculate(portfolioId, Instant.now()));
    }

    @Test
    void calculate_emptyPortfolio_returnsZeroDividends() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE)).thenReturn(List.of());

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, Instant.now());

        assertNotNull(response);
        assertEquals(portfolioId, response.portfolioId());
        assertEquals("GBP", response.baseCurrency());
        assertEquals(new BigDecimal("0.0000"), response.totalDividendsAllTime());
        assertEquals(new BigDecimal("0.0000"), response.totalDividendsYtd());
        assertEquals(new BigDecimal("0.0000"), response.totalDividendsTtm());
        assertEquals(new BigDecimal("0.0000"), response.totalWithholdingTaxAllTime());
        assertEquals(new BigDecimal("0.0000"), response.projectedAnnualDividendIncome());
        assertEquals(new BigDecimal("0.00"), response.portfolioDividendYieldPercentage());
        assertEquals(new BigDecimal("0.00"), response.portfolioYieldOnCostPercentage());
        assertTrue(response.monthlyHistory().isEmpty());
        assertTrue(response.yearlyHistory().isEmpty());
        assertEquals(12, response.projectedMonthlyCalendar().size());
        assertTrue(response.holdings().isEmpty());
    }

    @Test
    void calculate_singleCurrencyDividendsAndHoldings_calculatesCorrectMetrics() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant divDate1 = Instant.parse("2026-06-15T10:00:00Z"); // YTD & TTM
        Instant divDate2 = Instant.parse("2026-03-15T10:00:00Z"); // YTD & TTM
        Instant divDate3 = Instant.parse("2025-05-15T10:00:00Z"); // Older than TTM (All Time only)

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Dividend 1: Gross 50.00, Tax 5.00, Fee 0, Net 45.00 GBP on 100 shares
        Transaction tx1 = new Transaction(
                account, vusa, TransactionType.DIVIDEND, divDate1, divDate1,
                new BigDecimal("100"), new BigDecimal("0.50"),
                new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("5.00"),
                "GBP", BigDecimal.ONE, null, null, null
        );

        // Dividend 2: Gross 50.00, Tax 5.00, Fee 0, Net 45.00 GBP on 100 shares
        Transaction tx2 = new Transaction(
                account, vusa, TransactionType.DIVIDEND, divDate2, divDate2,
                new BigDecimal("100"), new BigDecimal("0.50"),
                new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("5.00"),
                "GBP", BigDecimal.ONE, null, null, null
        );

        // Dividend 3: Gross 40.00, Tax 4.00, Fee 0, Net 36.00 GBP on 100 shares
        Transaction tx3 = new Transaction(
                account, vusa, TransactionType.DIVIDEND, divDate3, divDate3,
                new BigDecimal("100"), new BigDecimal("0.40"),
                new BigDecimal("40.00"), BigDecimal.ZERO, new BigDecimal("4.00"),
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx1, tx2, tx3));

        // Position: 100 shares of VUSA, cost basis 7000.00 GBP
        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("100")), new Money(new BigDecimal("7000.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        // Passthrough FX conversions for GBP to GBP
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Market quote: VUSA = 80.00 GBP (Market Value = 8000.00 GBP)
        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(vusa.getId(), new BigDecimal("80.00"), "GBP", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        // All time net: 45 + 45 + 36 = 126.0000
        assertEquals(new BigDecimal("126.0000"), response.totalDividendsAllTime());
        // YTD net (tx1 + tx2): 45 + 45 = 90.0000
        assertEquals(new BigDecimal("90.0000"), response.totalDividendsYtd());
        // TTM net (tx1 + tx2): 45 + 45 = 90.0000
        assertEquals(new BigDecimal("90.0000"), response.totalDividendsTtm());
        // Withholding tax all time: 5 + 5 + 4 = 14.0000
        assertEquals(new BigDecimal("14.0000"), response.totalWithholdingTaxAllTime());

        // TTM DPS for VUSA: (50/100) + (50/100) = 1.0000 GBP per share
        // Projected annual income = 100 * 1.0000 = 100.0000 GBP
        assertEquals(new BigDecimal("100.0000"), response.projectedAnnualDividendIncome());

        // Portfolio Dividend Yield % = (100 / 8000) * 100 = 1.25%
        assertEquals(new BigDecimal("1.25"), response.portfolioDividendYieldPercentage());
        // Portfolio Yield on Cost % = (100 / 7000) * 100 = 1.43%
        assertEquals(new BigDecimal("1.43"), response.portfolioYieldOnCostPercentage());

        // Holdings validation
        assertEquals(1, response.holdings().size());
        HoldingDividendMetricResponse h = response.holdings().get(0);
        assertEquals("VUSA", h.ticker());
        assertEquals(new BigDecimal("100.0000"), h.currentShares());
        assertEquals(new BigDecimal("126.0000"), h.totalReceivedAllTime());
        assertEquals(new BigDecimal("90.0000"), h.totalReceivedYtd());
        assertEquals(new BigDecimal("90.0000"), h.totalReceivedTtm());
        assertEquals(new BigDecimal("1.0000"), h.trailingTwelveMonthsDps());
        assertEquals(new BigDecimal("100.0000"), h.projectedAnnualIncome());
        assertEquals(new BigDecimal("1.25"), h.currentYieldPercentage());
        assertEquals(new BigDecimal("1.43"), h.yieldOnCostPercentage());

        // Monthly calendar: historical payment months are June (6) and March (3)
        // 100 projected income / 2 months = 50.0000 in March and June
        assertEquals(12, response.projectedMonthlyCalendar().size());
        assertEquals(new BigDecimal("50.0000"), response.projectedMonthlyCalendar().get(2).projectedAmount()); // March (idx 2)
        assertEquals(new BigDecimal("50.0000"), response.projectedMonthlyCalendar().get(5).projectedAmount()); // June (idx 5)
    }

    @Test
    void calculate_multiCurrencyDividendsAndFx_convertsToBaseCurrency() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant divDate = Instant.parse("2026-07-10T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // AAPL Dividend: 100.00 USD gross, 15.00 USD tax, 85.00 USD net on 50 shares
        Transaction tx = new Transaction(
                account, aapl, TransactionType.DIVIDEND, divDate, divDate,
                new BigDecimal("50"), new BigDecimal("2.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("15.00"),
                "USD", new BigDecimal("1.25"), null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx));

        Position pos = new Position(account, aapl, new Quantity(new BigDecimal("50")), new Money(new BigDecimal("5000.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        // Mock FX: USD to GBP rate is 0.80 (i.e. $100 USD = £80 GBP, $85 USD = £68 GBP, $15 USD = £12 GBP)
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Money input = invocation.getArgument(0);
                    if ("USD".equals(input.currency().code())) {
                        return new Money(input.amount().multiply(new BigDecimal("0.80")), new Currency("GBP"));
                    }
                    return input;
                });

        when(marketDataService.getLatestPrice(eq(aapl.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(aapl.getId(), new BigDecimal("200.00"), "USD", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        // Net converted: 85 * 0.80 = 68.0000 GBP
        assertEquals(new BigDecimal("68.0000"), response.totalDividendsAllTime());
        assertEquals(new BigDecimal("68.0000"), response.totalDividendsYtd());
        // Tax converted: 15 * 0.80 = 12.0000 GBP
        assertEquals(new BigDecimal("12.0000"), response.totalWithholdingTaxAllTime());

        // TTM DPS in Base (GBP): Gross per share = $2.00 USD * 0.80 = £1.6000 GBP
        // Projected annual income = 50 shares * £1.6000 = £80.0000 GBP
        assertEquals(new BigDecimal("80.0000"), response.projectedAnnualDividendIncome());
        assertEquals(1, response.holdings().size());
        assertEquals("AAPL", response.holdings().get(0).ticker());
        assertEquals(new BigDecimal("1.6000"), response.holdings().get(0).trailingTwelveMonthsDps());
        assertEquals(new BigDecimal("80.0000"), response.holdings().get(0).projectedAnnualIncome());
    }

    @Test
    void calculate_olderDividendsOnly_estimatesFromQuarterlyProxy() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant oldDate = Instant.parse("2024-04-10T10:00:00Z"); // Older than TTM

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // 1 dividend in 2024 of 25.00 GBP on 100 shares (0.25 per share)
        Transaction oldTx = new Transaction(
                account, vusa, TransactionType.DIVIDEND, oldDate, oldDate,
                new BigDecimal("100"), new BigDecimal("0.25"),
                new BigDecimal("25.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(oldTx));

        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("100")), new Money(new BigDecimal("6000.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(vusa.getId(), new BigDecimal("75.00"), "GBP", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("25.0000"), response.totalDividendsAllTime());
        assertEquals(new BigDecimal("0.0000"), response.totalDividendsYtd());
        assertEquals(new BigDecimal("0.0000"), response.totalDividendsTtm());

        // Estimated DPS = 0.25 * 4 = 1.0000 GBP
        // Projected Income = 100 * 1.0000 = 100.0000 GBP
        assertEquals(new BigDecimal("100.0000"), response.projectedAnnualDividendIncome());
        assertEquals(new BigDecimal("1.0000"), response.holdings().get(0).trailingTwelveMonthsDps());
    }

    @Test
    void calculate_marketPriceException_handlesGracefully() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant divDate = Instant.parse("2026-05-10T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Transaction tx = new Transaction(
                account, vusa, TransactionType.DIVIDEND, divDate, divDate,
                new BigDecimal("100"), new BigDecimal("0.50"),
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx));

        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("100")), new Money(new BigDecimal("5000.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenThrow(new RuntimeException("Market data provider offline"));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.0000"), response.totalDividendsAllTime());
        assertEquals(new BigDecimal("50.0000"), response.projectedAnnualDividendIncome());
        // Yield % is 0.00 when market value could not be resolved
        assertEquals(new BigDecimal("0.00"), response.portfolioDividendYieldPercentage());
        // Yield on cost is still calculated: (50 / 5000) * 100 = 1.00%
        assertEquals(new BigDecimal("1.00"), response.portfolioYieldOnCostPercentage());
    }

    @Test
    void calculate_nullGrossAmountAndTax_usesFallbackDefaults() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant divDate = Instant.parse("2026-06-10T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // tx with null grossAmount, null taxAmount, null quantity
        Transaction tx = new Transaction(
                account, vusa, TransactionType.DIVIDEND, divDate, divDate,
                null, null,
                new BigDecimal("50.00"), null, null,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx));

        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("50")), null);
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(vusa.getId(), new BigDecimal("80.00"), "GBP", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.0000"), response.totalDividendsAllTime());
        assertEquals(new BigDecimal("0.0000"), response.totalWithholdingTaxAllTime());
        assertEquals(new BigDecimal("1.0000"), response.holdings().get(0).trailingTwelveMonthsDps());
        assertEquals(new BigDecimal("50.0000"), response.holdings().get(0).projectedAnnualIncome());
        assertEquals(new BigDecimal("1.25"), response.holdings().get(0).currentYieldPercentage());
        assertEquals(new BigDecimal("0.00"), response.holdings().get(0).yieldOnCostPercentage());
        assertEquals(new BigDecimal("0.00"), response.portfolioYieldOnCostPercentage());
    }

    @Test
    void calculate_olderDividendWithNullGrossAndNullQty_usesFallback() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant oldDate = Instant.parse("2024-04-10T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Transaction oldTx = new Transaction(
                account, vusa, TransactionType.DIVIDEND, oldDate, oldDate,
                null, null,
                new BigDecimal("25.00"), null, null,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(oldTx));

        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("100")), new Money(new BigDecimal("5000.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(vusa.getId(), BigDecimal.ZERO, "GBP", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        // DPS = (25 / 100) * 4 = 1.0000
        assertEquals(new BigDecimal("1.0000"), response.holdings().get(0).trailingTwelveMonthsDps());
        assertEquals(new BigDecimal("100.0000"), response.holdings().get(0).projectedAnnualIncome());
        assertEquals(new BigDecimal("0.00"), response.holdings().get(0).currentYieldPercentage());
        assertEquals(new BigDecimal("2.00"), response.holdings().get(0).yieldOnCostPercentage());
    }

    @Test
    void calculate_multiplePositionsAndHoldings_sortsByProjectedIncomeAndTotal() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant divDate = Instant.parse("2026-06-10T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Transaction txVusa = new Transaction(
                account, vusa, TransactionType.DIVIDEND, divDate, divDate,
                new BigDecimal("100"), new BigDecimal("0.50"),
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, null, null, null
        );

        Transaction txAapl = new Transaction(
                account, aapl, TransactionType.DIVIDEND, divDate, divDate,
                new BigDecimal("50"), new BigDecimal("2.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(txVusa, txAapl));

        Position posVusa1 = new Position(account, vusa, new Quantity(new BigDecimal("50")), new Money(new BigDecimal("2500.00"), new Currency("GBP")));
        Position posVusa2 = new Position(account, vusa, new Quantity(new BigDecimal("50")), new Money(new BigDecimal("2500.00"), new Currency("GBP")));
        Position posAapl = new Position(account, aapl, new Quantity(new BigDecimal("50")), new Money(new BigDecimal("5000.00"), new Currency("GBP")));

        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(posVusa1, posVusa2, posAapl));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(vusa.getId(), new BigDecimal("80.00"), "GBP", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));
        when(marketDataService.getLatestPrice(eq(aapl.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(aapl.getId(), new BigDecimal("200.00"), "GBP", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(2, response.holdings().size());
        // AAPL projected income (50 * 2 = 100) > VUSA projected income (100 * 0.5 = 50)
        assertEquals("AAPL", response.holdings().get(0).ticker());
        assertEquals(new BigDecimal("50.0000"), response.holdings().get(0).currentShares());
        assertEquals("VUSA", response.holdings().get(1).ticker());
        assertEquals(new BigDecimal("100.0000"), response.holdings().get(1).currentShares()); // posVusa1 + posVusa2 = 100
    }

    @Test
    void calculate_zeroSharesPositionWithHistoricalDividends_includesInHoldings() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant divDate = Instant.parse("2026-04-10T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Transaction tx = new Transaction(
                account, aapl, TransactionType.DIVIDEND, divDate, divDate,
                new BigDecimal("20"), new BigDecimal("1.00"),
                new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx));

        // AAPL has 0 open shares (fully sold)
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of());

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("20.0000"), response.totalDividendsAllTime());
        assertEquals(1, response.holdings().size());
        assertEquals(new BigDecimal("0.0000"), response.holdings().get(0).currentShares());
        assertEquals(new BigDecimal("20.0000"), response.holdings().get(0).totalReceivedAllTime());
        assertEquals(new BigDecimal("0.0000"), response.holdings().get(0).projectedAnnualIncome());
    }

    @Test
    void calculate_nonDividendAndCorrectedTransactions_filteredOut() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant divDate = Instant.parse("2026-06-15T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Transaction buyTx = new Transaction(
                account, vusa, TransactionType.BUY, divDate, divDate,
                new BigDecimal("10"), new BigDecimal("80.00"),
                new BigDecimal("800.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, null, null, null
        );

        Transaction correctedDivTx = new Transaction(
                account, vusa, TransactionType.DIVIDEND, divDate, divDate,
                new BigDecimal("100"), new BigDecimal("0.50"),
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, null, null, null
        );
        correctedDivTx.markCorrected();

        Transaction completedDivTx = new Transaction(
                account, vusa, TransactionType.DIVIDEND, divDate, divDate,
                new BigDecimal("100"), new BigDecimal("0.50"),
                new BigDecimal("50.00"), null, null,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(buyTx, correctedDivTx, completedDivTx));

        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of());

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.0000"), response.totalDividendsAllTime());
    }

    @Test
    void calculate_holdingWithNoDividends_yieldsZero() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of());

        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("100")), new Money(new BigDecimal("5000.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenReturn(null);

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("0.0000"), response.totalDividendsAllTime());
        assertEquals(new BigDecimal("0.0000"), response.projectedAnnualDividendIncome());
        assertEquals(new BigDecimal("0.00"), response.portfolioDividendYieldPercentage());
        assertEquals(new BigDecimal("0.00"), response.portfolioYieldOnCostPercentage());
        assertEquals(1, response.holdings().size());
        assertEquals(new BigDecimal("0.0000"), response.holdings().get(0).projectedAnnualIncome());
        assertEquals(new BigDecimal("0.00"), response.holdings().get(0).currentYieldPercentage());
        assertEquals(new BigDecimal("0.00"), response.holdings().get(0).yieldOnCostPercentage());
    }

    @Test
    void calculate_mixedGrossTaxAndQuantities_coversAllBranches() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        Instant div1Date = Instant.parse("2026-03-10T10:00:00Z");
        Instant div2Date = Instant.parse("2026-07-10T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Tx1: explicit gross and tax and quantity
        Transaction tx1 = new Transaction(
                account, vusa, TransactionType.DIVIDEND, div1Date, div1Date,
                new BigDecimal("50"), new BigDecimal("1.00"),
                new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("7.50"),
                "GBP", BigDecimal.ONE, null, null, null
        );

        // Tx2: null gross (fallback to net) and null tax (fallback to 0) and null quantity (fallback to currentShares)
        Transaction tx2 = new Transaction(
                account, vusa, TransactionType.DIVIDEND, div2Date, div2Date,
                null, null,
                new BigDecimal("50.00"), null, null,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx1, tx2));

        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("50")), new Money(new BigDecimal("2500.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(vusa.getId(), new BigDecimal("100.00"), "GBP", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("92.5000"), response.totalDividendsAllTime());
        assertEquals(new BigDecimal("7.5000"), response.totalWithholdingTaxAllTime());
        assertEquals(new BigDecimal("2.0000"), response.holdings().get(0).trailingTwelveMonthsDps());
        assertEquals(new BigDecimal("100.0000"), response.holdings().get(0).projectedAnnualIncome());
        assertEquals(new BigDecimal("2.00"), response.holdings().get(0).currentYieldPercentage());
        assertEquals(new BigDecimal("4.00"), response.holdings().get(0).yieldOnCostPercentage());
        assertEquals(new BigDecimal("2.00"), response.portfolioDividendYieldPercentage());
        assertEquals(new BigDecimal("4.00"), response.portfolioYieldOnCostPercentage());
    }

    @Test
    void calculate_olderDividendFallback_usesQuarterlyProxy() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        // Dividend from 18 months ago (outside TTM)
        Instant oldDivDate = Instant.parse("2025-01-10T10:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Transaction oldTx = new Transaction(
                account, vusa, TransactionType.DIVIDEND, oldDivDate, oldDivDate,
                new BigDecimal("50"), new BigDecimal("0.50"),
                new BigDecimal("25.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(oldTx));

        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("50")), new Money(new BigDecimal("2500.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class)))
                .thenReturn(new PriceQuote(vusa.getId(), new BigDecimal("100.00"), "GBP", now, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TEST", false, null));

        DividendAnalyticsResponse response = dividendAnalyticsService.calculate(portfolioId, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("25.0000"), response.totalDividendsAllTime());
        // 0.50 DPS * 4 = 2.0000 DPS proxy
        assertEquals(new BigDecimal("2.0000"), response.holdings().get(0).trailingTwelveMonthsDps());
        assertEquals(new BigDecimal("100.0000"), response.holdings().get(0).projectedAnnualIncome());
    }
}

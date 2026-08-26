package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
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
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import java.math.BigDecimal;
import java.time.Instant;
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
class AnalyticsEngineTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private FxRateService fxRateService;
    @Mock
    private PositionEngine positionEngine;

    private AnalyticsEngine analyticsEngine;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Account account;
    private Instrument aapl;
    private Instrument vusa;

    @BeforeEach
    void setUp() {
        analyticsEngine = new AnalyticsEngine(
                portfolioRepository, accountRepository, positionRepository,
                transactionRepository, marketDataService, fxRateService, positionEngine
        );

        portfolioId = UUID.randomUUID();
        portfolio = new Portfolio("Tech Growth", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);

        account = new Account(portfolio, "Brokerage SIPP", "Broker", new Currency("GBP"));

        aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        vusa = new Instrument("Vanguard S&P 500 ETF", AssetClass.ETF, "VUSA", "IE00B3XXRP09", "LSE", new Currency("GBP"));
    }

    @Test
    void calculate_portfolioNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> analyticsEngine.calculate(portfolioId, Instant.now()));
    }

    @Test
    void calculate_emptyPortfolio_returnsZeroValuationsAndAllocations() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(account.getId())).thenReturn(List.of());
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of());

        PortfolioAnalytics result = analyticsEngine.calculate(portfolioId, null);

        assertEquals(BigDecimal.ZERO.setScale(4), result.totalCurrentValue());
        assertEquals(BigDecimal.ZERO.setScale(4), result.totalCostBasis());
        assertEquals(BigDecimal.ZERO.setScale(4), result.totalUnrealizedGainLoss());
        assertEquals(BigDecimal.ZERO.setScale(4), result.totalUnrealizedReturnPercentage());
        assertTrue(result.byAssetClass().isEmpty());
        assertTrue(result.byCurrency().isEmpty());
        assertTrue(result.byAccount().isEmpty());
        assertTrue(result.topHoldings().isEmpty());
    }

    @Test
    void calculate_singlePositionWithCash_calculatesValuationsAndAllocations() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        // Account cash transactions: Deposit, Buy, Sell, Dividend, Interest, Withdrawal, Fee
        Instant now = Instant.now();
        Transaction dep = new Transaction(account, null, TransactionType.DEPOSIT, now, null, null, null, new BigDecimal("1000.00"), null, null, "GBP", null, null, null, null);
        Transaction buy = new Transaction(account, vusa, TransactionType.BUY, now, null, new BigDecimal("10"), new BigDecimal("50.00"), new BigDecimal("500.00"), null, null, "GBP", null, null, null, null);
        Transaction sell = new Transaction(account, vusa, TransactionType.SELL, now, null, new BigDecimal("2"), new BigDecimal("60.00"), new BigDecimal("120.00"), null, null, "GBP", null, null, null, null);
        Transaction div = new Transaction(account, vusa, TransactionType.DIVIDEND, now, null, null, null, new BigDecimal("15.00"), null, null, "GBP", null, null, null, null);
        Transaction interest = new Transaction(account, null, TransactionType.INTEREST, now, null, null, null, new BigDecimal("5.00"), null, null, "GBP", null, null, null, null);
        Transaction fee = new Transaction(account, null, TransactionType.FEE, now, null, null, null, new BigDecimal("2.00"), null, null, "GBP", null, null, null, null);
        Transaction withdrawal = new Transaction(account, null, TransactionType.WITHDRAWAL, now, null, null, null, new BigDecimal("100.00"), null, null, "GBP", null, null, null, null);
        Transaction split = new Transaction(account, vusa, TransactionType.STOCK_SPLIT, now, null, new BigDecimal("10"), null, BigDecimal.ZERO, null, null, "GBP", null, null, null, null);

        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(account.getId()))
                .thenReturn(List.of(dep, buy, sell, div, interest, fee, withdrawal, split));

        // FX conversions for GBP -> GBP (identity)
        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Position: 8 shares of VUSA, cost basis 400 GBP
        Position pos = new Position(account, vusa, new Quantity(new BigDecimal("8")), new Money(new BigDecimal("400.00"), new Currency("GBP")));
        // Zero quantity position that should be skipped
        Position zeroPos = new Position(account, aapl, new Quantity(BigDecimal.ZERO), new Money(BigDecimal.ZERO, new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos, zeroPos));

        // Live price: 60.00 GBP per share (Market value = 480 GBP, Unrealized = +80 GBP)
        PriceQuote quote = new PriceQuote(
                vusa.getId(), new BigDecimal("60.00"), "GBP", now,
                com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "EXCHANGE", false, null
        );
        when(marketDataService.getLatestPrice(eq(vusa.getId()), any(Instant.class))).thenReturn(quote);

        // Realized gains calculation result with null and non-null amounts
        when(positionEngine.recalculatePortfolio(portfolioId)).thenReturn(List.of(
                new PositionCalculationResult(account.getId(), vusa.getId(),
                        CostBasisMethod.FIFO, new BigDecimal("8"), new BigDecimal("400.00"), "GBP",
                        new BigDecimal("50.00"), new BigDecimal("20.00"), List.of(), List.of()),
                new PositionCalculationResult(account.getId(), aapl.getId(),
                        CostBasisMethod.FIFO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                        BigDecimal.ZERO, null, List.of(), List.of())
        ));

        PortfolioAnalytics result = analyticsEngine.calculate(portfolioId, now);

        assertEquals(portfolioId, result.portfolioId());
        assertEquals("GBP", result.baseCurrency());
        assertNotNull(result.totalCurrentValue());
        assertNotNull(result.totalCostBasis());
        assertNotNull(result.totalUnrealizedGainLoss());
        assertEquals(new BigDecimal("20.0000"), result.totalRealizedGainLoss());

        // Asset class allocation: CASH (538 GBP) and ETF (480 GBP)
        assertEquals(2, result.byAssetClass().size());
        assertEquals("CASH", result.byAssetClass().get(0).category());
        assertEquals("ETF", result.byAssetClass().get(1).category());

        // Top holdings
        assertEquals(1, result.topHoldings().size());
        assertEquals("VUSA", result.topHoldings().get(0).ticker());
    }

    @Test
    void calculate_multiCurrencyPositionWithWarnings_aggregatesCorrectly() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(account.getId())).thenReturn(List.of());

        // Position: 10 shares of AAPL (USD)
        Position pos = new Position(account, aapl, new Quantity(new BigDecimal("10")), new Money(new BigDecimal("1500.00"), new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        // Price quote in USD with warning
        PriceQuote quote = new PriceQuote(
                aapl.getId(), new BigDecimal("200.00"), "USD", Instant.now(),
                com.takakim.investtracker.domain.ObservationSourceType.MANUAL, "MANUAL_OVERRIDE", true, "Price is stale (>24h)"
        );
        when(marketDataService.getLatestPrice(eq(aapl.getId()), any(Instant.class))).thenReturn(quote);

        // FX conversion USD -> GBP: 1 USD = 0.80 GBP
        when(fxRateService.convert(eq(new Money(new BigDecimal("200.00"), new Currency("USD"))), eq(new Currency("GBP")), any(Instant.class)))
                .thenReturn(new Money(new BigDecimal("160.00"), new Currency("GBP"))); // 200 * 0.80 = 160 GBP per share
        when(fxRateService.convert(eq(new Money(new BigDecimal("1500.00"), new Currency("USD"))), eq(new Currency("GBP")), any(Instant.class)))
                .thenReturn(new Money(new BigDecimal("1200.00"), new Currency("GBP"))); // 1500 * 0.80 = 1200 GBP cost basis

        PortfolioAnalytics result = analyticsEngine.calculate(portfolioId, Instant.now());

        assertEquals(new BigDecimal("1600.0000"), result.totalCurrentValue()); // 10 * 160 GBP
        assertEquals(new BigDecimal("1200.0000"), result.totalCostBasis());
        assertEquals(new BigDecimal("400.0000"), result.totalUnrealizedGainLoss());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Price is stale"));
    }

    @Test
    void calculate_missingPriceQuote_fallsBackToCostBasisProxyWithWarning() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(account.getId())).thenReturn(List.of());

        // Instrument without ticker
        Instrument untracked = new Instrument("Private Fund", AssetClass.OTHER, null, null, null, new Currency("GBP"));
        Position pos = new Position(account, untracked, new Quantity(new BigDecimal("10")), new Money(new BigDecimal("500.00"), new Currency("GBP")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(marketDataService.getLatestPrice(eq(untracked.getId()), any(Instant.class)))
                .thenThrow(new ResourceNotFoundException("No quote found"));

        when(fxRateService.convert(any(Money.class), eq(new Currency("GBP")), any(Instant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(positionEngine.recalculatePortfolio(portfolioId)).thenThrow(new RuntimeException("Recalculation error"));

        PortfolioAnalytics result = analyticsEngine.calculate(portfolioId, Instant.now());

        assertEquals(new BigDecimal("500.0000"), result.totalCurrentValue());
        assertEquals(new BigDecimal("500.0000"), result.totalCostBasis());
        assertEquals(new BigDecimal("0.0000"), result.totalUnrealizedGainLoss());
        assertFalse(result.warnings().isEmpty());
        assertEquals("—", result.topHoldings().get(0).ticker());
    }
}

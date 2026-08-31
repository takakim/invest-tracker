package com.takakim.investtracker;

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
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.export.PortfolioExportService;
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
class PortfolioExportServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AnalyticsEngine analyticsEngine;

    private PortfolioExportService exportService;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Account account;
    private Instrument apple;
    private Instrument unlisted;

    @BeforeEach
    void setUp() {
        exportService = new PortfolioExportService(
                portfolioRepository, positionRepository, transactionRepository, analyticsEngine
        );

        portfolioId = UUID.randomUUID();
        portfolio = new Portfolio("Tech Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        account = new Account(portfolio, "Interactive Brokers", "Broker", new Currency("GBP"));

        apple = new Instrument("Apple, Inc. \"Special\"", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        unlisted = new Instrument("Unlisted Fund", AssetClass.OTHER, null, null, null, new Currency("GBP"));
    }

    @Test
    void exportPositionsCsv_portfolioNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> exportService.exportPositionsCsv(portfolioId));
    }

    @Test
    void exportPositionsCsv_validPortfolio_generatesCorrectCsv() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Position pos1 = new Position(account, apple, new Quantity(new BigDecimal("10.00")), new Money(new BigDecimal("1500.00"), new Currency("USD")));
        Position pos2 = new Position(account, unlisted, new Quantity(new BigDecimal("5.00")), new Money(new BigDecimal("500.00"), new Currency("GBP")));

        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos1, pos2));

        HoldingExposure holding = new HoldingExposure(
                apple.getId(), apple.getName(), "AAPL", AssetClass.STOCK,
                new BigDecimal("10.00"), new BigDecimal("200.00"), new BigDecimal("1600.00"),
                new BigDecimal("1200.00"), new BigDecimal("400.00"), new BigDecimal("1.00"), "USD"
        );

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", new BigDecimal("1600.00"), new BigDecimal("1200.00"),
                new BigDecimal("400.00"), new BigDecimal("33.33"), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(holding), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        String csv = exportService.exportPositionsCsv(portfolioId);

        assertNotNull(csv);
        assertTrue(csv.startsWith("Account,Instrument Name,Ticker,ISIN,Asset Class,Quantity,Cost Basis Amount,Cost Basis Currency,Current Price,Market Value (GBP),Unrealized Gain/Loss,Weight %"));
        assertTrue(csv.contains("\"Apple, Inc. \"\"Special\"\"\""));
        assertTrue(csv.contains("AAPL"));
        assertTrue(csv.contains("US0378331005"));
        assertTrue(csv.contains("Unlisted Fund"));
        assertTrue(csv.contains("1600.00"));
        assertTrue(csv.contains("400.00"));
    }

    @Test
    void exportTransactionsCsv_portfolioNotFound_throwsException() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> exportService.exportTransactionsCsv(portfolioId));
    }

    @Test
    void exportTransactionsCsv_validPortfolio_generatesCorrectCsv() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Transaction tx1 = new Transaction(
                account, apple, TransactionType.BUY,
                Instant.parse("2026-08-20T10:15:30Z"), null,
                new BigDecimal("10.00"), new BigDecimal("150.00"),
                new BigDecimal("1500.00"), new BigDecimal("5.00"), new BigDecimal("2.00"), "USD", null, null,
                "First acquisition, broker order #1234\nSecond line", null
        );

        Transaction tx2 = new Transaction(
                account, null, TransactionType.DEPOSIT,
                Instant.parse("2026-08-20T09:00:00Z"), null,
                null, null,
                new BigDecimal("5000.00"), null, null, "GBP", null, null,
                null, null
        );

        Transaction tx3 = new Transaction(
                account, unlisted, TransactionType.BUY,
                Instant.parse("2026-08-20T11:00:00Z"), null,
                new BigDecimal("5.00"), new BigDecimal("100.00"),
                new BigDecimal("500.00"), null, null, "GBP", null, null,
                "Simple note", null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId))
                .thenReturn(List.of(tx1, tx2, tx3));

        String csv = exportService.exportTransactionsCsv(portfolioId);

        assertNotNull(csv);
        assertTrue(csv.startsWith("Date,Type,Account,Instrument,Ticker,Quantity,Price,Gross Amount,Fee,Tax,Net Amount,Currency,Notes"));
        assertTrue(csv.contains("2026-08-20T10:15:30Z"));
        assertTrue(csv.contains("BUY"));
        assertTrue(csv.contains("DEPOSIT"));
        assertTrue(csv.contains("Interactive Brokers"));
        assertTrue(csv.contains("\"Apple, Inc. \"\"Special\"\"\""));
        assertTrue(csv.contains("5000.00"));
        assertTrue(csv.contains("\"First acquisition, broker order #1234\nSecond line\""));
    }

    @Test
    void exportPositionsCsv_withNullCostBasisAndCurrency_handlesDefaults() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Position pos = new Position(account, unlisted, new Quantity(new BigDecimal("5.00")), new Money(new BigDecimal("500.00"), new Currency("GBP")));
        // Clear cost basis amount/currency via reflection to test null fallback
        try {
            var f1 = Position.class.getDeclaredField("costBasisAmount");
            f1.setAccessible(true);
            f1.set(pos, null);
            var f2 = Position.class.getDeclaredField("costBasisCurrency");
            f2.setAccessible(true);
            f2.set(pos, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analytics);

        String csv = exportService.exportPositionsCsv(portfolioId);
        assertNotNull(csv);
        assertTrue(csv.contains("0.0000"));
    }
}

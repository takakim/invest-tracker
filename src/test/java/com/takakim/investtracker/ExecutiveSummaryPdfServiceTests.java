package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.AccountCashFlowSummary;
import com.takakim.investtracker.api.ApiDtos.CapitalGainsTaxSummaryDto;
import com.takakim.investtracker.api.ApiDtos.CashFlowAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.CashFlowSummary;
import com.takakim.investtracker.api.ApiDtos.DividendTaxSummaryDto;
import com.takakim.investtracker.api.ApiDtos.ItemizedDisposalDto;
import com.takakim.investtracker.api.ApiDtos.ItemizedDividendDto;
import com.takakim.investtracker.api.ApiDtos.TaxLossHarvestOpportunityDto;
import com.takakim.investtracker.api.ApiDtos.TaxReportResponse;
import com.takakim.investtracker.api.ApiDtos.TaxShelteredSummaryDto;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.AccountTaxTreatment;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.TaxRegime;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.AllocationItem;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.CashFlowAnalyticsService;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.export.ExecutiveSummaryPdfService;
import com.takakim.investtracker.service.performance.PerformanceEngine;
import com.takakim.investtracker.service.performance.PerformanceResult;
import com.takakim.investtracker.service.tax.TaxAllowanceService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveSummaryPdfServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AnalyticsEngine analyticsEngine;
    @Mock
    private PerformanceEngine performanceEngine;
    @Mock
    private CashFlowAnalyticsService cashFlowAnalyticsService;
    @Mock
    private TaxAllowanceService taxAllowanceService;

    private ExecutiveSummaryPdfService pdfService;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Account accountGbp;
    private Account accountUsd;

    @BeforeEach
    void setUp() {
        pdfService = new ExecutiveSummaryPdfService(
                portfolioRepository,
                accountRepository,
                analyticsEngine,
                performanceEngine,
                cashFlowAnalyticsService,
                taxAllowanceService
        );

        portfolio = new Portfolio(
                "Global Wealth Portfolio",
                new Currency("GBP"),
                CostBasisMethod.FIFO,
                ReturnMethod.TWR
        );
        portfolioId = portfolio.getId();

        accountGbp = new Account(
                portfolio,
                "Primary ISA",
                "Trading 212",
                new Currency("GBP"),
                AccountTaxTreatment.TAX_EXEMPT
        );

        accountUsd = new Account(
                portfolio,
                "GIA Trading",
                "Interactive Brokers",
                new Currency("USD"),
                AccountTaxTreatment.TAXABLE
        );
    }

    @Test
    @DisplayName("generateExecutiveSummaryPdf throws ResourceNotFoundException when portfolio does not exist")
    void testExecutiveSummaryPortfolioNotFound() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pdfService.generateExecutiveSummaryPdf(portfolioId));
    }

    @Test
    @DisplayName("generateExecutiveSummaryPdf creates valid multi-page PDF with full analytics")
    void testGenerateExecutiveSummaryPdfSuccess() throws Exception {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Create 25 holdings to trigger page pagination
        List<HoldingExposure> holdings = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            holdings.add(new HoldingExposure(
                    UUID.randomUUID(),
                    "Instrument Long Name Corporate Equity " + i,
                    "TKR" + i,
                    AssetClass.STOCK,
                    new BigDecimal("100.00"),
                    new BigDecimal("150.25"),
                    new BigDecimal("15025.00"),
                    new BigDecimal("12000.00"),
                    new BigDecimal("3025.00"),
                    new BigDecimal("0.0400"),
                    "GBP"
            ));
        }

        List<AllocationItem> assetClassAlloc = List.of(
                new AllocationItem("STOCK", new BigDecimal("75000.00"), new BigDecimal("0.7500"), new BigDecimal("60000.00"), new BigDecimal("15000.00")),
                new AllocationItem("CASH", new BigDecimal("25000.00"), new BigDecimal("0.2500"), new BigDecimal("25000.00"), BigDecimal.ZERO)
        );
        List<AllocationItem> currencyAlloc = List.of(
                new AllocationItem("GBP", new BigDecimal("80000.00"), new BigDecimal("0.8000"), new BigDecimal("70000.00"), new BigDecimal("10000.00")),
                new AllocationItem("USD", new BigDecimal("20000.00"), new BigDecimal("0.2000"), new BigDecimal("15000.00"), new BigDecimal("5000.00"))
        );
        List<AllocationItem> accountAlloc = List.of(
                new AllocationItem("Primary ISA", new BigDecimal("60000.00"), new BigDecimal("0.6000"), new BigDecimal("50000.00"), new BigDecimal("10000.00")),
                new AllocationItem("GIA Trading", new BigDecimal("40000.00"), new BigDecimal("0.4000"), new BigDecimal("35000.00"), new BigDecimal("5000.00"))
        );

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId,
                Instant.now(),
                "GBP",
                new BigDecimal("100000.00"),
                new BigDecimal("85000.00"),
                new BigDecimal("15000.00"),
                new BigDecimal("0.1765"),
                new BigDecimal("5000.00"),
                new BigDecimal("25000.00"),
                assetClassAlloc,
                currencyAlloc,
                accountAlloc,
                holdings,
                List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(analytics);

        PerformanceResult performance = new PerformanceResult(
                portfolioId,
                Instant.now(),
                "TWR",
                new BigDecimal("0.2500"),
                new BigDecimal("0.1250"),
                null,
                new BigDecimal("5000.00"),
                new BigDecimal("2500.00"),
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("7400.00"),
                new BigDecimal("80000.00"),
                new BigDecimal("80000.00"),
                "GBP",
                "MARKET_VALUE",
                List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(performance);

        CashFlowSummary cfSummary = new CashFlowSummary(
                new BigDecimal("80000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("70000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("50.00"),
                new BigDecimal("73150.00"),
                new BigDecimal("2500.00"),
                24,
                new BigDecimal("70000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("70.0"),
                new BigDecimal("30.0"),
                "GBP"
        );
        List<AccountCashFlowSummary> accSummaries = List.of(
                new AccountCashFlowSummary(
                        accountGbp.getId(), accountGbp.getName(), accountGbp.getBrokerName(),
                        "GBP", new BigDecimal("4500.00"), new BigDecimal("4500.00"),
                        new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("10000.00")
                ),
                new AccountCashFlowSummary(
                        accountUsd.getId(), accountUsd.getName(), accountUsd.getBrokerName(),
                        "USD", new BigDecimal("1250.75"), new BigDecimal("1000.00"),
                        new BigDecimal("2000.00"), BigDecimal.ZERO, new BigDecimal("2000.00")
                )
        );
        CashFlowAnalyticsResponse cashFlow = new CashFlowAnalyticsResponse(
                portfolioId, "Global Wealth Portfolio", "GBP", "ALL", "MONTH",
                Instant.now().minusSeconds(86400L * 30), Instant.now(),
                cfSummary, List.of(), accSummaries, List.of()
        );
        when(cashFlowAnalyticsService.calculateCashFlows(portfolioId, "ALL", "MONTH")).thenReturn(cashFlow);

        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp, accountUsd));

        byte[] pdfBytes = pdfService.generateExecutiveSummaryPdf(portfolioId);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 1000);
        String header = new String(pdfBytes, 0, Math.min(10, pdfBytes.length), StandardCharsets.US_ASCII);
        assertTrue(header.startsWith("%PDF-"));

        // Verify with PDFBox loader
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertTrue(doc.getNumberOfPages() >= 2, "Expected at least 2 pages due to 25 holdings");
        }
    }

    @Test
    @DisplayName("generateExecutiveSummaryPdf handles null cashflow summary and empty holdings")
    void testGenerateExecutiveSummaryPdfEmptyHoldings() throws Exception {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId,
                Instant.now(),
                "GBP",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(analytics);

        PerformanceResult performance = new PerformanceResult(
                portfolioId,
                Instant.now(),
                "TWR",
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "GBP",
                "COST_BASIS",
                List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(performance);
        when(cashFlowAnalyticsService.calculateCashFlows(portfolioId, "ALL", "MONTH")).thenReturn(null);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of());

        byte[] pdfBytes = pdfService.generateExecutiveSummaryPdf(portfolioId);

        assertNotNull(pdfBytes);
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("generateTaxReportPdf throws ResourceNotFoundException when portfolio is missing")
    void testTaxReportPortfolioNotFound() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                pdfService.generateTaxReportPdf(portfolioId, "2024/2025", TaxRegime.UK_HMRC));
    }

    @Test
    @DisplayName("generateTaxReportPdf creates complete multi-page audit statement with sheltered gains and loss harvesting")
    void testGenerateTaxReportPdfComprehensive() throws Exception {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant start = LocalDate.of(2024, 4, 6).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = LocalDate.of(2025, 4, 5).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        CapitalGainsTaxSummaryDto cgt = new CapitalGainsTaxSummaryDto(
                new BigDecimal("25000.00"),
                new BigDecimal("18000.00"),
                new BigDecimal("8000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("7000.00"),
                BigDecimal.ZERO,
                new BigDecimal("7000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                BigDecimal.ZERO,
                new BigDecimal("4000.00"),
                new BigDecimal("400.00"),
                new BigDecimal("800.00"),
                new BigDecimal("18.00"),
                new BigDecimal("24.00"),
                12
        );

        DividendTaxSummaryDto div = new DividendTaxSummaryDto(
                new BigDecimal("2200.00"),
                BigDecimal.ZERO,
                new BigDecimal("2200.00"),
                new BigDecimal("500.00"),
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                new BigDecimal("1700.00"),
                new BigDecimal("148.75"),
                new BigDecimal("573.75"),
                new BigDecimal("668.95"),
                new BigDecimal("8.75"),
                new BigDecimal("33.75"),
                new BigDecimal("39.35"),
                8
        );

        TaxShelteredSummaryDto sheltered = new TaxShelteredSummaryDto(
                new BigDecimal("12500.00"),
                BigDecimal.ZERO,
                new BigDecimal("1800.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("607.50"),
                new BigDecimal("3107.50")
        );

        // Disposals (including multiple to trigger space check)
        List<ItemizedDisposalDto> disposals = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            disposals.add(new ItemizedDisposalDto(
                    UUID.randomUUID(),
                    accountGbp.getId(),
                    "GIA Trading Account",
                    AccountTaxTreatment.TAXABLE,
                    UUID.randomUUID(),
                    "Apple Inc. Class A Shares",
                    "AAPL",
                    start.plusSeconds(i * 86400L),
                    new BigDecimal("10.00"),
                    new BigDecimal("1500.00"),
                    new BigDecimal("1200.00"),
                    "GBP",
                    new BigDecimal("1500.00"),
                    new BigDecimal("1200.00"),
                    new BigDecimal("300.00")
            ));
        }

        // Dividends
        List<ItemizedDividendDto> dividends = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            dividends.add(new ItemizedDividendDto(
                    UUID.randomUUID(),
                    accountGbp.getId(),
                    "GIA Trading Account",
                    AccountTaxTreatment.TAXABLE,
                    UUID.randomUUID(),
                    "Vanguard S&P 500 UCITS ETF",
                    "VUSA",
                    start.plusSeconds(i * 100000L),
                    new BigDecimal("120.00"),
                    new BigDecimal("18.00"),
                    "GBP",
                    new BigDecimal("120.00"),
                    new BigDecimal("18.00"),
                    new BigDecimal("102.00")
            ));
        }

        // Loss harvesting opportunities
        List<TaxLossHarvestOpportunityDto> lossOpps = List.of(
                new TaxLossHarvestOpportunityDto(
                        accountGbp.getId(),
                        "GIA Trading Account",
                        UUID.randomUUID(),
                        "Tesla Inc Common Stock",
                        "TSLA",
                        new BigDecimal("20.00"),
                        new BigDecimal("210.00"),
                        "GBP",
                        new BigDecimal("4200.00"),
                        new BigDecimal("5000.00"),
                        new BigDecimal("-800.00")
                )
        );

        TaxReportResponse report = new TaxReportResponse(
                portfolioId,
                "Global Wealth Portfolio",
                "GBP",
                "2024/2025",
                TaxRegime.UK_HMRC,
                start,
                end,
                cgt,
                div,
                sheltered,
                lossOpps,
                disposals,
                dividends,
                List.of()
        );

        when(taxAllowanceService.generateTaxReport(portfolioId, "2024/2025", TaxRegime.UK_HMRC)).thenReturn(report);

        byte[] pdfBytes = pdfService.generateTaxReportPdf(portfolioId, "2024/2025", TaxRegime.UK_HMRC);

        assertNotNull(pdfBytes);
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertTrue(doc.getNumberOfPages() >= 2, "Expected multi-page tax report");
        }
    }

    @Test
    @DisplayName("generateTaxReportPdf handles empty disposals, empty dividends, zero sheltered gains, and null regime")
    void testGenerateTaxReportPdfEmptyData() throws Exception {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant start = LocalDate.of(2025, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = LocalDate.of(2025, 12, 31).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        CapitalGainsTaxSummaryDto cgt = new CapitalGainsTaxSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0
        );

        DividendTaxSummaryDto div = new DividendTaxSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0
        );

        TaxShelteredSummaryDto shelteredZero = new TaxShelteredSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        TaxReportResponse report = new TaxReportResponse(
                portfolioId,
                "Global Wealth Portfolio",
                "GBP",
                "2025",
                TaxRegime.CALENDAR_YEAR,
                start,
                end,
                cgt,
                div,
                shelteredZero,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        // When regime is null, it defaults to UK_HMRC
        when(taxAllowanceService.generateTaxReport(portfolioId, "2025", TaxRegime.UK_HMRC)).thenReturn(report);

        byte[] pdfBytes = pdfService.generateTaxReportPdf(portfolioId, "2025", null);

        assertNotNull(pdfBytes);
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("generateExecutiveSummaryPdf exercises fallback account breakdown, null tickers, and special characters")
    void testGenerateExecutiveSummaryPdfBranches() throws Exception {
        Portfolio specialPortfolio = new Portfolio(
                "Special & Portfolio\tName\nWith £ € and 日本語",
                new Currency("GBP"),
                CostBasisMethod.FIFO,
                ReturnMethod.TWR
        );
        UUID specId = specialPortfolio.getId();
        when(portfolioRepository.findById(specId)).thenReturn(Optional.of(specialPortfolio));

        // Holding with null ticker, negative gain/loss
        List<HoldingExposure> holdings = List.of(
                new HoldingExposure(
                        UUID.randomUUID(),
                        "Unlisted Private Co\tEquity\nSpecial",
                        null,
                        AssetClass.OTHER,
                        new BigDecimal("50.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("800.00"),
                        new BigDecimal("-300.00"),
                        new BigDecimal("0.5000"),
                        "GBP"
                )
        );

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                specId,
                Instant.now(),
                "GBP",
                new BigDecimal("500.00"),
                new BigDecimal("800.00"),
                new BigDecimal("-300.00"),
                new BigDecimal("-0.3750"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new AllocationItem("OTHER", new BigDecimal("500.00"), new BigDecimal("1.0000"), new BigDecimal("800.00"), new BigDecimal("-300.00"))),
                null,
                null,
                holdings,
                List.of()
        );
        when(analyticsEngine.calculate(eq(specId), any(Instant.class))).thenReturn(analytics);

        PerformanceResult performance = new PerformanceResult(
                specId,
                Instant.now(),
                "TWR",
                new BigDecimal("-0.3750"),
                new BigDecimal("-0.1875"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("800.00"),
                new BigDecimal("800.00"),
                "GBP",
                "MARKET_VALUE",
                List.of()
        );
        when(performanceEngine.calculate(specId)).thenReturn(performance);

        // Cash flow present with empty account breakdown -> exercises fallback lines 188-194
        CashFlowSummary cfSummary = new CashFlowSummary(
                new BigDecimal("800.00"), BigDecimal.ZERO, new BigDecimal("800.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("800.00"), new BigDecimal("800.00"),
                1, new BigDecimal("800.00"), new BigDecimal("500.00"),
                new BigDecimal("100.0"), BigDecimal.ZERO, "GBP"
        );
        CashFlowAnalyticsResponse cashFlow = new CashFlowAnalyticsResponse(
                specId, "Special Portfolio", "GBP", "ALL", "MONTH",
                Instant.now().minusSeconds(86400L), Instant.now(),
                cfSummary, List.of(), List.of(), List.of()
        );
        when(cashFlowAnalyticsService.calculateCashFlows(specId, "ALL", "MONTH")).thenReturn(cashFlow);

        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(specId, AccountStatus.ACTIVE))
                .thenReturn(List.of(accountGbp));

        byte[] pdfBytes = pdfService.generateExecutiveSummaryPdf(specId);

        assertNotNull(pdfBytes);
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("generateExecutiveSummaryPdf handles unknown account in breakdown and null cashflow")
    void testGenerateExecutiveSummaryPdfUnknownAccountInBreakdown() throws Exception {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(analytics);

        PerformanceResult performance = new PerformanceResult(
                portfolioId, Instant.now(), "TWR", null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.TEN, "GBP", "MARKET_VALUE", List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(performance);

        // Breakdown has an account ID not in accounts list
        List<AccountCashFlowSummary> unknownBreakdown = List.of(
                new AccountCashFlowSummary(
                        UUID.randomUUID(), "External Unknown Account", "External Broker",
                        "GBP", new BigDecimal("100.00"), new BigDecimal("100.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
                )
        );
        CashFlowAnalyticsResponse cashFlow = new CashFlowAnalyticsResponse(
                portfolioId, "Global Wealth Portfolio", "GBP", "ALL", "MONTH",
                Instant.now().minusSeconds(86400L), Instant.now(),
                null, List.of(), unknownBreakdown, List.of()
        );
        when(cashFlowAnalyticsService.calculateCashFlows(portfolioId, "ALL", "MONTH")).thenReturn(cashFlow);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of());

        byte[] pdfBytes = pdfService.generateExecutiveSummaryPdf(portfolioId);
        assertNotNull(pdfBytes);
    }

    @Test
    @DisplayName("generateTaxReportPdf handles sheltered dividends only and negative realized gain/loss with null tickers")
    void testGenerateTaxReportPdfShelteredDividendsAndNegativeGain() throws Exception {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instant start = LocalDate.of(2024, 4, 6).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = LocalDate.of(2025, 4, 5).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        CapitalGainsTaxSummaryDto cgt = new CapitalGainsTaxSummaryDto(
                new BigDecimal("500.00"), new BigDecimal("800.00"),
                BigDecimal.ZERO, new BigDecimal("300.00"),
                new BigDecimal("-300.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("3000.00"),
                BigDecimal.ZERO, new BigDecimal("3000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("18.00"), new BigDecimal("24.00"), 1
        );

        DividendTaxSummaryDto div = new DividendTaxSummaryDto(
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                new BigDecimal("500.00"), new BigDecimal("100.00"), new BigDecimal("400.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("8.75"), new BigDecimal("33.75"), new BigDecimal("39.35"), 1
        );

        // Sheltered gains = 0, but sheltered dividends > 0
        TaxShelteredSummaryDto shelteredDivOnly = new TaxShelteredSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450.00"),
                BigDecimal.ZERO, new BigDecimal("39.38"), new BigDecimal("39.38")
        );

        // Disposal with negative gain/loss and null ticker
        List<ItemizedDisposalDto> disposals = List.of(
                new ItemizedDisposalDto(
                        UUID.randomUUID(), accountUsd.getId(), "GIA Account", AccountTaxTreatment.TAXABLE,
                        UUID.randomUUID(), "Loss Stock", null,
                        start.plusSeconds(3600L), new BigDecimal("10.00"),
                        new BigDecimal("500.00"), new BigDecimal("800.00"), "USD",
                        new BigDecimal("400.00"), new BigDecimal("640.00"), new BigDecimal("-240.00")
                )
        );

        // Dividend with null ticker
        List<ItemizedDividendDto> dividends = List.of(
                new ItemizedDividendDto(
                        UUID.randomUUID(), accountUsd.getId(), "GIA Account", AccountTaxTreatment.TAXABLE,
                        UUID.randomUUID(), "Dividend Stock", null,
                        start.plusSeconds(7200L), new BigDecimal("100.00"), BigDecimal.ZERO, "USD",
                        new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("80.00")
                )
        );

        // Loss opportunity with null ticker
        List<TaxLossHarvestOpportunityDto> lossOpps = List.of(
                new TaxLossHarvestOpportunityDto(
                        accountUsd.getId(), "GIA Account", UUID.randomUUID(),
                        "Unlisted Loser", null, new BigDecimal("5.00"),
                        new BigDecimal("10.00"), "USD", new BigDecimal("50.00"),
                        new BigDecimal("120.00"), new BigDecimal("-70.00")
                )
        );

        TaxReportResponse report = new TaxReportResponse(
                portfolioId, "Global Wealth Portfolio", "GBP", "2024/2025",
                TaxRegime.UK_HMRC, start, end,
                cgt, div, shelteredDivOnly, lossOpps, disposals, dividends, List.of()
        );

        when(taxAllowanceService.generateTaxReport(portfolioId, "2024/2025", TaxRegime.UK_HMRC)).thenReturn(report);

        byte[] pdfBytes = pdfService.generateTaxReportPdf(portfolioId, "2024/2025", TaxRegime.UK_HMRC);
        assertNotNull(pdfBytes);
    }

    @Test
    @DisplayName("formatMoney, formatPercent, and sanitizeText handle nulls and edge formatting")
    void testUtilityFormattingAndSanitization() {
        assertEquals("GBP 0.00", ExecutiveSummaryPdfService.formatMoney(null, "GBP"));
        assertEquals("GBP 1,234.56", ExecutiveSummaryPdfService.formatMoney(new BigDecimal("1234.56"), "GBP"));

        assertEquals("0.00%", ExecutiveSummaryPdfService.formatPercent(null));
        assertEquals("+12.34%", ExecutiveSummaryPdfService.formatPercent(new BigDecimal("12.34")));
        assertEquals("-5.67%", ExecutiveSummaryPdfService.formatPercent(new BigDecimal("-5.67")));

        assertEquals("", ExecutiveSummaryPdfService.sanitizeText(null));
        assertEquals("Hello World", ExecutiveSummaryPdfService.sanitizeText("Hello\tWorld\r\n"));
        assertEquals("???", ExecutiveSummaryPdfService.sanitizeText("日本語"));
    }

    @Test
    @DisplayName("PdfReportWriter handles null collections, empty tables, and edge alignments")
    void testPdfReportWriterEdgeCases() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            ExecutiveSummaryPdfService.PdfReportWriter writer =
                    new ExecutiveSummaryPdfService.PdfReportWriter(doc, "TEST REPORT", "Test Portfolio");

            // Null/empty KPI cards
            writer.writeKpiCards(null);
            writer.writeKpiCards(List.of());

            // KPI card with null subvalue and blank subvalue
            writer.writeKpiCards(List.of(
                    new ExecutiveSummaryPdfService.KpiCard("Metric 1", "100", null),
                    new ExecutiveSummaryPdfService.KpiCard("Metric 2", "200", "   ")
            ));

            // writeParagraph
            writer.writeParagraph("Test paragraph description text.");

            // writeAllocationTwoColumn with nulls
            writer.writeAllocationTwoColumn(null, null, "GBP");

            // writeTable with nulls
            writer.writeTable(null, null, null, null);
            writer.writeTable(new String[]{"Col1"}, null, null, null);
            writer.writeTable(new String[]{"Col1"}, new float[]{100}, null, null);

            // writeTable with positive, negative, and neutral values, plus null values and mismatch lengths
            writer.writeTable(
                    new String[]{"Label", "Value"},
                    new float[]{100, 100},
                    new int[]{0, 1},
                    List.of(
                            new String[]{"+Positive", "+100.00", "ExtraColIgnored"},
                            new String[]{"-Negative", "-50.00"},
                            new String[]{"Neutral", "0.00"},
                            new String[]{null, null},
                            new String[]{"SingleCol"}
                    )
            );

            writer.finish();
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("generateExecutiveSummaryPdf handles null account breakdown in cashflow response")
    void testExecutiveSummaryWithNullAccountBreakdown() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any(Instant.class))).thenReturn(analytics);
        PerformanceResult performance = new PerformanceResult(
                portfolioId, Instant.now(), "TWR", null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", "COST_BASIS", List.of()
        );
        when(performanceEngine.calculate(portfolioId)).thenReturn(performance);

        CashFlowAnalyticsResponse cashFlow = new CashFlowAnalyticsResponse(
                portfolioId, "Portfolio", "GBP", "ALL", "MONTH",
                Instant.now().minusSeconds(86400L), Instant.now(),
                null, List.of(), null, List.of()
        );
        when(cashFlowAnalyticsService.calculateCashFlows(portfolioId, "ALL", "MONTH")).thenReturn(cashFlow);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of());

        byte[] pdfBytes = pdfService.generateExecutiveSummaryPdf(portfolioId);
        assertNotNull(pdfBytes);
    }

    @Test
    @DisplayName("generateTaxReportPdf handles sheltered realized gains with zero dividends")
    void testTaxReportShelteredRealizedGainsOnly() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        Instant start = LocalDate.of(2024, 4, 6).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = LocalDate.of(2025, 4, 5).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        CapitalGainsTaxSummaryDto cgt = new CapitalGainsTaxSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0
        );
        DividendTaxSummaryDto div = new DividendTaxSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0
        );
        TaxShelteredSummaryDto shelteredGainsOnly = new TaxShelteredSummaryDto(
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00")
        );

        TaxReportResponse report = new TaxReportResponse(
                portfolioId, "Global Wealth Portfolio", "GBP", "2024/2025",
                TaxRegime.UK_HMRC, start, end,
                cgt, div, shelteredGainsOnly, List.of(), List.of(), List.of(), List.of()
        );
        when(taxAllowanceService.generateTaxReport(portfolioId, "2024/2025", TaxRegime.UK_HMRC)).thenReturn(report);

        byte[] pdfBytes = pdfService.generateTaxReportPdf(portfolioId, "2024/2025", TaxRegime.UK_HMRC);
        assertNotNull(pdfBytes);
    }

    @Test
    @DisplayName("generateTaxReportPdf handles null sheltered summary")
    void testTaxReportNullSheltered() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        Instant start = LocalDate.of(2024, 4, 6).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = LocalDate.of(2025, 4, 5).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        CapitalGainsTaxSummaryDto cgt = new CapitalGainsTaxSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0
        );
        DividendTaxSummaryDto div = new DividendTaxSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0
        );

        TaxReportResponse report = new TaxReportResponse(
                portfolioId, "Global Wealth Portfolio", "GBP", "2024/2025",
                TaxRegime.UK_HMRC, start, end,
                cgt, div, null, List.of(), List.of(), List.of(), List.of()
        );
        when(taxAllowanceService.generateTaxReport(portfolioId, "2024/2025", TaxRegime.UK_HMRC)).thenReturn(report);

        byte[] pdfBytes = pdfService.generateTaxReportPdf(portfolioId, "2024/2025", TaxRegime.UK_HMRC);
        assertNotNull(pdfBytes);
    }
}

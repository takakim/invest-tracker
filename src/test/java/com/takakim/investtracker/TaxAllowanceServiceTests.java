package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.*;
import com.takakim.investtracker.domain.*;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.repository.*;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.position.LotDisposal;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import com.takakim.investtracker.service.tax.TaxAllowanceService;
import com.takakim.investtracker.service.tax.TaxYearPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaxAllowanceServiceTests {

    private PortfolioRepository portfolioRepository;
    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private TransactionRepository transactionRepository;
    private PortfolioTaxSettingsRepository taxSettingsRepository;
    private PositionEngine positionEngine;
    private FxRateService fxRateService;
    private MarketDataService marketDataService;

    private TaxAllowanceService service;

    private Portfolio portfolio;
    private UUID portfolioId;

    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        taxSettingsRepository = mock(PortfolioTaxSettingsRepository.class);
        positionEngine = mock(PositionEngine.class);
        fxRateService = mock(FxRateService.class);
        marketDataService = mock(MarketDataService.class);

        service = new TaxAllowanceService(
                portfolioRepository,
                accountRepository,
                positionRepository,
                transactionRepository,
                taxSettingsRepository,
                positionEngine,
                fxRateService,
                marketDataService
        );

        portfolioId = UUID.randomUUID();
        portfolio = new Portfolio("UK Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when portfolio is missing")
    void testPortfolioNotFound() {
        UUID unknown = UUID.randomUUID();
        when(portfolioRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.generateTaxReport(unknown, "2024/25", TaxRegime.UK_HMRC));
    }

    @Test
    @DisplayName("Calculates CGT, dividend allowances, and sheltered savings with UK HMRC regime")
    void testGenerateTaxReportComprehensive() {
        // Setup Taxable Account and Tax-Exempt Account (ISA)
        Account taxableAccount = new Account(portfolio, "GIA", "Trading212", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        Account isaAccount = new Account(portfolio, "Stocks & Shares ISA", "Vanguard", new Currency("GBP"), AccountTaxTreatment.TAX_EXEMPT);

        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(taxableAccount, isaAccount));

        // Instruments
        Instrument apple = new Instrument("Apple Inc.", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"), false);
        Instrument vodafone = new Instrument("Vodafone Group", AssetClass.STOCK, "VOD", "GB00BH4HKS39", "LSE", new Currency("GBP"), false);

        // Taxable Account disposals:
        // Trade 1: Gain £4,500 on 2024-06-10
        Instant date1 = LocalDate.of(2024, 6, 10).atStartOfDay().toInstant(ZoneOffset.UTC);
        LotDisposal disp1 = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), date1,
                new BigDecimal("10.00000000"),
                new BigDecimal("1500.0000"),
                new BigDecimal("6000.0000"),
                new BigDecimal("4500.0000"),
                "GBP");

        // Trade 2: Loss £500 on 2024-08-15
        Instant date2 = LocalDate.of(2024, 8, 15).atStartOfDay().toInstant(ZoneOffset.UTC);
        LotDisposal disp2 = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), date2,
                new BigDecimal("20.00000000"),
                new BigDecimal("1500.0000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("-500.0000"),
                "GBP");

        PositionCalculationResult appleResult = new PositionCalculationResult(
                taxableAccount.getId(), apple.getId(), CostBasisMethod.FIFO,
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", BigDecimal.ZERO, new BigDecimal("4500.0000"),
                List.of(), List.of(disp1)
        );
        PositionCalculationResult vodafoneResult = new PositionCalculationResult(
                taxableAccount.getId(), vodafone.getId(), CostBasisMethod.FIFO,
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", BigDecimal.ZERO, new BigDecimal("-500.0000"),
                List.of(), List.of(disp2)
        );
        when(positionEngine.calculate(eq(taxableAccount), eq(apple), any(), eq(CostBasisMethod.FIFO)))
                .thenReturn(appleResult);
        when(positionEngine.calculate(eq(taxableAccount), eq(vodafone), any(), eq(CostBasisMethod.FIFO)))
                .thenReturn(vodafoneResult);

        // ISA Account disposal: Gain £2,000 on 2024-07-20
        Instant date3 = LocalDate.of(2024, 7, 20).atStartOfDay().toInstant(ZoneOffset.UTC);
        LotDisposal isaDisp = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), date3,
                new BigDecimal("10.00000000"),
                new BigDecimal("3000.0000"),
                new BigDecimal("5000.0000"),
                new BigDecimal("2000.0000"),
                "GBP");
        PositionCalculationResult isaResult = new PositionCalculationResult(
                isaAccount.getId(), apple.getId(), CostBasisMethod.FIFO,
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", BigDecimal.ZERO, new BigDecimal("2000.0000"),
                List.of(), List.of(isaDisp)
        );
        when(positionEngine.calculate(eq(isaAccount), any(), any(), eq(CostBasisMethod.FIFO)))
                .thenReturn(isaResult);

        // Transactions in accounts
        Transaction tx1 = new Transaction(taxableAccount, apple, TransactionType.SELL, date1, null,
                new BigDecimal("10"), new BigDecimal("600.00"), new BigDecimal("6000.00"), null, null,
                "GBP", null, null, null, null);
        Transaction tx2 = new Transaction(taxableAccount, vodafone, TransactionType.SELL, date2, null,
                new BigDecimal("20"), new BigDecimal("50.00"), new BigDecimal("1000.00"), null, null,
                "GBP", null, null, null, null);
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(taxableAccount.getId()))
                .thenReturn(List.of(tx1, tx2));

        Transaction isaTx = new Transaction(isaAccount, apple, TransactionType.SELL, date3, null,
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00"), null, null,
                "GBP", null, null, null, null);
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(isaAccount.getId()))
                .thenReturn(List.of(isaTx));

        // Dividends
        // Taxable dividend: £1,200 gross, £180 wht on 2024-09-01
        Instant divDate1 = LocalDate.of(2024, 9, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Transaction divTx1 = new Transaction(taxableAccount, apple, TransactionType.DIVIDEND, divDate1, null,
                null, null, new BigDecimal("1200.00"), null, new BigDecimal("180.00"),
                "GBP", null, null, null, null);

        // Sheltered dividend in ISA: £600 gross on 2024-10-01
        Instant divDate2 = LocalDate.of(2024, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Transaction divTx2 = new Transaction(isaAccount, vodafone, TransactionType.DIVIDEND, divDate2, null,
                null, null, new BigDecimal("600.00"), null, BigDecimal.ZERO,
                "GBP", null, null, null, null);

        when(transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(taxableAccount.getId(), TransactionType.DIVIDEND))
                .thenReturn(List.of(divTx1));
        when(transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(isaAccount.getId(), TransactionType.DIVIDEND))
                .thenReturn(List.of(divTx2));

        // Custom tax settings with £500 loss carryforward
        PortfolioTaxSettings customSettings = new PortfolioTaxSettings(
                portfolio, "2024/25", TaxRegime.UK_HMRC,
                new BigDecimal("3000.00"), new BigDecimal("500.00"),
                new BigDecimal("500.00"), "Prior year loss"
        );
        when(taxSettingsRepository.findByPortfolioIdAndTaxYear(portfolioId, "2024/25"))
                .thenReturn(Optional.of(customSettings));

        // Positions for tax-loss harvesting
        Position losingPos = new Position(taxableAccount, vodafone, new Quantity(new BigDecimal("10.00000000")),
                new Money(new BigDecimal("1000.0000"), new Currency("GBP")));
        when(positionRepository.findByAccountId(taxableAccount.getId())).thenReturn(List.of(losingPos));
        when(positionRepository.findByAccountId(isaAccount.getId())).thenReturn(List.of());

        // Market data quote for vodafone: price £75.00
        when(marketDataService.getLatestPrice(eq(vodafone.getId()), any()))
                .thenReturn(new PriceQuote(vodafone.getId(), new BigDecimal("75.00"), "GBP", Instant.now(), ObservationSourceType.MANUAL, "TEST", false, null));

        TaxReportResponse report = service.generateTaxReport(portfolioId, "2024/25", TaxRegime.UK_HMRC);

        assertNotNull(report);
        assertEquals("2024/25", report.taxYear());
        assertEquals(TaxRegime.UK_HMRC, report.taxRegime());

        // CGT Verification:
        // Gross Gains: £4,500.0000, Gross Losses: £500.0000 -> Net: £4,000.0000
        // Loss Carryforward applied: £500.0000 -> Net taxable before allowance: £3,500.0000
        // Statutory allowance: £3,000.0000 -> Allowance used: £3,000.0000, Remaining: £0.0000
        // Taxable Capital Gain: £500.0000
        // Estimated Tax: Basic (18% of 500) = £90.0000, Higher (24% of 500) = £120.0000
        CapitalGainsTaxSummaryDto cgt = report.capitalGains();
        assertEquals(new BigDecimal("4500.0000"), cgt.grossRealizedGains());
        assertEquals(new BigDecimal("500.0000"), cgt.grossRealizedLosses());
        assertEquals(new BigDecimal("4000.0000"), cgt.netRealizedGainLoss());
        assertEquals(new BigDecimal("500.0000"), cgt.lossCarryforwardApplied());
        assertEquals(new BigDecimal("3500.0000"), cgt.netTaxableGainBeforeAllowance());
        assertEquals(new BigDecimal("3000.0000"), cgt.annualExemptAmount());
        assertEquals(new BigDecimal("3000.0000"), cgt.allowanceUsed());
        assertEquals(new BigDecimal("0.0000"), cgt.allowanceRemaining());
        assertEquals(new BigDecimal("500.0000"), cgt.taxableCapitalGain());
        assertEquals(new BigDecimal("90.0000"), cgt.estimatedTaxBasicRate());
        assertEquals(new BigDecimal("120.0000"), cgt.estimatedTaxHigherRate());
        assertEquals(2, cgt.totalDisposalsCount());

        // Dividend Verification:
        // Gross: £1,200.0000, WHT: £180.0000, Net: £1,020.0000
        // Allowance: £500.0000, Used: £500.0000, Remaining: £0.0000
        // Taxable Dividend Income: £700.0000
        // Estimated Tax: Basic (8.75% of 700) = £61.2500, Higher (33.75% of 700) = £236.2500
        DividendTaxSummaryDto div = report.dividendIncome();
        assertEquals(new BigDecimal("1200.0000"), div.totalGrossDividends());
        assertEquals(new BigDecimal("180.0000"), div.totalWithholdingTax());
        assertEquals(new BigDecimal("500.0000"), div.annualDividendAllowance());
        assertEquals(new BigDecimal("500.0000"), div.allowanceUsed());
        assertEquals(new BigDecimal("0.0000"), div.allowanceRemaining());
        assertEquals(new BigDecimal("700.0000"), div.taxableDividendIncome());
        assertEquals(new BigDecimal("61.2500"), div.estimatedTaxBasicRate());
        assertEquals(new BigDecimal("236.2500"), div.estimatedTaxHigherRate());
        assertEquals(1, div.totalDividendsCount());

        // Sheltered Summary Verification:
        // ISA Gain £2,000, ISA Dividend £600
        // Estimated CGT saved: 20% of 2000 = £400
        // Estimated Div saved: 8.75% of 600 = £52.50
        TaxShelteredSummaryDto sheltered = report.shelteredSummary();
        assertEquals(new BigDecimal("2000.0000"), sheltered.shelteredRealizedGains());
        assertEquals(new BigDecimal("600.0000"), sheltered.shelteredGrossDividends());
        assertEquals(new BigDecimal("400.0000"), sheltered.estimatedCapitalGainsTaxSaved());
        assertEquals(new BigDecimal("52.5000"), sheltered.estimatedDividendTaxSaved());
        assertEquals(new BigDecimal("452.5000"), sheltered.totalEstimatedTaxSaved());

        // Loss harvesting candidate
        assertEquals(1, report.lossHarvestOpportunities().size());
        TaxLossHarvestOpportunityDto opp = report.lossHarvestOpportunities().get(0);
        assertEquals("Vodafone Group", opp.instrumentName());
        assertEquals(new BigDecimal("250.0000"), opp.unrealizedLossBase()); // 1000 cost - (10 * 75 = 750) = 250
    }

    @Test
    @DisplayName("Handles net capital losses correctly (no tax, carryforward untouched)")
    void testNetCapitalLossScenario() {
        Account taxableAccount = new Account(portfolio, "GIA", "Trading212", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(taxableAccount));

        Instrument apple = new Instrument("Apple Inc.", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("GBP"), false);
        Instant date = LocalDate.of(2024, 5, 10).atStartOfDay().toInstant(ZoneOffset.UTC);

        // Loss of £1,500
        LotDisposal lossDisp = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), date,
                new BigDecimal("10.00000000"),
                new BigDecimal("3500.0000"),
                new BigDecimal("2000.0000"),
                new BigDecimal("-1500.0000"),
                "GBP");

        Transaction tx = new Transaction(taxableAccount, apple, TransactionType.SELL, date, null,
                new BigDecimal("10"), new BigDecimal("200.00"), new BigDecimal("2000.00"), null, null,
                "GBP", null, null, null, null);
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(taxableAccount.getId()))
                .thenReturn(List.of(tx));

        PositionCalculationResult lossResult = new PositionCalculationResult(
                taxableAccount.getId(), apple.getId(), CostBasisMethod.FIFO,
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", BigDecimal.ZERO, new BigDecimal("-1500.0000"),
                List.of(), List.of(lossDisp)
        );
        when(positionEngine.calculate(eq(taxableAccount), any(), any(), eq(CostBasisMethod.FIFO)))
                .thenReturn(lossResult);
        when(transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(taxableAccount.getId(), TransactionType.DIVIDEND))
                .thenReturn(List.of());
        when(positionRepository.findByAccountId(taxableAccount.getId())).thenReturn(List.of());

        TaxReportResponse report = service.generateTaxReport(portfolioId, "2024/25", TaxRegime.UK_HMRC);

        CapitalGainsTaxSummaryDto cgt = report.capitalGains();
        assertEquals(new BigDecimal("-1500.0000"), cgt.netRealizedGainLoss());
        assertEquals(new BigDecimal("0.0000"), cgt.lossCarryforwardApplied());
        assertEquals(new BigDecimal("0.0000"), cgt.netTaxableGainBeforeAllowance());
        assertEquals(new BigDecimal("0.0000"), cgt.allowanceUsed());
        assertEquals(new BigDecimal("3000.0000"), cgt.allowanceRemaining());
        assertEquals(new BigDecimal("0.0000"), cgt.taxableCapitalGain());
    }

    @Test
    @DisplayName("Handles currency conversion and FX rate warning fallback")
    void testCurrencyConversionWithWarning() {
        Account taxableAccount = new Account(portfolio, "USD Account", "IBKR", new Currency("USD"), AccountTaxTreatment.TAXABLE);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(taxableAccount));

        Instrument msft = new Instrument("Microsoft", AssetClass.STOCK, "MSFT", "US5949181045", "NASDAQ", new Currency("USD"), false);
        Instant date = LocalDate.of(2024, 7, 1).atStartOfDay().toInstant(ZoneOffset.UTC);

        LotDisposal disp = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), date,
                new BigDecimal("5.00000000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("1500.0000"),
                new BigDecimal("500.0000"),
                "USD");

        Transaction tx = new Transaction(taxableAccount, msft, TransactionType.SELL, date, null,
                new BigDecimal("5"), new BigDecimal("300.00"), new BigDecimal("1500.00"), null, null,
                "USD", null, null, null, null);
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(taxableAccount.getId()))
                .thenReturn(List.of(tx));

        PositionCalculationResult calcResult = new PositionCalculationResult(
                taxableAccount.getId(), msft.getId(), CostBasisMethod.FIFO,
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", BigDecimal.ZERO, new BigDecimal("500.0000"),
                List.of(), List.of(disp)
        );
        when(positionEngine.calculate(eq(taxableAccount), any(), any(), eq(CostBasisMethod.FIFO)))
                .thenReturn(calcResult);
        when(transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(taxableAccount.getId(), TransactionType.DIVIDEND))
                .thenReturn(List.of());
        when(positionRepository.findByAccountId(taxableAccount.getId())).thenReturn(List.of());

        // Simulate FxRateService failure triggering 1.0 parity and warning
        when(fxRateService.convert(any(), any(), any()))
                .thenThrow(new RuntimeException("Rate not found"));

        TaxReportResponse report = service.generateTaxReport(portfolioId, "2024/25", TaxRegime.UK_HMRC);

        assertFalse(report.warnings().isEmpty());
        assertTrue(report.warnings().get(0).contains("Missing FX rate"));
        assertEquals(new BigDecimal("500.0000"), report.capitalGains().grossRealizedGains());
    }

    @Test
    @DisplayName("getAvailableTaxYears extracts distinct years from transactions")
    void testGetAvailableTaxYears() {
        Account acc = new Account(portfolio, "GIA", "Broker", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));

        Instant d1 = LocalDate.of(2023, 11, 10).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant d2 = LocalDate.of(2024, 5, 20).atStartOfDay().toInstant(ZoneOffset.UTC);

        Transaction tx1 = new Transaction(acc, null, TransactionType.DEPOSIT, d1, null,
                null, null, BigDecimal.TEN, null, null, "GBP", null, null, null, null);
        Transaction tx2 = new Transaction(acc, null, TransactionType.DEPOSIT, d2, null,
                null, null, BigDecimal.TEN, null, null, "GBP", null, null, null, null);

        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(tx1, tx2));

        AvailableTaxYearsResponse resp = service.getAvailableTaxYears(portfolioId);

        assertNotNull(resp);
        assertTrue(resp.availableUkTaxYears().contains("2023/24"));
        assertTrue(resp.availableUkTaxYears().contains("2024/25"));
        assertTrue(resp.availableCalendarYears().contains("2023"));
        assertTrue(resp.availableCalendarYears().contains("2024"));
    }

    @Test
    @DisplayName("saveTaxSettings creates and updates tax settings successfully")
    void testSaveTaxSettings() {
        TaxSettingsRequest req = new TaxSettingsRequest(
                "2024/25", TaxRegime.UK_HMRC,
                new BigDecimal("3000.00"), new BigDecimal("500.00"),
                new BigDecimal("1200.00"), "Custom carryforward"
        );

        when(taxSettingsRepository.findByPortfolioIdAndTaxYear(portfolioId, "2024/25"))
                .thenReturn(Optional.empty());

        PortfolioTaxSettings savedMock = new PortfolioTaxSettings(
                portfolio, "2024/25", TaxRegime.UK_HMRC,
                req.cgtAllowance(), req.dividendAllowance(), req.lossCarryforward(), req.notes()
        );
        when(taxSettingsRepository.save(any(PortfolioTaxSettings.class))).thenReturn(savedMock);

        TaxSettingsResponse resp = service.saveTaxSettings(portfolioId, req);

        assertNotNull(resp);
        assertEquals("2024/25", resp.taxYear());
        assertEquals(TaxRegime.UK_HMRC, resp.taxRegime());
        assertEquals(new BigDecimal("1200.00"), resp.lossCarryforward());
        assertEquals("Custom carryforward", resp.notes());
    }

    @Test
    @DisplayName("saveTaxSettings updates existing settings and falls back to current tax year when unspecified")
    void testUpdateExistingTaxSettingsAndDefaultYear() {
        String currentUkYear = TaxYearPeriod.currentUkTaxYearLabel();
        TaxSettingsRequest req = new TaxSettingsRequest(
                null, TaxRegime.UK_HMRC,
                new BigDecimal("2500.00"), new BigDecimal("400.00"),
                BigDecimal.ZERO, "Updated note"
        );

        PortfolioTaxSettings existing = new PortfolioTaxSettings(
                portfolio, currentUkYear, TaxRegime.UK_HMRC,
                new BigDecimal("3000.00"), new BigDecimal("500.00"),
                BigDecimal.ZERO, "Old note"
        );
        UUID settingId = existing.getId();

        when(taxSettingsRepository.findByPortfolioIdAndTaxYear(portfolioId, currentUkYear))
                .thenReturn(Optional.of(existing));
        when(taxSettingsRepository.existsById(settingId)).thenReturn(true);
        when(taxSettingsRepository.save(existing)).thenReturn(existing);

        TaxSettingsResponse resp = service.saveTaxSettings(portfolioId, req);

        assertNotNull(resp);
        assertEquals(currentUkYear, resp.taxYear());
        assertEquals(new BigDecimal("2500.00"), resp.cgtAllowance());
        assertEquals(new BigDecimal("400.00"), resp.dividendAllowance());
        assertEquals("Updated note", resp.notes());
    }

    @Test
    @DisplayName("generateTaxReport with Calendar Year regime, cash dividend, and outside-period filters")
    void testGenerateTaxReportCalendarYearAndEdgeCases() {
        Account taxableAccount = new Account(portfolio, "GIA", "Trading212", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(taxableAccount));

        // 1. Transaction in 2025 and 1 outside (2024)
        Instant inPeriod = LocalDate.of(2025, 6, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant outsidePeriod = LocalDate.of(2024, 6, 1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Instrument inst = new Instrument("Test Co", AssetClass.STOCK, "TEST", "GB0012345678", "LSE", new Currency("GBP"), false);

        LotDisposal inDisp = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), inPeriod,
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("100"), "GBP");
        LotDisposal outDisp = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), outsidePeriod,
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("100"), "GBP");

        Transaction txIn = new Transaction(taxableAccount, inst, TransactionType.SELL, inPeriod, null,
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("200"), null, null, "GBP", null, null, null, null);
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(taxableAccount.getId()))
                .thenReturn(List.of(txIn));

        PositionCalculationResult calcResult = new PositionCalculationResult(
                taxableAccount.getId(), inst.getId(), CostBasisMethod.FIFO,
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", BigDecimal.ZERO, new BigDecimal("100"),
                List.of(), List.of(inDisp, outDisp)
        );
        when(positionEngine.calculate(eq(taxableAccount), eq(inst), any(), eq(CostBasisMethod.FIFO)))
                .thenReturn(calcResult);

        // Dividend with instrument in period, plus 1 outside period
        Transaction divIn = new Transaction(taxableAccount, inst, TransactionType.DIVIDEND, inPeriod, null,
                null, null, new BigDecimal("100.00"), null, null, "GBP", null, null, null, null);
        Transaction divOut = new Transaction(taxableAccount, inst, TransactionType.DIVIDEND, outsidePeriod, null,
                null, null, new BigDecimal("100.00"), null, null, "GBP", null, null, null, null);

        when(transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(taxableAccount.getId(), TransactionType.DIVIDEND))
                .thenReturn(List.of(divIn, divOut));

        // Settings with null allowances to test fallback
        PortfolioTaxSettings nullAllowances = new PortfolioTaxSettings(
                portfolio, "2025", TaxRegime.CALENDAR_YEAR, null, null, null, null
        );
        when(taxSettingsRepository.findByPortfolioIdAndTaxYear(portfolioId, "2025"))
                .thenReturn(Optional.of(nullAllowances));

        // Active position with profit (should NOT be loss-harvested)
        Position profitablePos = new Position(taxableAccount, inst, new Quantity(new BigDecimal("10")),
                new Money(new BigDecimal("50.00"), new Currency("GBP")));
        when(positionRepository.findByAccountId(taxableAccount.getId())).thenReturn(List.of(profitablePos));
        when(marketDataService.getLatestPrice(eq(inst.getId()), any()))
                .thenReturn(new PriceQuote(inst.getId(), new BigDecimal("100.00"), "GBP", Instant.now(), ObservationSourceType.MANUAL, "T", false, null));

        TaxReportResponse report = service.generateTaxReport(portfolioId, "2025", TaxRegime.CALENDAR_YEAR);

        assertNotNull(report);
        assertEquals("2025", report.taxYear());
        assertEquals(TaxRegime.CALENDAR_YEAR, report.taxRegime());
        assertEquals(1, report.disposals().size());
        assertEquals(1, report.dividends().size());
        assertEquals("Test Co", report.dividends().get(0).instrumentName());
        assertTrue(report.lossHarvestOpportunities().isEmpty());
    }

    @Test
    @DisplayName("generateTaxReport with sheltered loss, null regime, and cash dividend with null instrument and null amounts")
    void testShelteredLossAndCashDividendWithoutInstrument() {
        Account isaAccount = new Account(portfolio, "Stocks & Shares ISA", "Vanguard", new Currency("GBP"), AccountTaxTreatment.TAX_EXEMPT);
        Account giaAccount = new Account(portfolio, "GIA", "Trading212", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(isaAccount, giaAccount));

        Instant dateInPeriod = LocalDate.of(2024, 8, 15).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instrument inst = new Instrument("Test Co", AssetClass.STOCK, "TEST", "GB0012345678", "LSE", new Currency("GBP"), false);

        // Sheltered disposal with realized LOSS: proceeds £50, cost £150, loss -£100
        LotDisposal lossDisp = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), dateInPeriod,
                new BigDecimal("10"), new BigDecimal("50"), new BigDecimal("150"), new BigDecimal("-100"), "GBP");
        PositionCalculationResult calcResult = new PositionCalculationResult(
                isaAccount.getId(), inst.getId(), CostBasisMethod.FIFO,
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", BigDecimal.ZERO, new BigDecimal("-100"),
                List.of(), List.of(lossDisp)
        );
        Transaction sellTx = new Transaction(isaAccount, inst, TransactionType.SELL, dateInPeriod, null,
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("50"), null, null, "GBP", null, null, null, null);
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(isaAccount.getId()))
                .thenReturn(List.of(sellTx));
        when(positionEngine.calculate(eq(isaAccount), eq(inst), any(), eq(CostBasisMethod.FIFO)))
                .thenReturn(calcResult);

        // Cash dividend with null instrument, null currency, and null gross/tax/net amounts
        Transaction nullDivTx = mock(Transaction.class);
        when(nullDivTx.getId()).thenReturn(UUID.randomUUID());
        when(nullDivTx.getType()).thenReturn(TransactionType.DIVIDEND);
        when(nullDivTx.getTradeDate()).thenReturn(dateInPeriod);
        when(nullDivTx.getInstrument()).thenReturn(null);
        when(nullDivTx.getCurrency()).thenReturn(null);
        when(nullDivTx.getGrossAmount()).thenReturn(null);
        when(nullDivTx.getTaxAmount()).thenReturn(null);
        when(nullDivTx.getNetAmount()).thenReturn(null);

        when(transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(giaAccount.getId(), TransactionType.DIVIDEND))
                .thenReturn(List.of(nullDivTx));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(giaAccount.getId()))
                .thenReturn(List.of());

        // Call with regime = null (to test fallback to UK_HMRC)
        TaxReportResponse report = service.generateTaxReport(portfolioId, "2024/25", null);

        assertNotNull(report);
        assertEquals(TaxRegime.UK_HMRC, report.taxRegime());
        assertEquals(0, new BigDecimal("100.00").compareTo(report.shelteredSummary().shelteredRealizedLosses()));
        assertEquals(1, report.dividends().size());
        assertEquals("Cash Dividend", report.dividends().get(0).instrumentName());
        assertNull(report.dividends().get(0).instrumentId());
        assertNull(report.dividends().get(0).ticker());
        assertEquals(0, new BigDecimal("0.00").compareTo(report.dividends().get(0).grossAmountBase()));
    }

    @Test
    @DisplayName("convertCurrency handles FX conversion exceptions by recording warnings and falling back to parity")
    void testFxConversionExceptionWarning() {
        Account giaAccount = new Account(portfolio, "GIA", "Trading212", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(giaAccount));

        Instant dateInPeriod = LocalDate.of(2024, 7, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instrument usdStock = new Instrument("US Stock", AssetClass.STOCK, "US", "US1111111111", "NYSE", new Currency("USD"), false);

        LotDisposal disp = new LotDisposal(UUID.randomUUID(), UUID.randomUUID(), dateInPeriod,
                new BigDecimal("5"), new BigDecimal("100"), new BigDecimal("60"), new BigDecimal("40"), "USD");
        PositionCalculationResult calcResult = new PositionCalculationResult(
                giaAccount.getId(), usdStock.getId(), CostBasisMethod.FIFO,
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", BigDecimal.ZERO, new BigDecimal("40"),
                List.of(), List.of(disp)
        );
        Transaction tx = new Transaction(giaAccount, usdStock, TransactionType.SELL, dateInPeriod, null,
                new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("100"), null, null, "USD", null, null, null, null);
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(giaAccount.getId()))
                .thenReturn(List.of(tx));
        when(positionEngine.calculate(eq(giaAccount), eq(usdStock), any(), eq(CostBasisMethod.FIFO)))
                .thenReturn(calcResult);

        // FxRateService throws exception for USD -> GBP
        when(fxRateService.convert(any(), any(), any())).thenThrow(new RuntimeException("FX provider offline"));

        TaxReportResponse report = service.generateTaxReport(portfolioId, "2024/25", TaxRegime.UK_HMRC);

        assertNotNull(report);
        assertFalse(report.warnings().isEmpty());
        assertTrue(report.warnings().get(0).contains("Missing FX rate USD/GBP"));
    }

    @Test
    @DisplayName("identifyLossHarvestOpportunities covers all branches: inactive positions, price exceptions, null quotes, and loss ordering")
    void testLossHarvestOpportunitiesBranches() {
        Account giaAccount = new Account(portfolio, "GIA", "Trading212", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        Account exemptAccount = new Account(portfolio, "ISA", "Vanguard", new Currency("GBP"), AccountTaxTreatment.TAX_EXEMPT);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(exemptAccount, giaAccount));

        Instrument inst1 = new Instrument("Inst 1", AssetClass.STOCK, "I1", "GB0000000001", "LSE", new Currency("USD"), false);
        Instrument inst2 = new Instrument("Inst 2", AssetClass.STOCK, "I2", "GB0000000002", "LSE", new Currency("GBP"), false);
        Instrument inst3 = new Instrument("Inst 3", AssetClass.STOCK, "I3", "GB0000000003", "LSE", new Currency("GBP"), false);
        Instrument inst4 = new Instrument("Inst 4", AssetClass.STOCK, "I4", "GB0000000004", "LSE", new Currency("GBP"), false);

        // Pos 1: in GIA, market price available with USD currency, has unrealized loss
        Position pos1 = new Position(giaAccount, inst1, new Quantity(new BigDecimal("10")),
                new Money(new BigDecimal("2000.00"), new Currency("GBP")));
        // Pos 2: marketDataService throws exception
        Position pos2 = new Position(giaAccount, inst2, new Quantity(new BigDecimal("5")),
                new Money(new BigDecimal("100.00"), new Currency("GBP")));
        // Pos 3: marketDataService returns null quote
        Position pos3 = new Position(giaAccount, inst3, new Quantity(new BigDecimal("5")),
                new Money(new BigDecimal("100.00"), new Currency("GBP")));
        // Pos 4: closed position (status != ACTIVE)
        Position pos4 = new Position(giaAccount, inst4, new Quantity(BigDecimal.ZERO),
                new Money(BigDecimal.ZERO, new Currency("GBP")));
        pos4.archive();

        when(positionRepository.findByAccountId(giaAccount.getId()))
                .thenReturn(List.of(pos1, pos2, pos3, pos4));

        when(marketDataService.getLatestPrice(eq(inst1.getId()), any()))
                .thenReturn(new PriceQuote(inst1.getId(), new BigDecimal("50.00"), "GBP", Instant.now(), ObservationSourceType.MANUAL, "T", false, null));
        when(marketDataService.getLatestPrice(eq(inst2.getId()), any()))
                .thenThrow(new RuntimeException("Market data unreachable"));
        when(marketDataService.getLatestPrice(eq(inst3.getId()), any()))
                .thenReturn(null);

        TaxReportResponse report = service.generateTaxReport(portfolioId, "2024/25", TaxRegime.UK_HMRC);

        assertNotNull(report);
        assertEquals(1, report.lossHarvestOpportunities().size());
        assertEquals("Inst 1", report.lossHarvestOpportunities().get(0).instrumentName());
        assertEquals(0, new BigDecimal("1500.00").compareTo(report.lossHarvestOpportunities().get(0).unrealizedLossBase()));
    }

    @Test
    @DisplayName("getAvailableTaxYears handles transactions with null trade dates gracefully")
    void testAvailableTaxYearsWithNullTradeDate() {
        Account acc = new Account(portfolio, "GIA", "Trading212", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));

        Transaction nullDateTx = mock(Transaction.class);
        when(nullDateTx.getTradeDate()).thenReturn(null);
        Instant validDate = LocalDate.of(2023, 5, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Transaction validTx = new Transaction(acc, null, TransactionType.DEPOSIT, validDate, null,
                null, null, new BigDecimal("100"), null, null, "GBP", null, null, null, null);

        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(nullDateTx, validTx));

        AvailableTaxYearsResponse resp = service.getAvailableTaxYears(portfolioId);

        assertNotNull(resp);
        assertTrue(resp.availableUkTaxYears().contains("2023/24"));
    }

    @Test
    @DisplayName("saveTaxSettings throws ResourceNotFoundException when portfolio is missing")
    void testSaveTaxSettingsPortfolioNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(portfolioRepository.findById(unknownId)).thenReturn(Optional.empty());

        TaxSettingsRequest req = new TaxSettingsRequest(
                "2024/25", TaxRegime.UK_HMRC, null, null, null, null
        );

        assertThrows(ResourceNotFoundException.class, () -> service.saveTaxSettings(unknownId, req));
    }
}

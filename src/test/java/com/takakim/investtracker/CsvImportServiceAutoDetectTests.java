package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.ImportBatchRepository;
import com.takakim.investtracker.repository.ImportRecordRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.service.TransactionService;
import com.takakim.investtracker.service.csv.AjBellCsvParser;
import com.takakim.investtracker.service.csv.BrokerCsvParser;
import com.takakim.investtracker.service.csv.CsvImportService;
import com.takakim.investtracker.service.csv.DegiroCsvParser;
import com.takakim.investtracker.service.csv.FreetradeCsvParser;
import com.takakim.investtracker.service.csv.InteractiveBrokersCsvParser;
import com.takakim.investtracker.service.csv.InvestEngineCsvParser;
import com.takakim.investtracker.service.csv.Trading212CsvParser;
import com.takakim.investtracker.service.csv.VanguardUkCsvParser;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import com.takakim.investtracker.service.position.PositionEngine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceAutoDetectTests {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private ImportBatchRepository importBatchRepository;
    @Mock
    private ImportRecordRepository importRecordRepository;
    @Mock
    private TransactionService transactionService;
    @Mock
    private PositionEngine positionEngine;

    private CsvImportService service;
    private List<BrokerCsvParser> parsers;

    @BeforeEach
    void setUp() {
        parsers = List.of(
                new Trading212CsvParser(),
                new FreetradeCsvParser(),
                new InvestEngineCsvParser(),
                new VanguardUkCsvParser(),
                new InteractiveBrokersCsvParser(),
                new DegiroCsvParser(),
                new AjBellCsvParser()
        );
        service = new CsvImportService(
                accountRepository,
                instrumentRepository,
                importBatchRepository,
                importRecordRepository,
                transactionService,
                positionEngine,
                parsers
        );
    }

    @Test
    @DisplayName("Returns sorted list of all 7 supported brokers")
    void returnsSupportedBrokers() {
        List<String> brokers = service.getSupportedBrokers();
        assertEquals(7, brokers.size());
        assertTrue(brokers.contains("Trading 212"));
        assertTrue(brokers.contains("Freetrade"));
        assertTrue(brokers.contains("InvestEngine"));
        assertTrue(brokers.contains("Vanguard UK"));
        assertTrue(brokers.contains("Interactive Brokers"));
        assertTrue(brokers.contains("DEGIRO"));
        assertTrue(brokers.contains("AJ Bell"));
    }

    @Test
    @DisplayName("Auto-detects broker templates from CSV content with HIGH confidence")
    void autoDetectsBrokerTemplates() {
        // Trading 212
        String t212 = "Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total (GBP)\n";
        var res1 = service.detectBroker(t212);
        assertEquals("Trading 212", res1.brokerName());
        assertEquals("HIGH", res1.confidence());
        assertTrue(res1.isSupported());

        // Vanguard UK with BOM
        String vanguard = "\uFEFFDate,Transaction Type,Investment Name,ISIN,Units,Unit Price,Amount,Charges,Net Amount\n";
        var res2 = service.detectBroker(vanguard);
        assertEquals("Vanguard UK", res2.brokerName());
        assertEquals("HIGH", res2.confidence());

        // Interactive Brokers
        String ibkr = "Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price\n";
        var res3 = service.detectBroker(ibkr);
        assertEquals("Interactive Brokers", res3.brokerName());

        // DEGIRO
        String degiro = "Date,Time,Product,ISIN,Reference,Venue,Quantity,Price,Local value,Value,Exchange rate,Fee,Total\n";
        var res4 = service.detectBroker(degiro);
        assertEquals("DEGIRO", res4.brokerName());

        // AJ Bell
        String ajbell = "Date,Transaction,Security,Ticker,ISIN,Quantity,Price,Value,Charges,Net Value\n";
        var res5 = service.detectBroker(ajbell);
        assertEquals("AJ Bell", res5.brokerName());

        // Unknown / Empty
        var resEmpty = service.detectBroker("");
        assertEquals("Unknown", resEmpty.brokerName());
        assertFalse(resEmpty.isSupported());

        var resUnknown = service.detectBroker("ColA,ColB,ColC\n1,2,3\n");
        assertEquals("Unknown", resUnknown.brokerName());
        assertFalse(resUnknown.isSupported());
    }

    @Test
    @DisplayName("Preview with broker override uses explicit parser")
    void previewWithBrokerOverride() {
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Portfolio p = new Portfolio("Main", new com.takakim.investtracker.domain.Currency("GBP"), com.takakim.investtracker.domain.CostBasisMethod.FIFO, com.takakim.investtracker.domain.ReturnMethod.TWR);
        java.lang.reflect.Field idF;
        try {
            idF = Portfolio.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(p, portfolioId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Account acc = new Account(p, "Vanguard SIPP", "Vanguard", new com.takakim.investtracker.domain.Currency("GBP"));
        try {
            idF = Account.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(acc, accountId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));

        String csv = """
            Date,Transaction Type,Investment Name,ISIN,Units,Unit Price,Amount,Charges,Net Amount
            15/01/2026,Buy,Vanguard S&P 500 UCITS ETF,IE00B3XXRP09,20.0000,£100.00,£2000.00,£0.00,£2000.00
            """;

        var preview = service.previewImport(portfolioId, accountId, "vanguard.csv", csv, "Vanguard UK");
        assertEquals("Vanguard UK", preview.brokerName());
        assertEquals(1, preview.totalRows());
        assertEquals(1, preview.importableRows());

        // Throws if unsupported broker override requested
        assertThrows(IllegalArgumentException.class, () ->
                service.previewImport(portfolioId, accountId, "test.csv", csv, "NonExistentBroker")
        );
    }

    @Test
    @DisplayName("executeImport handles duplicates and ignored rows properly")
    void executeImport_withDuplicatesAndIgnoredRows() {
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Portfolio p = new Portfolio("Main", new com.takakim.investtracker.domain.Currency("GBP"), com.takakim.investtracker.domain.CostBasisMethod.FIFO, com.takakim.investtracker.domain.ReturnMethod.TWR);
        try {
            var idF = Portfolio.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(p, portfolioId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Account acc = new Account(p, "Vanguard SIPP", "Vanguard", new com.takakim.investtracker.domain.Currency("GBP"));
        try {
            var idF = Account.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(acc, accountId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));
        when(importBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Mock duplicate record for first transaction
        when(importRecordRepository.existsByAccountIdAndFingerprint(eq(accountId), anyString()))
                .thenReturn(true)
                .thenReturn(false);

        String csv = """
            Date,Transaction Type,Investment Name,ISIN,Units,Unit Price,Amount,Charges,Net Amount
            15/01/2026,Buy,Vanguard S&P 500 UCITS ETF,IE00B3XXRP09,20.0000,£100.00,£2000.00,£0.00,£2000.00
            invalid-date,Buy,Fund,IE0012345678,10,10,100,0,100
            20/01/2026,Buy,Vanguard FTSE All-World,IE00BK5BQT80,5.0000,£90.00,£450.00,£0.00,£450.00
            """;

        var batch = service.executeImport(portfolioId, accountId, "vanguard.csv", csv, null);
        assertNotNull(batch);
        assertEquals(3, batch.getTotalRows());
    }

    @Test
    @DisplayName("Tests inferAssetClass across all domain asset classes and keywords")
    void testInferAssetClassAll() {
        assertEquals(AssetClass.BOND, CsvImportService.inferAssetClass("UK Treasury 2026", null, null));
        assertEquals(AssetClass.BOND, CsvImportService.inferAssetClass("UK Gilt 2030", null, null));
        assertEquals(AssetClass.BOND, CsvImportService.inferAssetClass("US T-Bill", null, null));
        assertEquals(AssetClass.BOND, CsvImportService.inferAssetClass("Corporate Bond", null, null));
        assertEquals(AssetClass.BOND, CsvImportService.inferAssetClass(null, "gb00b123", null));

        assertEquals(AssetClass.REIT, CsvImportService.inferAssetClass("Supermarket REIT", null, null));
        assertEquals(AssetClass.REIT, CsvImportService.inferAssetClass("Commercial Property Trust", null, null));

        assertEquals(AssetClass.MUTUAL_FUND, CsvImportService.inferAssetClass("Global Index Fund", null, null));
        assertEquals(AssetClass.MUTUAL_FUND, CsvImportService.inferAssetClass("Vanguard OEIC Growth", null, null));
        assertEquals(AssetClass.MUTUAL_FUND, CsvImportService.inferAssetClass("Fidelity Unit Trust", null, null));

        assertEquals(AssetClass.ETF, CsvImportService.inferAssetClass("Vanguard FTSE All-World UCITS", null, null));
        assertEquals(AssetClass.ETF, CsvImportService.inferAssetClass("iShares Core S&P 500", null, null));
        assertEquals(AssetClass.ETF, CsvImportService.inferAssetClass("SPDR S&P US Div Aristocrats", null, null));
        assertEquals(AssetClass.ETF, CsvImportService.inferAssetClass("FTSE 100 Index", null, null));
        assertEquals(AssetClass.ETF, CsvImportService.inferAssetClass(null, "xdpg", null));

        assertEquals(AssetClass.CRYPTO, CsvImportService.inferAssetClass("Crypto Holding", null, null));
        assertEquals(AssetClass.CRYPTO, CsvImportService.inferAssetClass("Bitcoin Physical", null, null));
        assertEquals(AssetClass.CRYPTO, CsvImportService.inferAssetClass(null, "btc", null));

        assertEquals(AssetClass.CASH, CsvImportService.inferAssetClass("Cash Interest", null, null));
        assertEquals(AssetClass.STOCK, CsvImportService.inferAssetClass("Apple Inc", "AAPL", "US0378331005"));
        assertEquals(AssetClass.STOCK, CsvImportService.inferAssetClass(null, null, null));

        // Test inferTicker directory
        assertEquals("FWRG", CsvImportService.inferTicker("IE000716YHJ7", "Invesco FTSE All-World"));
        assertEquals("EQQQ", CsvImportService.inferTicker("IE0032077012", "Invesco Nasdaq 100"));
        assertEquals("SPXP", CsvImportService.inferTicker("IE00B3YCGJ38", "Invesco S&P 500"));
        assertEquals("FLXI", CsvImportService.inferTicker("IE00BHZRQZ17", "Franklin FTSE India"));
        assertEquals("VWRP", CsvImportService.inferTicker("IE00BK5BQT80", "Vanguard FTSE All-World"));
        assertEquals("VEVE", CsvImportService.inferTicker("IE00BK5BQV03", "Vanguard FTSE Developed World"));
        assertEquals("VFEM", CsvImportService.inferTicker("IE00BK5BR733", "Vanguard FTSE Emerging Markets"));
        assertEquals("QYLD", CsvImportService.inferTicker("IE00BM8R0J59", "Global X NASDAQ 100 Covered Call"));
        assertEquals("VUSA", CsvImportService.inferTicker("IE00B3XXRP09", null));
        assertEquals("VUAG", CsvImportService.inferTicker("IE00BFMXXD54", null));
        assertNull(CsvImportService.inferTicker("UNKNOWN_ISIN", null));
        assertNull(CsvImportService.inferTicker(null, null));
    }

    @Test
    @DisplayName("Tests selectParser with override and BOM stripping")
    void testSelectParserWithOverrideAndBom() {
        assertNotNull(service.detectBroker("\uFEFFDatum,Tijd,Produkt,ISIN,Referentie,Beurs,Aantal,Koers,Lokale waarde,Waarde,Wisselkoers,Kosten,Totaal\n"));
        assertNotNull(service.detectBroker("Investment,Sedol,ISIN,Units,Price,Charges,Amount\n"));
    }

    @Test
    @DisplayName("calculateFingerprint handles null and empty fields gracefully")
    void calculateFingerprint_nullFields() {
        UUID accountId = UUID.randomUUID();
        ParsedTransactionRow row = new ParsedTransactionRow(
                1, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, false, null, "raw"
        );
        String fp = service.calculateFingerprint(accountId, "Broker", row);
        assertNotNull(fp);
        assertEquals(64, fp.length());
    }

    @Test
    @DisplayName("previewImport and executeImport throw when account not found or wrong portfolio")
    void getAccount_errorPaths() {
        UUID portfolioId = UUID.randomUUID();
        UUID otherPortfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class, () ->
                service.previewImport(portfolioId, accountId, "file.csv", "a,b,c", null)
        );

        Portfolio otherP = new Portfolio("Other", new com.takakim.investtracker.domain.Currency("GBP"), com.takakim.investtracker.domain.CostBasisMethod.FIFO, com.takakim.investtracker.domain.ReturnMethod.TWR);
        try {
            var idF = Portfolio.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(otherP, otherPortfolioId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Account acc = new Account(otherP, "Acc", "Broker", new com.takakim.investtracker.domain.Currency("GBP"));
        try {
            var idF = Account.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(acc, accountId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));

        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class, () ->
                service.previewImport(portfolioId, accountId, "file.csv", "a,b,c", null)
        );
    }

    @Test
    @DisplayName("Tests auto detection with leading blank lines and comments")
    void testAutoDetectWithLeadingBlankLines() {
        String csvWithBlanks = """
            
               
            Action,Time (UTC),ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),Exchange rate,Result,Currency (Result),Total,Currency (Total),Withholding tax,Currency (Withholding tax),Stamp duty,Currency (Stamp duty),Currency conversion fee,Currency (Currency conversion fee)
            Deposit,2024-07-13 06:37:13+00:00,,,,Notes,ID1,,,,,,,200.00,"GBP",,,,,,
            """;
        var result = service.detectBroker(csvWithBlanks);
        assertTrue(result.isSupported());
        assertEquals("Trading 212", result.brokerName());
    }

    @Test
    @DisplayName("Tests executeImport with new instruments, duplicates, ignored, and preview")
    void testExecuteImportLifecycle() {
        UUID portfolioId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Portfolio portfolio = new Portfolio("Main", new com.takakim.investtracker.domain.Currency("GBP"), com.takakim.investtracker.domain.CostBasisMethod.FIFO, com.takakim.investtracker.domain.ReturnMethod.TWR);
        try {
            var idF = Portfolio.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(portfolio, portfolioId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Account acc = new Account(portfolio, "InvestEngine SIPP", "InvestEngine", new com.takakim.investtracker.domain.Currency("GBP"));
        try {
            var idF = Account.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(acc, accountId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));
        when(importBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = """
            Transaction Statement: 01 Feb 2025 - 25 Aug 2026 (Portfolio: DIY 1 / Reference: IP01985296)
            Security / ISIN,Transaction Type,Quantity,Share Price,Total Trade Value,Trade Date/Time,Settlement Date,Broker
            Invesco S&P 500 / ISIN IE00B3YCGJ38,Buy,0.50,£895.00,£447.50,04/03/25 15:07:07,06/03/25,None
            Unknown Asset,UnknownType,1,10,10,04/03/25 15:07:07,06/03/25,None
            """;

        when(instrumentRepository.findByTicker("SPXP")).thenReturn(Optional.empty());
        when(instrumentRepository.findByIsin("IE00B3YCGJ38")).thenReturn(Optional.empty());
        when(instrumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var batch = service.executeImport(portfolioId, accountId, "SIPP.csv", csv);
        assertNotNull(batch);
        assertEquals(2, batch.getTotalRows());
        assertEquals(1, batch.getImportedRows());
        assertEquals(1, batch.getSkippedRows());

        // Preview import
        var preview = service.previewImport(portfolioId, accountId, "SIPP.csv", csv, null);
        assertEquals(2, preview.totalRows());
        assertEquals(1, preview.importableRows());
        assertEquals(1, preview.ignoredRows());
    }
}

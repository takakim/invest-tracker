package com.takakim.investtracker;

import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.csv.FreetradeCsvParser;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvImportDomainTests {

    private final FreetradeCsvParser parser = new FreetradeCsvParser();

    @Test
    @DisplayName("FreetradeCsvParser edge cases: null/empty content, malformed lines, unknown types")
    void parserEdgeCases() {
        // Null or blank csv content
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("Header Only\n").isEmpty());

        // Header check branches
        assertFalse(parser.supports(null));
        assertFalse(parser.supports(List.of("A", "B")));
        assertFalse(parser.supports(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")));
        assertFalse(parser.supports(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")));
        assertEquals("Freetrade", parser.getBrokerName());

        // CSV parsing with escaped quotes inside quotes
        List<String> tokens = FreetradeCsvParser.parseCsvLine("\"Company \"\"Sub\"\" Inc\",ORDER,2026-05-14");
        assertEquals("Company \"Sub\" Inc", tokens.get(0));

        // Unknown row type in CSV
        String csvWithUnknownType = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            Unknown Co,UNKNOWN_TYPE,2026-05-14T12:00:00Z,GBP,100.00,BUY,UNK,US0000000000,10.00,0.00,10.00
            """;
        List<ParsedTransactionRow> rows = parser.parse(csvWithUnknownType);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isIgnored());
        assertTrue(rows.get(0).ignoreReason().contains("Unsupported Freetrade row type"));

        // Malformed row timestamp handling exception fallback
        String csvMalformedTimestamp = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            Broken Time,ORDER,NOT_A_TIMESTAMP,GBP,100.00,BUY,BRK,US0000000001,10.00,0.00,10.00
            """;
        List<ParsedTransactionRow> brokenRows = parser.parse(csvMalformedTimestamp);
        assertEquals(1, brokenRows.size());
        assertTrue(brokenRows.get(0).isIgnored());
        assertTrue(brokenRows.get(0).ignoreReason().contains("Error parsing row"));

        // Invalid Buy / Sell string in ORDER row
        String csvInvalidBuySell = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            Invalid Order,ORDER,2026-05-14T12:00:00Z,GBP,100.00,HOLD,BRK,US0000000001,10.00,0.00,10.00
            """;
        List<ParsedTransactionRow> invalidBuySellRows = parser.parse(csvInvalidBuySell);
        assertEquals(1, invalidBuySellRows.size());
        assertTrue(invalidBuySellRows.get(0).isIgnored());

        // Malformed decimal parsing fallback
        String csvMalformedDecimal = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            Bad Dec,ORDER,2026-05-14T12:00:00Z,GBP,NOT_A_NUM,BUY,BRK,US0000000001,BAD,BAD,BAD
            """;
        List<ParsedTransactionRow> badDecRows = parser.parse(csvMalformedDecimal);
        assertEquals(1, badDecRows.size());
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(badDecRows.get(0).grossAmount()));

        // Dividend row with missing optional fields
        String csvBareDividend = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share,Stamp Duty,Quantity,Venue,Order ID,Order Type,Instrument Currency,Total Amount in Instrument Currency,Price per Share,FX Rate,Base FX Rate,FX Fee (BPS),FX Fee Amount,Dividend Ex Date,Dividend Pay Date,Dividend Eligible Quantity,Dividend Amount Per Share,Dividend Gross Distribution Amount,Dividend Net Distribution Amount,Dividend Withheld Tax Percentage,Dividend Withheld Tax Amount
            Apple,DIVIDEND,2026-05-14T14:50:00.000Z,GBP,14.78,,AAPL,US0378331005,,,,,,,,,,,,,,,,,,,,
            """;
        List<ParsedTransactionRow> bareDivRows = parser.parse(csvBareDividend);
        assertEquals(1, bareDivRows.size());
        assertEquals(TransactionType.DIVIDEND, bareDivRows.get(0).mappedType());
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(bareDivRows.get(0).taxAmount()));

        // SPECIAL_DIVIDEND and INTEREST_FROM_CASH
        String csvDivAndInt = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            Special Div Co,SPECIAL_DIVIDEND,2026-05-14T12:00:00Z,GBP,50.00,,SDIV,US0000000002,,,,
            Cash Interest,INTEREST_FROM_CASH,2026-05-14T12:00:00Z,GBP,1.25,,,,,,,,
            """;
        List<ParsedTransactionRow> divIntRows = parser.parse(csvDivAndInt);
        assertEquals(2, divIntRows.size());
        assertEquals(TransactionType.DIVIDEND, divIntRows.get(0).mappedType());
        assertEquals(TransactionType.INTEREST, divIntRows.get(1).mappedType());
    }

    @Test
    @DisplayName("ImportBatch and ImportRecord entity constructors and getters")
    void entityGetters() {
        com.takakim.investtracker.domain.Currency gbp = new com.takakim.investtracker.domain.Currency("GBP");
        com.takakim.investtracker.domain.Portfolio p = new com.takakim.investtracker.domain.Portfolio("Main", gbp, com.takakim.investtracker.domain.CostBasisMethod.FIFO, com.takakim.investtracker.domain.ReturnMethod.XIRR);
        com.takakim.investtracker.domain.Account account = new com.takakim.investtracker.domain.Account(p, "Freetrade Account", "Freetrade", gbp);

        com.takakim.investtracker.domain.ImportBatch batch = new com.takakim.investtracker.domain.ImportBatch(account, "test.csv", "Freetrade", 10);
        assertEquals(account, batch.getAccount());
        assertEquals("test.csv", batch.getFileName());
        assertEquals("Freetrade", batch.getBrokerType());
        assertEquals(com.takakim.investtracker.domain.ImportBatchStatus.PENDING, batch.getStatus());
        assertEquals(10, batch.getTotalRows());
        assertEquals(0, batch.getImportedRows());
        assertEquals(0, batch.getSkippedRows());
        assertNotNull(batch.getCreatedAt());
        assertNotNull(batch.getId());

        batch.updateProgress(8, 2, com.takakim.investtracker.domain.ImportBatchStatus.COMPLETED);
        assertEquals(8, batch.getImportedRows());
        assertEquals(2, batch.getSkippedRows());
        assertEquals(com.takakim.investtracker.domain.ImportBatchStatus.COMPLETED, batch.getStatus());

        com.takakim.investtracker.domain.ImportRecord record = new com.takakim.investtracker.domain.ImportRecord(
                batch, account, 1, "raw line", "fingerprint123",
                com.takakim.investtracker.domain.ImportRecordStatus.IMPORTED, null, null
        );
        assertEquals(batch, record.getBatch());
        assertEquals(account, record.getAccount());
        assertEquals(1, record.getRowNumber());
        assertEquals("raw line", record.getRawData());
        assertEquals("fingerprint123", record.getFingerprint());
        assertEquals(com.takakim.investtracker.domain.ImportRecordStatus.IMPORTED, record.getStatus());
        assertNull(record.getErrorMessage());
        assertNull(record.getTransaction());
        assertNotNull(record.getCreatedAt());
        assertNotNull(record.getId());
    }

    @Test
    @DisplayName("CsvImportService selectParser and resolveOrCreateInstrument branches")
    void csvImportServiceBranches() {
        var accountRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.AccountRepository.class);
        var instRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.InstrumentRepository.class);
        var batchRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.ImportBatchRepository.class);
        var recordRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.ImportRecordRepository.class);
        var txService = org.mockito.Mockito.mock(com.takakim.investtracker.service.TransactionService.class);

        var t212Parser = new com.takakim.investtracker.service.csv.Trading212CsvParser();
        var ieParser = new com.takakim.investtracker.service.csv.InvestEngineCsvParser();
        com.takakim.investtracker.service.csv.CsvImportService service = new com.takakim.investtracker.service.csv.CsvImportService(
                accountRepo, instRepo, batchRepo, recordRepo, txService, List.of(parser, t212Parser, ieParser)
        );

        java.util.UUID portfolioId = java.util.UUID.randomUUID();
        java.util.UUID accountId = java.util.UUID.randomUUID();
        com.takakim.investtracker.domain.Currency gbp = new com.takakim.investtracker.domain.Currency("GBP");
        com.takakim.investtracker.domain.Portfolio p = new com.takakim.investtracker.domain.Portfolio("Main", gbp, com.takakim.investtracker.domain.CostBasisMethod.FIFO, com.takakim.investtracker.domain.ReturnMethod.XIRR);
        com.takakim.investtracker.domain.Account account = new com.takakim.investtracker.domain.Account(p, "Freetrade Account", "Freetrade", gbp);

        org.mockito.Mockito.when(accountRepo.findById(accountId)).thenReturn(java.util.Optional.of(account));

        // Unsupported CSV header branch
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.previewImport(p.getId(), accountId, "test.csv", "ColA,ColB\n1,2"));

        // Null / blank CSV content branch
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.previewImport(p.getId(), accountId, "test.csv", "  "));

        // Account portfolio mismatch branch
        java.util.UUID otherPId = java.util.UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class, () ->
                service.previewImport(otherPId, accountId, "test.csv", "Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share,Stamp Duty,Quantity\n"));

        // Instrument already exists by Ticker branch
        com.takakim.investtracker.domain.Instrument existingInst = new com.takakim.investtracker.domain.Instrument("Apple Inc", com.takakim.investtracker.domain.AssetClass.STOCK, "AAPL", "US0378331005", null, gbp);
        org.mockito.Mockito.when(instRepo.findByTicker("AAPL")).thenReturn(java.util.Optional.of(existingInst));

        org.mockito.Mockito.when(batchRepo.save(org.mockito.Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        String singleRowCsv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            Apple,ORDER,2026-05-14T12:00:00Z,GBP,150.00,BUY,AAPL,US0378331005,150.00,0.00,1.00
            """;

        com.takakim.investtracker.domain.ImportBatch resultBatch = service.executeImport(p.getId(), accountId, "single.csv", singleRowCsv);
        assertNotNull(resultBatch);
        assertEquals(1, resultBatch.getImportedRows());

        // Instrument creation with blank title fallback to ticker
        org.mockito.Mockito.when(instRepo.findByTicker("UNKNOWN_TICKER")).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(instRepo.save(org.mockito.Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        String blankTitleCsv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            ,ORDER,2026-05-14T12:00:00Z,GBP,100.00,BUY,UNKNOWN_TICKER,US9999999999,100.00,0.00,1.00
            """;

        com.takakim.investtracker.domain.ImportBatch blankBatch = service.executeImport(p.getId(), accountId, "blank_title.csv", blankTitleCsv);
        assertEquals(1, blankBatch.getImportedRows());

        // Instrument lookup by ISIN branch
        org.mockito.Mockito.when(instRepo.findByTicker(org.mockito.Mockito.any())).thenReturn(java.util.Optional.empty());
        com.takakim.investtracker.domain.Instrument isinInst = new com.takakim.investtracker.domain.Instrument("ISIN Corp", com.takakim.investtracker.domain.AssetClass.STOCK, null, "US1111111111", null, gbp);
        org.mockito.Mockito.when(instRepo.findByIsin("US1111111111")).thenReturn(java.util.Optional.of(isinInst));

        String isinCsv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            ISIN Corp,ORDER,2026-05-14T12:00:00Z,GBP,100.00,BUY,,US1111111111,100.00,0.00,1.00
            """;
        com.takakim.investtracker.domain.ImportBatch isinBatch = service.executeImport(p.getId(), accountId, "isin.csv", isinCsv);
        assertEquals(1, isinBatch.getImportedRows());

        // Instrument creation with no title and no ticker (fallback to "Imported Asset")
        org.mockito.Mockito.when(instRepo.findByTicker(org.mockito.Mockito.any())).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(instRepo.findByIsin(org.mockito.Mockito.any())).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(instRepo.save(org.mockito.Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        String noTickerCsv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            ,ORDER,2026-05-14T12:00:00Z,GBP,100.00,BUY,,US2222222222,100.00,0.00,1.00
            """;
        com.takakim.investtracker.domain.ImportBatch noTickerBatch = service.executeImport(p.getId(), accountId, "noticker.csv", noTickerCsv);
        assertEquals(1, noTickerBatch.getImportedRows());

        // Instrument currency specified in CSV branch
        org.mockito.Mockito.when(txService.recordTransaction(
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        )).thenReturn(null);

        String instCurrCsv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity,Venue,Order ID,Order Type,Instrument Currency,Total Amount in Instrument Currency
            USD Corp,ORDER,2026-05-14T12:00:00Z,GBP,100.00,BUY,USDC,US4444444444,100.00,0.00,1.00,Venue1,Ord1,MARKET,USD,130.00
            """;
        com.takakim.investtracker.domain.ImportBatch instCurrBatch = service.executeImport(p.getId(), accountId, "usd.csv", instCurrCsv);
        assertEquals(1, instCurrBatch.getImportedRows());

        // Both ticker and ISIN null (cash top up / deposit row)
        String depositCsv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            Top up,TOP_UP,2026-05-14T12:00:00Z,GBP,500.00,,,,,,
            """;
        com.takakim.investtracker.domain.ImportBatch depositBatch = service.executeImport(p.getId(), accountId, "deposit.csv", depositCsv);
        assertEquals(1, depositBatch.getImportedRows());

        // Transaction recording exception branch -> error record (LAST test case in method)
        org.mockito.Mockito.when(txService.recordTransaction(
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        )).thenThrow(new IllegalArgumentException("Transaction recording error"));

        String errCsv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity
            Err Corp,ORDER,2026-05-14T12:00:00Z,GBP,100.00,BUY,ERR,US3333333333,100.00,0.00,1.00
            """;
        com.takakim.investtracker.domain.ImportBatch errBatch = service.executeImport(p.getId(), accountId, "err.csv", errCsv);
        assertEquals(0, errBatch.getImportedRows());
        assertEquals(1, errBatch.getSkippedRows());
    }

    @Test
    @DisplayName("Fingerprint and parser exception handling direct branch coverage")
    void fingerprintBranches() throws Exception {
        var accountRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.AccountRepository.class);
        var instRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.InstrumentRepository.class);
        var batchRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.ImportBatchRepository.class);
        var recordRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.ImportRecordRepository.class);
        var txService = org.mockito.Mockito.mock(com.takakim.investtracker.service.TransactionService.class);

        com.takakim.investtracker.service.csv.CsvImportService service = new com.takakim.investtracker.service.csv.CsvImportService(
                accountRepo, instRepo, batchRepo, recordRepo, txService, List.of(parser)
        );

        java.util.UUID accountId = java.util.UUID.randomUUID();

        // Fingerprint with all null optional fields
        ParsedTransactionRow rowAllNulls = new ParsedTransactionRow(
                1, java.time.Instant.now(), "RAW_TYPE", null, null, null, null,
                null, null, null, null, null, null, "GBP", null, null,
                null, null, false, null, "raw line"
        );
        String fp1 = service.calculateFingerprint(accountId, "Broker", rowAllNulls);
        assertNotNull(fp1);

        // Fingerprint with all non-null optional fields
        ParsedTransactionRow rowAllPresent = new ParsedTransactionRow(
                1, java.time.Instant.now(), "RAW_TYPE", TransactionType.BUY, "Title", "TCK", "ISIN123",
                "USD", new java.math.BigDecimal("10"), new java.math.BigDecimal("100"), new java.math.BigDecimal("1000"),
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, "GBP", new java.math.BigDecimal("1.3"), "USD",
                "EXT_REF_123", "MARKET", false, null, "raw line"
        );
        String fp2 = service.calculateFingerprint(accountId, "Broker", rowAllPresent);
        assertNotNull(fp2);
        assertFalse(fp1.equals(fp2));
    }

    @Test
    @DisplayName("inferAssetClass correctly detects BONDs, ETFs, REITs, Cryptos, Funds, and Stocks")
    void testInferAssetClass() {
        assertEquals(com.takakim.investtracker.domain.AssetClass.BOND,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("UK T-Bill 03/02/25", "GB00BSGJV473", "GB00BSGJV473"));
        assertEquals(com.takakim.investtracker.domain.AssetClass.BOND,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Treasury 2028", "TR28", "GB0012345678"));
        assertEquals(com.takakim.investtracker.domain.AssetClass.BOND,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("UK Gilt 2028", "GLT", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.BOND,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Corporate Bond", "BND", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.BOND,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Bond Note", "GB00BP123456", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.ETF,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("FTSE All World Dis", "VWRL", "IE00B3RBWM25"));
        assertEquals(com.takakim.investtracker.domain.AssetClass.ETF,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Vanguard S&P 500", "VUSA", "IE00B3XXRP09"));
        assertEquals(com.takakim.investtracker.domain.AssetClass.ETF,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("iShares S&P 500", "IITU", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.ETF,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("SPDR Core", "SPY", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.ETF,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Index ETF", "ETF", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.ETF,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("FTSE 100", "FTS", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.ETF,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("S&P 500 Tracker", "SP5", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.ETF,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Core Xtrackers", "xdpg", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.REIT,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Regional REIT", "RGL", "GG00BSY2LD72"));
        assertEquals(com.takakim.investtracker.domain.AssetClass.REIT,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Property Trust", "PROP", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.CRYPTO,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Crypto Index", "CRP", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.CRYPTO,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Bitcoin Physical Crypto", "btc", "US1234567890"));
        assertEquals(com.takakim.investtracker.domain.AssetClass.MUTUAL_FUND,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Global Index Fund", "GLBF", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.MUTUAL_FUND,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Global OEIC Fund", "OEIC", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.CASH,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("GBP Cash", "CASH", null));
        assertEquals(com.takakim.investtracker.domain.AssetClass.STOCK,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass("Apple Inc", "AAPL", "US0378331005"));
        assertEquals(com.takakim.investtracker.domain.AssetClass.STOCK,
                com.takakim.investtracker.service.csv.CsvImportService.inferAssetClass(null, null, null));
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.csv.FreetradeCsvParser;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreetradeCsvParserTest {

    private final FreetradeCsvParser parser = new FreetradeCsvParser();

    @Test
    @DisplayName("Freetrade parser supports Freetrade activity feed headers")
    void supportsHeaderCheck() {
        List<String> validHeaders = List.of(
                "Title", "Type", "Timestamp", "Account Currency", "Total Amount in Account Currency",
                "Buy / Sell", "Ticker", "ISIN", "Price per Share", "Stamp Duty", "Quantity"
        );
        assertTrue(parser.supports(validHeaders));
        assertFalse(parser.supports(List.of("Date", "Amount", "Category")));
        assertFalse(parser.supports(null));
    }

    @Test
    @DisplayName("Parses Freetrade CSV order, dividend, interest, top up, and statement rows")
    void parseSampleFreetradeCsv() {
        String csv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity,Venue,Order ID,Order Type,Instrument Currency,Total Amount in Instrument Currency,Price per Share,FX Rate,Base FX Rate,FX Fee (BPS),FX Fee Amount,Dividend Ex Date,Dividend Pay Date,Dividend Eligible Quantity,Dividend Amount Per Share,Dividend Gross Distribution Amount,Dividend Net Distribution Amount,Dividend Withheld Tax Percentage,Dividend Withheld Tax Amount
            Figma,ORDER,2026-05-15T13:45:41.754Z,GBP,824.19,SELL,FIG,US3168411052,16.58160000,0.00,50.00000000,NASDAQ - All Markets,OC1C3R8YIS6T,MARKET,USD,1106.14,22.12280000,1.34204330,1.33417169,59,4.89,,,,,,,,,,,,,,,,,,,,,,,
            Cerebras Systems,ORDER,2026-05-14T17:42:56.661Z,GBP,494.33,BUY,CBRS,US15675D1037,245.71500000,0.00,2.00000000,G1 Execution Services,339ABW9OS97A,MARKET,USD,659.40,329.69990000,1.33387544,1.34179201,59,2.90,,,,,,,,,,,,,,,,,,,,,,,
            Apple,DIVIDEND,2026-05-14T14:50:00.000Z,GBP,14.78,,AAPL,US0378331005,,,86.99994805,,,,USD,,,,0.74049939,0,0.00,2026-05-11,2026-05-14,86.99994805,0.27000000,23.49,19.97,15,3.52,,,,,,,,,,,,,,,
            Interest,INTEREST_FROM_CASH,2026-04-17T00:00:00.000Z,GBP,0.56,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
            Top up,TOP_UP,2025-01-16T19:42:13.426Z,GBP,550.21,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
            April Statement,MONTHLY_STATEMENT,2026-05-01T00:00:00.000Z,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(6, rows.size());

        // Row 1: Figma SELL order
        ParsedTransactionRow r1 = rows.get(0);
        assertEquals(TransactionType.SELL, r1.mappedType());
        assertEquals("FIG", r1.ticker());
        assertEquals("US3168411052", r1.isin());
        assertEquals(0, new BigDecimal("50.00000000").compareTo(r1.quantity()));
        assertEquals(0, new BigDecimal("829.08").compareTo(r1.grossAmount()));
        assertEquals(0, new BigDecimal("4.89").compareTo(r1.feeAmount()));
        assertFalse(r1.isIgnored());

        // Row 2: Cerebras Systems BUY order
        ParsedTransactionRow r2 = rows.get(1);
        assertEquals(TransactionType.BUY, r2.mappedType());
        assertEquals("CBRS", r2.ticker());
        assertEquals(0, new BigDecimal("491.43").compareTo(r2.grossAmount()));
        assertEquals(0, new BigDecimal("2.90").compareTo(r2.feeAmount()));

        // Row 3: Apple DIVIDEND
        ParsedTransactionRow r3 = rows.get(2);
        assertEquals(TransactionType.DIVIDEND, r3.mappedType());
        assertEquals("AAPL", r3.ticker());
        assertEquals(0, new BigDecimal("17.3866").compareTo(r3.grossAmount()));
        assertEquals(0, new BigDecimal("2.6066").compareTo(r3.taxAmount()));

        // Row 4: Interest
        ParsedTransactionRow r4 = rows.get(3);
        assertEquals(TransactionType.INTEREST, r4.mappedType());
        assertEquals(0, new BigDecimal("0.56").compareTo(r4.grossAmount()));

        // Row 5: Top Up
        ParsedTransactionRow r5 = rows.get(4);
        assertEquals(TransactionType.DEPOSIT, r5.mappedType());
        assertEquals(0, new BigDecimal("550.21").compareTo(r5.grossAmount()));

        // Row 6: Statement marker
        ParsedTransactionRow r6 = rows.get(5);
        assertTrue(r6.isIgnored());
    }

    @Test
    @DisplayName("Splits quoted CSV lines correctly")
    void parseCsvLineWithQuotes() {
        String line = "\"Jane Street Capital, LLC\",ORDER,2026-05-14,GBP,100.00,BUY,TEST,US123,\"1,234.56\"";
        List<String> tokens = FreetradeCsvParser.parseCsvLine(line);

        assertEquals("Jane Street Capital, LLC", tokens.get(0));
        assertEquals("ORDER", tokens.get(1));
        assertEquals("1,234.56", tokens.get(8));
    }

    @Test
    @DisplayName("Freetrade parser handles empty timestamp and unrecognized raw types")
    void parserEdgeCases() {
        String csv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share,Stamp Duty,Quantity
            Unknown,SOMETHING_ELSE,,GBP,10.00,BUY,UNK,US0000000000,10.00,0.00,1.00
            Invalid Order,ORDER,2026-05-14T12:00:00Z,GBP,10.00,HOLD,UNK,US0000000000,10.00,0.00,1.00
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(2, rows.size());

        assertTrue(rows.get(0).isIgnored());
        assertEquals("Unsupported Freetrade row type: SOMETHING_ELSE", rows.get(0).ignoreReason());
        assertNotNull(rows.get(0).timestamp());

        assertTrue(rows.get(1).isIgnored());
        assertEquals("Unknown buy/sell direction: HOLD", rows.get(1).ignoreReason());
    }

    @Test
    @DisplayName("Freetrade parser handles empty CSV and non-Freetrade headers")
    void parseEarlyCancelPaths() {
        // Empty CSV content → lines.isEmpty() early return
        List<ParsedTransactionRow> emptyRows = parser.parse("   \n  \n  ");
        assertEquals(0, emptyRows.size());

        // CSV with non-Freetrade headers → !supports(headers) early return
        String nonFreetradeCsv = """
            Date,Amount,Category
            2026-01-01,100,Salary
            """;
        List<ParsedTransactionRow> unsupportedRows = parser.parse(nonFreetradeCsv);
        assertEquals(0, unsupportedRows.size());
    }

    @Test
    @DisplayName("Freetrade parser auto-generates maturity redemption for T-Bills and adjusts RGL consolidation")
    void testTbillMaturityAndRglConsolidation() {
        String csv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share,Stamp Duty,Quantity,Venue,Order ID,Order Type,Instrument Currency,Total Amount in Instrument Currency,Price per Share,FX Rate
            UK T-Bill 10/02/25,ORDER,2025-01-10T08:19:48.000Z,GBP,256.74,BUY,GB00BSGJXG32,GB00BSGJXG32,0.99642106,0.00,257.67000000,Off-exchange,TC5D,BASIC,GBP,256.74,0.99642106,
            
            Regional REIT,ORDER,2024-10-17T09:00:50.713Z,GBP,157.75,SELL,RGL,GG00BSY2LD72,1.28252033,0.00,123.00000000,LSE,2N4J,BASIC,GBP,157.75,1.28252033,
            UK T-Bill No OrderID,ORDER,2025-01-10T08:19:48.000Z,,256.74,BUY,GB00BSGJXG32,GB00BSGJXG32,0.99642106,0.00,257.67000000,Off-exchange,,BASIC,,256.74,0.99642106,
            ShortLine,ORDER,2025-01-10
            InvalidNumber,ORDER,2025-01-10T08:00:00Z,GBP,NOT_A_NUMBER,BUY,TICK,ISIN,INVALID,0.00,INVALID,VENUE,ID,TYPE,CURR,INVALID,INVALID,INVALID
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertTrue(rows.size() >= 4);

        // Row 1: T-Bill BUY
        ParsedTransactionRow buy = rows.get(0);
        assertEquals(TransactionType.BUY, buy.mappedType());
        assertEquals("GB00BSGJXG32", buy.ticker());
        assertEquals(new BigDecimal("257.67000000"), buy.quantity());

        // Row 2: T-Bill auto SELL (maturity redemption at par)
        ParsedTransactionRow maturity = rows.get(1);
        assertEquals(TransactionType.SELL, maturity.mappedType());
        assertEquals("TC5D-MATURITY", maturity.externalReference());
        assertEquals(new BigDecimal("257.67000000"), maturity.quantity());
        assertEquals(new BigDecimal("257.67000000"), maturity.grossAmount());

        // Row 3: RGL SELL (adjusted for 10:1 consolidation)
        ParsedTransactionRow rgl = rows.get(2);
        assertEquals(TransactionType.SELL, rgl.mappedType());
        assertEquals("RGL", rgl.ticker());
        assertEquals(new BigDecimal("1232.00000000"), rgl.quantity());

        // extractMaturityDate edge cases
        assertNull(FreetradeCsvParser.extractMaturityDate(null));
        assertNull(FreetradeCsvParser.extractMaturityDate("Apple Inc"));
        assertNull(FreetradeCsvParser.extractMaturityDate("UK T-Bill 99/99/99"));
        assertNotNull(FreetradeCsvParser.extractMaturityDate("UK T-Bill 03/02/2025"));
    }

    @Test
    @DisplayName("Freetrade parser supports checks variations")
    void supportsHeaderVariations() {
        assertFalse(parser.supports(null));
        assertFalse(parser.supports(List.of("Title", "Type", "Timestamp")));
        assertFalse(parser.supports(List.of("Wrong", "Type", "Timestamp", "D", "E", "Buy / Sell", "G")));
        assertFalse(parser.supports(List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")));
        assertFalse(parser.supports(List.of("Title", "Wrong", "Timestamp", "D", "E", "Buy / Sell", "G", "H", "I", "J")));
        assertFalse(parser.supports(List.of("Title", "Type", "Wrong", "D", "E", "Buy / Sell", "G", "H", "I", "J")));
        assertFalse(parser.supports(List.of("Title", "Type", "Timestamp", "D", "E", "Wrong", "G", "H", "I", "J")));
    }

    @Test
    @DisplayName("Freetrade parser parses STOCK_SPLIT and REVERSE_STOCK_SPLIT rows")
    void testStockSplitAndReverseSplitParsing() {
        String csv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity,Venue,Order ID,Order Type,Instrument Currency,Total Amount in Instrument Currency,Price per Share,FX Rate,Base FX Rate,FX Fee (BPS),FX Fee Amount,Dividend Ex Date,Dividend Pay Date,Dividend Eligible Quantity,Dividend Amount Per Share,Dividend Gross Distribution Amount,Dividend Net Distribution Amount,Dividend Withheld Tax Percentage,Dividend Withheld Tax Amount,Stock Split Ex Date,Stock Split Pay Date,Stock Split New ISIN,Stock Split Rate of Share Outturn From,Stock Split Rate of Share Outturn To,Stock Split Maintain Holding of Initial ISIN,Stock Split New Share Quantity
            Nvidia,STOCK_SPLIT,2024-06-07T21:00:00.000Z,GBP,0.00,,NVDA,US67066G1040,,,27.00000000,,,,USD,,,,,,,,,,,,,,,,,,,,,
            Super Micro Computer,CORPORATE_ACTION,2024-10-01T08:00:00.000Z,GBP,0.00,STOCK_SPLIT,SMCI,US86800U1043,,,,,,,USD,,,,,,,,,,,,,,,,,,,,,36.00000000
            Regional REIT,REVERSE_STOCK_SPLIT,2024-08-27T08:00:00.000Z,,0.00,,RGL,GG00BSY2LD72,,,1108.80000000,,,,GBP,,,,,,,,,,,,,,,,,,,,,
            Regional REIT 2,CORPORATE_ACTION,2024-08-27T08:00:00.000Z,GBP,0.00,REVERSE_STOCK_SPLIT,RGL,GG00BSY2LD72,,,,,,,GBP,,,,,,,,,,,,,,,,,,,,,1108.80000000
            BP,SPECIAL_DIVIDEND,2024-08-27T08:00:00.000Z,,10.00,,BP,GB0007980591,,,10.00,,,,GBP,,,,,,,,,,,,,,,,,,,,,
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(5, rows.size());

        // Row 1: STOCK_SPLIT with direct quantity
        assertEquals(TransactionType.STOCK_SPLIT, rows.get(0).mappedType());
        assertEquals("NVDA", rows.get(0).ticker());
        assertEquals(new BigDecimal("27.00000000"), rows.get(0).quantity());
        assertFalse(rows.get(0).isIgnored());

        // Row 2: CORPORATE_ACTION forward split with col 35 fallback
        assertEquals(TransactionType.STOCK_SPLIT, rows.get(1).mappedType());
        assertEquals("SMCI", rows.get(1).ticker());
        assertEquals(new BigDecimal("36.00000000"), rows.get(1).quantity());
        assertFalse(rows.get(1).isIgnored());

        // Row 3: REVERSE_STOCK_SPLIT with null accountCurrency and null orderId
        assertEquals(TransactionType.REVERSE_STOCK_SPLIT, rows.get(2).mappedType());
        assertEquals("RGL", rows.get(2).ticker());
        assertEquals(new BigDecimal("1108.80000000"), rows.get(2).quantity());
        assertEquals("GBP", rows.get(2).currency());
        assertFalse(rows.get(2).isIgnored());

        // Row 4: CORPORATE_ACTION REVERSE_STOCK_SPLIT with col 35 fallback
        assertEquals(TransactionType.REVERSE_STOCK_SPLIT, rows.get(3).mappedType());
        assertEquals(new BigDecimal("1108.80000000"), rows.get(3).quantity());

        // Row 5: SPECIAL_DIVIDEND with null account currency
        assertEquals(TransactionType.DIVIDEND, rows.get(4).mappedType());
        assertEquals("GBP", rows.get(4).currency());
    }

    @Test
    @DisplayName("Parses remaining Freetrade activity types as ignored/unsupported")
    void testRemainingFreetradeTypes() {
        String csv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share,Stamp Duty,Quantity
            Subscription,MONTHLY_FEE,2026-01-01T00:00:00Z,GBP,5.99,,,,,,,
            Cash Withdrawal,WITHDRAWAL,2026-01-02T00:00:00Z,GBP,100.00,,,,,,,
            ISA Topup,ISA_SUBSCRIPTION,2026-01-03T00:00:00Z,GBP,500.00,,,,,,,
            Fee,ORDER_FEE,2026-01-04T00:00:00Z,GBP,1.00,,,,,,,
            Voting,PROXY_VOTING_FEE,2026-01-05T00:00:00Z,GBP,0.50,,,,,,,
            Refund,CARD_REFUND,2026-01-06T00:00:00Z,GBP,20.00,,,,,,,
            Interest Adj,INTEREST_ADJUSTMENT,2026-01-07T00:00:00Z,GBP,0.15,,,,,,,
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(7, rows.size());

        for (ParsedTransactionRow row : rows) {
            assertTrue(row.isIgnored());
            assertNotNull(row.ignoreReason());
        }
    }

    @Test
    @DisplayName("Tests Freetrade TOP_UP, INTEREST_FROM_CASH, and CSV line escaping branches")
    void testTopUpAndInterestBranches() {
        String csv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share,Stamp Duty,Quantity,Venue,Order ID,Order Type,Instrument Currency,Total Amount in Instrument Currency,Price per Share,FX Rate
            "Top Up, Direct Debit",TOP_UP,2026-01-01T00:00:00Z,USD,100.00,,,,,,,,,,,,
            Top Up 2,TOP_UP,2026-01-02T00:00:00Z,,50.00,,,,,,,,,,,,
            Interest,INTEREST_FROM_CASH,2026-01-03T00:00:00Z,GBP,2.50,,,,,,,,,,,,
            Interest 2,INTEREST_FROM_CASH,2026-01-04T00:00:00Z,,1.25,,,,,,,,,,,,
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(4, rows.size());

        assertEquals(TransactionType.DEPOSIT, rows.get(0).mappedType());
        assertEquals("USD", rows.get(0).currency());
        assertEquals(new BigDecimal("100.00"), rows.get(0).grossAmount());

        assertEquals(TransactionType.DEPOSIT, rows.get(1).mappedType());
        assertEquals("GBP", rows.get(1).currency());

        assertEquals(TransactionType.INTEREST, rows.get(2).mappedType());
        assertEquals("GBP", rows.get(2).currency());

        assertEquals(TransactionType.INTEREST, rows.get(3).mappedType());
        assertEquals("GBP", rows.get(3).currency());

        // Test parseCsvLine helper
        List<String> escaped = FreetradeCsvParser.parseCsvLine("a,\"b, \"\"c\"\"\",d");
        assertEquals(3, escaped.size());
        assertEquals("a", escaped.get(0));
        assertEquals("b, \"c\"", escaped.get(1));
        assertEquals("d", escaped.get(2));
    }

    @Test
    @DisplayName("Tests RGL consolidation, dividend fallback currency, and split/consolidation fallbacks")
    void testRglAndCorporateActionFallbacks() {
        String csv = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share,Stamp Duty,Quantity,Venue,Order ID,Order Type,Instrument Currency,Total Amount in Instrument Currency,Price per Share,FX Rate
            Regional REIT,ORDER,2026-06-01T10:00:00Z,GBP,123.20,SELL,RGL,GB00B1111111,1.00,0.00,123.20,,,,GBP,123.20,1.00,1.00
            Regional REIT,ORDER,2026-06-02T10:00:00Z,GBP,50.00,SELL,RGL,GB00B1111111,1.00,0.00,50.00,,,,GBP,50.00,1.00,1.00
            Apple,DIVIDEND,2026-06-03T10:00:00Z,,15.00,,AAPL,US0378331005,,0.00,10.00,,,,USD,20.00,2.00,0.75
            Tesla,CORPORATE_ACTION,2026-06-04T10:00:00Z,,0.00,STOCK_SPLIT,TSLA,US88160R1014,,0.00,0.00,,,,USD,0.00,0.00,1.00
            Tesla,CORPORATE_ACTION,2026-06-05T10:00:00Z,,0.00,REVERSE_STOCK_SPLIT,TSLA,US88160R1014,,0.00,0.00,,,,USD,0.00,0.00,1.00
            UK Treasury Bill,ORDER,2026-06-06T10:00:00Z,GBP,100.00,BUY,TBILL,,1.00,0.00,0.00,,,,GBP,100.00,1.00,1.00
            Ordinary Stock,ORDER,2026-06-07T10:00:00Z,GBP,100.00,BUY,ORD,GB00B2222222,1.00,0.00,100.00,,,,GBP,100.00,1.00,1.00
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        // Row 0: RGL SELL with qty 123.20 -> consolidated to 1232.00
        assertEquals(0, new BigDecimal("1232.00000000").compareTo(rows.get(0).quantity()));
        // Row 1: RGL SELL with qty 50.00 -> unchanged 50.00
        assertEquals(0, new BigDecimal("50.00").compareTo(rows.get(1).quantity()));
        // Row 2: Dividend with empty account currency -> GBP
        assertEquals("GBP", rows.get(2).currency());
        // Row 3: Corporate action STOCK_SPLIT
        assertEquals(TransactionType.STOCK_SPLIT, rows.get(3).mappedType());
        assertEquals("GBP", rows.get(3).currency());
        // Row 4: Corporate action REVERSE_STOCK_SPLIT
        assertEquals(TransactionType.REVERSE_STOCK_SPLIT, rows.get(4).mappedType());
        assertEquals("GBP", rows.get(4).currency());
        // Row 5: T-Bill with 0 quantity -> no maturity row generated
        assertEquals(TransactionType.BUY, rows.get(5).mappedType());
        // Row 6: Ordinary Stock with no maturity date in title -> no maturity row generated
        assertEquals(TransactionType.BUY, rows.get(6).mappedType());
        assertEquals(7, rows.size());
    }
}


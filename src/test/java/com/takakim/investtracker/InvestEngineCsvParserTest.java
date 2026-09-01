package com.takakim.investtracker;

import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.csv.InvestEngineCsvParser;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvestEngineCsvParserTest {

    private final InvestEngineCsvParser parser = new InvestEngineCsvParser();

    @Test
    @DisplayName("InvestEngine parser supports statement headers")
    void supportsHeaderCheck() {
        List<String> validHeaders = List.of(
                "Security / ISIN", "Transaction Type", "Quantity", "Share Price", "Total Trade Value", "Trade Date/Time"
        );
        assertTrue(parser.supports(validHeaders));
        assertTrue(parser.supports(List.of("Security", "Action", "Total Trade Value", "Broker")));
        assertFalse(parser.supports(List.of("Date", "Amount", "Category")));
        assertFalse(parser.supports(List.of("Security", "Action")));
        assertFalse(parser.supports(null));
    }

    @Test
    @DisplayName("Parses InvestEngine statement CSV with banner title row")
    void parseSampleInvestEngineCsv() {
        String csv = """
            Transaction Statement: 01 Feb 2025 - 25 Aug 2026 (Portfolio: DIY 1 / Reference: IP01985296)
            Security / ISIN,Transaction Type,Quantity,Share Price,Total Trade Value,Trade Date/Time,Settlement Date,Broker
            Global X NASDAQ 100 Covered Call / ISIN IE00BM8R0J59,Buy,18.882175,£13.2400,£250.00,04/03/25 15:06:44,06/03/25,None
            Franklin FTSE India / ISIN IE00BHZRQZ17,Sell,7.296903,£30.7884,£224.66,04/03/25 15:06:44,06/03/25,None
            Vanguard FTSE All-World / ISIN: IE00BK5BQT80,Dividend,1.000000,£100.00,£5.00,05/03/25 10:00:00,05/03/25,None
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(3, rows.size());

        // Row 1: Buy
        ParsedTransactionRow r1 = rows.get(0);
        assertEquals(TransactionType.BUY, r1.mappedType());
        assertEquals("Global X NASDAQ 100 Covered Call", r1.instrumentTitle());
        assertEquals("IE00BM8R0J59", r1.isin());
        assertEquals("QYLD", r1.ticker());
        assertEquals(new BigDecimal("18.882175"), r1.quantity());
        assertEquals(new BigDecimal("13.2400"), r1.price());
        assertEquals(new BigDecimal("250.00"), r1.grossAmount());
        assertFalse(r1.isIgnored());

        // Row 2: Sell
        ParsedTransactionRow r2 = rows.get(1);
        assertEquals(TransactionType.SELL, r2.mappedType());
        assertEquals("Franklin FTSE India", r2.instrumentTitle());
        assertEquals("IE00BHZRQZ17", r2.isin());
        assertEquals("FLXI", r2.ticker());

        // Row 3: Dividend
        ParsedTransactionRow r3 = rows.get(2);
        assertEquals(TransactionType.DIVIDEND, r3.mappedType());
        assertEquals("Vanguard FTSE All-World", r3.instrumentTitle());
        assertEquals("IE00BK5BQT80", r3.isin());
        assertEquals("VWRP", r3.ticker());
    }

    @Test
    @DisplayName("InvestEngine handles edge cases and empty content")
    void edgeCases() {
        assertEquals(0, parser.parse(null).size());
        assertEquals(0, parser.parse("").size());
        assertEquals(0, parser.parse("   ").size());
        assertEquals(0, parser.parse("Security / ISIN\n").size());

        String csv = """
            Security / ISIN,Transaction Type,Quantity,Share Price,Total Trade Value,Trade Date/Time
            
            ShortRow
            Unknown Security / ISIN US12345,UnknownType,1,10,10,04/03/25 15:06:44
            Plain Name,Buy,1,10,10,04/03/25 15:06:44
            Null Total,Buy,1,10,,invalid-date
            Empty Date,Buy,1,10,10,
            Malformed Dec,Buy,bad,bad,bad,04/03/25 15:06:44
            Invesco S&P 500 / ISIN IE00B3YCGJ38,Stock Split,181.605204,0,0,12/01/26 08:00:00
            Invesco S&P 500 / ISIN IE00B3YCGJ38,Reverse Stock Split,10.000000,0,0,12/01/26 08:00:00
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(7, rows.size());
        assertTrue(rows.get(0).isIgnored());
        assertEquals("Plain Name", rows.get(1).instrumentTitle());
        assertEquals(BigDecimal.ZERO, rows.get(2).grossAmount());
        assertNotNull(rows.get(3).timestamp());
        assertEquals(TransactionType.STOCK_SPLIT, rows.get(5).mappedType());
        assertEquals(new BigDecimal("181.605204"), rows.get(5).quantity());
        assertNull(rows.get(5).price());
        assertEquals(BigDecimal.ZERO, rows.get(5).grossAmount());
        assertEquals(TransactionType.REVERSE_STOCK_SPLIT, rows.get(6).mappedType());
        assertEquals("InvestEngine", parser.getBrokerName());
    }

    @Test
    @DisplayName("Parses enriched InvestEngine CSV schema with 12 columns")
    void parseEnrichedCsv() {
        String csv = """
            Security,Ticker,ISIN,Currency,Exchange,Transaction Type,Quantity,Share Price,Total Trade Value,Trade Date/Time,Settlement Date,Broker
            Invesco S&P 500 UCITS ETF,SPXP,IE00B3YCGJ38,GBP,LSE,Stock Split,181.605204,£0.0000,£0.00,12/01/26 08:00:00,12/01/26,None
            Invesco S&P 500 UCITS ETF,SPXP,IE00B3YCGJ38,GBP,LSE,Buy,14.396643,£10.3419,£148.89,16/01/26 14:59:41,20/01/26,None
            Invesco S&P 500 UCITS ETF,SPXP,IE00B3YCGJ38,GBP,LSE,Dividend,10.0,£0.0,£5.50,16/02/26 14:59:41,20/02/26,None
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(3, rows.size());

        ParsedTransactionRow r1 = rows.get(0);
        assertEquals(TransactionType.STOCK_SPLIT, r1.mappedType());
        assertEquals("SPXP", r1.ticker());
        assertEquals("IE00B3YCGJ38", r1.isin());
        assertEquals("Invesco S&P 500 UCITS ETF", r1.instrumentTitle());
        assertEquals("GBP", r1.currency());
        assertEquals(new BigDecimal("181.605204"), r1.quantity());
        assertNull(r1.price());
        assertEquals(BigDecimal.ZERO, r1.grossAmount());

        ParsedTransactionRow r2 = rows.get(1);
        assertEquals(TransactionType.BUY, r2.mappedType());
        assertEquals(new BigDecimal("14.396643"), r2.quantity());
        assertEquals(new BigDecimal("10.3419"), r2.price());
        assertEquals(new BigDecimal("148.89"), r2.grossAmount());

        ParsedTransactionRow r3 = rows.get(2);
        assertEquals(TransactionType.DIVIDEND, r3.mappedType());
        assertEquals(new BigDecimal("5.50"), r3.grossAmount());
    }

    @Test
    @DisplayName("Parses real InvestEngine export statement from docs directory")
    void parseRealFiles() throws Exception {
        java.nio.file.Path dir = java.nio.file.Paths.get("docs/csv/investengine");
        if (java.nio.file.Files.exists(dir)) {
            try (var stream = java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path file : stream.filter(p -> p.toString().endsWith(".csv")).toList()) {
                    String content = java.nio.file.Files.readString(file);
                    List<ParsedTransactionRow> rows = parser.parse(content);
                    assertFalse(rows.isEmpty(), "Rows should not be empty for " + file.getFileName());
                }
            }
        }
    }
}

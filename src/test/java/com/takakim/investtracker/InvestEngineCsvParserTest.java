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
        assertEquals(new BigDecimal("18.882175"), r1.quantity());
        assertEquals(new BigDecimal("13.2400"), r1.price());
        assertEquals(new BigDecimal("250.00"), r1.grossAmount());
        assertFalse(r1.isIgnored());

        // Row 2: Sell
        ParsedTransactionRow r2 = rows.get(1);
        assertEquals(TransactionType.SELL, r2.mappedType());
        assertEquals("Franklin FTSE India", r2.instrumentTitle());
        assertEquals("IE00BHZRQZ17", r2.isin());

        // Row 3: Dividend
        ParsedTransactionRow r3 = rows.get(2);
        assertEquals(TransactionType.DIVIDEND, r3.mappedType());
        assertEquals("Vanguard FTSE All-World", r3.instrumentTitle());
        assertEquals("IE00BK5BQT80", r3.isin());
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
            Unknown Security / ISIN US12345,UnknownType,1,10,10,2025-01-01T00:00:00Z
            Plain Name,Buy,1,10,10,2025-01-01T00:00:00Z
            Null Total,Buy,1,10,,invalid-date
            Malformed Dec,Buy,bad,bad,bad,2025-01-01T00:00:00Z
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(4, rows.size());
        assertTrue(rows.get(0).isIgnored());
        assertEquals("Plain Name", rows.get(1).instrumentTitle());
        assertEquals(BigDecimal.ZERO, rows.get(2).grossAmount());
        assertEquals("InvestEngine", parser.getBrokerName());
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

package com.takakim.investtracker;

import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import com.takakim.investtracker.service.csv.Trading212CsvParser;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Trading212CsvParserTest {

    private final Trading212CsvParser parser = new Trading212CsvParser();

    @Test
    @DisplayName("Trading 212 parser supports Trading 212 activity headers")
    void supportsHeaderCheck() {
        List<String> validHeaders = List.of(
                "Action", "Time (UTC)", "ISIN", "Ticker", "Name", "Notes", "ID", "No. of shares"
        );
        assertTrue(parser.supports(validHeaders));
        assertFalse(parser.supports(List.of("Date", "Amount", "Category")));
        assertFalse(parser.supports(null));
        assertFalse(parser.supports(List.of("A", "B", "C", "D")));
        assertFalse(parser.supports(List.of("Other", "Time", "ISIN", "Ticker", "5", "6", "7", "8")));
    }

    @Test
    @DisplayName("Parses Trading 212 deposit, withdrawal, buy, sell, dividend, interest, and spinoff")
    void parseSampleTrading212Csv() {
        String csv = """
            Action,Time (UTC),ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),Exchange rate,Result,Currency (Result),Total,Currency (Total),Withholding tax,Currency (Withholding tax),Stamp duty,Currency (Stamp duty),Currency conversion fee,Currency (Currency conversion fee)
            Deposit,2024-07-13 06:37:13+00:00,,,,Notes,ID1,,,,,,,200.00,"GBP",,,,,,
            Withdrawal,2024-07-14 08:00:00+00:00,,,,Notes,ID2,,,,,,,50.00,"GBP",,,,,,
            Market buy,2024-07-15 07:00:31+00:00,IE00BLPK3577,CYSE,"WisdomTree Cybersecurity (Acc)",,EOF1,1.6910702700,1939.6000000000,GBX,100.00000000,,,32.80,"GBP",,,,,,
            Market sell,2024-07-16 09:00:00+00:00,IE00BLPK3577,CYSE,"WisdomTree Cybersecurity (Acc)",,EOF2,1.0000000000,20.0000000000,GBP,1.00000000,,,20.00,"GBP",,,,,,
            Dividend (Dividend),2024-07-17 10:00:00+00:00,US0378331005,AAPL,"Apple",,EOF3,10.0000000000,,,1.28000000,,,15.00,"GBP",2.00,"USD",,,,
            Interest on cash,2024-07-18 00:00:00+00:00,,,,,EOF4,,,,,,,1.25,"GBP",,,,,,
            Spin off,2026-06-30 12:40:32+00:00,US43849R1059,HONA,"Honeywell Aerospace",,EOF5,1.3729929900,0E-10,USD,,,,0.00,"GBP",,,,
            Stock split close,2026-06-29 07:15:56+00:00,US4385162056,HON,"Honeywell International",,EOF6,2.7459859700,214.7498226300,USD,1.33624889,0.00,"GBP",441.31,"GBP",,,,
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(8, rows.size());

        // Row 1: Deposit
        ParsedTransactionRow r1 = rows.get(0);
        assertEquals(TransactionType.DEPOSIT, r1.mappedType());
        assertEquals(new BigDecimal("200.00"), r1.grossAmount());

        // Row 2: Withdrawal
        ParsedTransactionRow r2 = rows.get(1);
        assertEquals(TransactionType.WITHDRAWAL, r2.mappedType());
        assertEquals(new BigDecimal("50.00"), r2.grossAmount());

        // Row 3: Market buy with GBX conversion to GBP price
        ParsedTransactionRow r3 = rows.get(2);
        assertEquals(TransactionType.BUY, r3.mappedType());
        assertEquals("CYSE", r3.ticker());
        assertEquals("IE00BLPK3577", r3.isin());
        assertEquals("GBP", r3.instrumentCurrency());
        assertEquals(new BigDecimal("19.396000"), r3.price());
        assertEquals(new BigDecimal("32.80"), r3.grossAmount());

        // Row 4: Market sell
        ParsedTransactionRow r4 = rows.get(3);
        assertEquals(TransactionType.SELL, r4.mappedType());

        // Row 5: Dividend
        ParsedTransactionRow r5 = rows.get(4);
        assertEquals(TransactionType.DIVIDEND, r5.mappedType());
        assertEquals("AAPL", r5.ticker());
        assertEquals(new BigDecimal("2.00"), r5.taxAmount());

        // Row 6: Interest
        ParsedTransactionRow r6 = rows.get(5);
        assertEquals(TransactionType.INTEREST, r6.mappedType());
        assertEquals(new BigDecimal("1.25"), r6.grossAmount());

        // Row 7: Spin Off allotment at 0 cost
        ParsedTransactionRow r7 = rows.get(6);
        assertEquals(TransactionType.BUY, r7.mappedType());
        assertEquals("HONA", r7.ticker());
        assertEquals(new BigDecimal("1.3729929900"), r7.quantity());
        assertEquals(BigDecimal.ZERO, r7.grossAmount());

        // Row 8: Stock split marker (ignored)
        ParsedTransactionRow r8 = rows.get(7);
        assertTrue(r8.isIgnored());
    }

    @Test
    @DisplayName("Trading 212 parser handles edge cases, blank lines, nulls, and formatting errors")
    void edgeCases() {
        assertEquals(0, parser.parse(null).size());
        assertEquals(0, parser.parse("").size());
        assertEquals(0, parser.parse("   ").size());
        assertEquals(0, parser.parse("Action,Time,ISIN,Ticker\n").size());

        String csv = """
            Action,Time (UTC),ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),Exchange rate,Result,Currency (Result),Total,Currency (Total),Withholding tax,Currency (Withholding tax),Stamp duty,Currency (Stamp duty),Currency conversion fee,Currency (Currency conversion fee)
            
            ShortRow
            Deposit,,,,,Custom Note,D1,,,,,,,100.00,"",,,,,,
            Withdrawal,,,,,Custom Note,W1,,,,,,,50.00,"",,,,,,
            Deposit,,,,, ,D1b,,,,,,,,,,,,,,
            Withdrawal,,,,, ,W1b,,,,,,,,,,,,,,
            Interest on cash,,,,, ,INT1,,,,,,,,,,,,,,
            Market buy,invalid-date,ISIN1,TICK1,Name1,Notes,B1,10,25.00,USD,1.25,,,200.00,USD,,,0.50,GBP,0.25,GBP
            Market buy,invalid-date,ISIN1,TICK1,,Notes,B2,10,invalid,USD,1.25,,,invalid,USD,,,,,,
            Market sell,invalid-date,ISIN2,TICK2,Name2,Notes,S1,5,30.00,USD,1.25,,,120.00,USD,,,0.50,GBP,0.25,GBP
            Market sell,invalid-date,ISIN2,TICK2,,Notes,S2,5,30.00,USD,1.25,,,120.00,USD,,,,,,
            Dividend,invalid-date,ISIN3,TICK3,Name3,Notes,DIV1,10,0.00,USD,1.25,,,10.00,USD,1.50,USD,,,
            Dividend,invalid-date,ISIN3,TICK3,,Notes,DIV2,10,0.00,USD,1.25,,,10.00,USD,,,,,,
            Spin off,invalid-date,ISIN4,TICK4,,Notes,SPIN1,10,0.00,USD,,,,,,,,,,,
            Top up,2024-01-01 10:00:00,,,,Notes,D2,,,,,,,100.00,GBP,,,,,,
            UnknownAction,2024-07-15 07:00:00,ISIN,TICK,Name,,ID,1,10,GBP,1,,GBP,10,GBP
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(14, rows.size());
        assertEquals(TransactionType.DEPOSIT, rows.get(0).mappedType());
        assertEquals("Custom Note", rows.get(0).notes());
        assertEquals(TransactionType.WITHDRAWAL, rows.get(1).mappedType());
        assertEquals("Deposit", rows.get(2).instrumentTitle());
        assertEquals("Withdrawal", rows.get(3).instrumentTitle());
        assertEquals(TransactionType.INTEREST, rows.get(4).mappedType());
        assertEquals(TransactionType.BUY, rows.get(5).mappedType());
        assertEquals(new BigDecimal("0.75"), rows.get(5).feeAmount());
        assertEquals("TICK1", rows.get(6).instrumentTitle());
        assertEquals(TransactionType.SELL, rows.get(7).mappedType());
        assertEquals("TICK2", rows.get(8).instrumentTitle());
        assertEquals(TransactionType.DIVIDEND, rows.get(9).mappedType());
        assertEquals("TICK3", rows.get(10).instrumentTitle());
        assertEquals("TICK4", rows.get(11).instrumentTitle());
        assertEquals(TransactionType.DEPOSIT, rows.get(12).mappedType());
        assertTrue(rows.get(13).isIgnored());
        String timestampVariations = """
            Action,Time (UTC),ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),Exchange rate,Result,Currency (Result),Total,Currency (Total),Withholding tax,Currency (Withholding tax),Stamp duty,Currency (Stamp duty),Currency conversion fee,Currency (Currency conversion fee)
            Deposit,2024-07-13T06:37:13Z,,,,Notes,ID1,,,,,,,200.00,"GBP",,,,,,
            Deposit,2024-07-14 06:37:13,,,,Notes,ID2,,,,,,,100.00,"GBP",,,,,,
            """;
        List<ParsedTransactionRow> tsRows = parser.parse(timestampVariations);
        assertEquals(2, tsRows.size());
        assertNotNull(tsRows.get(0).timestamp());
        assertNotNull(tsRows.get(1).timestamp());
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.csv.AjBellCsvParser;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AjBellCsvParserTests {

    private AjBellCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new AjBellCsvParser();
    }

    @Test
    @DisplayName("Supports AJ Bell headers")
    void supportsHeaders() {
        assertEquals("AJ Bell", parser.getBrokerName());

        List<String> headers1 = List.of("Date", "Transaction", "Security", "Ticker", "ISIN", "Quantity", "Price", "Value", "Charges", "Net Value");
        assertTrue(parser.supports(headers1));

        List<String> headers2 = List.of("Date", "Description", "Charges", "Security", "Sedol", "Quantity");
        assertTrue(parser.supports(headers2));

        assertTrue(parser.supports(List.of("Net Value", "ISIN", "A", "B")));
        assertTrue(parser.supports(List.of("Net Value", "Ticker", "A", "B")));
        assertTrue(parser.supports(List.of("Net Value", "Sedol", "A", "B")));
        assertTrue(parser.supports(List.of("Net Value", "Transaction", "A", "B")));
        assertTrue(parser.supports(List.of("Charges", "Security", "ISIN", "A")));
        assertTrue(parser.supports(List.of("Charges", "Security", "Ticker", "A")));
        assertTrue(parser.supports(List.of("Charges", "Security", "Sedol", "A")));
        assertTrue(parser.supports(List.of("Charges", "Security", "Transaction", "A")));
        assertFalse(parser.supports(List.of("Net Value", "None", "None", "None")));
        assertFalse(parser.supports(List.of("Charges", "None", "ISIN", "A")));

        assertFalse(parser.supports(List.of("Action", "Time", "ISIN")));
        assertFalse(parser.supports(null));
        assertFalse(parser.supports(List.of("A")));
    }

    @Test
    @DisplayName("Parses AJ Bell transactions across all supported types and date formats")
    void parsesAjBellTransactions() {
        String csv = """
            Date,Transaction,Security,Ticker,ISIN,Quantity,Price,Value,Charges,Net Value
            15/01/2026,Buy,Vanguard FTSE 100 UCITS ETF,VUKE,IE00B810Q511,50,£35.00,£1750.00,£1.50,£1751.50
            20-01-2026,Sell,Vanguard S&P 500,VUSA,IE00B3XXRP09,10,£100.00,£1000.00,£1.50,£998.50
            2026-01-22,Sale,Vanguard S&P 500,VUSA,IE00B3XXRP09,-5,£100.00,£500.00,£1.50,£498.50
            25/01/2026,Dividend,Vanguard FTSE 100,VUKE,IE00B810Q511,-,-,£25.00,£0.00,£25.00
            01/02/2026,Subscription,Cash Deposit,-,-,-,-,£500.00,£0.00,£500.00
            02/02/2026,Deposit,Direct Debit,-,-,-,-,£250.00,£0.00,£250.00
            03/02/2026,Cash In,Transfer,-,-,-,-,£100.00,£0.00,£100.00
            05/02/2026,Withdrawal,Cash Out,-,-,-,-,£200.00,£0.00,£200.00
            06/02/2026,Transfer Out,Wire Transfer,-,-,-,-,£150.00,£0.00,£150.00
            07/02/2026,Cash Out,Bank Payment,-,-,-,-,£50.00,£0.00,£50.00
            10/02/2026,Custody Fee,Charge,-,-,-,-,£10.00,£0.00,£10.00
            11/02/2026,Charge,Platform Fee,-,-,-,-,£5.00,£0.00,£5.00
            12/02/2026,Fee,Service Fee,-,-,-,-,£2.00,£0.00,£2.00
            15/02/2026,Interest,Cash Interest,-,-,-,-,£2.50,£0.00,£2.50
            16/02/2026,B,British American Tobacco,BATS,GB0002875804,10,£25.00,£250.00,£0.00,£250.00
            17/02/2026,S,British American Tobacco,BATS,GB0002875804,5,£26.00,£130.00,£0.00,£130.00
            18/02/2026,UNKNOWN,Unknown Asset,,,-2,£50.00,£100.00,£0.00,£100.00
            19/02/2026,UNKNOWN,Unknown Asset,,,3,£50.00,£150.00,£0.00,£150.00
            20/02/2026,Buy,Asset Without Value,NOVAL,GB0012345678,10,£10.00,-,£0.00,-
            21/02/2026,Buy,Asset Without Numbers,NONUM,GB0012345678,-,-,-,£0.00,-
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(20, rows.size());

        assertEquals(TransactionType.BUY, rows.get(0).mappedType());
        assertEquals(TransactionType.SELL, rows.get(1).mappedType());
        assertEquals(TransactionType.SELL, rows.get(2).mappedType());
        assertEquals(TransactionType.DIVIDEND, rows.get(3).mappedType());
        assertEquals(TransactionType.DEPOSIT, rows.get(4).mappedType());
        assertEquals(TransactionType.DEPOSIT, rows.get(5).mappedType());
        assertEquals(TransactionType.DEPOSIT, rows.get(6).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, rows.get(7).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, rows.get(8).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, rows.get(9).mappedType());
        assertEquals(TransactionType.FEE, rows.get(10).mappedType());
        assertEquals(TransactionType.FEE, rows.get(11).mappedType());
        assertEquals(TransactionType.FEE, rows.get(12).mappedType());
        assertEquals(TransactionType.INTEREST, rows.get(13).mappedType());
        assertEquals(TransactionType.BUY, rows.get(14).mappedType());
        assertEquals(TransactionType.SELL, rows.get(15).mappedType());
        assertEquals(TransactionType.SELL, rows.get(16).mappedType());
        assertEquals(TransactionType.BUY, rows.get(17).mappedType());
        assertEquals(new BigDecimal("100.00"), rows.get(18).grossAmount()); // qty * price fallback
        assertEquals(BigDecimal.ZERO, rows.get(19).grossAmount()); // zero fallback
    }

    @Test
    @DisplayName("Handles alternative column header configurations")
    void handlesAlternativeHeaders() {
        String csv = """
            Date,Type,Investment,Sedol,ISIN,Units,Unit Price,Gross,Charges
            15/01/2026,Purchase,HSBC Holdings,0540528,GB0005405286,100,£6.00,£600.00,£1.50
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(1, rows.size());
        assertEquals(TransactionType.BUY, rows.get(0).mappedType());
        assertEquals("HSBC Holdings", rows.get(0).instrumentTitle());
    }

    @Test
    @DisplayName("Handles malformed AJ Bell rows safely")
    void handlesMalformedRows() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("Date,Transaction\n").isEmpty());

        String malformed = """
            Date,Transaction,Security,Ticker,ISIN,Quantity,Price,Value,Charges,Net Value
            bad-date,Buy,Sec,TICK,ISIN1,1,1,1,0,1
            """;
        List<ParsedTransactionRow> rows = parser.parse(malformed);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isIgnored());
    }
}

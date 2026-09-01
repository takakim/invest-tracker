package com.takakim.investtracker;

import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import com.takakim.investtracker.service.csv.VanguardUkCsvParser;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VanguardUkCsvParserTests {

    private VanguardUkCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new VanguardUkCsvParser();
    }

    @Test
    @DisplayName("Supports Vanguard UK header variants")
    void supportsHeaderVariants() {
        assertEquals("Vanguard UK", parser.getBrokerName());

        List<String> headers1 = List.of("Date", "Transaction Type", "Investment Name", "ISIN", "Units", "Unit Price", "Amount", "Charges", "Net Amount");
        assertTrue(parser.supports(headers1));

        List<String> headers2 = List.of("Date", "Type", "Investment", "ISIN", "Units", "Unit Price (£)", "Amount (£)", "Charges (£)");
        assertTrue(parser.supports(headers2));

        List<String> headers3 = List.of("Date", "Investment", "Units / Shares", "Unit Price", "Charges");
        assertTrue(parser.supports(headers3));

        assertFalse(parser.supports(List.of("Action", "Time", "ISIN", "Ticker")));
        assertFalse(parser.supports(null));
        assertFalse(parser.supports(List.of("A", "B")));
    }

    @Test
    @DisplayName("Parses Vanguard UK transactions including Buy, Sell, Dividend, Fee, Interest, and Withdrawal across synonyms")
    void parsesVanguardUkTransactions() {
        String csv = """
            Date,Transaction Type,Investment Name,ISIN,Units,Unit Price,Amount,Charges,Net Amount
            15/01/2026,Buy,Vanguard S&P 500 UCITS ETF,IE00B3XXRP09,20.0000,£100.00,£2000.00,£0.00,£2000.00
            16/01/2026,Purchase,Vanguard S&P 500,IE00B3XXRP09,10.0000,£100.00,£1000.00,£0.00,£1000.00
            17/01/2026,Reinvestment,Vanguard S&P 500,IE00B3XXRP09,5.0000,£100.00,£500.00,£0.00,£500.00
            18/01/2026,Invest,Vanguard S&P 500,IE00B3XXRP09,2.0000,£100.00,£200.00,£0.00,£200.00
            20-01-2026,Sell,Vanguard FTSE All-World UCITS ETF,IE00BK5BQT80,5.0000,£90.00,£450.00,£1.50,£448.50
            21-01-2026,Sale,Vanguard FTSE All-World,IE00BK5BQT80,2.0000,£90.00,£180.00,£0.00,£180.00
            22-01-2026,Redemption,Vanguard FTSE All-World,IE00BK5BQT80,1.0000,£90.00,£90.00,£0.00,£90.00
            2026-01-25,Dividend,Vanguard LifeStrategy 80% Equity Fund,GB00B59G4H30,-,-,£15.50,-,£15.50
            2026-01-26,Distribution,Vanguard LifeStrategy 80,GB00B59G4H30,-,-,£10.00,-,£10.00
            2026-01-27,Income,Vanguard LifeStrategy 80,GB00B59G4H30,-,-,£5.00,-,£5.00
            15-Feb-2026,Account Fee,Account Fee,-,-,-,£5.00,-,£5.00
            15-Feb-2026,Charge,Platform Charge,-,-,-,£2.50,-,£2.50
            15 Feb 2026,Cash In,Deposit,-,-,-,£500.00,-,£500.00
            16 Feb 2026,Direct Debit,Monthly Deposit,-,-,-,£250.00,-,£250.00
            17 Feb 2026,Contribution,SIPP Contribution,-,-,-,£1000.00,-,£1000.00
            18/02/2026,Transfer Out,Withdrawal,-,-,-,£200.00,-,£200.00
            19/02/2026,Cash Out,Cash Withdrawal,-,-,-,£100.00,-,£100.00
            20/02/2026,Cash Interest,Interest,-,-,-,£3.25,-,£3.25
            21/02/2026,Buy,Unknown Asset,GB0099999999,10.00,£10.00,-,-,-
            22/02/2026,Buy,,,,,-,-,-
            23/02/2026,Unknown Transaction,Some Unknown Security,GB00XXXXXX,10.0,£10.00,£100.00,-,£100.00
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(21, rows.size());

        // Row 1: Buy
        ParsedTransactionRow r1 = rows.get(0);
        assertEquals(TransactionType.BUY, r1.mappedType());
        assertEquals("Vanguard S&P 500 UCITS ETF", r1.instrumentTitle());
        assertEquals("VUSA", r1.ticker());
        assertEquals("IE00B3XXRP09", r1.isin());
        assertEquals(new BigDecimal("20.0000"), r1.quantity());
        assertEquals(new BigDecimal("100.00"), r1.price());
        assertEquals(new BigDecimal("2000.00"), r1.grossAmount());
        assertFalse(r1.isIgnored());

        // Buy synonyms
        assertEquals(TransactionType.BUY, rows.get(1).mappedType());
        assertEquals(TransactionType.BUY, rows.get(2).mappedType());
        assertEquals(TransactionType.BUY, rows.get(3).mappedType());

        // Sell synonyms
        assertEquals(TransactionType.SELL, rows.get(4).mappedType());
        assertEquals(TransactionType.SELL, rows.get(5).mappedType());
        assertEquals(TransactionType.SELL, rows.get(6).mappedType());

        // Dividend synonyms
        assertEquals(TransactionType.DIVIDEND, rows.get(7).mappedType());
        assertEquals(TransactionType.DIVIDEND, rows.get(8).mappedType());
        assertEquals(TransactionType.DIVIDEND, rows.get(9).mappedType());

        // Fee synonyms
        assertEquals(TransactionType.FEE, rows.get(10).mappedType());
        assertEquals(TransactionType.FEE, rows.get(11).mappedType());

        // Deposit synonyms
        assertEquals(TransactionType.DEPOSIT, rows.get(12).mappedType());
        assertEquals(TransactionType.DEPOSIT, rows.get(13).mappedType());
        assertEquals(TransactionType.DEPOSIT, rows.get(14).mappedType());

        // Withdrawal synonyms
        assertEquals(TransactionType.WITHDRAWAL, rows.get(15).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, rows.get(16).mappedType());

        // Interest
        assertEquals(TransactionType.INTEREST, rows.get(17).mappedType());

        // Amount null, gross calculated from units * price
        assertEquals(0, new BigDecimal("100.00").compareTo(rows.get(18).grossAmount()));
        assertNull(rows.get(18).ticker());

        // Empty instrument and ISIN become null, amount/units/price null
        assertNull(rows.get(19).instrumentTitle());
        assertNull(rows.get(19).isin());
        assertEquals(BigDecimal.ZERO, rows.get(19).grossAmount());

        // Row 21: Unrecognized type
        ParsedTransactionRow r21 = rows.get(20);
        assertTrue(r21.isIgnored());
        assertTrue(r21.ignoreReason().contains("Unrecognized Vanguard transaction type"));
    }

    @Test
    @DisplayName("Handles currency symbols, pennies, and number formats in clean decimal parser")
    void handlesNumberFormats() {
        String csv = """
            Date,Transaction Type,Investment Name,ISIN,Units,Unit Price,Amount,Charges,Net Amount
            15/01/2026,Buy,Fund,IE0012345678,10.0,150.5p,$15.05,€0.50,$14.55
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("150.5"), rows.get(0).price());
        assertEquals(new BigDecimal("15.05"), rows.get(0).grossAmount());
        assertEquals(new BigDecimal("0.50"), rows.get(0).feeAmount());
    }

    @Test
    @DisplayName("Parses additional Vanguard UK transaction types and formats")
    void parsesAdditionalVanguardTypes() {
        String csv = """
            Transaction date,Details,Investment,ISIN,Units,Price,Amount,Charges
            15/01/2026,Distribution,Vanguard FTSE Developed World,IE00BK5BQV03,-,-,£50.00,£0.00
            16/01/2026,Income,Vanguard Global Bond,IE00B18GC888,-,-,£25.00,£0.00
            17/01/2026,Direct Debit,Direct Debit,-,-,-,£500.00,£0.00
            18/01/2026,Cash In,Bank Transfer,-,-,-,£200.00,£0.00
            19/01/2026,Cash Out,Bank Withdrawal,-,-,-,-£100.00,£0.00
            20/01/2026,Transfer Out,Transfer,-,-,-,-£50.00,£0.00
            21/01/2026,Account Fee,Account Fee,-,-,-,-£5.00,£0.00
            22/01/2026,Charges,Service Charges,-,-,-,-£2.50,£0.00
            23/01/2026,Unknown Type,Unknown Asset,IE0000000000,10,£10.00,£100.00,£0.00
            24/01/2026,Purchase,Asset In Pence,IE0011111111,100,500p,£500.00,£0.00
            25/01/2026,Purchase,Asset In Dollars,IE0022222222,10,$50.00,$500.00,$0.00
            26/01/2026,Purchase,Asset In Euros,IE0033333333,10,€50.00,€500.00,€0.00
            27/01/2026,Purchase,Asset Without Amount,IE0044444444,10,£10.00,-,£0.00
            28/01/2026,Purchase,Asset Without Numbers,IE0055555555,-,-,-,£0.00
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(14, rows.size());

        assertEquals(TransactionType.DIVIDEND, rows.get(0).mappedType());
        assertEquals(TransactionType.DIVIDEND, rows.get(1).mappedType());
        assertEquals(TransactionType.DEPOSIT, rows.get(2).mappedType());
        assertEquals(TransactionType.DEPOSIT, rows.get(3).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, rows.get(4).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, rows.get(5).mappedType());
        assertEquals(TransactionType.FEE, rows.get(6).mappedType());
        assertEquals(TransactionType.FEE, rows.get(7).mappedType());
        assertTrue(rows.get(8).isIgnored()); // Unknown Type -> ignored
        assertNull(rows.get(8).mappedType());
        assertEquals(TransactionType.BUY, rows.get(9).mappedType());
        assertEquals(TransactionType.BUY, rows.get(10).mappedType());
        assertEquals("GBP", rows.get(10).currency());
        assertEquals(TransactionType.BUY, rows.get(11).mappedType());
        assertEquals("GBP", rows.get(11).currency());
        assertEquals(new BigDecimal("100.00"), rows.get(12).grossAmount()); // qty * price fallback
        assertEquals(BigDecimal.ZERO, rows.get(13).grossAmount()); // zero fallback

        String moreKeywordsCsv = """
            Date,Transaction Type,Investment Name,ISIN,Units,Unit Price,Gross Amount,Charges,Net Amount
            01/01/2026,Contribution,-,-,-,-,£200.00,£0.00,£200.00
            02/01/2026,Direct Debit,-,-,-,-,£300.00,£0.00,£300.00
            03/01/2026,Transfer Out,-,-,-,-,£150.00,£0.00,£150.00
            04/01/2026,Account Fee,-,-,-,-,£5.00,£0.00,£5.00
            05/01/2026,Buy,Vanguard FTSE All-World UCITS,IE00BK5BQT80,10,£100.00,£1000.00,£0.00,£1000.00
            06/01/2026,Buy,Vanguard LifeStrategy 80% Equity Fund,GB00B59G4H30,20,£50.00,£1000.00,£0.00,£1000.00
            07/01/2026,Buy,Custom S&P 500 UCITS ETF,OTHERISIN123,5,£50.00,£250.00,£0.00,£250.00
            """;
        List<ParsedTransactionRow> moreRows = parser.parse(moreKeywordsCsv);
        assertEquals(7, moreRows.size());
        assertEquals(TransactionType.DEPOSIT, moreRows.get(0).mappedType());
        assertEquals(TransactionType.DEPOSIT, moreRows.get(1).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, moreRows.get(2).mappedType());
        assertEquals(TransactionType.FEE, moreRows.get(3).mappedType());
        assertEquals("VWRP", moreRows.get(4).ticker());
        assertEquals("V80A", moreRows.get(5).ticker());
        assertEquals("VUSA", moreRows.get(6).ticker());
    }

    @Test
    @DisplayName("Tests supports headers combinations for Vanguard UK")
    void testSupportsCombinations() {
        assertTrue(parser.supports(List.of("Investment", "ISIN", "Units", "Price")));
        assertTrue(parser.supports(List.of("Investment", "Units", "Amount", "Charges")));
        assertTrue(parser.supports(List.of("Investment", "Price", "Date", "Details")));
        assertTrue(parser.supports(List.of("Investment", "Charges", "Amount", "Details")));
        assertFalse(parser.supports(List.of("Investment", "Other1", "Other2", "Other3")));
    }

    @Test
    @DisplayName("Handles empty and malformed Vanguard UK CSV gracefully")
    void handlesEmptyAndMalformedCsv() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("Date,Transaction Type,Investment Name\n").isEmpty());

        String malformed = """
            Date,Transaction Type,Investment Name,ISIN,Units,Unit Price,Amount,Charges,Net Amount
            invalid-date,Buy,Fund,IE0012345678,10,10,100,0,100
            
            15/01/2026
            """;
        List<ParsedTransactionRow> rows = parser.parse(malformed);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isIgnored());
        assertTrue(rows.get(0).ignoreReason().contains("Error parsing Vanguard UK"));
    }
}

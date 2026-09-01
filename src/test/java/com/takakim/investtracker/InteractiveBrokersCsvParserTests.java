package com.takakim.investtracker;

import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.csv.InteractiveBrokersCsvParser;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveBrokersCsvParserTests {

    private InteractiveBrokersCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new InteractiveBrokersCsvParser();
    }

    @Test
    @DisplayName("Supports IBKR header discriminators")
    void supportsHeaderDiscriminators() {
        assertEquals("Interactive Brokers", parser.getBrokerName());

        List<String> headers1 = List.of("Trades", "Header", "DataDiscriminator", "Asset Category", "Currency", "Symbol", "Date/Time", "Quantity", "T. Price");
        assertTrue(parser.supports(headers1));

        List<String> headers2 = List.of("Symbol", "Date/Time", "Proceeds", "Comm/Fee", "Currency");
        assertTrue(parser.supports(headers2));

        List<String> headers3 = List.of("Conid", "Security ID", "Price", "Amount");
        assertTrue(parser.supports(headers3));

        assertFalse(parser.supports(List.of("Action", "Time", "ISIN")));
        assertFalse(parser.supports(null));
        assertFalse(parser.supports(List.of()));
        assertFalse(parser.supports(List.of("A", "B")));
    }

    @Test
    @DisplayName("Parses IBKR Activity Statement format with Trades, Deposits, Cash Report, and Dividends sections")
    void parsesIbkrStatementFormat() {
        String csv = """
            Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,C. Price,Proceeds,Comm/Fee,Basis,Realized P/L,MTM P/L,Code
            Trades,Data,Order,Stocks,USD,AAPL,"2026-01-15, 14:30:00",10,185.50,185.50,-1855.00,-1.00,-1856.00,0,0,O
            Trades,Data,Order,Stocks,USD,MSFT,"2026-01-20, 15:00:00",-5,400.00,400.00,2000.00,-1.50,1500.00,500.00,0,C
            Trades,Data,Order,Stocks,,NVDA,"20260122;160000",2,120.00,120.00,-240.00,-0.50,-240.50,0,0,O
            Trades,Data,Order,Stocks,USD,GOOGL,"2026-01-23 10:00:00",5,150.00,150.00,-,-,-,0,0,O
            Deposits & Withdrawals,Data,Deposit,USD,"2026-01-10",5000.00,Cash Deposit
            Deposits & Withdrawals,Data,Withdrawal,USD,"2026-01-12",-1000.00,Cash Withdrawal
            Cash Report,Data,Deposit,GBP,"2026-01-13",1500.00,Wire Transfer
            Dividends,Data,USD,AAPL,"2026-01-25",25.00,Dividend Payment
            Dividends,Data,AAPL,USD,50.00,"2026-01-26",Dividend 2
            Trades,Other,Header,Ignored
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(9, rows.size());

        // Row 1: Buy AAPL
        ParsedTransactionRow r1 = rows.get(0);
        assertEquals(TransactionType.BUY, r1.mappedType());
        assertEquals("AAPL", r1.ticker());
        assertEquals("USD", r1.currency());
        assertEquals(new BigDecimal("10"), r1.quantity());
        assertEquals(new BigDecimal("185.50"), r1.price());
        assertEquals(new BigDecimal("1855.00"), r1.grossAmount());
        assertEquals(new BigDecimal("1.00"), r1.feeAmount());

        // Row 2: Sell MSFT
        ParsedTransactionRow r2 = rows.get(1);
        assertEquals(TransactionType.SELL, r2.mappedType());
        assertEquals("MSFT", r2.ticker());
        assertEquals(new BigDecimal("5"), r2.quantity());
        assertEquals(new BigDecimal("2000.00"), r2.grossAmount());
        assertEquals(new BigDecimal("1.50"), r2.feeAmount());

        // Row 3: Trade without currency col -> defaults to USD
        ParsedTransactionRow r3 = rows.get(2);
        assertEquals("NVDA", r3.ticker());
        assertEquals("USD", r3.currency());

        // Row 4: Trade without proceeds -> calculated from qty * price
        ParsedTransactionRow r4 = rows.get(3);
        assertEquals(new BigDecimal("750.00"), r4.grossAmount());

        // Row 5: Deposit
        ParsedTransactionRow r5 = rows.get(4);
        assertEquals(TransactionType.DEPOSIT, r5.mappedType());
        assertEquals(new BigDecimal("5000.00"), r5.grossAmount());

        // Row 6: Withdrawal
        ParsedTransactionRow r6 = rows.get(5);
        assertEquals(TransactionType.WITHDRAWAL, r6.mappedType());
        assertEquals(new BigDecimal("1000.00"), r6.grossAmount());

        // Row 7: Cash Report Deposit
        ParsedTransactionRow r7 = rows.get(6);
        assertEquals(TransactionType.DEPOSIT, r7.mappedType());
        assertEquals(new BigDecimal("1500.00"), r7.grossAmount());

        // Row 8: Dividend
        ParsedTransactionRow r8 = rows.get(7);
        assertEquals(TransactionType.DIVIDEND, r8.mappedType());
        assertEquals("AAPL", r8.ticker());
        assertEquals(new BigDecimal("25.00"), r8.grossAmount());

        // Row 9: Dividend 2
        ParsedTransactionRow r9 = rows.get(8);
        assertEquals(TransactionType.DIVIDEND, r9.mappedType());
    }

    @Test
    @DisplayName("Parses standard IBKR Trade table format across multiple types and headers")
    void parsesStandardIbkrFormat() {
        String csv = """
            Symbol,Date/Time,Type,Quantity,Price,Amount,Commission,Currency
            GOOGL,2026-02-01 10:00:00,BUY,15,175.00,2625.00,1.00,USD
            AMZN,2026-02-05 11:30:00,SLD,-10,180.00,1800.00,1.00,USD
            META,2026-02-06,BOT,5,500.00,2500.00,1.00,USD
            TSLA,2026-02-07 12:00:00,SELL,8,200.00,1600.00,1.00,USD
            CASH,2026-02-10 09:00:00,Deposit,1,500.00,500.00,0.00,GBP
            CASH,2026-02-12 09:00:00,Withdrawal,-1,100.00,100.00,0.00,GBP
            AAPL,2026-02-15 09:00:00,Dividend,1,50.00,50.00,0.00,USD
            NFLX,2026-02-16 10:00:00,UNKNOWN,-5,600.00,3000.00,1.00,USD
            DIS,2026-02-17 11:00:00,UNKNOWN,10,100.00,1000.00,1.00,USD
            PYPL,2026-02-18 12:00:00,BUY,2,50.00,-,-,USD
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(10, rows.size());

        assertEquals(TransactionType.BUY, rows.get(0).mappedType());
        assertEquals("GOOGL", rows.get(0).ticker());
        assertEquals(TransactionType.SELL, rows.get(1).mappedType());
        assertEquals("AMZN", rows.get(1).ticker());
        assertEquals(TransactionType.BUY, rows.get(2).mappedType());
        assertEquals("META", rows.get(2).ticker());
        assertEquals(TransactionType.SELL, rows.get(3).mappedType());
        assertEquals(TransactionType.DEPOSIT, rows.get(4).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, rows.get(5).mappedType());
        assertEquals(TransactionType.DIVIDEND, rows.get(6).mappedType());
        assertEquals(TransactionType.SELL, rows.get(7).mappedType()); // mapped from negative qty
        assertEquals(TransactionType.BUY, rows.get(8).mappedType()); // fallback buy
        assertEquals(new BigDecimal("100.00"), rows.get(9).grossAmount()); // qty * price fallback
    }

    @Test
    @DisplayName("Handles standard IBKR with alternative column names")
    void handlesStandardAlternativeColumns() {
        String csv = """
            Ticker,Date,Description,Shares,Unit Price,Proceeds,Comm/Fee,Ccy
            IBM,2026-02-01,Buy,10,140.00,1400.00,1.00,USD
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(1, rows.size());
        assertEquals(TransactionType.BUY, rows.get(0).mappedType());
        assertEquals("IBM", rows.get(0).ticker());
    }

    @Test
    @DisplayName("Handles malformed and empty IBKR rows safely")
    void handlesMalformedRows() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("Symbol,Date/Time\n").isEmpty());

        String malformed = """
            Symbol,Date/Time,Type,Quantity,Price,Amount,Commission,Currency
            BAD,not-a-date,BUY,10,10,100,0,USD
            
            15/01/2026
            """;
        List<ParsedTransactionRow> rows = parser.parse(malformed);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isIgnored());
        assertTrue(rows.get(0).ignoreReason().contains("Error parsing IBKR row"));

        String malformedStatement = """
            Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price
            Trades,Data,Order,Stocks,USD,AAPL,bad-date,10,185.50
            Deposits & Withdrawals,Data,Cash,EUR,2026/01/10-invalid,100
            Dividends,Data,AAPL,USD,2026-01-26-bad,50.00
            """;
        List<ParsedTransactionRow> statementRows = parser.parse(malformedStatement);
        assertEquals(3, statementRows.size());
        assertTrue(statementRows.get(0).isIgnored());
        assertTrue(statementRows.get(1).isIgnored());
        assertTrue(statementRows.get(2).isIgnored());
    }

    @Test
    @DisplayName("Tests IBKR statement cash and dividend parsing branches")
    void testStatementCashAndDividendBranches() {
        String csv = """
            Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,Proceeds,Comm/Fee
            Deposits & Withdrawals,Data,Cash,EUR,"2026/01/10",2500.00,Wire Deposit
            Deposits & Withdrawals,Data,Cash,GBP,"15/01/2026",-500.00,ATM Withdrawal
            Dividends,Data,DIV,GBP,MSFT,"2026/01/28",75.00,Dividend MSFT
            Dividends,Data,DIV,EUR,SAP.DE,"2026-01-29",120.00,Dividend SAP
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(4, rows.size());

        assertEquals(TransactionType.DEPOSIT, rows.get(0).mappedType());
        assertEquals("EUR", rows.get(0).currency());
        assertEquals(new BigDecimal("2500.00"), rows.get(0).grossAmount());

        assertEquals(TransactionType.WITHDRAWAL, rows.get(1).mappedType());
        assertEquals("GBP", rows.get(1).currency());
        assertEquals(new BigDecimal("500.00"), rows.get(1).grossAmount());

        assertEquals(TransactionType.DIVIDEND, rows.get(2).mappedType());
        assertEquals("GBP", rows.get(2).currency());
        assertEquals("MSFT", rows.get(2).ticker());

        assertEquals(TransactionType.DIVIDEND, rows.get(3).mappedType());
        assertEquals("EUR", rows.get(3).currency());
        assertEquals("SAP.DE", rows.get(3).ticker());
    }

    @Test
    @DisplayName("Tests IBKR statement without Trades Header line (tests default index fallback)")
    void testStatementWithoutTradesHeader() {
        String csv = """
            Deposits & Withdrawals,Header,Currency,Date/Time,Amount,Description
            Deposits & Withdrawals,Data,Cash,EUR,2026-01-10,2500.00,Wire Deposit
            Dividends,Data,DIV,USD,AAPL,2026-01-15,50.00,Dividend AAPL
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(2, rows.size());
        assertEquals(TransactionType.DEPOSIT, rows.get(0).mappedType());
        assertEquals(TransactionType.DIVIDEND, rows.get(1).mappedType());
    }

    @Test
    @DisplayName("Tests supports combinations for IBKR")
    void testSupportsCombinations() {
        assertTrue(parser.supports(List.of("DataDiscriminator", "Asset", "Currency")));
        assertTrue(parser.supports(List.of("Trades", "Header", "Symbol")));
        assertTrue(parser.supports(List.of("Proceeds", "Comm", "Quantity")));
        assertTrue(parser.supports(List.of("Conid", "Security ID", "Price")));
        assertFalse(parser.supports(List.of("Trades", "Only", "None")));
        assertFalse(parser.supports(List.of("Proceeds", "None")));
        assertFalse(parser.supports(List.of("Conid", "None")));
    }

    @Test
    @DisplayName("Tests parseAmount edge cases with parentheses, commas, currencies and dashes")
    void testParseAmountEdgeCases() {
        String csv = """
            Symbol,Date/Time,Quantity,Price,Proceeds,Comm/Fee,Currency
            AAPL,"2026-01-15 10:00:00",(10.50),150.00,"(1,575.00)",$2.50,USD
            MSFT,"2026/01/16 11:00:00",5.00," $400.00 ",2000.00,-,USD
            GOOG,"17/01/2026 12:00:00",2,100.00,200.00,0.00,
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(3, rows.size());

        assertEquals(new BigDecimal("10.50"), rows.get(0).quantity());
        assertEquals(TransactionType.SELL, rows.get(0).mappedType());
        assertEquals(new BigDecimal("2.50"), rows.get(0).feeAmount());

        assertEquals(new BigDecimal("400.00"), rows.get(1).price());
        assertEquals(BigDecimal.ZERO, rows.get(1).feeAmount());

        assertEquals("USD", rows.get(2).currency());
    }

    @Test
    @DisplayName("Tests statement rows with empty or malformed tokens")
    void testStatementMalformedRows() {
        String csv = """
            Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,Proceeds,Comm/Fee
            Trades,Data,Order,Stocks,USD,AAPL,"",10,185.50,-1855.00,-1.00
            Trades,Data,Order,Stocks,USD,,2026-01-15,10,185.50,-1855.00,-1.00
            Trades,Data,Order,Stocks,USD,MSFT,2026-01-15,-,-,-,-
            Deposits & Withdrawals,Data
            Dividends,Data
            Cash Report,Data
            """;
        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertNotNull(rows);
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionDomainTests {

    @Test
    @DisplayName("Transaction enforces required inputs and type-specific rules for BUY and SELL")
    void transactionEnforcesTradeRules() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Instant now = Instant.now();

        // Required account, type, tradeDate, currency
        assertThrows(NullPointerException.class, () -> new Transaction(null, instrument, TransactionType.BUY, now, null, BigDecimal.TEN, BigDecimal.TEN, null, null, null, "USD", null, null, null, null));
        assertThrows(NullPointerException.class, () -> new Transaction(account, instrument, null, now, null, BigDecimal.TEN, BigDecimal.TEN, null, null, null, "USD", null, null, null, null));
        assertThrows(NullPointerException.class, () -> new Transaction(account, instrument, TransactionType.BUY, null, null, BigDecimal.TEN, BigDecimal.TEN, null, null, null, "USD", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.BUY, now, null, BigDecimal.TEN, BigDecimal.TEN, null, null, null, "INVALID", null, null, null, null));

        // BUY requires instrument, positive quantity, positive price
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, null, TransactionType.BUY, now, null, BigDecimal.TEN, BigDecimal.TEN, null, null, null, "USD", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.BUY, now, null, BigDecimal.ZERO, BigDecimal.TEN, null, null, null, "USD", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.BUY, now, null, BigDecimal.TEN, BigDecimal.ZERO, null, null, null, "USD", null, null, null, null));

        // BUY calculates netAmount = gross + fee
        Transaction buy = new Transaction(
                account, instrument, TransactionType.BUY, now, null,
                new BigDecimal("10.00"), new BigDecimal("150.00"), null,
                new BigDecimal("5.00"), null, "USD", null, null, "Trade note", null
        );

        assertEquals(0, new BigDecimal("1500.00").compareTo(buy.getGrossAmount()));
        assertEquals(0, new BigDecimal("5.00").compareTo(buy.getFeeAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(buy.getTaxAmount()));
        assertEquals(0, new BigDecimal("1505.00").compareTo(buy.getNetAmount()));
        assertEquals(TransactionStatus.COMPLETED, buy.getStatus());
        assertEquals("Trade note", buy.getNotes());
        assertNotNull(buy.getId());

        // SELL calculates netAmount = gross - fee - tax
        Transaction sell = new Transaction(
                account, instrument, TransactionType.SELL, now, null,
                new BigDecimal("5.00"), new BigDecimal("200.00"), null,
                new BigDecimal("10.00"), new BigDecimal("15.00"), "USD", null, null, null, null
        );

        assertEquals(0, new BigDecimal("1000.00").compareTo(sell.getGrossAmount()));
        assertEquals(0, new BigDecimal("975.00").compareTo(sell.getNetAmount()));
    }

    @Test
    @DisplayName("Transaction enforces rules for DIVIDEND, DEPOSIT, WITHDRAWAL, and FEE")
    void transactionCashAndDividendRules() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Instant now = Instant.now();

        // DIVIDEND requires instrument and gross amount
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, null, TransactionType.DIVIDEND, now, null, null, null, new BigDecimal("100.00"), null, null, "USD", null, null, null, null));

        Transaction dividend = new Transaction(
                account, instrument, TransactionType.DIVIDEND, now, null,
                null, null, new BigDecimal("100.00"), null, new BigDecimal("15.00"), "USD", null, null, null, null
        );
        assertEquals(0, new BigDecimal("100.00").compareTo(dividend.getGrossAmount()));
        assertEquals(0, new BigDecimal("85.00").compareTo(dividend.getNetAmount()));

        // DEPOSIT cash operation (instrument is null)
        Transaction deposit = new Transaction(
                account, null, TransactionType.DEPOSIT, now, null,
                null, null, new BigDecimal("5000.00"), null, null, "GBP", null, null, "Initial Deposit", null
        );
        assertNull(deposit.getInstrument());
        assertEquals(0, new BigDecimal("5000.00").compareTo(deposit.getGrossAmount()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(deposit.getNetAmount()));

        // STOCK_SPLIT sets gross/net amount to 0
        Transaction split = new Transaction(
                account, instrument, TransactionType.STOCK_SPLIT, now, null,
                new BigDecimal("2.00"), null, null, null, null, "USD", null, null, "2-for-1 split", null
        );
        assertEquals(BigDecimal.ZERO, split.getGrossAmount());
        assertEquals(BigDecimal.ZERO, split.getNetAmount());

        // REVERSE_STOCK_SPLIT
        Transaction revSplit = new Transaction(
                account, instrument, TransactionType.REVERSE_STOCK_SPLIT, now, null,
                new BigDecimal("1.00"), null, null, null, null, "USD", null, null, "1-for-2 split", null
        );
        assertEquals(BigDecimal.ZERO, revSplit.getGrossAmount());

        // Negative fee or tax validation
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.BUY, now, null, BigDecimal.TEN, BigDecimal.TEN, null, new BigDecimal("-1.0"), null, "USD", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.BUY, now, null, BigDecimal.TEN, BigDecimal.TEN, null, null, new BigDecimal("-1.0"), "USD", null, null, null, null));

        // Negative grossAmount validation for DEPOSIT/FEE
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, null, TransactionType.DEPOSIT, now, null, null, null, new BigDecimal("-100.00"), null, null, "GBP", null, null, null, null));

        // FX rate & counter currency getters
        Transaction fxTx = new Transaction(
                account, null, TransactionType.TRANSFER, now, now, null, null, new BigDecimal("500.00"), null, null, "GBP", new BigDecimal("1.25"), "USD", "FX transfer", null
        );
        assertEquals(new BigDecimal("1.25"), fxTx.getFxRate());
        assertEquals("USD", fxTx.getCounterCurrency());
        assertEquals(now, fxTx.getSettlementDate());
        assertNotNull(fxTx.getCreatedAt());
        assertNotNull(fxTx.getUpdatedAt());

        // FEE transaction
        Transaction feeTx = new Transaction(
                account, null, TransactionType.FEE, now, null, null, null, new BigDecimal("25.00"), null, null, "GBP", null, null, "Platform fee", null
        );
        assertEquals(new BigDecimal("25.00"), feeTx.getGrossAmount());
        assertEquals(new BigDecimal("25.00"), feeTx.getNetAmount());

        // INTEREST transaction
        Transaction intTx = new Transaction(
                account, null, TransactionType.INTEREST, now, null, null, null, new BigDecimal("12.50"), null, null, "GBP", null, null, "Cash interest", null
        );
        assertEquals(new BigDecimal("12.50"), intTx.getGrossAmount());

        // WITHDRAWAL transaction
        Transaction wTx = new Transaction(
                account, null, TransactionType.WITHDRAWAL, now, null, null, null, new BigDecimal("100.00"), null, null, "GBP", null, null, "Cash withdrawal", null
        );
        assertEquals(new BigDecimal("100.00"), wTx.getGrossAmount());

        // Explicit gross amount on BUY
        Transaction buyExplicit = new Transaction(
                account, instrument, TransactionType.BUY, now, null, new BigDecimal("10.00"), new BigDecimal("100.00"), new BigDecimal("1000.00"), new BigDecimal("5.00"), null, "USD", null, null, null, null
        );
        assertEquals(new BigDecimal("1000.00"), buyExplicit.getGrossAmount());

        // Currency validation checks
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, null, TransactionType.DEPOSIT, now, null, null, null, new BigDecimal("100.00"), null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, null, TransactionType.DEPOSIT, now, null, null, null, new BigDecimal("100.00"), null, null, "INVALID", null, null, null, null));

        // Quantity & Price validation branches for BUY/SELL
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.BUY, now, null, null, BigDecimal.TEN, null, null, null, "USD", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.BUY, now, null, BigDecimal.TEN, null, null, null, null, "USD", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.BUY, now, null, BigDecimal.TEN, new BigDecimal("-5.0"), null, null, null, "USD", null, null, null, null));

        // GrossAmount validation branches for DIVIDEND & FEE
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.DIVIDEND, now, null, null, null, null, null, null, "USD", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, instrument, TransactionType.DIVIDEND, now, null, null, null, BigDecimal.ZERO, null, null, "USD", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(account, null, TransactionType.FEE, now, null, null, null, null, null, null, "GBP", null, null, null, null));
    }

    @Test
    @DisplayName("Transaction status transitions on markCorrected")
    void transactionCorrectionStateTransition() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instant now = Instant.now();

        Transaction deposit = new Transaction(
                account, null, TransactionType.DEPOSIT, now, null,
                null, null, new BigDecimal("100.00"), null, null, "GBP", null, null, null, null
        );

        deposit.markCorrected();
        assertEquals(TransactionStatus.CORRECTED, deposit.getStatus());
    }
}

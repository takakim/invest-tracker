package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CorporateAction;
import com.takakim.investtracker.domain.CorporateActionStatus;
import com.takakim.investtracker.domain.CorporateActionType;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorporateActionDomainTests {

    private Instrument instrument;
    private Portfolio portfolio;
    private Account account;

    @BeforeEach
    void setUp() {
        instrument = new Instrument("Apple Inc.", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        portfolio = new Portfolio("Main Portfolio", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        account = new Account(portfolio, "Brokerage", "Broker", new Currency("USD"));
    }

    @Test
    void createStockSplit_validParameters_success() {
        Instant exDate = Instant.now();
        CorporateAction action = new CorporateAction(
                instrument,
                CorporateActionType.STOCK_SPLIT,
                exDate,
                null,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                null,
                null,
                "10-for-1 forward split",
                "YAHOO_FINANCE",
                "EXT-123"
        );

        assertNotNull(action.getId());
        assertEquals(instrument, action.getInstrument());
        assertEquals(CorporateActionType.STOCK_SPLIT, action.getActionType());
        assertEquals(CorporateActionStatus.PENDING, action.getStatus());
        assertEquals(exDate, action.getExDate());
        assertEquals(BigDecimal.ONE, action.getRatioFrom());
        assertEquals(BigDecimal.TEN, action.getRatioTo());
        assertEquals("YAHOO_FINANCE", action.getSource());
        assertEquals("EXT-123", action.getExternalId());
        assertNull(action.getAppliedTransaction());
        assertNull(action.getAccount());
    }

    @Test
    void createDividend_validParameters_success() {
        Instant exDate = Instant.now();
        CorporateAction action = new CorporateAction(
                instrument,
                CorporateActionType.DIVIDEND,
                exDate,
                null,
                null,
                null,
                null,
                new BigDecimal("0.2500"),
                "USD",
                "Quarterly dividend",
                "YAHOO_FINANCE",
                "DIV-456"
        );

        assertEquals(CorporateActionType.DIVIDEND, action.getActionType());
        assertEquals(CorporateActionStatus.PENDING, action.getStatus());
        assertEquals(new BigDecimal("0.2500"), action.getAmountPerShare());
        assertEquals("USD", action.getCurrency());
    }

    @Test
    void createCorporateAction_nullValidation() {
        Instant now = Instant.now();
        assertThrows(NullPointerException.class, () -> new CorporateAction(
                null, CorporateActionType.STOCK_SPLIT, now, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, null, null, null));

        assertThrows(NullPointerException.class, () -> new CorporateAction(
                instrument, null, now, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, null, null, null));

        assertThrows(NullPointerException.class, () -> new CorporateAction(
                instrument, CorporateActionType.STOCK_SPLIT, null, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, null, null, null));
    }

    @Test
    void createCorporateAction_invalidRatiosAndAmounts_throwsException() {
        Instant now = Instant.now();

        // Stock split with null or non-positive ratios
        assertThrows(IllegalArgumentException.class, () -> new CorporateAction(
                instrument, CorporateActionType.STOCK_SPLIT, now, null, null,
                null, BigDecimal.TEN, null, null, null, null, null));

        assertThrows(IllegalArgumentException.class, () -> new CorporateAction(
                instrument, CorporateActionType.STOCK_SPLIT, now, null, null,
                BigDecimal.ZERO, BigDecimal.TEN, null, null, null, null, null));

        assertThrows(IllegalArgumentException.class, () -> new CorporateAction(
                instrument, CorporateActionType.REVERSE_STOCK_SPLIT, now, null, null,
                BigDecimal.TEN, BigDecimal.ZERO, null, null, null, null, null));

        // Dividend with null or non-positive amount
        assertThrows(IllegalArgumentException.class, () -> new CorporateAction(
                instrument, CorporateActionType.DIVIDEND, now, null, null,
                null, null, null, "USD", null, null, null));

        assertThrows(IllegalArgumentException.class, () -> new CorporateAction(
                instrument, CorporateActionType.DIVIDEND, now, null, null,
                null, null, BigDecimal.ZERO, "USD", null, null, null));
    }

    @Test
    void markAppliedAndDismissed_stateTransitions() {
        Instant now = Instant.now();
        CorporateAction action = new CorporateAction(
                instrument, CorporateActionType.STOCK_SPLIT, now, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, "10:1 split", null, null);

        Transaction tx = new Transaction(
                account, instrument, TransactionType.STOCK_SPLIT, now, null,
                new BigDecimal("9.00"), null, BigDecimal.ZERO, null, null, "USD", null, null, null, null);

        action.markApplied(tx, account);
        assertEquals(CorporateActionStatus.APPLIED, action.getStatus());
        assertEquals(tx, action.getAppliedTransaction());
        assertEquals(account, action.getAccount());

        // Cannot re-apply or dismiss once applied
        assertThrows(IllegalStateException.class, () -> action.markApplied(tx, account));
        assertThrows(IllegalStateException.class, action::markDismissed);

        // Fresh action for dismissal
        CorporateAction dismissAction = new CorporateAction(
                instrument, CorporateActionType.STOCK_SPLIT, now, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, "10:1 split", null, null);

        dismissAction.markDismissed();
        assertEquals(CorporateActionStatus.DISMISSED, dismissAction.getStatus());

        // Cannot re-dismiss or apply once dismissed
        assertThrows(IllegalStateException.class, dismissAction::markDismissed);
        assertThrows(IllegalStateException.class, () -> dismissAction.markApplied(tx, account));
    }

    @Test
    void createCorporateAction_edgeCasesForBranches() {
        Instant now = Instant.now();

        // Blank source falls back to MANUAL
        CorporateAction blankSource = new CorporateAction(
                instrument, CorporateActionType.STOCK_SPLIT, now, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, null, "   ", null);
        assertEquals("MANUAL", blankSource.getSource());

        // Null ratioTo for STOCK_SPLIT throws
        assertThrows(IllegalArgumentException.class, () -> new CorporateAction(
                instrument, CorporateActionType.STOCK_SPLIT, now, null, null,
                BigDecimal.ONE, null, null, null, null, null, null));

        // Negative ratioFrom for REVERSE_STOCK_SPLIT throws
        assertThrows(IllegalArgumentException.class, () -> new CorporateAction(
                instrument, CorporateActionType.REVERSE_STOCK_SPLIT, now, null, null,
                new BigDecimal("-1"), BigDecimal.ONE, null, null, null, null, null));

        // Negative amountPerShare for DIVIDEND throws
        assertThrows(IllegalArgumentException.class, () -> new CorporateAction(
                instrument, CorporateActionType.DIVIDEND, now, null, null,
                null, null, new BigDecimal("-0.50"), "USD", null, null, null));
    }
}

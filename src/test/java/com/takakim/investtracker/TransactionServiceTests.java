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
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.TransactionService;
import com.takakim.investtracker.service.position.PositionEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PositionEngine positionEngine;

    private TransactionService service;

    private Portfolio portfolio;
    private Account account;
    private Instrument instrument1;
    private Instrument instrument2;
    private UUID portfolioId;
    private UUID accountId;
    private UUID inst1Id;
    private UUID inst2Id;

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                portfolioRepository,
                accountRepository,
                instrumentRepository,
                transactionRepository,
                positionEngine
        );

        Currency gbp = new Currency("GBP");
        portfolio = new Portfolio("Main Portfolio", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        portfolioId = portfolio.getId();

        account = new Account(portfolio, "Trading ISA", "Broker", gbp);
        accountId = account.getId();

        instrument1 = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        inst1Id = instrument1.getId();

        instrument2 = new Instrument("Microsoft Corp", AssetClass.STOCK, "MSFT", "US5949181045", "NASDAQ", new Currency("USD"));
        inst2Id = instrument2.getId();
    }

    @Test
    @DisplayName("listTransactions returns filtered and unfiltered transactions")
    void listTransactionsFiltering() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Transaction tx = new Transaction(
                account, null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("100.00"), null, null, "GBP", null, null, null, null
        );

        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(accountId)).thenReturn(List.of(tx));
        when(transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(accountId, TransactionType.DEPOSIT))
                .thenReturn(List.of(tx));

        List<Transaction> all = service.listTransactions(portfolioId, accountId, null);
        assertEquals(1, all.size());

        List<Transaction> filtered = service.listTransactions(portfolioId, accountId, TransactionType.DEPOSIT);
        assertEquals(1, filtered.size());
    }

    @Test
    @DisplayName("listPortfolioTransactions verifies portfolio existence")
    void listPortfolioTransactions() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)).thenReturn(List.of());

        List<Transaction> result = service.listPortfolioTransactions(portfolioId);
        assertNotNull(result);

        UUID missing = UUID.randomUUID();
        when(portfolioRepository.existsById(missing)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.listPortfolioTransactions(missing));
    }

    @Test
    @DisplayName("getTransaction verifies ownership and existence")
    void getTransactionValidation() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Transaction tx = new Transaction(
                account, null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("100.00"), null, null, "GBP", null, null, null, null
        );

        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        Transaction retrieved = service.getTransaction(portfolioId, accountId, tx.getId());
        assertEquals(tx.getId(), retrieved.getId());

        UUID randomTxId = UUID.randomUUID();
        when(transactionRepository.findById(randomTxId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getTransaction(portfolioId, accountId, randomTxId));

        // Account mismatch
        Account otherAccount = new Account(portfolio, "Other Account", "Broker", new Currency("GBP"));
        Transaction otherTx = new Transaction(
                otherAccount, null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("50.00"), null, null, "GBP", null, null, null, null
        );
        when(transactionRepository.findById(otherTx.getId())).thenReturn(Optional.of(otherTx));
        assertThrows(ResourceNotFoundException.class, () -> service.getTransaction(portfolioId, accountId, otherTx.getId()));
    }

    @Test
    @DisplayName("recordTransaction handles missing instrument and syncPosition flag")
    void recordTransactionBranches() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Missing instrument throws
        UUID missingInstId = UUID.randomUUID();
        when(instrumentRepository.findById(missingInstId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.recordTransaction(
                portfolioId, accountId, missingInstId, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, null, null
        ));

        // Cash transaction with instrumentId = null
        Transaction cashTx = service.recordTransaction(
                portfolioId, accountId, null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("500.00"), null, null, "GBP", null, null, null, null
        );
        assertNotNull(cashTx);
        verify(positionEngine, never()).recalculateAndSync(any(), any());

        // Trade with syncPosition = false
        when(instrumentRepository.findById(inst1Id)).thenReturn(Optional.of(instrument1));
        Transaction tradeNoSync = service.recordTransaction(
                portfolioId, accountId, inst1Id, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, null, null, false
        );
        assertNotNull(tradeNoSync);
        verify(positionEngine, never()).recalculateAndSync(eq(account), eq(instrument1));
    }

    @Test
    @DisplayName("correctTransaction recalculates both old and replacement instruments if different")
    void correctTransactionBranches() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction origBuy = new Transaction(
                account, instrument1, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, null, null
        );
        when(transactionRepository.findById(origBuy.getId())).thenReturn(Optional.of(origBuy));
        when(instrumentRepository.findById(inst2Id)).thenReturn(Optional.of(instrument2));

        // Correct with different replacement instrument (instrument1 -> instrument2)
        Transaction replacement = service.correctTransaction(
                portfolioId, accountId, origBuy.getId(),
                inst2Id, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, "Corrected"
        );

        assertNotNull(replacement);
        assertEquals(TransactionStatus.CORRECTED, origBuy.getStatus());
        verify(positionEngine).recalculateAndSync(account, instrument1);
        verify(positionEngine).recalculateAndSync(account, instrument2);

        // Attempting to correct already corrected throws IllegalStateException
        assertThrows(IllegalStateException.class, () -> service.correctTransaction(
                portfolioId, accountId, origBuy.getId(),
                inst2Id, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, "Again"
        ));
    }

    @Test
    @DisplayName("correctTransaction with null replacement instrument or null original instrument")
    void correctTransactionNullInstrumentBranches() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Original with null instrument (DEPOSIT) corrected to another DEPOSIT
        Transaction origDeposit = new Transaction(
                account, null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("100.00"), null, null, "GBP", null, null, null, null
        );
        when(transactionRepository.findById(origDeposit.getId())).thenReturn(Optional.of(origDeposit));

        Transaction repDeposit = service.correctTransaction(
                portfolioId, accountId, origDeposit.getId(),
                null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("150.00"), null, null, "GBP", null, null, "Corrected Deposit"
        );
        assertNotNull(repDeposit);

        // Original trade corrected to replacement with null instrumentId
        Transaction origTrade = new Transaction(
                account, instrument1, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, null, null
        );
        when(transactionRepository.findById(origTrade.getId())).thenReturn(Optional.of(origTrade));

        service.correctTransaction(
                portfolioId, accountId, origTrade.getId(),
                null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("100.00"), null, null, "USD", null, null, "Changed to deposit"
        );
        verify(positionEngine).recalculateAndSync(account, instrument1);
    }

    @Test
    @DisplayName("updateTransaction throws when instrument not found")
    void updateTransactionMissingInstrumentThrows() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Transaction tx = new Transaction(
                account, instrument1, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, null, null
        );
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        UUID missingInst = UUID.randomUUID();
        when(instrumentRepository.findById(missingInst)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.updateTransaction(
                portfolioId, accountId, tx.getId(),
                missingInst, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, null
        ));
    }

    @Test
    @DisplayName("updateTransaction from trade to cash flow recalculates old instrument position")
    void updateTransactionTradeToCashFlow() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = new Transaction(
                account, instrument1, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, null, null
        );
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        Transaction updatedToCash = service.updateTransaction(
                portfolioId, accountId, tx.getId(),
                null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("500.00"), null, null, "USD", null, null, "Updated to cash"
        );
        assertEquals(TransactionType.DEPOSIT, updatedToCash.getType());
        verify(positionEngine).recalculateAndSync(account, instrument1);
    }

    @Test
    @DisplayName("updateTransaction on cash flow preserves null instrument")
    void updateTransactionCashFlow() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction cashTx = new Transaction(
                account, null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("50.00"), null, null, "GBP", null, null, null, null
        );
        when(transactionRepository.findById(cashTx.getId())).thenReturn(Optional.of(cashTx));

        Transaction updatedCash = service.updateTransaction(
                portfolioId, accountId, cashTx.getId(),
                null, TransactionType.DEPOSIT, Instant.now(), null,
                null, null, new BigDecimal("75.00"), null, null, "GBP", null, null, "Updated notes"
        );
        assertEquals(new BigDecimal("75.00"), updatedCash.getGrossAmount());
        verify(positionEngine, never()).recalculateAndSync(any(), any());
    }

    @Test
    @DisplayName("updateTransaction with same instrument recalculates position")
    void updateTransactionSameInstrument() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tradeTx = new Transaction(
                account, instrument1, TransactionType.BUY, Instant.now(), null,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("100.00"), null, null, "USD", null, null, null, null
        );
        when(transactionRepository.findById(tradeTx.getId())).thenReturn(Optional.of(tradeTx));
        when(instrumentRepository.findById(inst1Id)).thenReturn(Optional.of(instrument1));

        Transaction updatedSame = service.updateTransaction(
                portfolioId, accountId, tradeTx.getId(),
                inst1Id, TransactionType.BUY, Instant.now(), null,
                new BigDecimal("20.00"), BigDecimal.TEN, new BigDecimal("200.00"), null, null, "USD", null, null, "Qty updated"
        );
        assertEquals(new BigDecimal("20.00"), updatedSame.getQuantity());
        verify(positionEngine).recalculateAndSync(account, instrument1);
    }

    @Test
    @DisplayName("getValidatedAccount handles missing portfolio, missing account, and account-portfolio mismatch")
    void getValidatedAccountValidation() {
        UUID missingP = UUID.randomUUID();
        when(portfolioRepository.existsById(missingP)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.listTransactions(missingP, accountId, null));

        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        UUID missingA = UUID.randomUUID();
        when(accountRepository.findById(missingA)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.listTransactions(portfolioId, missingA, null));

        Portfolio otherPortfolio = new Portfolio("Other", new Currency("EUR"), CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account mismatchAccount = new Account(otherPortfolio, "Mismatch", "Broker", new Currency("EUR"));
        when(accountRepository.findById(mismatchAccount.getId())).thenReturn(Optional.of(mismatchAccount));
        assertThrows(ResourceNotFoundException.class, () -> service.listTransactions(portfolioId, mismatchAccount.getId(), null));
    }
}

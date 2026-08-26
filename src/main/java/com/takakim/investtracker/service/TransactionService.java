package com.takakim.investtracker.service;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.domain.TransactionType;

import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.takakim.investtracker.service.position.PositionEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionService {

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final TransactionRepository transactionRepository;
    private final PositionEngine positionEngine;

    public TransactionService(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            TransactionRepository transactionRepository,
            PositionEngine positionEngine) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.transactionRepository = transactionRepository;
        this.positionEngine = positionEngine;
    }

    @Transactional(readOnly = true)
    public List<Transaction> listTransactions(UUID portfolioId, UUID accountId, TransactionType typeFilter) {
        getValidatedAccount(portfolioId, accountId);
        if (typeFilter != null) {
            return transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(accountId, typeFilter);
        }
        return transactionRepository.findByAccountIdOrderByTradeDateDesc(accountId);
    }

    @Transactional(readOnly = true)
    public List<Transaction> listPortfolioTransactions(UUID portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        return transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId);
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(UUID portfolioId, UUID accountId, UUID transactionId) {
        getValidatedAccount(portfolioId, accountId);
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        if (!tx.getAccount().getId().equals(accountId)) {
            throw new ResourceNotFoundException("Transaction " + transactionId + " does not belong to account " + accountId);
        }
        return tx;
    }

    public Transaction recordTransaction(
            UUID portfolioId,
            UUID accountId,
            UUID instrumentId,
            TransactionType type,
            Instant tradeDate,
            Instant settlementDate,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal taxAmount,
            String currency,
            BigDecimal fxRate,
            String counterCurrency,
            String notes,
            UUID correctionOfTransactionId) {
        return recordTransaction(
                portfolioId, accountId, instrumentId, type, tradeDate, settlementDate,
                quantity, price, grossAmount, feeAmount, taxAmount, currency, fxRate,
                counterCurrency, notes, correctionOfTransactionId, true
        );
    }

    public Transaction recordTransaction(
            UUID portfolioId,
            UUID accountId,
            UUID instrumentId,
            TransactionType type,
            Instant tradeDate,
            Instant settlementDate,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal taxAmount,
            String currency,
            BigDecimal fxRate,
            String counterCurrency,
            String notes,
            UUID correctionOfTransactionId,
            boolean syncPosition) {

        Account account = getValidatedAccount(portfolioId, accountId);
        Instrument instrument = null;
        if (instrumentId != null) {
            instrument = instrumentRepository.findById(instrumentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));
        }

        Transaction transaction = new Transaction(
                account,
                instrument,
                type,
                tradeDate,
                settlementDate,
                quantity,
                price,
                grossAmount,
                feeAmount,
                taxAmount,
                currency,
                fxRate,
                counterCurrency,
                notes,
                correctionOfTransactionId
        );

        Transaction saved = transactionRepository.save(transaction);
        if (syncPosition) {
            recalculatePositionIfTrade(account, instrument);
        }
        return saved;
    }

    public Transaction correctTransaction(
            UUID portfolioId,
            UUID accountId,
            UUID originalTransactionId,
            UUID replacementInstrumentId,
            TransactionType replacementType,
            Instant replacementTradeDate,
            Instant replacementSettlementDate,
            BigDecimal replacementQuantity,
            BigDecimal replacementPrice,
            BigDecimal replacementGrossAmount,
            BigDecimal replacementFeeAmount,
            BigDecimal replacementTaxAmount,
            String replacementCurrency,
            BigDecimal replacementFxRate,
            String replacementCounterCurrency,
            String replacementNotes) {

        Transaction original = getTransaction(portfolioId, accountId, originalTransactionId);
        if (original.getStatus() == TransactionStatus.CORRECTED) {
            throw new IllegalStateException("Transaction " + originalTransactionId + " is already corrected");
        }

        original.markCorrected();
        transactionRepository.save(original);

        Transaction replacement = recordTransaction(
                portfolioId,
                accountId,
                replacementInstrumentId,
                replacementType,
                replacementTradeDate,
                replacementSettlementDate,
                replacementQuantity,
                replacementPrice,
                replacementGrossAmount,
                replacementFeeAmount,
                replacementTaxAmount,
                replacementCurrency,
                replacementFxRate,
                replacementCounterCurrency,
                replacementNotes,
                original.getId()
        );

        return replacement;
    }

    private void recalculatePositionIfTrade(Account account, Instrument instrument) {
        if (instrument == null) {
            return;
        }
        positionEngine.recalculateAndSync(account, instrument);
    }

    private Account getValidatedAccount(UUID portfolioId, UUID accountId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        if (!account.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException("Account " + accountId + " does not belong to portfolio " + portfolioId);
        }
        return account;
    }
}

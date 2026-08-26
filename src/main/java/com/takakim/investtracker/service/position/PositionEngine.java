package com.takakim.investtracker.service.position;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic calculation engine for deriving positions, lots, and cost basis from the transaction ledger.
 */
@Service
@Transactional
public class PositionEngine {

    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final Map<CostBasisMethod, CostBasisStrategy> strategies;

    public PositionEngine(
            PositionRepository positionRepository,
            TransactionRepository transactionRepository,
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            List<CostBasisStrategy> strategyList) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(CostBasisStrategy::getMethod, Function.identity()));
    }

    /**
     * Calculates the derived position result for an account and instrument given transaction history and cost basis method.
     */
    public PositionCalculationResult calculate(
            Account account,
            Instrument instrument,
            List<Transaction> transactions,
            CostBasisMethod method) {
        CostBasisMethod effectiveMethod = method != null ? method : CostBasisMethod.FIFO;
        CostBasisStrategy strategy = strategies.getOrDefault(effectiveMethod, strategies.get(CostBasisMethod.FIFO));
        return strategy.calculate(account, instrument, transactions);
    }

    /**
     * Recalculates and persists the position entity for a specific account and instrument.
     */
    public PositionCalculationResult recalculateAndSync(Account account, Instrument instrument) {
        if (instrument == null || account == null) {
            return null;
        }

        List<Transaction> history = transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(
                account.getId(), instrument.getId());

        CostBasisMethod method = account.getPortfolio() != null && account.getPortfolio().getCostBasisMethod() != null
                ? account.getPortfolio().getCostBasisMethod()
                : CostBasisMethod.FIFO;

        PositionCalculationResult result = calculate(account, instrument, history, method);

        Optional<Position> existingOpt = positionRepository.findByAccountIdAndInstrumentId(
                account.getId(), instrument.getId());

        BigDecimal qty = result.quantity();
        BigDecimal cost = result.costBasisAmount();
        String currencyCode = result.costBasisCurrency();

        Money costBasisMoney = (cost != null && cost.compareTo(BigDecimal.ZERO) > 0 && currencyCode != null)
                ? new Money(cost, new Currency(currencyCode))
                : null;

        if (existingOpt.isPresent()) {
            Position position = existingOpt.get();
            if (position.getStatus() == PositionStatus.ARCHIVED) {
                if (qty.compareTo(BigDecimal.ZERO) > 0) {
                    position.unarchive();
                    position.update(new Quantity(qty), costBasisMoney);
                    positionRepository.save(position);
                }
            } else {
                position.update(new Quantity(qty), costBasisMoney);
                positionRepository.save(position);
            }
        } else if (qty.compareTo(BigDecimal.ZERO) > 0) {
            Position position = new Position(account, instrument, new Quantity(qty), costBasisMoney);
            positionRepository.save(position);
        }

        return result;
    }

    /**
     * Recalculates all positions across all accounts belonging to a portfolio.
     */
    public List<PositionCalculationResult> recalculatePortfolio(UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        List<Account> accounts = accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(
                portfolio.getId(), com.takakim.investtracker.domain.AccountStatus.ACTIVE);
        List<PositionCalculationResult> results = new java.util.ArrayList<>();

        for (Account account : accounts) {
            List<Transaction> txs = transactionRepository.findByAccountIdOrderByTradeDateDesc(account.getId());
            Map<Instrument, List<Transaction>> txsByInstrument = txs.stream()
                    .filter(tx -> tx.getInstrument() != null)
                    .collect(Collectors.groupingBy(Transaction::getInstrument));

            for (Map.Entry<Instrument, List<Transaction>> entry : txsByInstrument.entrySet()) {
                PositionCalculationResult res = recalculateAndSync(account, entry.getKey());
                if (res != null) {
                    results.add(res);
                }
            }
        }

        return results;
    }

    /**
     * Retrieves the calculation result including open tax lots for a specific position.
     */
    @Transactional(readOnly = true)
    public PositionCalculationResult getPositionLots(UUID portfolioId, UUID accountId, UUID positionId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        if (!account.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException("Account " + accountId + " does not belong to portfolio " + portfolioId);
        }

        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));
        if (!position.getAccount().getId().equals(accountId)) {
            throw new ResourceNotFoundException("Position " + positionId + " does not belong to account " + accountId);
        }

        List<Transaction> history = transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(
                accountId, position.getInstrument().getId());

        CostBasisMethod method = account.getPortfolio() != null && account.getPortfolio().getCostBasisMethod() != null
                ? account.getPortfolio().getCostBasisMethod()
                : CostBasisMethod.FIFO;

        return calculate(account, position.getInstrument(), history, method);
    }
}

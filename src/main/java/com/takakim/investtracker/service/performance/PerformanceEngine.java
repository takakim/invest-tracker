package com.takakim.investtracker.service.performance;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.position.LotDisposal;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic performance calculation engine.
 *
 * <p>Phase 6 produces TWR and MWR performance from cost-basis valuations (no live market prices).
 * The {@code valuationBasis} field in the result explicitly labels this approximation.
 * XIRR (numerical IRR) is deferred to a later phase that introduces market data.
 */
@Service
@Transactional
public class PerformanceEngine {

    private static final String VALUATION_BASIS_COST = "COST_BASIS";
    private static final int SCALE = 10;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PositionEngine positionEngine;

    public PerformanceEngine(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PositionEngine positionEngine) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.positionEngine = positionEngine;
    }

    /**
     * Calculates portfolio performance as of now.
     *
     * @param portfolioId the portfolio to evaluate
     * @return performance result (TWR/MWR) with income, cost, and realized gain breakdowns
     * @throws ResourceNotFoundException if portfolio not found
     * @throws UnsupportedOperationException if the portfolio's return method is XIRR (not yet implemented)
     */
    public PerformanceResult calculate(UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        List<Account> accounts = accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(
                portfolioId, AccountStatus.ACTIVE);

        // Fetch all completed transactions for the portfolio ordered by date
        List<Transaction> allTxs = transactionRepository
                .findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)
                .stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
                .sorted((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()))
                .toList();

        // Per-account aggregations
        List<AccountPerformanceSummary> accountSummaries = new ArrayList<>();
        BigDecimal totalRealizedGain = BigDecimal.ZERO;
        BigDecimal totalDividends = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        // Realized gain/loss and cost basis — compute once for the entire portfolio
        List<PositionCalculationResult> allPositionResults = positionEngine.recalculatePortfolio(portfolioId);

        for (Account account : accounts) {
            AccountPerformanceSummary summary = calculateForAccount(account, allPositionResults);
            accountSummaries.add(summary);
            totalRealizedGain = totalRealizedGain.add(summary.realizedGainLoss());
            totalDividends = totalDividends.add(summary.dividendIncome());
            totalInterest = totalInterest.add(summary.interestIncome());
            totalFees = totalFees.add(summary.fees());
            totalTaxes = totalTaxes.add(summary.taxes());
            totalCostBasis = totalCostBasis.add(summary.costBasis());
        }

        BigDecimal totalNetIncome = totalDividends.add(totalInterest)
                .subtract(totalFees).subtract(totalTaxes);

        // TWR / MWR from portfolio-level transactions
        BigDecimal twrReturn = null;
        BigDecimal twrAnnualized = null;
        BigDecimal mwrReturn = null;

        ReturnMethod method = portfolio.getReturnMethod();
        Instant asOf = Instant.now();

        if (method == ReturnMethod.TWR) {
            twrReturn = calculateTwr(allTxs, totalCostBasis);
            twrAnnualized = annualize(twrReturn, allTxs, asOf);
        } else if (method == ReturnMethod.MWR) {
            mwrReturn = calculateMwr(allTxs, totalCostBasis, totalRealizedGain, totalNetIncome);
        } else if (method == ReturnMethod.XIRR) {
            mwrReturn = calculateMwr(allTxs, totalCostBasis, totalRealizedGain, totalNetIncome);
            twrReturn = calculateTwr(allTxs, totalCostBasis);
            twrAnnualized = annualize(twrReturn, allTxs, asOf);
        }

        return new PerformanceResult(
                portfolioId,
                asOf,
                method.name(),
                twrReturn != null ? twrReturn.setScale(8, ROUNDING) : null,
                twrAnnualized != null ? twrAnnualized.setScale(8, ROUNDING) : null,
                mwrReturn != null ? mwrReturn.setScale(8, ROUNDING) : null,
                totalRealizedGain.setScale(4, ROUNDING),
                totalDividends.setScale(4, ROUNDING),
                totalInterest.setScale(4, ROUNDING),
                totalFees.setScale(4, ROUNDING),
                totalTaxes.setScale(4, ROUNDING),
                totalNetIncome.setScale(4, ROUNDING),
                totalCostBasis.setScale(4, ROUNDING),
                portfolio.getBaseCurrency().code(),
                VALUATION_BASIS_COST,
                accountSummaries
        );
    }

    // ----------------------------------------------------------------
    // Per-account calculation
    // ----------------------------------------------------------------

    private AccountPerformanceSummary calculateForAccount(
            Account account,
            List<PositionCalculationResult> allPositionResults) {
        List<Transaction> txs = transactionRepository
                .findByAccountIdOrderByTradeDateDesc(account.getId())
                .stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
                .toList();

        BigDecimal dividends = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;

        for (Transaction tx : txs) {
            TransactionType type = tx.getType();
            if (type == TransactionType.DIVIDEND) {
                dividends = dividends.add(tx.getGrossAmount());
            } else if (type == TransactionType.INTEREST) {
                interest = interest.add(tx.getGrossAmount());
            } else if (type == TransactionType.FEE) {
                fees = fees.add(tx.getGrossAmount());
            }
            // Tax embedded in all transactions
            if (tx.getTaxAmount() != null) {
                taxes = taxes.add(tx.getTaxAmount());
            }
        }

        // Filter pre-computed position results for this account
        List<PositionCalculationResult> accountPositions = allPositionResults.stream()
                .filter(r -> r.accountId().equals(account.getId()))
                .toList();

        BigDecimal realizedGain = accountPositions.stream()
                .flatMap(r -> r.disposals().stream())
                .map(LotDisposal::realizedGainLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costBasis = accountPositions.stream()
                .map(PositionCalculationResult::costBasisAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AccountPerformanceSummary(
                account.getId(),
                account.getName(),
                realizedGain.setScale(4, ROUNDING),
                dividends.setScale(4, ROUNDING),
                interest.setScale(4, ROUNDING),
                fees.setScale(4, ROUNDING),
                taxes.setScale(4, ROUNDING),
                costBasis.setScale(4, ROUNDING),
                account.getAccountCurrency().code()
        );
    }

    // ----------------------------------------------------------------
    // TWR — Time-Weighted Return
    // ----------------------------------------------------------------

    /**
     * Calculates TWR by chain-linking sub-period returns across external cash flow events.
     * Uses cost basis as the portfolio value proxy (no market prices in Phase 6).
     */
    private BigDecimal calculateTwr(List<Transaction> orderedTxs, BigDecimal finalCostBasis) {
        if (orderedTxs.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<PerformancePeriod> periods = buildPeriods(orderedTxs, finalCostBasis);
        if (periods.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Chain-link: TWR = ∏(1 + Rᵢ) - 1
        BigDecimal product = BigDecimal.ONE;
        for (PerformancePeriod period : periods) {
            product = product.multiply(BigDecimal.ONE.add(period.subPeriodReturn()));
        }
        return product.subtract(BigDecimal.ONE);
    }

    /**
     * Builds TWR sub-periods. A new period starts immediately after each external cash flow
     * (DEPOSIT or WITHDRAWAL). Period values are approximated using cumulative cost basis.
     */
    private List<PerformancePeriod> buildPeriods(List<Transaction> ordered, BigDecimal finalCostBasis) {
        List<PerformancePeriod> periods = new ArrayList<>();
        BigDecimal runningCostBasis = BigDecimal.ZERO;
        Instant periodStart = ordered.isEmpty() ? Instant.now() : ordered.get(0).getTradeDate();
        BigDecimal periodStartValue = BigDecimal.ZERO;

        for (Transaction tx : ordered) {
            TransactionType type = tx.getType();
            boolean isExternalFlow = (type == TransactionType.DEPOSIT || type == TransactionType.WITHDRAWAL);

            if (isExternalFlow) {
                BigDecimal flow = tx.getType() == TransactionType.DEPOSIT
                        ? tx.getGrossAmount()
                        : tx.getGrossAmount().negate();

                // Close the current period (just before this cash flow)
                BigDecimal periodEndValue = runningCostBasis;
                if (periodStartValue.compareTo(BigDecimal.ZERO) > 0 || periodEndValue.compareTo(BigDecimal.ZERO) > 0) {
                    periods.add(new PerformancePeriod(
                            periodStart, tx.getTradeDate(),
                            periodStartValue, periodEndValue,
                            BigDecimal.ZERO // no external flow within the period
                    ));
                }

                // Open the next period after the cash flow
                periodStart = tx.getTradeDate();
                periodStartValue = runningCostBasis.add(flow);
                runningCostBasis = periodStartValue;
            } else if (type == TransactionType.BUY) {
                runningCostBasis = runningCostBasis.add(tx.getNetAmount());
            } else if (type == TransactionType.SELL) {
                // Cost basis reduction is handled by PositionEngine; approximate here by proceeds
                runningCostBasis = runningCostBasis.subtract(tx.getNetAmount())
                        .max(BigDecimal.ZERO);
            }
        }

        // Final period to now
        if (periodStartValue.compareTo(BigDecimal.ZERO) > 0 || finalCostBasis.compareTo(BigDecimal.ZERO) > 0) {
            periods.add(new PerformancePeriod(
                    periodStart, Instant.now(),
                    periodStartValue, finalCostBasis,
                    BigDecimal.ZERO
            ));
        }

        return periods;
    }

    /**
     * Annualizes a TWR return over the actual holding period.
     * annualized = (1 + twr)^(365/days) - 1
     */
    private BigDecimal annualize(BigDecimal twr, List<Transaction> ordered, Instant asOf) {
        if (twr == null || ordered.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Instant start = ordered.get(0).getTradeDate();
        long days = ChronoUnit.DAYS.between(start, asOf);
        if (days <= 0) {
            return BigDecimal.ZERO;
        }
        double twrDouble = twr.doubleValue();
        double annualized = Math.pow(1.0 + twrDouble, 365.0 / days) - 1.0;
        return BigDecimal.valueOf(annualized);
    }

    // ----------------------------------------------------------------
    // MWR — Money-Weighted Return (simple approximation)
    // ----------------------------------------------------------------

    /**
     * Simple MWR approximation:
     * MWR = (EndValue - StartValue - NetExternalFlows) / WeightedAverageCostBase
     *
     * <p>EndValue = costBasis + realizedGains + netIncome (cost-basis proxy).
     * StartValue = 0 (new portfolio assumption).
     */
    private BigDecimal calculateMwr(
            List<Transaction> ordered,
            BigDecimal finalCostBasis,
            BigDecimal realizedGain,
            BigDecimal netIncome) {

        if (ordered.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal netDeposits = BigDecimal.ZERO;
        BigDecimal weightedDeposits = BigDecimal.ZERO;
        Instant start = ordered.get(0).getTradeDate();
        long totalDays = ChronoUnit.DAYS.between(start, Instant.now());

        if (totalDays <= 0) {
            return BigDecimal.ZERO;
        }

        for (Transaction tx : ordered) {
            if (tx.getType() == TransactionType.DEPOSIT) {
                BigDecimal amount = tx.getGrossAmount();
                netDeposits = netDeposits.add(amount);
                long daysHeld = ChronoUnit.DAYS.between(tx.getTradeDate(), Instant.now());
                BigDecimal weight = BigDecimal.valueOf((double) daysHeld / totalDays);
                weightedDeposits = weightedDeposits.add(amount.multiply(weight));
            } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                netDeposits = netDeposits.subtract(tx.getGrossAmount());
            }
        }

        BigDecimal endValue = finalCostBasis.add(realizedGain).add(netIncome);
        BigDecimal gain = endValue.subtract(netDeposits);

        if (weightedDeposits.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return gain.divide(weightedDeposits, SCALE, ROUNDING);
    }
}

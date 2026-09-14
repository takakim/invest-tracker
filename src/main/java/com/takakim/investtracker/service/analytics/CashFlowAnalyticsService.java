package com.takakim.investtracker.service.analytics;

import com.takakim.investtracker.api.ApiDtos.AccountCashFlowSummary;
import com.takakim.investtracker.api.ApiDtos.CashFlowAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.CashFlowPeriodPoint;
import com.takakim.investtracker.api.ApiDtos.CashFlowSummary;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.currency.FxRateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CashFlowAnalyticsService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AnalyticsEngine analyticsEngine;
    private final FxRateService fxRateService;

    public CashFlowAnalyticsService(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            AnalyticsEngine analyticsEngine,
            FxRateService fxRateService) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.analyticsEngine = analyticsEngine;
        this.fxRateService = fxRateService;
    }

    public CashFlowAnalyticsResponse calculateCashFlows(UUID portfolioId, String periodStr, String groupByStr) {
        Objects.requireNonNull(portfolioId, "Portfolio ID must not be null");

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        Currency baseCurrency = portfolio.getBaseCurrency();
        String period = normalizePeriod(periodStr);
        String groupBy = normalizeGroupBy(groupByStr);
        Instant now = Instant.now();

        List<Transaction> completedTxs = transactionRepository
                .findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)
                .stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
                .sorted(Comparator.comparing(Transaction::getTradeDate))
                .toList();

        Instant periodStart = computePeriodStart(period, completedTxs, now);

        List<String> warnings = new ArrayList<>();

        // 1. Build periodic buckets
        List<Bucket> buckets = generateBuckets(periodStart, now, groupBy);

        // 2. Aggregate flows per bucket and cumulative tracking
        List<CashFlowPeriodPoint> periodPoints = new ArrayList<>();
        BigDecimal runningCumulative = calculateNetContributionsUpTo(completedTxs, baseCurrency, periodStart);

        for (Bucket bucket : buckets) {
            BigDecimal deposits = BigDecimal.ZERO;
            BigDecimal withdrawals = BigDecimal.ZERO;
            BigDecimal internalIncome = BigDecimal.ZERO;

            for (Transaction tx : completedTxs) {
                if (tx.getTradeDate().isBefore(bucket.start) || tx.getTradeDate().isAfter(bucket.end)) {
                    continue;
                }
                BigDecimal convertedAmount = convertToPortfolioCurrency(tx, baseCurrency);
                if (tx.getType() == TransactionType.DEPOSIT) {
                    deposits = deposits.add(convertedAmount);
                } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                    withdrawals = withdrawals.add(convertedAmount);
                } else if (tx.getType() == TransactionType.DIVIDEND || tx.getType() == TransactionType.INTEREST) {
                    internalIncome = internalIncome.add(convertedAmount);
                } else if (tx.getType() == TransactionType.FEE) {
                    internalIncome = internalIncome.subtract(convertedAmount);
                }
            }

            BigDecimal net = deposits.subtract(withdrawals);
            runningCumulative = runningCumulative.add(net);

            periodPoints.add(new CashFlowPeriodPoint(
                    bucket.label,
                    bucket.start,
                    bucket.end,
                    deposits.setScale(SCALE, ROUNDING),
                    withdrawals.setScale(SCALE, ROUNDING),
                    net.setScale(SCALE, ROUNDING),
                    internalIncome.setScale(SCALE, ROUNDING),
                    runningCumulative.setScale(SCALE, ROUNDING)
            ));
        }

        // 3. Overall summary for the selected period
        BigDecimal periodDeposits = BigDecimal.ZERO;
        BigDecimal periodWithdrawals = BigDecimal.ZERO;
        BigDecimal periodDividends = BigDecimal.ZERO;
        BigDecimal periodInterest = BigDecimal.ZERO;
        BigDecimal periodFees = BigDecimal.ZERO;
        Set<YearMonth> activeMonths = new HashSet<>();

        for (Transaction tx : completedTxs) {
            if (tx.getTradeDate().isBefore(periodStart) || tx.getTradeDate().isAfter(now)) {
                continue;
            }
            BigDecimal convertedAmount = convertToPortfolioCurrency(tx, baseCurrency);
            TransactionType type = tx.getType();

            if (type == TransactionType.DEPOSIT) {
                periodDeposits = periodDeposits.add(convertedAmount);
                activeMonths.add(YearMonth.from(tx.getTradeDate().atZone(ZoneOffset.UTC)));
            } else if (type == TransactionType.WITHDRAWAL) {
                periodWithdrawals = periodWithdrawals.add(convertedAmount);
                activeMonths.add(YearMonth.from(tx.getTradeDate().atZone(ZoneOffset.UTC)));
            } else if (type == TransactionType.DIVIDEND) {
                periodDividends = periodDividends.add(convertedAmount);
            } else if (type == TransactionType.INTEREST) {
                periodInterest = periodInterest.add(convertedAmount);
            } else if (type == TransactionType.FEE) {
                periodFees = periodFees.add(convertedAmount);
            }
        }

        BigDecimal netContributions = periodDeposits.subtract(periodWithdrawals);
        BigDecimal netCashFlow = netContributions.add(periodDividends).add(periodInterest).subtract(periodFees);
        int activeMonthsCount = activeMonths.size();
        BigDecimal avgMonthlyContribution = activeMonthsCount > 0
                ? netContributions.divide(BigDecimal.valueOf(activeMonthsCount), SCALE, ROUNDING)
                : BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        // 4. All-time cumulative contributions and current valuation for wealth attribution
        BigDecimal cumulativeAllTimeContributions = calculateNetContributionsUpTo(completedTxs, baseCurrency, now);

        BigDecimal currentPortfolioValue = BigDecimal.ZERO;
        try {
            PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, now);
            if (analytics != null && analytics.totalCurrentValue() != null) {
                currentPortfolioValue = analytics.totalCurrentValue();
            }
            if (analytics != null && analytics.warnings() != null) {
                warnings.addAll(analytics.warnings());
            }
        } catch (Exception ex) {
            warnings.add("Portfolio live valuation was approximated: " + ex.getMessage());
        }

        BigDecimal capitalContributionsPct = calculateCapitalPercentage(cumulativeAllTimeContributions, currentPortfolioValue);
        BigDecimal marketGrowthPct = calculateGrowthPercentage(capitalContributionsPct, currentPortfolioValue);

        CashFlowSummary summary = new CashFlowSummary(
                periodDeposits.setScale(SCALE, ROUNDING),
                periodWithdrawals.setScale(SCALE, ROUNDING),
                netContributions.setScale(SCALE, ROUNDING),
                periodDividends.setScale(SCALE, ROUNDING),
                periodInterest.setScale(SCALE, ROUNDING),
                periodFees.setScale(SCALE, ROUNDING),
                netCashFlow.setScale(SCALE, ROUNDING),
                avgMonthlyContribution,
                activeMonthsCount,
                cumulativeAllTimeContributions.setScale(SCALE, ROUNDING),
                currentPortfolioValue.setScale(SCALE, ROUNDING),
                capitalContributionsPct,
                marketGrowthPct,
                baseCurrency.code()
        );

        // 5. Account breakdown
        List<Account> accounts = accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE);
        List<AccountCashFlowSummary> accountSummaries = new ArrayList<>();

        for (Account acc : accounts) {
            BigDecimal accNativeCash = calculateAccountNativeCash(acc.getId(), completedTxs);
            BigDecimal accCashInBase = BigDecimal.ZERO;
            if (accNativeCash.compareTo(BigDecimal.ZERO) != 0) {
                Money money = new Money(accNativeCash, acc.getAccountCurrency());
                accCashInBase = fxRateService.convert(money, baseCurrency, now).amount();
            }

            BigDecimal accDeposits = BigDecimal.ZERO;
            BigDecimal accWithdrawals = BigDecimal.ZERO;

            for (Transaction tx : completedTxs) {
                if (!tx.getAccount().getId().equals(acc.getId())) {
                    continue;
                }
                if (tx.getTradeDate().isBefore(periodStart) || tx.getTradeDate().isAfter(now)) {
                    continue;
                }
                BigDecimal converted = convertToPortfolioCurrency(tx, baseCurrency);
                if (tx.getType() == TransactionType.DEPOSIT) {
                    accDeposits = accDeposits.add(converted);
                } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                    accWithdrawals = accWithdrawals.add(converted);
                }
            }

            BigDecimal accNet = accDeposits.subtract(accWithdrawals);

            accountSummaries.add(new AccountCashFlowSummary(
                    acc.getId(),
                    acc.getName(),
                    acc.getBrokerName(),
                    acc.getAccountCurrency().code(),
                    accNativeCash.setScale(SCALE, ROUNDING),
                    accCashInBase.setScale(SCALE, ROUNDING),
                    accDeposits.setScale(SCALE, ROUNDING),
                    accWithdrawals.setScale(SCALE, ROUNDING),
                    accNet.setScale(SCALE, ROUNDING)
            ));
        }

        return new CashFlowAnalyticsResponse(
                portfolio.getId(),
                portfolio.getName(),
                baseCurrency.code(),
                period,
                groupBy,
                periodStart,
                now,
                summary,
                periodPoints,
                accountSummaries,
                warnings
        );
    }

    private BigDecimal convertToPortfolioCurrency(Transaction tx, Currency baseCurrency) {
        BigDecimal rawAmount = tx.getNetAmount() != null ? tx.getNetAmount().abs() : BigDecimal.ZERO;
        Currency txCurrency = tx.getCurrency() != null ? new Currency(tx.getCurrency()) : tx.getAccount().getAccountCurrency();
        Money money = new Money(rawAmount, txCurrency);
        return fxRateService.convert(money, baseCurrency, tx.getTradeDate()).amount();
    }

    private BigDecimal calculateNetContributionsUpTo(List<Transaction> txs, Currency baseCurrency, Instant upTo) {
        BigDecimal net = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if (tx.getTradeDate().isAfter(upTo)) {
                continue;
            }
            if (tx.getType() == TransactionType.DEPOSIT) {
                net = net.add(convertToPortfolioCurrency(tx, baseCurrency));
            } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                net = net.subtract(convertToPortfolioCurrency(tx, baseCurrency));
            }
        }
        return net;
    }

    private BigDecimal calculateAccountNativeCash(UUID accountId, List<Transaction> completedTxs) {
        BigDecimal cash = BigDecimal.ZERO;
        for (Transaction tx : completedTxs) {
            if (!tx.getAccount().getId().equals(accountId)) {
                continue;
            }
            TransactionType type = tx.getType();
            BigDecimal net = tx.getNetAmount() != null ? tx.getNetAmount().abs() : BigDecimal.ZERO;
            BigDecimal gross = tx.getGrossAmount() != null ? tx.getGrossAmount().abs() : net;

            if (type == TransactionType.DEPOSIT || type == TransactionType.DIVIDEND || type == TransactionType.INTEREST || type == TransactionType.SELL) {
                cash = cash.add(net);
            } else if (type == TransactionType.WITHDRAWAL || type == TransactionType.FEE) {
                cash = cash.subtract(gross);
            } else if (type == TransactionType.BUY) {
                cash = cash.subtract(net);
            }
        }
        return cash.max(BigDecimal.ZERO);
    }

    private BigDecimal calculateCapitalPercentage(BigDecimal cumulativeContributions, BigDecimal currentPortfolioValue) {
        if (currentPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return cumulativeContributions.compareTo(BigDecimal.ZERO) > 0 ? ONE_HUNDRED : BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        if (cumulativeContributions.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        if (cumulativeContributions.compareTo(currentPortfolioValue) >= 0) {
            return ONE_HUNDRED;
        }
        return cumulativeContributions.divide(currentPortfolioValue, 4, ROUNDING)
                .multiply(ONE_HUNDRED)
                .setScale(SCALE, ROUNDING);
    }

    private BigDecimal calculateGrowthPercentage(BigDecimal capitalPct, BigDecimal currentPortfolioValue) {
        if (currentPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return ONE_HUNDRED.subtract(capitalPct).max(BigDecimal.ZERO).setScale(SCALE, ROUNDING);
    }

    private String normalizePeriod(String periodStr) {
        if (periodStr == null || periodStr.isBlank()) {
            return "ALL";
        }
        String p = periodStr.trim().toUpperCase();
        return switch (p) {
            case "1M", "3M", "6M", "YTD", "1Y", "ALL" -> p;
            default -> "ALL";
        };
    }

    private String normalizeGroupBy(String groupByStr) {
        if (groupByStr == null || groupByStr.isBlank()) {
            return "MONTH";
        }
        String g = groupByStr.trim().toUpperCase();
        return switch (g) {
            case "MONTH", "QUARTER", "YEAR" -> g;
            default -> "MONTH";
        };
    }

    private Instant computePeriodStart(String period, List<Transaction> completedTxs, Instant now) {
        return switch (period) {
            case "1M" -> now.minus(30, ChronoUnit.DAYS);
            case "3M" -> now.minus(90, ChronoUnit.DAYS);
            case "6M" -> now.minus(180, ChronoUnit.DAYS);
            case "YTD" -> {
                ZonedDateTime zdt = now.atZone(ZoneOffset.UTC);
                yield ZonedDateTime.of(zdt.getYear(), 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
            }
            case "1Y" -> now.minus(365, ChronoUnit.DAYS);
            case "ALL" -> {
                if (!completedTxs.isEmpty()) {
                    yield completedTxs.get(0).getTradeDate();
                }
                yield now.minus(365, ChronoUnit.DAYS);
            }
            default -> now.minus(365, ChronoUnit.DAYS);
        };
    }

    private List<Bucket> generateBuckets(Instant start, Instant end, String groupBy) {
        List<Bucket> buckets = new ArrayList<>();
        ZonedDateTime startZdt = start.atZone(ZoneOffset.UTC);
        ZonedDateTime endZdt = end.atZone(ZoneOffset.UTC);

        if (startZdt.isAfter(endZdt)) {
            buckets.add(new Bucket("Current", start, end));
            return buckets;
        }

        switch (groupBy) {
            case "YEAR" -> {
                int startYear = startZdt.getYear();
                int endYear = endZdt.getYear();
                for (int y = startYear; y <= endYear; y++) {
                    Instant bStart = ZonedDateTime.of(y, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
                    Instant bEnd = ZonedDateTime.of(y, 12, 31, 23, 59, 59, 999_999_999, ZoneOffset.UTC).toInstant();
                    buckets.add(new Bucket(String.valueOf(y), bStart, bEnd));
                }
            }
            case "QUARTER" -> {
                LocalDate curDate = LocalDate.of(startZdt.getYear(), ((startZdt.getMonthValue() - 1) / 3) * 3 + 1, 1);
                LocalDate lastDate = LocalDate.of(endZdt.getYear(), ((endZdt.getMonthValue() - 1) / 3) * 3 + 1, 1);

                while (!curDate.isAfter(lastDate)) {
                    int quarter = (curDate.getMonthValue() - 1) / 3 + 1;
                    String label = curDate.getYear() + "-Q" + quarter;
                    Instant bStart = curDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                    Instant bEnd = curDate.plusMonths(3).minusDays(1)
                            .atTime(23, 59, 59, 999_999_999).atZone(ZoneOffset.UTC).toInstant();

                    buckets.add(new Bucket(label, bStart, bEnd));
                    curDate = curDate.plusMonths(3);
                }
            }
            default -> { // "MONTH"
                YearMonth curYm = YearMonth.from(startZdt);
                YearMonth endYm = YearMonth.from(endZdt);

                while (!curYm.isAfter(endYm)) {
                    String label = curYm.toString();
                    Instant bStart = curYm.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                    Instant bEnd = curYm.atEndOfMonth().atTime(23, 59, 59, 999_999_999).atZone(ZoneOffset.UTC).toInstant();

                    buckets.add(new Bucket(label, bStart, bEnd));
                    curYm = curYm.plusMonths(1);
                }
            }
        }

        return buckets;
    }

    private record Bucket(String label, Instant start, Instant end) { }
}

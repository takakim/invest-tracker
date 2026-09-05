package com.takakim.investtracker.service.analytics;

import com.takakim.investtracker.api.ApiDtos.HistoricalPerformanceSummary;
import com.takakim.investtracker.api.ApiDtos.HistoricalValuationPoint;
import com.takakim.investtracker.api.ApiDtos.PortfolioHistoryResponse;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortfolioHistoryService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final InstrumentRepository instrumentRepository;
    private final AnalyticsEngine analyticsEngine;
    private final MarketDataService marketDataService;
    private final FxRateService fxRateService;

    public PortfolioHistoryService(
            PortfolioRepository portfolioRepository,
            TransactionRepository transactionRepository,
            InstrumentRepository instrumentRepository,
            AnalyticsEngine analyticsEngine,
            MarketDataService marketDataService,
            FxRateService fxRateService) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.instrumentRepository = instrumentRepository;
        this.analyticsEngine = analyticsEngine;
        this.marketDataService = marketDataService;
        this.fxRateService = fxRateService;
    }

    public PortfolioHistoryResponse generateHistory(
            UUID portfolioId,
            String periodStr,
            String intervalStr,
            UUID benchmarkId) {

        Objects.requireNonNull(portfolioId, "Portfolio ID must not be null");

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        String period = (periodStr != null && !periodStr.isBlank()) ? periodStr.toUpperCase() : "1Y";
        Instant now = Instant.now();

        List<Transaction> completedTxs = transactionRepository
                .findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId)
                .stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
                .sorted(Comparator.comparing(Transaction::getTradeDate))
                .toList();

        Instant periodStart = computePeriodStart(period, completedTxs, now);
        String interval = determineInterval(intervalStr, period);

        List<Instant> timeline = generateTimeline(periodStart, now, interval);

        // Fetch benchmark metadata if provided
        Instrument benchmarkInst = null;
        BigDecimal benchmarkStartPrice = null;
        if (benchmarkId != null) {
            benchmarkInst = instrumentRepository.findById(benchmarkId).orElse(null);
            if (benchmarkInst != null) {
                if (marketDataService.getObservationCount(benchmarkId) < 10) {
                    try {
                        marketDataService.backfillHistoricalPrices(benchmarkId, periodStart, now);
                    } catch (Exception ignored) {
                    }
                }
                try {
                    PriceQuote bStartQuote = marketDataService.getLatestPrice(benchmarkId, periodStart);
                    benchmarkStartPrice = extractPositivePrice(bStartQuote);
                } catch (Exception ignored) {
                }
            }
        }

        // Auto-backfill historical quotes for portfolio held instruments if low observation count
        Set<UUID> heldInstrumentIds = new HashSet<>();
        for (Transaction tx : completedTxs) {
            if (tx.getInstrument() != null && tx.getInstrument().getId() != null) {
                heldInstrumentIds.add(tx.getInstrument().getId());
            }
        }
        for (UUID instId : heldInstrumentIds) {
            try {
                if (marketDataService.getObservationCount(instId) < 10) {
                    marketDataService.backfillHistoricalPrices(instId, periodStart, now);
                }
            } catch (Exception ignored) {
            }
        }

        List<HistoricalValuationPoint> dataPoints = new ArrayList<>();
        BigDecimal peakValue = BigDecimal.ZERO;
        BigDecimal maxDrawdownPct = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        BigDecimal initialPortfolioValue = BigDecimal.ZERO;
        boolean initialValueSet = false;

        for (Instant sampleTime : timeline) {
            PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, sampleTime);
            BigDecimal marketVal = analytics.totalCurrentValue().setScale(SCALE, ROUNDING);
            BigDecimal costBasis = analytics.totalCostBasis().setScale(SCALE, ROUNDING);
            BigDecimal cashVal = analytics.totalCashValue().setScale(SCALE, ROUNDING);
            BigDecimal unrealized = marketVal.subtract(costBasis).setScale(SCALE, ROUNDING);

            BigDecimal netDeposits = calculateNetDepositsUpTo(completedTxs, portfolio.getBaseCurrency(), sampleTime);
            BigDecimal investedCapital = netDeposits.compareTo(BigDecimal.ZERO) > 0 ? netDeposits : costBasis.add(cashVal);

            if (!initialValueSet && marketVal.compareTo(BigDecimal.ZERO) > 0) {
                initialPortfolioValue = marketVal;
                initialValueSet = true;
            }

            // Portfolio return percentage relative to starting value / invested capital
            BigDecimal portfolioReturnPct = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
            if (investedCapital.compareTo(BigDecimal.ZERO) > 0) {
                portfolioReturnPct = marketVal.subtract(investedCapital)
                        .divide(investedCapital, 4, ROUNDING)
                        .multiply(ONE_HUNDRED)
                        .setScale(SCALE, ROUNDING);
            } else if (initialPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                portfolioReturnPct = marketVal.subtract(initialPortfolioValue)
                        .divide(initialPortfolioValue, 4, ROUNDING)
                        .multiply(ONE_HUNDRED)
                        .setScale(SCALE, ROUNDING);
            }

            // Benchmark return percentage
            BigDecimal benchmarkReturnPct = null;
            if (benchmarkInst != null && benchmarkStartPrice != null) {
                try {
                    PriceQuote bQuote = marketDataService.getLatestPrice(benchmarkInst.getId(), sampleTime);
                    BigDecimal bPrice = extractPositivePrice(bQuote);
                    if (bPrice != null && benchmarkStartPrice.compareTo(BigDecimal.ZERO) > 0) {
                        benchmarkReturnPct = bPrice.subtract(benchmarkStartPrice)
                                .divide(benchmarkStartPrice, 4, ROUNDING)
                                .multiply(ONE_HUNDRED)
                                .setScale(SCALE, ROUNDING);
                    }
                } catch (Exception ignored) {
                }
            }

            // Track Drawdown
            if (marketVal.compareTo(peakValue) > 0) {
                peakValue = marketVal;
            } else if (peakValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = peakValue.subtract(marketVal)
                        .divide(peakValue, 4, ROUNDING)
                        .multiply(ONE_HUNDRED)
                        .setScale(SCALE, ROUNDING);
                if (drawdown.compareTo(maxDrawdownPct) > 0) {
                    maxDrawdownPct = drawdown;
                }
            }

            dataPoints.add(new HistoricalValuationPoint(
                    sampleTime,
                    marketVal,
                    costBasis,
                    cashVal,
                    investedCapital.setScale(SCALE, ROUNDING),
                    unrealized,
                    portfolioReturnPct,
                    benchmarkReturnPct
            ));
        }

        // Summary Calculations
        HistoricalValuationPoint firstPoint = dataPoints.get(0);
        HistoricalValuationPoint lastPoint = dataPoints.get(dataPoints.size() - 1);
        BigDecimal startingVal = firstPoint.marketValue();
        BigDecimal endingVal = lastPoint.marketValue();
        BigDecimal netCashFlowsInPeriod = calculateNetDepositsInPeriod(completedTxs, portfolio.getBaseCurrency(), periodStart, now);

        BigDecimal totalGainLoss;
        if (startingVal.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal totalDeposits = calculateNetDepositsUpTo(completedTxs, portfolio.getBaseCurrency(), now);
            totalGainLoss = endingVal.subtract(totalDeposits).setScale(SCALE, ROUNDING);
        } else {
            totalGainLoss = endingVal.subtract(startingVal).subtract(netCashFlowsInPeriod).setScale(SCALE, ROUNDING);
        }

        BigDecimal finalPortfolioReturnPct = lastPoint.portfolioReturnPercentage();
        BigDecimal finalBenchmarkReturnPct = lastPoint.benchmarkReturnPercentage();
        BigDecimal excessReturnPct = null;
        if (finalBenchmarkReturnPct != null) {
            excessReturnPct = finalPortfolioReturnPct.subtract(finalBenchmarkReturnPct).setScale(SCALE, ROUNDING);
        }

        HistoricalPerformanceSummary summary = new HistoricalPerformanceSummary(
                startingVal,
                endingVal,
                netCashFlowsInPeriod.setScale(SCALE, ROUNDING),
                totalGainLoss,
                finalPortfolioReturnPct,
                finalBenchmarkReturnPct,
                excessReturnPct,
                maxDrawdownPct
        );

        return new PortfolioHistoryResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getBaseCurrency().code(),
                period,
                interval,
                periodStart,
                now,
                benchmarkInst != null ? benchmarkInst.getId() : null,
                benchmarkInst != null ? benchmarkInst.getTicker() : null,
                benchmarkInst != null ? benchmarkInst.getName() : null,
                summary,
                dataPoints
        );
    }

    private BigDecimal extractPositivePrice(PriceQuote quote) {
        if (quote != null && quote.price() != null && quote.price().compareTo(BigDecimal.ZERO) > 0) {
            return quote.price();
        }
        return null;
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
            case "3Y" -> now.minus(3 * 365, ChronoUnit.DAYS);
            case "ALL" -> {
                if (!completedTxs.isEmpty()) {
                    yield completedTxs.get(0).getTradeDate();
                }
                yield now.minus(365, ChronoUnit.DAYS);
            }
            default -> now.minus(365, ChronoUnit.DAYS); // 1Y
        };
    }

    private String determineInterval(String requestedInterval, String period) {
        if (requestedInterval != null && !requestedInterval.isBlank()) {
            return requestedInterval.toUpperCase();
        }
        return switch (period) {
            case "1M", "3M" -> "DAILY";
            case "6M", "YTD", "1Y" -> "WEEKLY";
            default -> "MONTHLY";
        };
    }

    public List<Instant> generateTimeline(Instant start, Instant end, String interval) {
        List<Instant> points = new ArrayList<>();
        if (start.isAfter(end)) {
            points.add(end);
            return points;
        }

        long stepDays = switch (interval) {
            case "DAILY" -> 1;
            case "MONTHLY" -> 30;
            default -> 7; // WEEKLY
        };

        Instant current = start;
        while (current.isBefore(end)) {
            points.add(current);
            current = current.plus(stepDays, ChronoUnit.DAYS);
        }

        // Always include the latest endpoint (now)
        if (points.isEmpty() || points.get(points.size() - 1).isBefore(end)) {
            points.add(end);
        }

        return points;
    }

    private BigDecimal calculateNetDepositsUpTo(List<Transaction> txs, Currency baseCurrency, Instant upTo) {
        BigDecimal net = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if (tx.getTradeDate().isAfter(upTo)) {
                continue;
            }
            if (tx.getType() == TransactionType.DEPOSIT) {
                Money money = new Money(tx.getNetAmount().abs(), tx.getAccount().getAccountCurrency());
                net = net.add(fxRateService.convert(money, baseCurrency, tx.getTradeDate()).amount());
            } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                Money money = new Money(tx.getNetAmount().abs(), tx.getAccount().getAccountCurrency());
                net = net.subtract(fxRateService.convert(money, baseCurrency, tx.getTradeDate()).amount());
            }
        }
        return net;
    }

    private BigDecimal calculateNetDepositsInPeriod(List<Transaction> txs, Currency baseCurrency, Instant start, Instant end) {
        BigDecimal net = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if (tx.getTradeDate().isBefore(start) || tx.getTradeDate().isAfter(end)) {
                continue;
            }
            if (tx.getType() == TransactionType.DEPOSIT) {
                Money money = new Money(tx.getNetAmount().abs(), tx.getAccount().getAccountCurrency());
                net = net.add(fxRateService.convert(money, baseCurrency, tx.getTradeDate()).amount());
            } else if (tx.getType() == TransactionType.WITHDRAWAL) {
                Money money = new Money(tx.getNetAmount().abs(), tx.getAccount().getAccountCurrency());
                net = net.subtract(fxRateService.convert(money, baseCurrency, tx.getTradeDate()).amount());
            }
        }
        return net;
    }
}

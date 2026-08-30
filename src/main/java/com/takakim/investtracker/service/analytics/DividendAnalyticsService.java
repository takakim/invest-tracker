package com.takakim.investtracker.service.analytics;

import com.takakim.investtracker.api.ApiDtos.DividendAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.HoldingDividendMetricResponse;
import com.takakim.investtracker.api.ApiDtos.MonthlyDividendHistoryResponse;
import com.takakim.investtracker.api.ApiDtos.ProjectedMonthlyIncomeResponse;
import com.takakim.investtracker.api.ApiDtos.YearlyDividendHistoryResponse;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(noRollbackFor = {ResourceNotFoundException.class})
public class DividendAnalyticsService {

    private static final int SCALE = 4;
    private static final int PCT_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final MarketDataService marketDataService;
    private final FxRateService fxRateService;

    public DividendAnalyticsService(
            PortfolioRepository portfolioRepository,
            PositionRepository positionRepository,
            TransactionRepository transactionRepository,
            MarketDataService marketDataService,
            FxRateService fxRateService) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.marketDataService = marketDataService;
        this.fxRateService = fxRateService;
    }

    public DividendAnalyticsResponse calculate(UUID portfolioId, Instant asOf) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        Instant targetTime = asOf != null ? asOf : Instant.now();
        Currency baseCurrency = portfolio.getBaseCurrency();

        ZonedDateTime targetZdt = targetTime.atZone(ZoneOffset.UTC);
        Instant startOfYear = targetZdt.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant ttmStart = targetTime.minus(365, ChronoUnit.DAYS);

        // 1. Fetch all portfolio transactions
        List<Transaction> allTxs = transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId);
        List<Transaction> dividendTxs = allTxs.stream()
                .filter(t -> t.getType() == TransactionType.DIVIDEND && t.getStatus() == TransactionStatus.COMPLETED)
                .toList();

        BigDecimal totalDividendsAllTime = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalDividendsYtd = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalDividendsTtm = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalWithholdingTaxAllTime = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        Map<YearMonth, MonthlyDividendAccumulator> monthlyMap = new TreeMap<>();
        Map<Integer, YearlyDividendAccumulator> yearlyMap = new TreeMap<>();
        Map<UUID, List<Transaction>> dividendsByInstrument = new LinkedHashMap<>();

        for (Transaction tx : dividendTxs) {
            BigDecimal net = tx.getNetAmount();
            BigDecimal gross = tx.getGrossAmount() != null ? tx.getGrossAmount() : net;
            BigDecimal tax = tx.getTaxAmount() != null ? tx.getTaxAmount() : BigDecimal.ZERO;
            Currency txCurrency = new Currency(tx.getCurrency());

            Money netConverted = fxRateService.convert(new Money(net, txCurrency), baseCurrency, tx.getTradeDate());
            Money grossConverted = fxRateService.convert(new Money(gross, txCurrency), baseCurrency, tx.getTradeDate());
            Money taxConverted = fxRateService.convert(new Money(tax, txCurrency), baseCurrency, tx.getTradeDate());

            totalDividendsAllTime = totalDividendsAllTime.add(netConverted.amount());
            totalWithholdingTaxAllTime = totalWithholdingTaxAllTime.add(taxConverted.amount());

            if (!tx.getTradeDate().isBefore(startOfYear)) {
                totalDividendsYtd = totalDividendsYtd.add(netConverted.amount());
            }
            if (!tx.getTradeDate().isBefore(ttmStart)) {
                totalDividendsTtm = totalDividendsTtm.add(netConverted.amount());
            }

            // Monthly breakdown
            ZonedDateTime txZdt = tx.getTradeDate().atZone(ZoneOffset.UTC);
            YearMonth ym = YearMonth.of(txZdt.getYear(), txZdt.getMonth());
            monthlyMap.computeIfAbsent(ym, k -> new MonthlyDividendAccumulator())
                    .add(netConverted.amount(), grossConverted.amount(), taxConverted.amount());

            // Yearly breakdown
            yearlyMap.computeIfAbsent(txZdt.getYear(), k -> new YearlyDividendAccumulator())
                    .add(netConverted.amount(), grossConverted.amount(), taxConverted.amount());

            dividendsByInstrument.computeIfAbsent(tx.getInstrument().getId(), k -> new ArrayList<>()).add(tx);
        }

        // 2. Fetch positions
        List<Position> positions = positionRepository
                .findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE);

        Map<UUID, List<Position>> positionsByInstrument = positions.stream()
                .collect(Collectors.groupingBy(p -> p.getInstrument().getId()));

        Set<UUID> allInstrumentIds = new HashSet<>();
        allInstrumentIds.addAll(positionsByInstrument.keySet());
        allInstrumentIds.addAll(dividendsByInstrument.keySet());

        List<HoldingDividendMetricResponse> holdings = new ArrayList<>();
        BigDecimal projectedAnnualDividendIncome = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalPortfolioMarketValue = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalPortfolioCostBasis = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        Map<Integer, BigDecimal> projectedMonthlyTotals = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            projectedMonthlyTotals.put(m, BigDecimal.ZERO.setScale(SCALE, ROUNDING));
        }

        for (UUID instId : allInstrumentIds) {
            List<Position> instPositions = positionsByInstrument.getOrDefault(instId, Collections.emptyList());
            List<Transaction> instDividends = dividendsByInstrument.getOrDefault(instId, Collections.emptyList());

            Instrument instrument = !instPositions.isEmpty()
                    ? instPositions.get(0).getInstrument()
                    : instDividends.get(0).getInstrument();

            BigDecimal currentShares = instPositions.stream()
                    .map(Position::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal instCostBasisInBase = BigDecimal.ZERO;
            for (Position p : instPositions) {
                Money costMoney = p.getCostBasisMoney();
                if (costMoney != null) {
                    Money costInBase = fxRateService.convert(costMoney, baseCurrency, targetTime);
                    instCostBasisInBase = instCostBasisInBase.add(costInBase.amount());
                }
            }

            totalPortfolioCostBasis = totalPortfolioCostBasis.add(instCostBasisInBase);

            // Holding market value
            BigDecimal instMarketValueInBase = BigDecimal.ZERO;
            if (currentShares.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    PriceQuote quote = marketDataService.getLatestPrice(instrument.getId(), targetTime);
                    if (quote != null && quote.price().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal nativeVal = currentShares.multiply(quote.price());
                        Currency quoteCurr = new Currency(quote.currency());
                        Money valMoney = new Money(nativeVal, quoteCurr);
                        Money valInBase = fxRateService.convert(valMoney, baseCurrency, targetTime);
                        instMarketValueInBase = valInBase.amount();
                    }
                } catch (Exception ignored) {
                }
            }

            totalPortfolioMarketValue = totalPortfolioMarketValue.add(instMarketValueInBase);

            // Historical dividend totals for this instrument
            BigDecimal instTotalAllTime = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
            BigDecimal instTotalYtd = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
            BigDecimal instTotalTtm = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

            List<Transaction> ttmDividends = new ArrayList<>();

            for (Transaction tx : instDividends) {
                Currency txCurr = new Currency(tx.getCurrency());
                Money netConverted = fxRateService.convert(new Money(tx.getNetAmount(), txCurr), baseCurrency, tx.getTradeDate());
                instTotalAllTime = instTotalAllTime.add(netConverted.amount());

                if (!tx.getTradeDate().isBefore(startOfYear)) {
                    instTotalYtd = instTotalYtd.add(netConverted.amount());
                }
                if (!tx.getTradeDate().isBefore(ttmStart)) {
                    instTotalTtm = instTotalTtm.add(netConverted.amount());
                    ttmDividends.add(tx);
                }
            }

            Set<Integer> paymentMonths = new HashSet<>();
            if (!ttmDividends.isEmpty()) {
                for (Transaction tx : ttmDividends) {
                    paymentMonths.add(tx.getTradeDate().atZone(ZoneOffset.UTC).getMonthValue());
                }
            } else {
                for (Transaction tx : instDividends) {
                    paymentMonths.add(tx.getTradeDate().atZone(ZoneOffset.UTC).getMonthValue());
                }
            }

            // Calculate Trailing Twelve Months DPS (Dividend Per Share)
            BigDecimal ttmDps = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
            if (!ttmDividends.isEmpty()) {
                BigDecimal dpsSum = BigDecimal.ZERO;
                for (Transaction ttmTx : ttmDividends) {
                    BigDecimal txGross = ttmTx.getGrossAmount() != null ? ttmTx.getGrossAmount() : ttmTx.getNetAmount();
                    BigDecimal txShares = ttmTx.getQuantity();
                    if (txShares == null || txShares.compareTo(BigDecimal.ZERO) <= 0) {
                        txShares = currentShares;
                    }
                    if (txShares.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal perShareNative = txGross.divide(txShares, SCALE, ROUNDING);
                        Currency txCurr = new Currency(ttmTx.getCurrency());
                        Money dpsBase = fxRateService.convert(new Money(perShareNative, txCurr), baseCurrency, ttmTx.getTradeDate());
                        dpsSum = dpsSum.add(dpsBase.amount());
                    }
                }
                ttmDps = dpsSum.setScale(SCALE, ROUNDING);
            } else if (!instDividends.isEmpty() && currentShares.compareTo(BigDecimal.ZERO) > 0) {
                // If no dividend in TTM but older dividend exists, estimate from last known dividend * 4 (quarterly proxy)
                Transaction lastDiv = instDividends.get(0);
                BigDecimal lastGross = lastDiv.getGrossAmount() != null ? lastDiv.getGrossAmount() : lastDiv.getNetAmount();
                BigDecimal lastShares = lastDiv.getQuantity();
                if (lastShares == null || lastShares.compareTo(BigDecimal.ZERO) <= 0) {
                    lastShares = currentShares;
                }
                if (lastShares.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal singleDps = lastGross.divide(lastShares, SCALE, ROUNDING);
                    Currency txCurr = new Currency(lastDiv.getCurrency());
                    Money dpsBase = fxRateService.convert(new Money(singleDps, txCurr), baseCurrency, lastDiv.getTradeDate());
                    ttmDps = dpsBase.amount().multiply(new BigDecimal("4")).setScale(SCALE, ROUNDING);
                }
            }

            // Projected Annual Income for this holding
            BigDecimal holdingProjectedAnnualIncome = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
            if (currentShares.compareTo(BigDecimal.ZERO) > 0 && ttmDps.compareTo(BigDecimal.ZERO) > 0) {
                holdingProjectedAnnualIncome = currentShares.multiply(ttmDps).setScale(SCALE, ROUNDING);
            }

            projectedAnnualDividendIncome = projectedAnnualDividendIncome.add(holdingProjectedAnnualIncome);

            // Yield % and Yield on Cost %
            BigDecimal currentYieldPct = BigDecimal.ZERO.setScale(PCT_SCALE, ROUNDING);
            if (instMarketValueInBase.compareTo(BigDecimal.ZERO) > 0 && holdingProjectedAnnualIncome.compareTo(BigDecimal.ZERO) > 0) {
                currentYieldPct = holdingProjectedAnnualIncome.divide(instMarketValueInBase, 6, ROUNDING)
                        .multiply(new BigDecimal("100")).setScale(PCT_SCALE, ROUNDING);
            }

            BigDecimal yieldOnCostPct = BigDecimal.ZERO.setScale(PCT_SCALE, ROUNDING);
            if (instCostBasisInBase.compareTo(BigDecimal.ZERO) > 0 && holdingProjectedAnnualIncome.compareTo(BigDecimal.ZERO) > 0) {
                yieldOnCostPct = holdingProjectedAnnualIncome.divide(instCostBasisInBase, 6, ROUNDING)
                        .multiply(new BigDecimal("100")).setScale(PCT_SCALE, ROUNDING);
            }

            // Distribute projected income into monthly calendar
            if (holdingProjectedAnnualIncome.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal paymentPerMonth = holdingProjectedAnnualIncome.divide(
                        new BigDecimal(paymentMonths.size()), SCALE, ROUNDING);
                for (Integer monthVal : paymentMonths) {
                    projectedMonthlyTotals.merge(monthVal, paymentPerMonth, BigDecimal::add);
                }
            }

            holdings.add(new HoldingDividendMetricResponse(
                    instrument.getId(),
                    instrument.getName(),
                    instrument.getTicker(),
                    instrument.getIsin(),
                    instrument.getAssetClass(),
                    currentShares.setScale(SCALE, ROUNDING),
                    instTotalAllTime,
                    instTotalYtd,
                    instTotalTtm,
                    ttmDps,
                    holdingProjectedAnnualIncome,
                    currentYieldPct,
                    yieldOnCostPct,
                    baseCurrency.code()
            ));
        }

        // Sort holdings: active shares with highest projected income first, then by total all time
        holdings.sort(Comparator.comparing(HoldingDividendMetricResponse::projectedAnnualIncome)
                .thenComparing(HoldingDividendMetricResponse::totalReceivedAllTime).reversed());

        // Portfolio Yields
        BigDecimal portfolioDividendYieldPercentage = BigDecimal.ZERO.setScale(PCT_SCALE, ROUNDING);
        if (totalPortfolioMarketValue.compareTo(BigDecimal.ZERO) > 0 && projectedAnnualDividendIncome.compareTo(BigDecimal.ZERO) > 0) {
            portfolioDividendYieldPercentage = projectedAnnualDividendIncome.divide(totalPortfolioMarketValue, 6, ROUNDING)
                    .multiply(new BigDecimal("100")).setScale(PCT_SCALE, ROUNDING);
        }

        BigDecimal portfolioYieldOnCostPercentage = BigDecimal.ZERO.setScale(PCT_SCALE, ROUNDING);
        if (totalPortfolioCostBasis.compareTo(BigDecimal.ZERO) > 0 && projectedAnnualDividendIncome.compareTo(BigDecimal.ZERO) > 0) {
            portfolioYieldOnCostPercentage = projectedAnnualDividendIncome.divide(totalPortfolioCostBasis, 6, ROUNDING)
                    .multiply(new BigDecimal("100")).setScale(PCT_SCALE, ROUNDING);
        }

        // Build Monthly History
        List<MonthlyDividendHistoryResponse> monthlyHistory = monthlyMap.entrySet().stream()
                .map(e -> new MonthlyDividendHistoryResponse(
                        e.getKey().toString(),
                        e.getValue().net.setScale(SCALE, ROUNDING),
                        e.getValue().gross.setScale(SCALE, ROUNDING),
                        e.getValue().tax.setScale(SCALE, ROUNDING),
                        baseCurrency.code()
                ))
                .toList();

        // Build Yearly History
        List<YearlyDividendHistoryResponse> yearlyHistory = yearlyMap.entrySet().stream()
                .map(e -> new YearlyDividendHistoryResponse(
                        e.getKey(),
                        e.getValue().net.setScale(SCALE, ROUNDING),
                        e.getValue().gross.setScale(SCALE, ROUNDING),
                        e.getValue().tax.setScale(SCALE, ROUNDING),
                        baseCurrency.code()
                ))
                .toList();

        // Build Projected Monthly Calendar
        List<ProjectedMonthlyIncomeResponse> projectedMonthlyCalendar = projectedMonthlyTotals.entrySet().stream()
                .map(e -> new ProjectedMonthlyIncomeResponse(
                        e.getKey(),
                        Month.of(e.getKey()).getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        e.getValue().setScale(SCALE, ROUNDING),
                        baseCurrency.code()
                ))
                .toList();

        return new DividendAnalyticsResponse(
                portfolioId,
                targetTime,
                baseCurrency.code(),
                totalDividendsAllTime,
                totalDividendsYtd,
                totalDividendsTtm,
                totalWithholdingTaxAllTime,
                projectedAnnualDividendIncome,
                portfolioDividendYieldPercentage,
                portfolioYieldOnCostPercentage,
                monthlyHistory,
                yearlyHistory,
                projectedMonthlyCalendar,
                holdings
        );
    }

    private static class MonthlyDividendAccumulator {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        void add(BigDecimal n, BigDecimal g, BigDecimal t) {
            this.net = this.net.add(n);
            this.gross = this.gross.add(g);
            this.tax = this.tax.add(t);
        }
    }

    private static class YearlyDividendAccumulator {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        void add(BigDecimal n, BigDecimal g, BigDecimal t) {
            this.net = this.net.add(n);
            this.gross = this.gross.add(g);
            this.tax = this.tax.add(t);
        }
    }
}

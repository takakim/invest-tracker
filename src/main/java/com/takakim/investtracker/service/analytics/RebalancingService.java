package com.takakim.investtracker.service.analytics;

import com.takakim.investtracker.api.ApiDtos.RebalanceAnalysisResponse;
import com.takakim.investtracker.api.ApiDtos.RebalanceOrderItemResponse;
import com.takakim.investtracker.domain.AllocationType;
import com.takakim.investtracker.domain.DriftStatus;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.RebalanceAction;
import com.takakim.investtracker.domain.TargetAllocationItem;
import com.takakim.investtracker.domain.TargetAllocationPlan;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TargetAllocationPlanRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RebalancingService {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final PortfolioRepository portfolioRepository;
    private final TargetAllocationPlanRepository planRepository;
    private final AnalyticsEngine analyticsEngine;
    private final MarketDataService marketDataService;

    public RebalancingService(
            PortfolioRepository portfolioRepository,
            TargetAllocationPlanRepository planRepository,
            AnalyticsEngine analyticsEngine,
            MarketDataService marketDataService) {
        this.portfolioRepository = portfolioRepository;
        this.planRepository = planRepository;
        this.analyticsEngine = analyticsEngine;
        this.marketDataService = marketDataService;
    }

    public RebalanceAnalysisResponse generateRebalanceAnalysis(UUID portfolioId, BigDecimal cashInjection, Instant asOf) {
        Objects.requireNonNull(portfolioId, "Portfolio ID must not be null");

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with ID: " + portfolioId));

        TargetAllocationPlan plan = planRepository.findByPortfolioIdWithItems(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Target allocation plan not found for portfolio: " + portfolioId));

        Instant targetTime = asOf != null ? asOf : Instant.now();
        PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, targetTime);

        BigDecimal totalCurrentValue = analytics.totalCurrentValue().setScale(SCALE, ROUNDING);
        BigDecimal cashAmount = cashInjection != null && cashInjection.compareTo(BigDecimal.ZERO) > 0
                ? cashInjection.setScale(SCALE, ROUNDING)
                : BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        BigDecimal totalPostValue = totalCurrentValue.add(cashAmount).setScale(SCALE, ROUNDING);
        BigDecimal tolerance = plan.getDriftTolerancePct().setScale(2, ROUNDING);

        List<RebalanceOrderItemResponse> orderItems = new ArrayList<>();
        boolean hasExceededTolerance = false;

        if (plan.getAllocationType() == AllocationType.ASSET_CLASS) {
            Map<String, BigDecimal> currentValuesByCategory = new HashMap<>();
            for (AllocationItem ai : analytics.byAssetClass()) {
                currentValuesByCategory.put(ai.category().toUpperCase(), ai.marketValue());
            }
            if (analytics.totalCashValue().compareTo(BigDecimal.ZERO) > 0) {
                currentValuesByCategory.put("CASH", analytics.totalCashValue());
            }

            orderItems = calculateAssetClassOrders(
                    plan.getItems(), currentValuesByCategory, totalCurrentValue, cashAmount, totalPostValue, tolerance, portfolio.getBaseCurrency().code()
            );
        } else {
            // INSTRUMENT allocation type
            Map<UUID, HoldingExposure> holdingsByInst = new HashMap<>();
            for (HoldingExposure h : analytics.topHoldings()) {
                holdingsByInst.put(h.instrumentId(), h);
            }

            orderItems = calculateInstrumentOrders(
                    plan.getItems(), holdingsByInst, totalCurrentValue, cashAmount, totalPostValue, tolerance, portfolio.getBaseCurrency().code(), targetTime
            );
        }

        for (RebalanceOrderItemResponse item : orderItems) {
            if (item.isDriftExceeded()) {
                hasExceededTolerance = true;
                break;
            }
        }

        return new RebalanceAnalysisResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getBaseCurrency().code(),
                targetTime,
                plan.getAllocationType(),
                totalCurrentValue.setScale(2, ROUNDING),
                cashAmount.setScale(2, ROUNDING),
                totalPostValue.setScale(2, ROUNDING),
                tolerance,
                hasExceededTolerance,
                orderItems
        );
    }

    private List<RebalanceOrderItemResponse> calculateAssetClassOrders(
            List<TargetAllocationItem> planItems,
            Map<String, BigDecimal> currentValues,
            BigDecimal totalCurrentValue,
            BigDecimal cashAmount,
            BigDecimal totalPostValue,
            BigDecimal tolerance,
            String currency) {

        List<RebalanceOrderItemResponse> items = new ArrayList<>();
        boolean isCashInjectionMode = cashAmount.compareTo(BigDecimal.ZERO) > 0;

        // First pass: compute current weights, drifts, ideal target post values
        Map<String, BigDecimal> shortfalls = new HashMap<>();
        BigDecimal totalPositiveShortfall = BigDecimal.ZERO;

        for (TargetAllocationItem planItem : planItems) {
            String key = planItem.getCategoryKey().toUpperCase();
            BigDecimal currentVal = currentValues.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal targetPct = planItem.getTargetPercentage();

            BigDecimal idealPostVal = totalPostValue.multiply(targetPct).divide(ONE_HUNDRED, SCALE, ROUNDING);
            BigDecimal shortfall = idealPostVal.subtract(currentVal);

            if (shortfall.compareTo(BigDecimal.ZERO) > 0) {
                shortfalls.put(key, shortfall);
                totalPositiveShortfall = totalPositiveShortfall.add(shortfall);
            } else {
                shortfalls.put(key, BigDecimal.ZERO);
            }
        }

        for (TargetAllocationItem planItem : planItems) {
            String key = planItem.getCategoryKey().toUpperCase();
            BigDecimal currentVal = currentValues.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal currentWeight = totalCurrentValue.compareTo(BigDecimal.ZERO) > 0
                    ? currentVal.divide(totalCurrentValue, 6, ROUNDING).multiply(ONE_HUNDRED).setScale(2, ROUNDING)
                    : BigDecimal.ZERO.setScale(2, ROUNDING);

            BigDecimal targetPct = planItem.getTargetPercentage().setScale(2, ROUNDING);
            BigDecimal driftPct = currentWeight.subtract(targetPct).setScale(2, ROUNDING);

            DriftStatus driftStatus;
            boolean isDriftExceeded = driftPct.abs().compareTo(tolerance) > 0;
            if (driftPct.compareTo(tolerance) > 0) {
                driftStatus = DriftStatus.OVERWEIGHT;
            } else if (driftPct.compareTo(tolerance.negate()) < 0) {
                driftStatus = DriftStatus.UNDERWEIGHT;
            } else {
                driftStatus = DriftStatus.IN_TOLERANCE;
            }

            BigDecimal targetValue = totalPostValue.multiply(targetPct).divide(ONE_HUNDRED, SCALE, ROUNDING);
            RebalanceAction action = RebalanceAction.HOLD;
            BigDecimal orderAmount = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

            if (isCashInjectionMode) {
                BigDecimal shortfall = shortfalls.getOrDefault(key, BigDecimal.ZERO);
                if (totalPositiveShortfall.compareTo(BigDecimal.ZERO) > 0 && shortfall.compareTo(BigDecimal.ZERO) > 0) {
                    orderAmount = cashAmount.multiply(shortfall).divide(totalPositiveShortfall, SCALE, ROUNDING);
                    if (orderAmount.compareTo(BigDecimal.ZERO) > 0) {
                        action = RebalanceAction.BUY;
                    }
                }
            } else {
                // Full Rebalance (Buy/Sell)
                BigDecimal delta = targetValue.subtract(currentVal);
                if (delta.compareTo(new BigDecimal("0.01")) > 0) {
                    action = RebalanceAction.BUY;
                    orderAmount = delta;
                } else if (delta.compareTo(new BigDecimal("-0.01")) < 0) {
                    action = RebalanceAction.SELL;
                    orderAmount = delta.abs();
                }
            }

            BigDecimal postValue = currentVal;
            if (action == RebalanceAction.BUY) {
                postValue = postValue.add(orderAmount);
            } else if (action == RebalanceAction.SELL) {
                postValue = postValue.subtract(orderAmount);
            }

            BigDecimal postWeight = totalPostValue.compareTo(BigDecimal.ZERO) > 0
                    ? postValue.divide(totalPostValue, 6, ROUNDING).multiply(ONE_HUNDRED).setScale(2, ROUNDING)
                    : BigDecimal.ZERO.setScale(2, ROUNDING);

            items.add(new RebalanceOrderItemResponse(
                    planItem.getCategoryKey(),
                    planItem.getCategoryLabel(),
                    null,
                    null,
                    null,
                    action,
                    currentVal.setScale(2, ROUNDING),
                    currentWeight,
                    targetPct,
                    driftPct,
                    driftStatus,
                    isDriftExceeded,
                    targetValue.setScale(2, ROUNDING),
                    orderAmount.setScale(2, ROUNDING),
                    null,
                    null,
                    postWeight,
                    currency
            ));
        }

        return items;
    }

    private List<RebalanceOrderItemResponse> calculateInstrumentOrders(
            List<TargetAllocationItem> planItems,
            Map<UUID, HoldingExposure> holdingsByInst,
            BigDecimal totalCurrentValue,
            BigDecimal cashAmount,
            BigDecimal totalPostValue,
            BigDecimal tolerance,
            String currency,
            Instant asOf) {

        List<RebalanceOrderItemResponse> items = new ArrayList<>();
        boolean isCashInjectionMode = cashAmount.compareTo(BigDecimal.ZERO) > 0;

        Map<UUID, BigDecimal> shortfalls = new HashMap<>();
        BigDecimal totalPositiveShortfall = BigDecimal.ZERO;

        for (TargetAllocationItem planItem : planItems) {
            Instrument inst = planItem.getInstrument();
            UUID instId = inst != null ? inst.getId() : null;
            BigDecimal currentVal = BigDecimal.ZERO;

            if (instId != null && holdingsByInst.containsKey(instId)) {
                currentVal = holdingsByInst.get(instId).marketValue();
            }

            BigDecimal targetPct = planItem.getTargetPercentage();
            BigDecimal idealPostVal = totalPostValue.multiply(targetPct).divide(ONE_HUNDRED, SCALE, ROUNDING);
            BigDecimal shortfall = idealPostVal.subtract(currentVal);

            if (shortfall.compareTo(BigDecimal.ZERO) > 0 && instId != null) {
                shortfalls.put(instId, shortfall);
                totalPositiveShortfall = totalPositiveShortfall.add(shortfall);
            }
        }

        for (TargetAllocationItem planItem : planItems) {
            Instrument inst = planItem.getInstrument();
            UUID instId = inst != null ? inst.getId() : null;

            BigDecimal currentVal = BigDecimal.ZERO;
            BigDecimal currentPrice = null;

            if (instId != null && holdingsByInst.containsKey(instId)) {
                HoldingExposure h = holdingsByInst.get(instId);
                currentVal = h.marketValue();
                currentPrice = h.currentPrice();
            } else if (instId != null) {
                try {
                    PriceQuote q = marketDataService.getLatestPrice(instId, asOf);
                    if (q != null && q.price() != null) {
                        currentPrice = q.price();
                    }
                } catch (Exception ignored) {
                }
            }

            BigDecimal currentWeight = totalCurrentValue.compareTo(BigDecimal.ZERO) > 0
                    ? currentVal.divide(totalCurrentValue, 6, ROUNDING).multiply(ONE_HUNDRED).setScale(2, ROUNDING)
                    : BigDecimal.ZERO.setScale(2, ROUNDING);

            BigDecimal targetPct = planItem.getTargetPercentage().setScale(2, ROUNDING);
            BigDecimal driftPct = currentWeight.subtract(targetPct).setScale(2, ROUNDING);

            DriftStatus driftStatus;
            boolean isDriftExceeded = driftPct.abs().compareTo(tolerance) > 0;
            if (driftPct.compareTo(tolerance) > 0) {
                driftStatus = DriftStatus.OVERWEIGHT;
            } else if (driftPct.compareTo(tolerance.negate()) < 0) {
                driftStatus = DriftStatus.UNDERWEIGHT;
            } else {
                driftStatus = DriftStatus.IN_TOLERANCE;
            }

            BigDecimal targetValue = totalPostValue.multiply(targetPct).divide(ONE_HUNDRED, SCALE, ROUNDING);
            RebalanceAction action = RebalanceAction.HOLD;
            BigDecimal orderAmount = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

            if (isCashInjectionMode) {
                BigDecimal shortfall = instId != null ? shortfalls.getOrDefault(instId, BigDecimal.ZERO) : BigDecimal.ZERO;
                if (totalPositiveShortfall.compareTo(BigDecimal.ZERO) > 0 && shortfall.compareTo(BigDecimal.ZERO) > 0) {
                    orderAmount = cashAmount.multiply(shortfall).divide(totalPositiveShortfall, SCALE, ROUNDING);
                    if (orderAmount.compareTo(BigDecimal.ZERO) > 0) {
                        action = RebalanceAction.BUY;
                    }
                }
            } else {
                BigDecimal delta = targetValue.subtract(currentVal);
                if (delta.compareTo(new BigDecimal("0.01")) > 0) {
                    action = RebalanceAction.BUY;
                    orderAmount = delta;
                } else if (delta.compareTo(new BigDecimal("-0.01")) < 0) {
                    action = RebalanceAction.SELL;
                    orderAmount = delta.abs();
                }
            }

            BigDecimal estimatedQuantity = null;
            if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0 && orderAmount.compareTo(BigDecimal.ZERO) > 0) {
                estimatedQuantity = orderAmount.divide(currentPrice, SCALE, ROUNDING);
            }

            BigDecimal postValue = currentVal;
            if (action == RebalanceAction.BUY) {
                postValue = postValue.add(orderAmount);
            } else if (action == RebalanceAction.SELL) {
                postValue = postValue.subtract(orderAmount);
            }

            BigDecimal postWeight = totalPostValue.compareTo(BigDecimal.ZERO) > 0
                    ? postValue.divide(totalPostValue, 6, ROUNDING).multiply(ONE_HUNDRED).setScale(2, ROUNDING)
                    : BigDecimal.ZERO.setScale(2, ROUNDING);

            items.add(new RebalanceOrderItemResponse(
                    planItem.getCategoryKey(),
                    planItem.getCategoryLabel(),
                    instId,
                    inst != null ? inst.getTicker() : null,
                    inst != null ? inst.getName() : null,
                    action,
                    currentVal.setScale(2, ROUNDING),
                    currentWeight,
                    targetPct,
                    driftPct,
                    driftStatus,
                    isDriftExceeded,
                    targetValue.setScale(2, ROUNDING),
                    orderAmount.setScale(2, ROUNDING),
                    currentPrice != null ? currentPrice.setScale(2, ROUNDING) : null,
                    estimatedQuantity != null ? estimatedQuantity.setScale(4, ROUNDING) : null,
                    postWeight,
                    currency
            ));
        }

        return items;
    }
}

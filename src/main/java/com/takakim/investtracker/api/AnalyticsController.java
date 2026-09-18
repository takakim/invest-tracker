package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.AllocationItemResponse;
import com.takakim.investtracker.api.ApiDtos.DividendAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.HoldingExposureResponse;
import com.takakim.investtracker.api.ApiDtos.PortfolioAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationPlanRequest;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationPlanResponse;
import com.takakim.investtracker.api.ApiDtos.RebalanceAnalysisResponse;
import com.takakim.investtracker.service.analytics.AllocationItem;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.DividendAnalyticsService;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.analytics.RebalancingService;
import com.takakim.investtracker.service.analytics.TargetAllocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

    private final AnalyticsEngine analyticsEngine;
    private final DividendAnalyticsService dividendAnalyticsService;
    private final TargetAllocationService targetAllocationService;
    private final RebalancingService rebalancingService;
    private final com.takakim.investtracker.service.analytics.PortfolioHistoryService portfolioHistoryService;

    public AnalyticsController(
            AnalyticsEngine analyticsEngine,
            DividendAnalyticsService dividendAnalyticsService,
            TargetAllocationService targetAllocationService,
            RebalancingService rebalancingService,
            com.takakim.investtracker.service.analytics.PortfolioHistoryService portfolioHistoryService) {
        this.analyticsEngine = analyticsEngine;
        this.dividendAnalyticsService = dividendAnalyticsService;
        this.targetAllocationService = targetAllocationService;
        this.rebalancingService = rebalancingService;
        this.portfolioHistoryService = portfolioHistoryService;
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/history")
    public ApiDtos.PortfolioHistoryResponse getPortfolioHistory(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) UUID benchmarkId) {
        return portfolioHistoryService.generateHistory(portfolioId, period, interval, benchmarkId);
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/history/backfill")
    public ApiDtos.PortfolioBackfillResponse backfillPortfolioHistory(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String range) {
        return portfolioHistoryService.syncPortfolioHistory(portfolioId, range);
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/analytics")
    public PortfolioAnalyticsResponse getPortfolioAnalytics(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) Instant asOf) {
        PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, asOf);
        return toResponse(analytics);
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/analytics/dividends")
    public DividendAnalyticsResponse getDividendAnalytics(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) Instant asOf) {
        return dividendAnalyticsService.calculate(portfolioId, asOf);
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/target-allocation")
    public ResponseEntity<TargetAllocationPlanResponse> getTargetAllocation(
            @PathVariable UUID portfolioId) {
        return targetAllocationService.getTargetPlan(portfolioId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/portfolios/{portfolioId}/target-allocation")
    public TargetAllocationPlanResponse saveTargetAllocation(
            @PathVariable UUID portfolioId,
            @RequestBody TargetAllocationPlanRequest request) {
        return targetAllocationService.saveTargetPlan(portfolioId, request);
    }

    @DeleteMapping("/api/v1/portfolios/{portfolioId}/target-allocation")
    public ResponseEntity<Void> deleteTargetAllocation(
            @PathVariable UUID portfolioId) {
        targetAllocationService.deleteTargetPlan(portfolioId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/rebalancing")
    public RebalanceAnalysisResponse getRebalancingAnalysis(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) BigDecimal cashInjection,
            @RequestParam(required = false) Instant asOf) {
        return rebalancingService.generateRebalanceAnalysis(portfolioId, cashInjection, asOf);
    }

    private PortfolioAnalyticsResponse toResponse(PortfolioAnalytics a) {
        return new PortfolioAnalyticsResponse(
                a.portfolioId(),
                a.asOf(),
                a.baseCurrency(),
                a.totalCurrentValue(),
                a.totalCostBasis(),
                a.totalUnrealizedGainLoss(),
                a.totalUnrealizedReturnPercentage(),
                a.totalRealizedGainLoss(),
                a.totalCashValue(),
                a.byAssetClass().stream().map(this::toAllocationResponse).toList(),
                a.byCurrency().stream().map(this::toAllocationResponse).toList(),
                a.byAccount().stream().map(this::toAllocationResponse).toList(),
                a.topHoldings().stream().map(this::toHoldingResponse).toList(),
                a.warnings()
        );
    }

    private AllocationItemResponse toAllocationResponse(AllocationItem item) {
        return new AllocationItemResponse(
                item.category(),
                item.marketValue(),
                item.percentage(),
                item.costBasis(),
                item.unrealizedGainLoss()
        );
    }

    private HoldingExposureResponse toHoldingResponse(HoldingExposure h) {
        return new HoldingExposureResponse(
                h.instrumentId(),
                h.instrumentName(),
                h.ticker(),
                h.assetClass().name(),
                h.quantity(),
                h.currentPrice(),
                h.marketValue(),
                h.costBasis(),
                h.unrealizedGainLoss(),
                h.weightPercentage(),
                h.currency(),
                h.nativePrice(),
                h.nativeCurrency(),
                h.nativeCostBasis(),
                h.nativeUnrealizedGainLoss(),
                h.nativeGainLossPercentage()
        );
    }
}

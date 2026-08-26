package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.AllocationItemResponse;
import com.takakim.investtracker.api.ApiDtos.HoldingExposureResponse;
import com.takakim.investtracker.api.ApiDtos.PortfolioAnalyticsResponse;
import com.takakim.investtracker.service.analytics.AllocationItem;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

    private final AnalyticsEngine analyticsEngine;

    public AnalyticsController(AnalyticsEngine analyticsEngine) {
        this.analyticsEngine = analyticsEngine;
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/analytics")
    public PortfolioAnalyticsResponse getPortfolioAnalytics(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) Instant asOf) {
        PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, asOf);
        return toResponse(analytics);
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
                h.currency()
        );
    }
}

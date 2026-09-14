package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.CashFlowAnalyticsResponse;
import com.takakim.investtracker.service.analytics.CashFlowAnalyticsService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CashFlowController {

    private final CashFlowAnalyticsService cashFlowAnalyticsService;

    public CashFlowController(CashFlowAnalyticsService cashFlowAnalyticsService) {
        this.cashFlowAnalyticsService = cashFlowAnalyticsService;
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/cash-flows")
    public CashFlowAnalyticsResponse getCashFlowAnalytics(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String groupBy) {
        return cashFlowAnalyticsService.calculateCashFlows(portfolioId, period, groupBy);
    }
}

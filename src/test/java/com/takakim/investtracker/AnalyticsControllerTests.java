package com.takakim.investtracker;

import com.takakim.investtracker.api.AnalyticsController;
import com.takakim.investtracker.api.ApiDtos.DividendAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.PortfolioBackfillResponse;
import com.takakim.investtracker.api.ApiDtos.PortfolioHistoryResponse;
import com.takakim.investtracker.api.ApiDtos.RebalanceAnalysisResponse;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationPlanRequest;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationPlanResponse;
import com.takakim.investtracker.domain.AllocationType;
import com.takakim.investtracker.service.analytics.AllocationItem;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.DividendAnalyticsService;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.analytics.PortfolioHistoryService;
import com.takakim.investtracker.service.analytics.RebalancingService;
import com.takakim.investtracker.service.analytics.TargetAllocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsControllerTests {

    private AnalyticsEngine analyticsEngine;
    private DividendAnalyticsService dividendAnalyticsService;
    private TargetAllocationService targetAllocationService;
    private RebalancingService rebalancingService;
    private PortfolioHistoryService portfolioHistoryService;
    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        analyticsEngine = mock(AnalyticsEngine.class);
        dividendAnalyticsService = mock(DividendAnalyticsService.class);
        targetAllocationService = mock(TargetAllocationService.class);
        rebalancingService = mock(RebalancingService.class);
        portfolioHistoryService = mock(PortfolioHistoryService.class);

        controller = new AnalyticsController(
                analyticsEngine,
                dividendAnalyticsService,
                targetAllocationService,
                rebalancingService,
                portfolioHistoryService
        );
    }

    @Test
    void testBackfillPortfolioHistoryDelegation() {
        UUID portfolioId = UUID.randomUUID();
        PortfolioBackfillResponse mockResponse = new PortfolioBackfillResponse(
                portfolioId, 2, 250, List.of("AAPL: 150 points (SUCCESS)")
        );

        when(portfolioHistoryService.syncPortfolioHistory(portfolioId, "1Y")).thenReturn(mockResponse);

        PortfolioBackfillResponse actual = controller.backfillPortfolioHistory(portfolioId, "1Y");
        assertNotNull(actual);
        assertEquals(portfolioId, actual.portfolioId());
        assertEquals(2, actual.instrumentsProcessed());
        assertEquals(250, actual.totalObservationsSynced());
        assertEquals(1, actual.details().size());

        verify(portfolioHistoryService).syncPortfolioHistory(portfolioId, "1Y");
    }

    @Test
    void testGetPortfolioHistoryDelegation() {
        UUID portfolioId = UUID.randomUUID();
        UUID benchmarkId = UUID.randomUUID();
        PortfolioHistoryResponse mockResponse = new PortfolioHistoryResponse(
                portfolioId, "Retirement", "GBP", "1Y", "MONTHLY",
                Instant.now(), Instant.now(), benchmarkId, "SPX", "S&P 500",
                null, List.of()
        );

        when(portfolioHistoryService.generateHistory(portfolioId, "1Y", "MONTHLY", benchmarkId))
                .thenReturn(mockResponse);

        PortfolioHistoryResponse actual = controller.getPortfolioHistory(portfolioId, "1Y", "MONTHLY", benchmarkId);
        assertNotNull(actual);
        assertEquals("Retirement", actual.portfolioName());
    }

    @Test
    void testGetPortfolioAnalyticsDelegation() {
        UUID portfolioId = UUID.randomUUID();
        Instant now = Instant.now();
        AllocationItem item = new AllocationItem("Stocks", BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.TEN, BigDecimal.ZERO);
        HoldingExposure holding = new HoldingExposure(
                UUID.randomUUID(), "Apple", "AAPL", com.takakim.investtracker.domain.AssetClass.STOCK,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.valueOf(100), "USD"
        );

        PortfolioAnalytics analytics = new PortfolioAnalytics(
                portfolioId, now, "USD", BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(item), List.of(item), List.of(item), List.of(holding), List.of("Warning")
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(analytics);

        var resp = controller.getPortfolioAnalytics(portfolioId, now);
        assertNotNull(resp);
        assertEquals(portfolioId, resp.portfolioId());
        assertEquals(1, resp.byAssetClass().size());
        assertEquals(1, resp.topHoldings().size());
    }

    @Test
    void testGetDividendAnalyticsDelegation() {
        UUID portfolioId = UUID.randomUUID();
        Instant now = Instant.now();
        DividendAnalyticsResponse mockResp = mock(DividendAnalyticsResponse.class);
        when(mockResp.portfolioId()).thenReturn(portfolioId);

        when(dividendAnalyticsService.calculate(portfolioId, now)).thenReturn(mockResp);

        var actual = controller.getDividendAnalytics(portfolioId, now);
        assertNotNull(actual);
        assertEquals(portfolioId, actual.portfolioId());
    }

    @Test
    void testTargetAllocationEndpoints() {
        UUID portfolioId = UUID.randomUUID();
        TargetAllocationPlanResponse plan = new TargetAllocationPlanResponse(
                UUID.randomUUID(), portfolioId, "Core", AllocationType.ASSET_CLASS,
                BigDecimal.valueOf(5), List.of(), Instant.now()
        );

        when(targetAllocationService.getTargetPlan(portfolioId)).thenReturn(Optional.of(plan));
        assertEquals(ResponseEntity.ok(plan), controller.getTargetAllocation(portfolioId));

        when(targetAllocationService.getTargetPlan(portfolioId)).thenReturn(Optional.empty());
        assertEquals(ResponseEntity.notFound().build(), controller.getTargetAllocation(portfolioId));

        TargetAllocationPlanRequest req = new TargetAllocationPlanRequest("Core", AllocationType.ASSET_CLASS, BigDecimal.valueOf(5), List.of());
        when(targetAllocationService.saveTargetPlan(portfolioId, req)).thenReturn(plan);
        assertEquals(plan, controller.saveTargetAllocation(portfolioId, req));

        ResponseEntity<Void> delResp = controller.deleteTargetAllocation(portfolioId);
        assertEquals(ResponseEntity.noContent().build(), delResp);
        verify(targetAllocationService).deleteTargetPlan(portfolioId);
    }

    @Test
    void testGetRebalanceAnalysis() {
        UUID portfolioId = UUID.randomUUID();
        BigDecimal cash = BigDecimal.valueOf(500);
        Instant now = Instant.now();
        RebalanceAnalysisResponse mockResp = mock(RebalanceAnalysisResponse.class);

        when(rebalancingService.generateRebalanceAnalysis(portfolioId, cash, now)).thenReturn(mockResp);

        assertEquals(mockResp, controller.getRebalancingAnalysis(portfolioId, cash, now));
        verify(rebalancingService).generateRebalanceAnalysis(portfolioId, cash, now);
    }
}

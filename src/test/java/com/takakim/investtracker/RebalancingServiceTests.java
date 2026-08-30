package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.RebalanceAnalysisResponse;
import com.takakim.investtracker.api.ApiDtos.RebalanceOrderItemResponse;
import com.takakim.investtracker.domain.AllocationType;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.DriftStatus;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.RebalanceAction;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.TargetAllocationItem;
import com.takakim.investtracker.domain.TargetAllocationPlan;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TargetAllocationPlanRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.AllocationItem;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.analytics.RebalancingService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RebalancingServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private TargetAllocationPlanRepository planRepository;
    @Mock
    private AnalyticsEngine analyticsEngine;
    @Mock
    private MarketDataService marketDataService;

    private RebalancingService rebalancingService;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Instrument vusa;
    private Instrument aapl;

    @BeforeEach
    void setUp() {
        rebalancingService = new RebalancingService(
                portfolioRepository, planRepository, analyticsEngine, marketDataService
        );

        portfolio = new Portfolio("Core Growth", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        portfolioId = portfolio.getId();
        vusa = new Instrument("Vanguard S&P 500 ETF", AssetClass.ETF, "VUSA", "IE00B3XXRP09", "LSE", new Currency("GBP"));
        aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
    }

    @Test
    void generateRebalanceAnalysis_portfolioNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> rebalancingService.generateRebalanceAnalysis(portfolioId, null, null));
    }

    @Test
    void generateRebalanceAnalysis_targetPlanNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> rebalancingService.generateRebalanceAnalysis(portfolioId, null, null));
    }

    @Test
    void generateRebalanceAnalysis_assetClassFullRebalance_calculatesBuysAndSells() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Target Plan: 60% STOCK, 30% ETF, 10% CASH (tolerance 5%)
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "60/30/10 Asset Plan", AllocationType.ASSET_CLASS, new BigDecimal("5.00"));
        plan.addItem(new TargetAllocationItem(plan, "STOCK", "Equities", new BigDecimal("60.00"), null));
        plan.addItem(new TargetAllocationItem(plan, "ETF", "ETFs", new BigDecimal("30.00"), null));
        plan.addItem(new TargetAllocationItem(plan, "CASH", "Cash Buffer", new BigDecimal("10.00"), null));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        // Current Portfolio: Total 10,000 GBP (STOCK = 8,000 (80%), ETF = 1,000 (10%), CASH = 1,000 (10%))
        // STOCK is overweight (+20% drift > 5% tolerance -> SELL 2,000 GBP)
        // ETF is underweight (-20% drift > 5% tolerance -> BUY 2,000 GBP)
        // CASH is on target (10% -> HOLD 0 GBP)
        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("10000.00"), new BigDecimal("9000.00"),
                new BigDecimal("1000.00"), new BigDecimal("11.11"),
                BigDecimal.ZERO, new BigDecimal("1000.00"),
                List.of(
                        new AllocationItem("STOCK", new BigDecimal("8000.00"), new BigDecimal("80.00"), new BigDecimal("7000.00"), new BigDecimal("1000.00")),
                        new AllocationItem("ETF", new BigDecimal("1000.00"), new BigDecimal("10.00"), new BigDecimal("1000.00"), BigDecimal.ZERO)
                ),
                List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, null, now);

        assertNotNull(response);
        assertEquals(portfolioId, response.portfolioId());
        assertEquals("Core Growth", response.portfolioName());
        assertEquals("GBP", response.baseCurrency());
        assertEquals(AllocationType.ASSET_CLASS, response.allocationType());
        assertEquals(new BigDecimal("10000.00"), response.totalPortfolioValue());
        assertEquals(new BigDecimal("0.00"), response.cashInjectionAmount());
        assertEquals(new BigDecimal("10000.00"), response.totalPostRebalanceValue());
        assertTrue(response.hasDriftToleranceExceeded());

        assertEquals(3, response.items().size());

        // Item 1: STOCK
        RebalanceOrderItemResponse stockItem = response.items().get(0);
        assertEquals("STOCK", stockItem.categoryKey());
        assertEquals(new BigDecimal("8000.00"), stockItem.currentMarketValue());
        assertEquals(new BigDecimal("80.00"), stockItem.currentWeightPercentage());
        assertEquals(new BigDecimal("60.00"), stockItem.targetWeightPercentage());
        assertEquals(new BigDecimal("20.00"), stockItem.driftPercentage());
        assertEquals(DriftStatus.OVERWEIGHT, stockItem.driftStatus());
        assertTrue(stockItem.isDriftExceeded());
        assertEquals(RebalanceAction.SELL, stockItem.action());
        assertEquals(new BigDecimal("2000.00"), stockItem.orderAmount());
        assertEquals(new BigDecimal("60.00"), stockItem.projectedPostWeightPercentage());

        // Item 2: ETF
        RebalanceOrderItemResponse etfItem = response.items().get(1);
        assertEquals("ETF", etfItem.categoryKey());
        assertEquals(new BigDecimal("1000.00"), etfItem.currentMarketValue());
        assertEquals(new BigDecimal("10.00"), etfItem.currentWeightPercentage());
        assertEquals(new BigDecimal("30.00"), etfItem.targetWeightPercentage());
        assertEquals(new BigDecimal("-20.00"), etfItem.driftPercentage());
        assertEquals(DriftStatus.UNDERWEIGHT, etfItem.driftStatus());
        assertTrue(etfItem.isDriftExceeded());
        assertEquals(RebalanceAction.BUY, etfItem.action());
        assertEquals(new BigDecimal("2000.00"), etfItem.orderAmount());
        assertEquals(new BigDecimal("30.00"), etfItem.projectedPostWeightPercentage());

        // Item 3: CASH
        RebalanceOrderItemResponse cashItem = response.items().get(2);
        assertEquals("CASH", cashItem.categoryKey());
        assertEquals(new BigDecimal("1000.00"), cashItem.currentMarketValue());
        assertEquals(new BigDecimal("10.00"), cashItem.currentWeightPercentage());
        assertEquals(new BigDecimal("10.00"), cashItem.targetWeightPercentage());
        assertEquals(new BigDecimal("0.00"), cashItem.driftPercentage());
        assertEquals(DriftStatus.IN_TOLERANCE, cashItem.driftStatus());
        assertFalse(cashItem.isDriftExceeded());
        assertEquals(RebalanceAction.HOLD, cashItem.action());
        assertEquals(new BigDecimal("0.00"), cashItem.orderAmount());
        assertEquals(new BigDecimal("10.00"), cashItem.projectedPostWeightPercentage());
    }

    @Test
    void generateRebalanceAnalysis_assetClassCashInjection_distributesOnlyToUnderweight() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Target Plan: 50% STOCK, 50% ETF
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "50/50 Equity/ETF", AllocationType.ASSET_CLASS, new BigDecimal("5.00"));
        plan.addItem(new TargetAllocationItem(plan, "STOCK", "Equities", new BigDecimal("50.00"), null));
        plan.addItem(new TargetAllocationItem(plan, "ETF", "ETFs", new BigDecimal("50.00"), null));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        // Current: Total 10,000 GBP (STOCK = 7,000, ETF = 3,000)
        // Inject 4,000 GBP cash -> Total post-value = 14,000 GBP
        // Target post-values: STOCK = 7,000 (0 shortfall), ETF = 7,000 (4,000 shortfall)
        // All 4,000 cash goes to BUY ETF without selling STOCK!
        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("10000.00"), new BigDecimal("9000.00"),
                new BigDecimal("1000.00"), new BigDecimal("11.11"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(
                        new AllocationItem("STOCK", new BigDecimal("7000.00"), new BigDecimal("70.00"), new BigDecimal("6000.00"), new BigDecimal("1000.00")),
                        new AllocationItem("ETF", new BigDecimal("3000.00"), new BigDecimal("30.00"), new BigDecimal("3000.00"), BigDecimal.ZERO)
                ),
                List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        BigDecimal cashInjection = new BigDecimal("4000.00");
        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, cashInjection, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("10000.00"), response.totalPortfolioValue());
        assertEquals(new BigDecimal("4000.00"), response.cashInjectionAmount());
        assertEquals(new BigDecimal("14000.00"), response.totalPostRebalanceValue());

        // STOCK: HOLD (no sells in cash injection mode)
        RebalanceOrderItemResponse stockItem = response.items().get(0);
        assertEquals(RebalanceAction.HOLD, stockItem.action());
        assertEquals(new BigDecimal("0.00"), stockItem.orderAmount());
        assertEquals(new BigDecimal("50.00"), stockItem.projectedPostWeightPercentage());

        // ETF: BUY 4,000
        RebalanceOrderItemResponse etfItem = response.items().get(1);
        assertEquals(RebalanceAction.BUY, etfItem.action());
        assertEquals(new BigDecimal("4000.00"), etfItem.orderAmount());
        assertEquals(new BigDecimal("50.00"), etfItem.projectedPostWeightPercentage());
    }

    @Test
    void generateRebalanceAnalysis_instrumentFullRebalance_calculatesEstimatedQuantities() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Target Plan: 60% VUSA, 40% AAPL
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Stock Weights", AllocationType.INSTRUMENT, new BigDecimal("3.00"));
        plan.addItem(new TargetAllocationItem(plan, vusa.getId().toString(), "VUSA ETF", new BigDecimal("60.00"), vusa));
        plan.addItem(new TargetAllocationItem(plan, aapl.getId().toString(), "AAPL Stock", new BigDecimal("40.00"), aapl));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        // Current: Total 10,000 GBP (VUSA = 4,000 @ 80.00 GBP, AAPL = 6,000 @ 200.00 GBP)
        // VUSA target = 6,000 -> BUY 2,000 GBP (~25 shares @ 80.00)
        // AAPL target = 4,000 -> SELL 2,000 GBP (~10 shares @ 200.00)
        HoldingExposure vusaExposure = new HoldingExposure(
                vusa.getId(), vusa.getName(), vusa.getTicker(), vusa.getAssetClass(),
                new BigDecimal("50"), new BigDecimal("80.00"), new BigDecimal("4000.00"),
                new BigDecimal("3500.00"), new BigDecimal("500.00"), new BigDecimal("40.00"),
                "GBP", null, null, null, null, null
        );

        HoldingExposure aaplExposure = new HoldingExposure(
                aapl.getId(), aapl.getName(), aapl.getTicker(), aapl.getAssetClass(),
                new BigDecimal("30"), new BigDecimal("200.00"), new BigDecimal("6000.00"),
                new BigDecimal("5000.00"), new BigDecimal("1000.00"), new BigDecimal("60.00"),
                "GBP", null, null, null, null, null
        );

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("10000.00"), new BigDecimal("8500.00"),
                new BigDecimal("1500.00"), new BigDecimal("17.65"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(),
                List.of(vusaExposure, aaplExposure),
                List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, null, now);

        assertNotNull(response);
        assertEquals(AllocationType.INSTRUMENT, response.allocationType());
        assertEquals(2, response.items().size());

        // VUSA: BUY 2000 GBP -> 25 shares
        RebalanceOrderItemResponse vusaItem = response.items().get(0);
        assertEquals("VUSA", vusaItem.instrumentTicker());
        assertEquals(RebalanceAction.BUY, vusaItem.action());
        assertEquals(new BigDecimal("2000.00"), vusaItem.orderAmount());
        assertEquals(new BigDecimal("80.00"), vusaItem.estimatedPrice());
        assertEquals(new BigDecimal("25.0000"), vusaItem.estimatedQuantity());
        assertEquals(new BigDecimal("60.00"), vusaItem.projectedPostWeightPercentage());

        // AAPL: SELL 2000 GBP -> 10 shares
        RebalanceOrderItemResponse aaplItem = response.items().get(1);
        assertEquals("AAPL", aaplItem.instrumentTicker());
        assertEquals(RebalanceAction.SELL, aaplItem.action());
        assertEquals(new BigDecimal("2000.00"), aaplItem.orderAmount());
        assertEquals(new BigDecimal("200.00"), aaplItem.estimatedPrice());
        assertEquals(new BigDecimal("10.0000"), aaplItem.estimatedQuantity());
        assertEquals(new BigDecimal("40.00"), aaplItem.projectedPostWeightPercentage());
    }

    @Test
    void generateRebalanceAnalysis_instrumentNotCurrentlyHeld_fetchesMarketPrice() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Target Plan has VUSA (100%), which is currently 0 in portfolio
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "New Allocation", AllocationType.INSTRUMENT, new BigDecimal("5.00"));
        plan.addItem(new TargetAllocationItem(plan, vusa.getId().toString(), "VUSA ETF", new BigDecimal("100.00"), vusa));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        // 0 holdings, 5,000 GBP cash
        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("5000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("5000.00"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);
        when(marketDataService.getLatestPrice(eq(vusa.getId()), eq(now)))
                .thenReturn(new PriceQuote(vusa.getId(), new BigDecimal("100.00"), "GBP", now, ObservationSourceType.PROVIDER, "TEST", false, null));

        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, null, now);

        assertNotNull(response);
        assertEquals(1, response.items().size());
        RebalanceOrderItemResponse item = response.items().get(0);
        assertEquals("VUSA", item.instrumentTicker());
        assertEquals(RebalanceAction.BUY, item.action());
        assertEquals(new BigDecimal("5000.00"), item.orderAmount());
        assertEquals(new BigDecimal("100.00"), item.estimatedPrice());
        assertEquals(new BigDecimal("50.0000"), item.estimatedQuantity());
    }

    @Test
    void generateRebalanceAnalysis_balancedPortfolioCashInjection_distributesProportionally() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Target Plan: 50% STOCK, 50% ETF
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "50/50 Balanced", AllocationType.ASSET_CLASS, new BigDecimal("5.00"));
        plan.addItem(new TargetAllocationItem(plan, "STOCK", "Equities", new BigDecimal("50.00"), null));
        plan.addItem(new TargetAllocationItem(plan, "ETF", "ETFs", new BigDecimal("50.00"), null));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        // Already 50/50 (5,000 each)
        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("10000.00"), new BigDecimal("9000.00"),
                new BigDecimal("1000.00"), new BigDecimal("11.11"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(
                        new AllocationItem("STOCK", new BigDecimal("5000.00"), new BigDecimal("50.00"), new BigDecimal("4500.00"), new BigDecimal("500.00")),
                        new AllocationItem("ETF", new BigDecimal("5000.00"), new BigDecimal("50.00"), new BigDecimal("4500.00"), new BigDecimal("500.00"))
                ),
                List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        // Inject 2,000 GBP cash -> should allocate 1,000 to STOCK and 1,000 to ETF
        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, new BigDecimal("2000.00"), now);

        assertNotNull(response);
        assertFalse(response.hasDriftToleranceExceeded());
        assertEquals(RebalanceAction.BUY, response.items().get(0).action());
        assertEquals(new BigDecimal("1000.00"), response.items().get(0).orderAmount());
        assertEquals(RebalanceAction.BUY, response.items().get(1).action());
        assertEquals(new BigDecimal("1000.00"), response.items().get(1).orderAmount());
    }

    @Test
    void generateRebalanceAnalysis_instrumentCashInjection_distributesOnlyToUnderweight() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Stock Weights", AllocationType.INSTRUMENT, new BigDecimal("3.00"));
        plan.addItem(new TargetAllocationItem(plan, vusa.getId().toString(), "VUSA ETF", new BigDecimal("50.00"), vusa));
        plan.addItem(new TargetAllocationItem(plan, aapl.getId().toString(), "AAPL Stock", new BigDecimal("50.00"), aapl));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        HoldingExposure vusaExposure = new HoldingExposure(
                vusa.getId(), vusa.getName(), vusa.getTicker(), vusa.getAssetClass(),
                new BigDecimal("50"), new BigDecimal("60.00"), new BigDecimal("3000.00"),
                new BigDecimal("2500.00"), new BigDecimal("500.00"), new BigDecimal("30.00"),
                "GBP", null, null, null, null, null
        );

        HoldingExposure aaplExposure = new HoldingExposure(
                aapl.getId(), aapl.getName(), aapl.getTicker(), aapl.getAssetClass(),
                new BigDecimal("70"), new BigDecimal("100.00"), new BigDecimal("7000.00"),
                new BigDecimal("6000.00"), new BigDecimal("1000.00"), new BigDecimal("70.00"),
                "GBP", null, null, null, null, null
        );

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("10000.00"), new BigDecimal("8500.00"),
                new BigDecimal("1500.00"), new BigDecimal("17.65"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(),
                List.of(vusaExposure, aaplExposure),
                List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        // Inject 4000 cash -> Total post = 14000 (Target VUSA = 7000, Shortfall = 4000; AAPL = 7000, Shortfall = 0)
        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, new BigDecimal("4000.00"), now);

        assertNotNull(response);
        assertEquals(2, response.items().size());

        RebalanceOrderItemResponse vusaItem = response.items().get(0);
        assertEquals(RebalanceAction.BUY, vusaItem.action());
        assertEquals(new BigDecimal("4000.00"), vusaItem.orderAmount());

        RebalanceOrderItemResponse aaplItem = response.items().get(1);
        assertEquals(RebalanceAction.HOLD, aaplItem.action());
        assertEquals(new BigDecimal("0.00"), aaplItem.orderAmount());
    }

    @Test
    void generateRebalanceAnalysis_instrumentCashInjectionBalanced_distributesProportionally() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Stock Weights", AllocationType.INSTRUMENT, new BigDecimal("3.00"));
        plan.addItem(new TargetAllocationItem(plan, vusa.getId().toString(), "VUSA ETF", new BigDecimal("50.00"), vusa));
        plan.addItem(new TargetAllocationItem(plan, aapl.getId().toString(), "AAPL Stock", new BigDecimal("50.00"), aapl));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        HoldingExposure vusaExposure = new HoldingExposure(
                vusa.getId(), vusa.getName(), vusa.getTicker(), vusa.getAssetClass(),
                new BigDecimal("50"), new BigDecimal("100.00"), new BigDecimal("5000.00"),
                new BigDecimal("4500.00"), new BigDecimal("500.00"), new BigDecimal("50.00"),
                "GBP", null, null, null, null, null
        );

        HoldingExposure aaplExposure = new HoldingExposure(
                aapl.getId(), aapl.getName(), aapl.getTicker(), aapl.getAssetClass(),
                new BigDecimal("50"), new BigDecimal("100.00"), new BigDecimal("5000.00"),
                new BigDecimal("4500.00"), new BigDecimal("500.00"), new BigDecimal("50.00"),
                "GBP", null, null, null, null, null
        );

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("10000.00"), new BigDecimal("9000.00"),
                new BigDecimal("1000.00"), new BigDecimal("11.11"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(),
                List.of(vusaExposure, aaplExposure),
                List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        // Inject 2000 cash into perfectly balanced portfolio -> 1000 to VUSA, 1000 to AAPL
        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, new BigDecimal("2000.00"), now);

        assertNotNull(response);
        assertEquals(RebalanceAction.BUY, response.items().get(0).action());
        assertEquals(new BigDecimal("1000.00"), response.items().get(0).orderAmount());
        assertEquals(RebalanceAction.BUY, response.items().get(1).action());
        assertEquals(new BigDecimal("1000.00"), response.items().get(1).orderAmount());
    }

    @Test
    void generateRebalanceAnalysis_zeroPortfolioValue_handlesGracefully() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Stock Weights", AllocationType.INSTRUMENT, new BigDecimal("3.00"));
        plan.addItem(new TargetAllocationItem(plan, vusa.getId().toString(), "VUSA ETF", new BigDecimal("100.00"), vusa));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, null, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("0.00"), response.totalPortfolioValue());
        assertEquals(new BigDecimal("0.00"), response.items().get(0).currentWeightPercentage());
    }

    @Test
    void generateRebalanceAnalysis_assetClassZeroPortfolioValue_handlesGracefully() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Asset Class Weights", AllocationType.ASSET_CLASS, new BigDecimal("3.00"));
        plan.addItem(new TargetAllocationItem(plan, "STOCK", "Equities", new BigDecimal("100.00"), null));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, null, now);

        assertNotNull(response);
        assertEquals(new BigDecimal("0.00"), response.totalPortfolioValue());
        assertEquals(new BigDecimal("0.00"), response.items().get(0).currentWeightPercentage());
    }

    @Test
    void generateRebalanceAnalysis_marketDataThrowsException_handledGracefully() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Stock Weights", AllocationType.INSTRUMENT, new BigDecimal("3.00"));
        plan.addItem(new TargetAllocationItem(plan, vusa.getId().toString(), "VUSA ETF", new BigDecimal("100.00"), vusa));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("5000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("5000.00"),
                List.of(), List.of(), List.of(), List.of(), List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);
        when(marketDataService.getLatestPrice(eq(vusa.getId()), eq(now))).thenThrow(new RuntimeException("Market data provider offline"));

        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, null, now);

        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertNull(response.items().get(0).estimatedPrice());
        assertNull(response.items().get(0).estimatedQuantity());
    }

    @Test
    void generateRebalanceAnalysis_instrumentHoldAndNullTickerAndNullInstrument() {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        Instrument noTickerInst = new Instrument("Private Fund", AssetClass.OTHER, null, null, null, new Currency("GBP"));

        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Mixed Weights", AllocationType.INSTRUMENT, new BigDecimal("3.00"));
        plan.addItem(new TargetAllocationItem(plan, noTickerInst.getId().toString(), "Private Fund", new BigDecimal("50.00"), noTickerInst));
        plan.addItem(new TargetAllocationItem(plan, "null-inst", "Custom Bucket", new BigDecimal("50.00"), null));

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        HoldingExposure noTickerExposure = new HoldingExposure(
                noTickerInst.getId(), noTickerInst.getName(), null, noTickerInst.getAssetClass(),
                new BigDecimal("50"), new BigDecimal("100.00"), new BigDecimal("5000.00"),
                new BigDecimal("5000.00"), BigDecimal.ZERO, new BigDecimal("50.00"),
                "GBP", null, null, null, null, null
        );

        PortfolioAnalytics mockAnalytics = new PortfolioAnalytics(
                portfolioId, now, "GBP",
                new BigDecimal("10000.00"), new BigDecimal("10000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("5000.00"),
                List.of(), List.of(), List.of(),
                List.of(noTickerExposure),
                List.of()
        );

        when(analyticsEngine.calculate(portfolioId, now)).thenReturn(mockAnalytics);

        RebalanceAnalysisResponse response = rebalancingService.generateRebalanceAnalysis(portfolioId, null, now);

        assertNotNull(response);
        assertEquals(2, response.items().size());

        // Item 1 is HOLD (5,000 on target of 5,000)
        RebalanceOrderItemResponse item1 = response.items().get(0);
        assertEquals(RebalanceAction.HOLD, item1.action());
        assertNull(item1.instrumentTicker());
        assertNull(item1.estimatedQuantity());

        // Item 2 has null instrument
        RebalanceOrderItemResponse item2 = response.items().get(1);
        assertNull(item2.instrumentId());
        assertNull(item2.instrumentTicker());
        assertNull(item2.instrumentName());
    }
}


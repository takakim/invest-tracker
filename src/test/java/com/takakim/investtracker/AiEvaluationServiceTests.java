package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.AccountTaxTreatment;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.HoldingAiEvaluation;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.PortfolioAiEvaluation;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.HoldingAiEvaluationRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioAiEvaluationRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.service.PositionService;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.ai.AiEvaluationService;
import com.takakim.investtracker.service.ai.AiGateway;
import com.takakim.investtracker.service.ai.AiGatewayFactory;
import com.takakim.investtracker.service.ai.dto.AiRiskLevel;
import com.takakim.investtracker.service.ai.dto.AiStance;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import com.takakim.investtracker.service.ai.dto.HoldingAiEvaluationDto;
import com.takakim.investtracker.service.ai.dto.PortfolioAiEvaluationDto;
import com.takakim.investtracker.service.analytics.AllocationItem;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceDtos;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiEvaluationServiceTests {

    @Mock
    private AiGatewayFactory gatewayFactory;
    @Mock
    private AiGateway gateway;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private PositionService positionService;
    @Mock
    private AnalyticsEngine analyticsEngine;
    @Mock
    private YahooFinanceGateway yahooFinanceGateway;
    @Mock
    private PortfolioAiEvaluationRepository portfolioAiEvaluationRepository;
    @Mock
    private HoldingAiEvaluationRepository holdingAiEvaluationRepository;

    private AiEvaluationService service;
    private ObjectMapper objectMapper;

    private UUID portfolioId;
    private UUID instrumentId;
    private Portfolio testPortfolio;
    private Instrument testInstrument;
    private ApiDtos.PositionPerformanceResponse testPosition;
    private PortfolioAnalytics testAnalytics;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(gatewayFactory.getActiveGateway()).thenReturn(gateway);
        lenient().when(gateway.getProviderName()).thenReturn("LM_STUDIO");
        service = new AiEvaluationService(
                gatewayFactory,
                portfolioRepository,
                accountRepository,
                instrumentRepository,
                positionService,
                analyticsEngine,
                yahooFinanceGateway,
                objectMapper,
                portfolioAiEvaluationRepository,
                holdingAiEvaluationRepository
        );

        // Stub persistence repositories so evaluations can be saved without errors
        lenient().when(portfolioAiEvaluationRepository.findByPortfolioId(any())).thenReturn(Optional.empty());
        lenient().when(holdingAiEvaluationRepository.findByPortfolioIdAndInstrumentId(any(), any())).thenReturn(Optional.empty());

        testPortfolio = new Portfolio("Growth Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        testInstrument = new Instrument("Apple Inc.", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        portfolioId = testPortfolio.getId();
        instrumentId = testInstrument.getId();

        testPosition = new ApiDtos.PositionPerformanceResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Trading 212 ISA",
                instrumentId, "Apple Inc.", "AAPL", "US0378331005", AssetClass.STOCK,
                "ACTIVE",
                new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ZERO,
                new BigDecimal("150.00"), BigDecimal.ZERO,
                new BigDecimal("1500.00"), BigDecimal.ZERO,
                new BigDecimal("1500.00"), new BigDecimal("180.00"),
                new BigDecimal("1800.00"), BigDecimal.ZERO,
                new BigDecimal("300.00"), new BigDecimal("25.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("325.00"), new BigDecimal("21.67"),
                "GBP", new BigDecimal("225.00"), "USD",
                new BigDecimal("325.00"), new BigDecimal("21.67"),
                List.of(), List.of(), List.of()
        );

        testAnalytics = new PortfolioAnalytics(
                portfolioId,
                Instant.now(),
                "GBP",
                new BigDecimal("5000.00"),
                new BigDecimal("4000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("25.00"),
                new BigDecimal("50.00"),
                new BigDecimal("500.00"),
                List.of(new AllocationItem("STOCKS", new BigDecimal("4500.00"), new BigDecimal("0.90"), new BigDecimal("3500.00"), new BigDecimal("1000.00"))),
                List.of(new AllocationItem("GBP", new BigDecimal("5000.00"), new BigDecimal("1.00"), new BigDecimal("4000.00"), new BigDecimal("1000.00"))),
                List.of(),
                List.of(new HoldingExposure(
                        instrumentId, "Apple Inc.", "AAPL", AssetClass.STOCK,
                        new BigDecimal("10"), new BigDecimal("180.00"),
                        new BigDecimal("1800.00"), new BigDecimal("1500.00"),
                        new BigDecimal("300.00"), new BigDecimal("36.0"),
                        "GBP"
                )),
                List.of()
        );
    }

    @Test
    @DisplayName("getStatus delegates to LmStudioGateway")
    void testGetStatus() {
        AiStatusDto expected = new AiStatusDto(true, true, "LM_STUDIO", "http://localhost:1234", "gemma4-12b", List.of("gemma4-12b"), null);
        when(gateway.checkStatus()).thenReturn(expected);

        AiStatusDto actual = service.getStatus();
        assertSame(expected, actual);
        verify(gateway).checkStatus();
    }

    @Test
    @DisplayName("evaluateHolding returns complete evaluation when LM Studio returns valid JSON")
    void testEvaluateHoldingSuccess() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String llmOutput = """
                {
                  "stance": "ACCUMULATE",
                  "riskScore": 4,
                  "riskLevel": "MODERATE",
                  "executiveSummary": "Apple demonstrates strong free cash flow and resilient ecosystem loyalty.",
                  "strengths": ["High return on capital", "Robust services revenue growth"],
                  "risks": ["China revenue exposure", "Extended hardware replacement cycles"],
                  "holdingVsSellingTradeoff": "Risk of holding is valuation multiple compression; selling now forfeits ongoing buyback accretion.",
                  "fundamentalMetrics": {
                    "peRatio": 31.5,
                    "forwardPe": 27.2,
                    "pegRatio": 2.4,
                    "priceToBook": 45.0,
                    "dividendYield": 0.005,
                    "debtToEquity": 1.5,
                    "returnOnEquity": 1.45
                  }
                }
                """;

        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(llmOutput);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);

        assertNotNull(result);
        assertEquals(instrumentId, result.instrumentId());
        assertEquals("AAPL", result.symbol());
        assertEquals(AiStance.ACCUMULATE, result.stance());
        assertEquals(4, result.riskScore());
        assertEquals(AiRiskLevel.MODERATE, result.riskLevel());
        assertEquals(2, result.strengths().size());
        assertEquals(2, result.risks().size());
        assertEquals(31.5, result.fundamentalMetrics().peRatio());
        assertEquals("LM_STUDIO", result.modelUsed());
    }

    @Test
    @DisplayName("evaluateHolding extracts JSON enclosed in markdown code fences")
    void testEvaluateHoldingMarkdownCodeblock() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String markdownOutput = """
                Here is the financial risk evaluation:
                ```json
                {
                  "stance": "HOLD",
                  "riskScore": 5,
                  "riskLevel": "MODERATE",
                  "executiveSummary": "Solid core holding with balanced risk-reward.",
                  "strengths": ["Strong balance sheet"],
                  "risks": ["Macro sensitivity"],
                  "holdingVsSellingTradeoff": "Hold for long-term compounding."
                }
                ```
                Let me know if you need more details.
                """;

        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(markdownOutput);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);

        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
        assertEquals(5, result.riskScore());
        assertEquals("Solid core holding with balanced risk-reward.", result.executiveSummary());
    }

    @Test
    @DisplayName("evaluateHolding uses fallback evaluation if LM Studio output is unparseable")
    void testEvaluateHoldingUnparseableFallback() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        when(gateway.generateChatCompletion(anyString(), anyString()))
                .thenReturn("Sorry, I cannot provide financial advice as an AI model.");

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);

        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
        assertEquals(5, result.riskScore());
        assertEquals(AiRiskLevel.MODERATE, result.riskLevel());
        assertTrue(result.executiveSummary().contains("Sorry"));
    }

    @Test
    @DisplayName("evaluateHolding throws ResourceNotFoundException if portfolio missing")
    void testEvaluateHoldingPortfolioNotFound() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.evaluateHolding(portfolioId, instrumentId));
    }

    @Test
    @DisplayName("evaluateHolding throws ResourceNotFoundException if instrument missing")
    void testEvaluateHoldingInstrumentNotFound() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.evaluateHolding(portfolioId, instrumentId));
    }

    @Test
    @DisplayName("evaluateHolding throws ResourceNotFoundException if no positions exist for instrument")
    void testEvaluateHoldingNoPositions() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.evaluateHolding(portfolioId, instrumentId));
    }

    @Test
    @DisplayName("evaluateHolding extracts 52-week range from Yahoo Finance when available")
    void testEvaluateHoldingYahooRange() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        YahooFinanceDtos.QuoteIndicator qi = new YahooFinanceDtos.QuoteIndicator(
                null,
                List.of(new BigDecimal("195.00"), new BigDecimal("199.50")),
                List.of(new BigDecimal("140.00"), new BigDecimal("145.00")),
                null, null
        );
        YahooFinanceDtos.ChartEntry entry = new YahooFinanceDtos.ChartEntry(
                null, null, new YahooFinanceDtos.ChartIndicators(List.of(qi)), null
        );

        when(yahooFinanceGateway.fetchChart("AAPL", "1d", "1mo")).thenReturn(Optional.of(entry));

        String llmOutput = """
                {
                  "stance": "TRIM",
                  "riskScore": 7,
                  "riskLevel": "HIGH",
                  "executiveSummary": "Trading near 52-week high; take partial profits."
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(llmOutput);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);

        assertNotNull(result);
        assertEquals(AiStance.TRIM, result.stance());
        assertEquals(7, result.riskScore());
        assertEquals(199.50, result.fundamentalMetrics().fiftyTwoWeekHigh());
        assertEquals(140.00, result.fundamentalMetrics().fiftyTwoWeekLow());
    }

    @Test
    @DisplayName("evaluatePortfolio returns complete portfolio diagnosis")
    void testEvaluatePortfolioSuccess() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));

        Account account = new Account(testPortfolio, "Trading 212 ISA", "Trading 212", new Currency("GBP"), AccountTaxTreatment.TAX_EXEMPT);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        String llmOutput = """
                {
                  "overallRiskScore": 5,
                  "overallRiskLevel": "MODERATE",
                  "executiveSummary": "Well-constructed growth portfolio with high equity concentration in US technology.",
                  "diversificationAssessment": "High equity allocation (90%) with 10% cash buffer. Recommend global broadening.",
                  "concentrationRisks": ["AAPL represents 36% of portfolio market value", "USD exposure without currency hedge"],
                  "taxAndLocationOptimization": ["Asset is sheltered in ISA wrapper; CGT and dividend taxes are neutralized"],
                  "topRecommendations": ["Consider rebalancing cash into a low-cost global all-cap ETF", "Trim AAPL if weight exceeds 40%"],
                  "macroStressScenarios": ["Tech valuation compression would induce 15-20% drawdown", "Resilient against inflation due to pricing power"]
                }
                """;

        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(llmOutput);

        PortfolioAiEvaluationDto result = service.evaluatePortfolio(portfolioId);

        assertNotNull(result);
        assertEquals(portfolioId, result.portfolioId());
        assertEquals("Growth Portfolio", result.portfolioName());
        assertEquals(5, result.overallRiskScore());
        assertEquals(AiRiskLevel.MODERATE, result.overallRiskLevel());
        assertEquals(2, result.concentrationRisks().size());
        assertEquals(1, result.taxAndLocationOptimization().size());
        assertEquals(2, result.topRecommendations().size());
        assertEquals(2, result.macroStressScenarios().size());
    }

    @Test
    @DisplayName("evaluatePortfolio falls back safely when LLM response is unparseable")
    void testEvaluatePortfolioFallback() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of());

        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn("Error generating output.");

        PortfolioAiEvaluationDto result = service.evaluatePortfolio(portfolioId);

        assertNotNull(result);
        assertEquals(5, result.overallRiskScore());
        assertEquals(AiRiskLevel.MODERATE, result.overallRiskLevel());
        assertFalse(result.topRecommendations().isEmpty());
    }

    @Test
    @DisplayName("evaluatePortfolio throws ResourceNotFoundException if portfolio missing")
    void testEvaluatePortfolioNotFound() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.evaluatePortfolio(portfolioId));
    }

    @Test
    @DisplayName("evaluateHolding clamps riskScore and handles null metrics and missing ISIN/currency")
    void testEvaluateHoldingClampingAndNulls() {
        Instrument instrumentNoIsin = new Instrument("Cash Fund", AssetClass.CASH, "CASH", null, "LSE", new Currency("GBP"));
        UUID cashId = instrumentNoIsin.getId();
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(cashId)).thenReturn(Optional.of(instrumentNoIsin));

        ApiDtos.PositionPerformanceResponse posZeroPrice = new ApiDtos.PositionPerformanceResponse(
                UUID.randomUUID(), UUID.randomUUID(), null,
                cashId, "Cash Fund", "CASH", null, AssetClass.CASH,
                "ACTIVE",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ZERO, "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of()
        );
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(posZeroPrice));

        PortfolioAnalytics zeroAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(zeroAnalytics);

        String llmOutput = """
                {
                  "stance": "INVALID_STANCE",
                  "riskScore": 15,
                  "riskLevel": "INVALID_LEVEL",
                  "executiveSummary": "Clamped risk score test",
                  "fundamentalMetrics": null
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(llmOutput);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, cashId);

        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
        assertEquals(10, result.riskScore());
        assertEquals(AiRiskLevel.VERY_HIGH, result.riskLevel());
        assertEquals(0.0, result.portfolioWeightPercentage());
        assertEquals(0.0, result.unrealizedGainLossPercentage());
    }

    @Test
    @DisplayName("evaluatePortfolio clamps risk score under 1 and over 10 and handles null topHoldings")
    void testEvaluatePortfolioClampedScoresAndNullHoldings() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));

        PortfolioAnalytics zeroValAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(zeroValAnalytics);
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of());
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE)).thenReturn(List.of());

        String llmLowScore = """
                {
                  "overallRiskScore": -5,
                  "overallRiskLevel": "UNKNOWN",
                  "executiveSummary": "Low score test",
                  "concentrationRisks": "not-an-array"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(llmLowScore);

        PortfolioAiEvaluationDto result = service.evaluatePortfolio(portfolioId);

        assertNotNull(result);
        assertEquals(1, result.overallRiskScore());
        assertEquals(AiRiskLevel.LOW, result.overallRiskLevel());
        assertTrue(result.concentrationRisks().isEmpty());
    }

    @Test
    @DisplayName("constructor with null ObjectMapper initializes default")
    void testConstructorWithNullObjectMapper() {
        AiEvaluationService s = new AiEvaluationService(
                gatewayFactory, portfolioRepository, accountRepository, instrumentRepository,
                positionService, analyticsEngine, null, null,
                portfolioAiEvaluationRepository, holdingAiEvaluationRepository
        );
        assertNotNull(s);
    }

    @Test
    @DisplayName("evaluateHolding parses all complete fundamental financial metrics")
    void testEvaluateHoldingWithCompleteFinancialMetrics() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String fullMetricsJson = """
                {
                  "stance": "STRONG_BUY",
                  "riskScore": 2,
                  "riskLevel": "LOW",
                  "executiveSummary": "Pristine balance sheet and high cash generation.",
                  "strengths": ["Leader in tech", "High ROE"],
                  "risks": ["Cyclical slowing"],
                  "holdingVsSellingTradeoff": "Hold for long-term compounding.",
                  "fundamentalMetrics": {
                    "peRatio": 30.5,
                    "forwardPe": 25.0,
                    "pegRatio": 1.2,
                    "priceToBook": 15.4,
                    "dividendYield": 0.006,
                    "debtToEquity": 0.35,
                    "returnOnEquity": 0.45,
                    "expenseRatio": 0.001
                  }
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(fullMetricsJson);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);

        assertNotNull(result);
        assertEquals(AiStance.STRONG_BUY, result.stance());
        assertEquals(2, result.riskScore());
        assertEquals(AiRiskLevel.LOW, result.riskLevel());
        assertNotNull(result.fundamentalMetrics());
        assertEquals(30.5, result.fundamentalMetrics().peRatio());
        assertEquals(25.0, result.fundamentalMetrics().forwardPe());
        assertEquals(1.2, result.fundamentalMetrics().pegRatio());
        assertEquals(15.4, result.fundamentalMetrics().priceToBook());
        assertEquals(0.006, result.fundamentalMetrics().dividendYield());
        assertEquals(0.35, result.fundamentalMetrics().debtToEquity());
        assertEquals(0.45, result.fundamentalMetrics().returnOnEquity());
        assertEquals(0.001, result.fundamentalMetrics().expenseRatio());
    }

    @Test
    @DisplayName("evaluateHolding handles all stance enums and risk scores 1 to 10")
    void testEvaluateHoldingStanceAndRiskBranches() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        // Test TRIM stance and score 8 (HIGH)
        String jsonTrim = """
                {
                  "stance": "TRIM",
                  "riskScore": 8,
                  "executiveSummary": "High valuation risk.",
                  "strengths": ["Strong momentum", 123, "   "],
                  "risks": []
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(jsonTrim);

        HoldingAiEvaluationDto resultTrim = service.evaluateHolding(portfolioId, instrumentId);
        assertEquals(AiStance.TRIM, resultTrim.stance());
        assertEquals(8, resultTrim.riskScore());
        assertEquals(AiRiskLevel.HIGH, resultTrim.riskLevel());
        assertEquals(1, resultTrim.strengths().size());
        assertEquals("Strong momentum", resultTrim.strengths().get(0));

        // Test SELL stance and score 9 (VERY_HIGH)
        String jsonSell = """
                {
                  "stance": "SELL",
                  "riskScore": 9,
                  "executiveSummary": "Thesis broken."
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(jsonSell);

        HoldingAiEvaluationDto resultSell = service.evaluateHolding(portfolioId, instrumentId);
        assertEquals(AiStance.SELL, resultSell.stance());
        assertEquals(9, resultSell.riskScore());
        assertEquals(AiRiskLevel.VERY_HIGH, resultSell.riskLevel());

        // Test ACCUMULATE stance and score 4 (MODERATE)
        String jsonAccumulate = """
                {
                  "stance": "ACCUMULATE",
                  "riskScore": 4,
                  "executiveSummary": "Attractive entry point."
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(jsonAccumulate);

        HoldingAiEvaluationDto resultAccumulate = service.evaluateHolding(portfolioId, instrumentId);
        assertEquals(AiStance.ACCUMULATE, resultAccumulate.stance());
        assertEquals(4, resultAccumulate.riskScore());
        assertEquals(AiRiskLevel.MODERATE, resultAccumulate.riskLevel());
    }

    @Test
    @DisplayName("evaluateHolding handles Yahoo quote with 0.0 or null values for 52-week ranges")
    void testEvaluateHoldingWithYahooZeroRange() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        YahooFinanceDtos.QuoteIndicator qi = new YahooFinanceDtos.QuoteIndicator(
                List.of(BigDecimal.ZERO),
                List.of(BigDecimal.ZERO),
                List.of(BigDecimal.ZERO),
                List.of(BigDecimal.ZERO),
                List.of(100L)
        );
        YahooFinanceDtos.ChartIndicators ind = new YahooFinanceDtos.ChartIndicators(List.of(qi));
        YahooFinanceDtos.ChartEntry entry = new YahooFinanceDtos.ChartEntry(null, List.of(1000L), ind, null);
        when(yahooFinanceGateway.fetchChart("AAPL", "1d", "1mo")).thenReturn(Optional.of(entry));

        String llmOutput = """
                {
                  "stance": "HOLD",
                  "riskScore": 5,
                  "executiveSummary": "Zero range quote handled."
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(llmOutput);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertNull(result.fundamentalMetrics().fiftyTwoWeekHigh());
        assertNull(result.fundamentalMetrics().fiftyTwoWeekLow());
    }

    @Test
    @DisplayName("evaluateHolding handles explicit null values in fundamental metrics")
    void testEvaluateHoldingWithExplicitNullMetrics() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String nullMetricsJson = """
                {
                  "stance": "HOLD",
                  "riskScore": 5,
                  "riskLevel": "MODERATE",
                  "executiveSummary": "Explicit null metrics test",
                  "fundamentalMetrics": {
                    "peRatio": null,
                    "forwardPe": null,
                    "pegRatio": null,
                    "priceToBook": null,
                    "dividendYield": null,
                    "debtToEquity": null,
                    "returnOnEquity": null,
                    "expenseRatio": null
                  }
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(nullMetricsJson);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);

        assertNotNull(result);
        assertNotNull(result.fundamentalMetrics());
        assertNull(result.fundamentalMetrics().peRatio());
        assertNull(result.fundamentalMetrics().forwardPe());
        assertNull(result.fundamentalMetrics().pegRatio());
        assertNull(result.fundamentalMetrics().priceToBook());
        assertNull(result.fundamentalMetrics().dividendYield());
        assertNull(result.fundamentalMetrics().debtToEquity());
        assertNull(result.fundamentalMetrics().returnOnEquity());
        assertNull(result.fundamentalMetrics().expenseRatio());
    }

    @Test
    @DisplayName("evaluateHolding handles Yahoo fetchChart exception and empty rawText in fallback")
    void testEvaluateHoldingYahooExceptionAndBlankFallback() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);
        when(yahooFinanceGateway.fetchChart("AAPL", "1d", "1mo")).thenThrow(new RuntimeException("Network timeout"));

        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn("   ");

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);

        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
        assertEquals("Position reviewed based on cost basis and current market price.", result.executiveSummary());
    }

    @Test
    @DisplayName("evaluateHolding handles unclosed markdown code fence in LLM response")
    void testEvaluateHoldingUnclosedCodeFence() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String unclosedFence = "```json\n{\n  \"stance\": \"HOLD\",\n  \"riskScore\": 6,\n  \"executiveSummary\": \"Unclosed fence test\"\n}";
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(unclosedFence);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);

        assertNotNull(result);
        assertEquals("Unclosed fence test", result.executiveSummary());
    }

    @Test
    @DisplayName("evaluateHolding clamps riskScore under 1 and handles instrument without ISIN")
    void testEvaluateHoldingClampedUnderOneAndNoIsin() {
        Instrument instNoIsin = new Instrument("No Isin Stock", AssetClass.STOCK, "NOISIN", null, "LSE", new Currency("USD"));
        UUID noIsinId = instNoIsin.getId();

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(noIsinId)).thenReturn(Optional.of(instNoIsin));

        ApiDtos.PositionPerformanceResponse pos = new ApiDtos.PositionPerformanceResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Account Name",
                noIsinId, "No Isin Stock", "NOISIN", null, AssetClass.STOCK,
                "ACTIVE",
                new BigDecimal("5"), new BigDecimal("5"), BigDecimal.ZERO,
                new BigDecimal("100"), new BigDecimal("500"),
                new BigDecimal("100"), new BigDecimal("500"),
                new BigDecimal("500"), BigDecimal.ZERO,
                new BigDecimal("500"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", BigDecimal.ONE, "USD",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of()
        );
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(pos));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String jsonUnderOne = """
                {
                  "stance": "HOLD",
                  "riskScore": -3,
                  "executiveSummary": "Clamped under one"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(jsonUnderOne);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, noIsinId);
        assertNotNull(result);
        assertEquals(1, result.riskScore());
        assertEquals(AiRiskLevel.LOW, result.riskLevel());
    }

    @Test
    @DisplayName("evaluatePortfolio handles overallRiskScore > 10 and top holdings")
    void testEvaluatePortfolioHighScoreAndTopHoldings() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));

        HoldingExposure exp = new HoldingExposure(
                UUID.randomUUID(), "Apple Inc.", "AAPL", AssetClass.STOCK,
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1000"),
                new BigDecimal("800"), new BigDecimal("200"), new BigDecimal("10.0"), "GBP"
        );
        PortfolioAnalytics analyticsWithHoldings = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                new BigDecimal("10000"), new BigDecimal("8000"), new BigDecimal("2000"),
                new BigDecimal("20"), new BigDecimal("0"), new BigDecimal("2000"),
                List.of(), List.of(), List.of(),
                List.of(exp), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(analyticsWithHoldings);
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of());
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE)).thenReturn(List.of());

        String jsonHighScore = """
                {
                  "overallRiskScore": 25,
                  "overallRiskLevel": "VERY_HIGH",
                  "executiveSummary": "High score clamped test",
                  "topRecommendations": ["Diversify"]
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(jsonHighScore);

        PortfolioAiEvaluationDto result = service.evaluatePortfolio(portfolioId);
        assertNotNull(result);
        assertEquals(10, result.overallRiskScore());
        assertEquals(AiRiskLevel.VERY_HIGH, result.overallRiskLevel());
    }

    @Test
    @DisplayName("evaluateHolding handles explicit null stance and null ticker")
    void testEvaluateHoldingExplicitNullStanceAndNullTicker() {
        Instrument instNoTicker = new Instrument("Private Asset", AssetClass.STOCK, null, "GB0000000001", "LSE", new Currency("GBP"));
        UUID assetId = instNoTicker.getId();

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(assetId)).thenReturn(Optional.of(instNoTicker));

        ApiDtos.PositionPerformanceResponse pos = new ApiDtos.PositionPerformanceResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Account Name",
                assetId, "Private Asset", null, "GB0000000001", AssetClass.STOCK,
                "ACTIVE",
                new BigDecimal("1"), new BigDecimal("1"), BigDecimal.ZERO,
                new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("100"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of()
        );
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(pos));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String jsonNullStance = """
                {
                  "stance": null,
                  "riskScore": 5,
                  "executiveSummary": "Null stance test"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(jsonNullStance);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, assetId);
        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
    }

    @Test
    @DisplayName("evaluatePortfolio handles explicit null overallRiskLevel")
    void testEvaluatePortfolioExplicitNullRiskLevel() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of());
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE)).thenReturn(List.of());

        String jsonNullRiskLevel = """
                {
                  "overallRiskScore": 7,
                  "overallRiskLevel": null,
                  "executiveSummary": "Null risk level test"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(jsonNullRiskLevel);

        PortfolioAiEvaluationDto result = service.evaluatePortfolio(portfolioId);
        assertNotNull(result);
        assertEquals(7, result.overallRiskScore());
        assertEquals(AiRiskLevel.HIGH, result.overallRiskLevel());
    }

    @Test
    @DisplayName("evaluateHolding handles Yahoo quote indicators with null high and null low lists")
    void testEvaluateHoldingYahooNullHighLowLists() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        YahooFinanceDtos.QuoteIndicator qiNulls = new YahooFinanceDtos.QuoteIndicator(null, null, null, null, null);
        YahooFinanceDtos.ChartIndicators ind = new YahooFinanceDtos.ChartIndicators(List.of(qiNulls));
        YahooFinanceDtos.ChartEntry entry = new YahooFinanceDtos.ChartEntry(null, List.of(1000L), ind, null);
        when(yahooFinanceGateway.fetchChart("AAPL", "1d", "1mo")).thenReturn(Optional.of(entry));

        String json = """
                {
                  "stance": "HOLD",
                  "riskScore": 5,
                  "executiveSummary": "Null indicators handled"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(json);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertNull(result.fundamentalMetrics().fiftyTwoWeekHigh());
        assertNull(result.fundamentalMetrics().fiftyTwoWeekLow());
    }

    @Test
    @DisplayName("evaluateHolding handles invalid stance, invalid risk level, and falls back to risk score thresholds")
    void testEvaluateHoldingInvalidStanceAndRiskLevelFallback() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String json = """
                {
                  "stance": "UNRECOGNIZED_STANCE_ENUM",
                  "riskScore": 2,
                  "riskLevel": "INVALID_RISK_LEVEL",
                  "executiveSummary": "Fallback stance test",
                  "strengths": ["Valid strength", "", null, 42]
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(json);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
        assertEquals(AiRiskLevel.LOW, result.riskLevel());
        assertEquals(1, result.strengths().size());
        assertEquals("Valid strength", result.strengths().get(0));
    }

    @Test
    @DisplayName("evaluateHolding handles position with null/zero currentPrice and portfolio with zero value")
    void testEvaluateHoldingNullPriceAndZeroPortfolioValue() {
        Instrument inst = new Instrument("Test Co", AssetClass.STOCK, "TEST", "US0000000002", "NYSE", new Currency("USD"));
        UUID id = inst.getId();

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(inst));

        ApiDtos.PositionPerformanceResponse posNullPrice = new ApiDtos.PositionPerformanceResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Account 1",
                id, "Test Co", "TEST", "US0000000002", AssetClass.STOCK,
                "ACTIVE",
                new BigDecimal("5"), new BigDecimal("5"), BigDecimal.ZERO,
                new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("500"), BigDecimal.ZERO,
                new BigDecimal("500"), null,
                new BigDecimal("500"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, "USD",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of()
        );

        ApiDtos.PositionPerformanceResponse posZeroPrice = new ApiDtos.PositionPerformanceResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Account 2",
                id, "Test Co", "TEST", "US0000000002", AssetClass.STOCK,
                "ACTIVE",
                new BigDecimal("5"), new BigDecimal("5"), BigDecimal.ZERO,
                new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("500"), BigDecimal.ZERO,
                new BigDecimal("500"), BigDecimal.ZERO,
                new BigDecimal("500"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", BigDecimal.ONE, "USD",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of()
        );

        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(posNullPrice, posZeroPrice));

        PortfolioAnalytics zeroValAnalytics = new PortfolioAnalytics(
                portfolioId, Instant.now(), "GBP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(zeroValAnalytics);

        String json = """
                {
                  "stance": "SELL",
                  "riskScore": 9,
                  "riskLevel": null,
                  "executiveSummary": "Zero value handled"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(json);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, id);
        assertNotNull(result);
        assertEquals(AiStance.SELL, result.stance());
        assertEquals(9, result.riskScore());
        assertEquals(AiRiskLevel.VERY_HIGH, result.riskLevel());
    }

    @Test
    @DisplayName("evaluateHolding works when yahooFinanceGateway is null and extracts json without code fence")
    void testEvaluateHoldingWithNullYahooGatewayAndRawJsonText() {
        AiEvaluationService serviceWithoutYahoo = new AiEvaluationService(
                gatewayFactory,
                portfolioRepository,
                accountRepository,
                instrumentRepository,
                positionService,
                analyticsEngine,
                null,
                objectMapper,
                portfolioAiEvaluationRepository,
                holdingAiEvaluationRepository
        );

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String responseWithoutFences = "Here is the result: {\"stance\": \"ACCUMULATE\", \"riskScore\": 6} Thank you!";
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(responseWithoutFences);

        HoldingAiEvaluationDto result = serviceWithoutYahoo.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertEquals(AiStance.ACCUMULATE, result.stance());
        assertEquals(6, result.riskScore());
        assertEquals(AiRiskLevel.MODERATE, result.riskLevel());
    }

    @Test
    @DisplayName("getLatestPortfolioEvaluation returns empty when no evaluation exists")
    void testGetLatestPortfolioEvaluationEmpty() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(portfolioAiEvaluationRepository.findByPortfolioId(portfolioId)).thenReturn(Optional.empty());

        Optional<PortfolioAiEvaluationDto> result = service.getLatestPortfolioEvaluation(portfolioId);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getLatestPortfolioEvaluation throws when portfolio does not exist")
    void testGetLatestPortfolioEvaluationPortfolioNotFound() {
        UUID missingId = UUID.randomUUID();
        when(portfolioRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getLatestPortfolioEvaluation(missingId));
    }

    @Test
    @DisplayName("getLatestPortfolioEvaluation returns deserialized DTO when valid JSON exists")
    void testGetLatestPortfolioEvaluationSuccess() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        String json = """
                {
                  "portfolioId": "%s",
                  "portfolioName": "Growth Portfolio",
                  "baseCurrency": "GBP",
                  "overallRiskScore": 5,
                  "overallRiskLevel": "MODERATE",
                  "executiveSummary": "Healthy thesis",
                  "diversificationAssessment": "Good",
                  "concentrationRisks": [],
                  "taxAndLocationOptimization": [],
                  "topRecommendations": [],
                  "macroStressScenarios": [],
                  "topHoldingEvaluations": [],
                  "modelUsed": "LM_STUDIO",
                  "evaluatedAt": "2026-09-15T00:00:00Z"
                }
                """.formatted(portfolioId);
        PortfolioAiEvaluation eval = new PortfolioAiEvaluation(
                portfolioId, "LM_STUDIO", "gemma4-12b", 5, "MODERATE", "Healthy thesis", json, Instant.now()
        );
        when(portfolioAiEvaluationRepository.findByPortfolioId(portfolioId)).thenReturn(Optional.of(eval));

        Optional<PortfolioAiEvaluationDto> result = service.getLatestPortfolioEvaluation(portfolioId);
        assertTrue(result.isPresent());
        assertEquals("Growth Portfolio", result.get().portfolioName());
        assertEquals(5, result.get().overallRiskScore());
    }

    @Test
    @DisplayName("getLatestPortfolioEvaluation returns empty when JSON is invalid")
    void testGetLatestPortfolioEvaluationInvalidJson() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        PortfolioAiEvaluation eval = new PortfolioAiEvaluation(
                portfolioId, "LM_STUDIO", "gemma4-12b", 5, "MODERATE", "Healthy thesis", "not-valid-json", Instant.now()
        );
        when(portfolioAiEvaluationRepository.findByPortfolioId(portfolioId)).thenReturn(Optional.of(eval));

        Optional<PortfolioAiEvaluationDto> result = service.getLatestPortfolioEvaluation(portfolioId);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getLatestHoldingEvaluation returns empty when no evaluation exists")
    void testGetLatestHoldingEvaluationEmpty() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(holdingAiEvaluationRepository.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)).thenReturn(Optional.empty());

        Optional<HoldingAiEvaluationDto> result = service.getLatestHoldingEvaluation(portfolioId, instrumentId);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getLatestHoldingEvaluation throws when portfolio does not exist")
    void testGetLatestHoldingEvaluationPortfolioNotFound() {
        UUID missingId = UUID.randomUUID();
        when(portfolioRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getLatestHoldingEvaluation(missingId, instrumentId));
    }

    @Test
    @DisplayName("getLatestHoldingEvaluation returns deserialized DTO when valid JSON exists")
    void testGetLatestHoldingEvaluationSuccess() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        String json = """
                {
                  "instrumentId": "%s",
                  "symbol": "AAPL",
                  "name": "Apple Inc.",
                  "assetClass": "STOCK",
                  "quantity": 10,
                  "currentPrice": 200,
                  "averageCostBasis": 150,
                  "unrealizedGainLoss": 500,
                  "unrealizedGainLossPercentage": 33.3,
                  "portfolioWeightPercentage": 12.5,
                  "stance": "HOLD",
                  "riskScore": 4,
                  "riskLevel": "MODERATE",
                  "executiveSummary": "Solid cash flows",
                  "strengths": ["Brand"],
                  "risks": ["Valuation"],
                  "holdingVsSellingTradeoff": "Hold",
                  "fundamentalMetrics": null,
                  "modelUsed": "LM_STUDIO",
                  "evaluatedAt": "2026-09-15T00:00:00Z"
                }
                """.formatted(instrumentId);
        HoldingAiEvaluation eval = new HoldingAiEvaluation(
                portfolioId, instrumentId, "LM_STUDIO", "gemma4-12b", "HOLD", 4, "MODERATE", "Solid cash flows", json, Instant.now()
        );
        when(holdingAiEvaluationRepository.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)).thenReturn(Optional.of(eval));

        Optional<HoldingAiEvaluationDto> result = service.getLatestHoldingEvaluation(portfolioId, instrumentId);
        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().symbol());
        assertEquals(AiStance.HOLD, result.get().stance());
    }

    @Test
    @DisplayName("getLatestHoldingEvaluation returns empty when JSON is unparseable")
    void testGetLatestHoldingEvaluationInvalidJson() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        HoldingAiEvaluation eval = new HoldingAiEvaluation(
                portfolioId, instrumentId, "LM_STUDIO", "gemma4-12b", "HOLD", 4, "MODERATE", "Solid cash flows", "corrupt-json", Instant.now()
        );
        when(holdingAiEvaluationRepository.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)).thenReturn(Optional.of(eval));

        Optional<HoldingAiEvaluationDto> result = service.getLatestHoldingEvaluation(portfolioId, instrumentId);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getLatestHoldingEvaluations returns list and filters out invalid JSON items")
    void testGetLatestHoldingEvaluationsList() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        String validJson = """
                {
                  "instrumentId": "%s",
                  "symbol": "AAPL",
                  "name": "Apple Inc.",
                  "assetClass": "STOCK",
                  "quantity": 10,
                  "currentPrice": 200,
                  "averageCostBasis": 150,
                  "unrealizedGainLoss": 500,
                  "unrealizedGainLossPercentage": 33.3,
                  "portfolioWeightPercentage": 12.5,
                  "stance": "HOLD",
                  "riskScore": 4,
                  "riskLevel": "MODERATE",
                  "executiveSummary": "Solid cash flows",
                  "strengths": ["Brand"],
                  "risks": ["Valuation"],
                  "holdingVsSellingTradeoff": "Hold",
                  "fundamentalMetrics": null,
                  "modelUsed": "LM_STUDIO",
                  "evaluatedAt": "2026-09-15T00:00:00Z"
                }
                """.formatted(instrumentId);
        HoldingAiEvaluation validEval = new HoldingAiEvaluation(
                portfolioId, instrumentId, "LM_STUDIO", "gemma4-12b", "HOLD", 4, "MODERATE", "Solid cash flows", validJson, Instant.now()
        );
        HoldingAiEvaluation invalidEval = new HoldingAiEvaluation(
                portfolioId, UUID.randomUUID(), "LM_STUDIO", "gemma4-12b", "HOLD", 4, "MODERATE", "Corrupt", "invalid-json", Instant.now()
        );
        when(holdingAiEvaluationRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(validEval, invalidEval));

        List<HoldingAiEvaluationDto> list = service.getLatestHoldingEvaluations(portfolioId);
        assertEquals(1, list.size());
        assertEquals("AAPL", list.get(0).symbol());
    }

    @Test
    @DisplayName("getLatestHoldingEvaluations throws when portfolio does not exist")
    void testGetLatestHoldingEvaluationsPortfolioNotFound() {
        UUID missingId = UUID.randomUUID();
        when(portfolioRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getLatestHoldingEvaluations(missingId));
    }

    @Test
    @DisplayName("evaluatePortfolio updates existing evaluation entity when already present in repository")
    void testEvaluatePortfolioUpdatesExistingEntity() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE)).thenReturn(List.of());

        PortfolioAiEvaluation existing = new PortfolioAiEvaluation(
                portfolioId, "OLD_PROVIDER", "old-model", 7, "HIGH", "Old summary", "{}", Instant.now().minusSeconds(3600)
        );
        when(portfolioAiEvaluationRepository.findByPortfolioId(portfolioId)).thenReturn(Optional.of(existing));

        String json = """
                {
                  "overallRiskScore": 3,
                  "overallRiskLevel": "LOW",
                  "executiveSummary": "Updated portfolio health",
                  "diversificationAssessment": "Strong",
                  "concentrationRisks": [],
                  "taxAndLocationOptimization": [],
                  "topRecommendations": ["Hold"],
                  "macroStressScenarios": []
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(json);

        PortfolioAiEvaluationDto result = service.evaluatePortfolio(portfolioId);
        assertNotNull(result);
        assertEquals(3, result.overallRiskScore());
        verify(portfolioAiEvaluationRepository).save(existing);
        assertEquals(3, existing.getOverallRiskScore());
        assertEquals("LM_STUDIO", existing.getProvider());
    }

    @Test
    @DisplayName("evaluateHolding updates existing holding evaluation entity when already present in repository")
    void testEvaluateHoldingUpdatesExistingEntity() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        HoldingAiEvaluation existing = new HoldingAiEvaluation(
                portfolioId, instrumentId, "OLD_PROVIDER", "old-model", "HOLD", 6, "MODERATE", "Old summary", "{}", Instant.now().minusSeconds(3600)
        );
        when(holdingAiEvaluationRepository.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)).thenReturn(Optional.of(existing));

        String json = """
                {
                  "stance": "STRONG_BUY",
                  "riskScore": 2,
                  "riskLevel": "LOW",
                  "executiveSummary": "Upgraded stance"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(json);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertEquals(AiStance.STRONG_BUY, result.stance());
        verify(holdingAiEvaluationRepository).save(existing);
        assertEquals(2, existing.getRiskScore());
        assertEquals("STRONG_BUY", existing.getStance());
    }

    @Test
    @DisplayName("evaluatePortfolio handles exception during persistence gracefully")
    void testEvaluatePortfolioPersistenceHandlesRepositoryException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE)).thenReturn(List.of());

        doThrow(new RuntimeException("Database offline")).when(portfolioAiEvaluationRepository).save(any());

        String json = """
                {
                  "overallRiskScore": 4,
                  "overallRiskLevel": "MODERATE",
                  "executiveSummary": "Persistence error tolerance test"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(json);

        PortfolioAiEvaluationDto result = service.evaluatePortfolio(portfolioId);
        assertNotNull(result);
        assertEquals(4, result.overallRiskScore());
    }

    @Test
    @DisplayName("evaluateHolding handles exception during persistence gracefully")
    void testEvaluateHoldingPersistenceHandlesRepositoryException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        doThrow(new RuntimeException("Database offline")).when(holdingAiEvaluationRepository).save(any());

        String json = """
                {
                  "stance": "HOLD",
                  "riskScore": 5,
                  "executiveSummary": "Holding persistence error tolerance test"
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(json);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
    }

    @Test
    @DisplayName("evaluateHolding handles empty json object string fallback")
    void testEvaluateHoldingEmptyJsonObjectFallback() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn("{}");

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
    }

    @Test
    @DisplayName("evaluateHolding parses partial fundamental metrics when only some fields are present")
    void testEvaluateHoldingPartialMetrics() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        String partialMetricsJson = """
                {
                  "stance": "HOLD",
                  "riskScore": 5,
                  "fundamentalMetrics": {
                    "peRatio": 22.5,
                    "dividendYield": 0.02
                  }
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(partialMetricsJson);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertNotNull(result.fundamentalMetrics());
        assertEquals(22.5, result.fundamentalMetrics().peRatio());
        assertEquals(0.02, result.fundamentalMetrics().dividendYield());
        assertNull(result.fundamentalMetrics().forwardPe());
        assertNull(result.fundamentalMetrics().priceToBook());
    }

    @Test
    @DisplayName("persistPortfolioEvaluation handles null modelUsed when existing entity is present and absent")
    void testPersistPortfolioEvaluationNullModelUsed() {
        PortfolioAiEvaluationDto dto = new PortfolioAiEvaluationDto(
                portfolioId, "Test Portfolio", "GBP",
                5, AiRiskLevel.MODERATE, "Summary", "Diversification",
                List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Instant.now()
        );

        // When existing entity is absent
        when(portfolioAiEvaluationRepository.findByPortfolioId(portfolioId)).thenReturn(Optional.empty());
        service.persistPortfolioEvaluation(portfolioId, "TEST_PROVIDER", dto);
        verify(portfolioAiEvaluationRepository).save(argThat(entity -> "TEST_PROVIDER".equals(entity.getModelUsed())));

        // When existing entity is present
        PortfolioAiEvaluation existing = new PortfolioAiEvaluation(
                portfolioId, "OLD_PROVIDER", "old-model", 4, "LOW", "Old", "{}", Instant.now()
        );
        when(portfolioAiEvaluationRepository.findByPortfolioId(portfolioId)).thenReturn(Optional.of(existing));
        service.persistPortfolioEvaluation(portfolioId, "NEW_PROVIDER", dto);
        assertEquals("NEW_PROVIDER", existing.getModelUsed());
    }

    @Test
    @DisplayName("persistHoldingEvaluation handles null modelUsed when existing entity is present and absent")
    void testPersistHoldingEvaluationNullModelUsed() {
        HoldingAiEvaluationDto dto = new HoldingAiEvaluationDto(
                instrumentId, "AAPL", "Apple Inc.", "STOCK",
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO,
                0.0, 10.0, AiStance.HOLD, 5, AiRiskLevel.MODERATE,
                "Summary", List.of(), List.of(), "Tradeoff", null,
                null, Instant.now()
        );

        // When existing entity is absent
        when(holdingAiEvaluationRepository.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)).thenReturn(Optional.empty());
        service.persistHoldingEvaluation(portfolioId, instrumentId, "TEST_PROVIDER", dto);
        verify(holdingAiEvaluationRepository).save(argThat(entity -> "TEST_PROVIDER".equals(entity.getModelUsed())));

        // When existing entity is present
        HoldingAiEvaluation existing = new HoldingAiEvaluation(
                portfolioId, instrumentId, "OLD_PROVIDER", "old-model", "HOLD", 4, "LOW", "Old", "{}", Instant.now()
        );
        when(holdingAiEvaluationRepository.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)).thenReturn(Optional.of(existing));
        service.persistHoldingEvaluation(portfolioId, instrumentId, "NEW_PROVIDER", dto);
        assertEquals("NEW_PROVIDER", existing.getModelUsed());
    }

    @Test
    @DisplayName("evaluateHolding handles null totalPortfolioVal, blank ticker, and unbalanced brace in response")
    void testEvaluateHoldingNullPortfolioValAndUnbalancedBrace() {
        Instrument blankTickerInst = new Instrument("Blank Ticker", AssetClass.STOCK, "   ", "US1111111111", "NASDAQ", new Currency("USD"));
        UUID blankId = blankTickerInst.getId();

        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(blankId)).thenReturn(Optional.of(blankTickerInst));

        ApiDtos.PositionPerformanceResponse pos = new ApiDtos.PositionPerformanceResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Account",
                blankId, "Blank Ticker", "   ", "US1111111111", AssetClass.STOCK,
                "ACTIVE",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", BigDecimal.ONE, "USD",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), List.of()
        );
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(pos));

        // Mock Analytics with null totalCurrentValue
        PortfolioAnalytics mockAnalytics = mock(PortfolioAnalytics.class);
        when(mockAnalytics.totalCurrentValue()).thenReturn(null);
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(mockAnalytics);

        // Response with unbalanced brace
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn("Some text with { only opening brace");

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, blankId);
        assertNotNull(result);
        assertEquals(0.0, result.portfolioWeightPercentage());
        assertEquals(AiStance.HOLD, result.stance());
    }

    @Test
    @DisplayName("evaluateHolding handles empty quote indicators and missing stance field")
    void testEvaluateHoldingEmptyQuoteIndicatorsAndMissingStance() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(testPortfolio));
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(testInstrument));
        when(positionService.listPortfolioPositionsPerformance(portfolioId, false)).thenReturn(List.of(testPosition));
        when(analyticsEngine.calculate(eq(portfolioId), any())).thenReturn(testAnalytics);

        YahooFinanceDtos.ChartEntry entryEmptyQuote = new YahooFinanceDtos.ChartEntry(
                null, null, new YahooFinanceDtos.ChartIndicators(List.of()), null
        );
        when(yahooFinanceGateway.fetchChart("AAPL", "1d", "1mo")).thenReturn(Optional.of(entryEmptyQuote));

        // JSON missing stance and overallRiskLevel completely
        String jsonNoStance = """
                {
                  "riskScore": 6,
                  "executiveSummary": "Missing stance and riskLevel test",
                  "fundamentalMetrics": {
                    "forwardPe": 18.0
                  }
                }
                """;
        when(gateway.generateChatCompletion(anyString(), anyString())).thenReturn(jsonNoStance);

        HoldingAiEvaluationDto result = service.evaluateHolding(portfolioId, instrumentId);
        assertNotNull(result);
        assertEquals(AiStance.HOLD, result.stance());
        assertEquals(6, result.riskScore());
        assertEquals(AiRiskLevel.MODERATE, result.riskLevel());
        assertNull(result.fundamentalMetrics().peRatio());
        assertNull(result.fundamentalMetrics().dividendYield());
        assertEquals(18.0, result.fundamentalMetrics().forwardPe());
    }
}


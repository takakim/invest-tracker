package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.takakim.investtracker.api.AiController;
import com.takakim.investtracker.service.ai.AiEvaluationService;
import com.takakim.investtracker.service.ai.dto.AiRiskLevel;
import com.takakim.investtracker.service.ai.dto.AiStance;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import com.takakim.investtracker.service.ai.dto.HoldingAiEvaluationDto;
import com.takakim.investtracker.service.ai.dto.HoldingFinancialMetricsDto;
import com.takakim.investtracker.service.ai.dto.PortfolioAiEvaluationDto;
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
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AiControllerTests {

    @Mock
    private AiEvaluationService aiEvaluationService;

    private AiController controller;
    private UUID portfolioId;
    private UUID instrumentId;

    @BeforeEach
    void setUp() {
        controller = new AiController(aiEvaluationService);
        portfolioId = UUID.randomUUID();
        instrumentId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getStatus returns AI connectivity status")
    void testGetStatus() {
        AiStatusDto mockStatus = new AiStatusDto(true, true, "LM_STUDIO", "http://localhost:1234", "gemma4-12b", List.of("gemma4-12b"), null);
        when(aiEvaluationService.getStatus()).thenReturn(mockStatus);

        ResponseEntity<AiStatusDto> response = controller.getStatus();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().connected());
        assertEquals("gemma4-12b", response.getBody().configuredModel());
        verify(aiEvaluationService).getStatus();
    }

    @Test
    @DisplayName("evaluatePortfolio delegates to service and returns 200 OK")
    void testEvaluatePortfolio() {
        PortfolioAiEvaluationDto mockEvaluation = new PortfolioAiEvaluationDto(
                portfolioId, "Growth Portfolio", "GBP",
                4, AiRiskLevel.MODERATE,
                "Strong portfolio health with balanced risk.",
                "Diversified across US tech and global equities.",
                List.of("Tech concentration"),
                List.of("ISA tax-sheltered"),
                List.of("Add UK bond buffer"),
                List.of("Inflation resilient"),
                List.of(),
                "gemma4-12b",
                Instant.now()
        );

        when(aiEvaluationService.evaluatePortfolio(portfolioId)).thenReturn(mockEvaluation);

        ResponseEntity<PortfolioAiEvaluationDto> response = controller.evaluatePortfolio(portfolioId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(portfolioId, response.getBody().portfolioId());
        assertEquals(4, response.getBody().overallRiskScore());
        verify(aiEvaluationService).evaluatePortfolio(portfolioId);
    }

    @Test
    @DisplayName("evaluateHolding delegates to service and returns 200 OK")
    void testEvaluateHolding() {
        HoldingAiEvaluationDto mockHoldingEval = new HoldingAiEvaluationDto(
                instrumentId, "AAPL", "Apple Inc.", "STOCK",
                new BigDecimal("10"), new BigDecimal("180.00"), new BigDecimal("150.00"),
                new BigDecimal("300.00"), 20.0, 15.5,
                AiStance.HOLD, 4, AiRiskLevel.MODERATE,
                "High quality compounder.",
                List.of("High ROIC"), List.of("Valuation"),
                "Hold for earnings compounding.",
                new HoldingFinancialMetricsDto(30.0, 26.0, 2.0, 40.0, 0.005, 1.2, 1.4, 199.0, 140.0, 2800000000000.0, null, "STOCK", "USD"),
                "gemma4-12b",
                Instant.now()
        );

        when(aiEvaluationService.evaluateHolding(portfolioId, instrumentId)).thenReturn(mockHoldingEval);

        ResponseEntity<HoldingAiEvaluationDto> response = controller.evaluateHolding(portfolioId, instrumentId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("AAPL", response.getBody().symbol());
        assertEquals(AiStance.HOLD, response.getBody().stance());
        assertEquals(4, response.getBody().riskScore());
        verify(aiEvaluationService).evaluateHolding(portfolioId, instrumentId);
    }

    @Test
    @DisplayName("getLatestPortfolioEvaluation returns 200 when cached evaluation exists")
    void testGetLatestPortfolioEvaluation_found() {
        PortfolioAiEvaluationDto mockEvaluation = new PortfolioAiEvaluationDto(
                portfolioId, "Growth Portfolio", "GBP",
                4, AiRiskLevel.MODERATE,
                "Strong portfolio health with balanced risk.",
                "Diversified across US tech and global equities.",
                List.of("Tech concentration"),
                List.of("ISA tax-sheltered"),
                List.of("Add UK bond buffer"),
                List.of("Inflation resilient"),
                List.of(),
                "OPENAI",
                Instant.now()
        );

        when(aiEvaluationService.getLatestPortfolioEvaluation(portfolioId))
                .thenReturn(Optional.of(mockEvaluation));

        ResponseEntity<PortfolioAiEvaluationDto> response = controller.getLatestPortfolioEvaluation(portfolioId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("OPENAI", response.getBody().modelUsed());
        verify(aiEvaluationService).getLatestPortfolioEvaluation(portfolioId);
    }

    @Test
    @DisplayName("getLatestPortfolioEvaluation returns 204 when no evaluation exists yet")
    void testGetLatestPortfolioEvaluation_notFound() {
        when(aiEvaluationService.getLatestPortfolioEvaluation(portfolioId))
                .thenReturn(Optional.empty());

        ResponseEntity<PortfolioAiEvaluationDto> response = controller.getLatestPortfolioEvaluation(portfolioId);

        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
        verify(aiEvaluationService).getLatestPortfolioEvaluation(portfolioId);
    }

    @Test
    @DisplayName("getLatestHoldingEvaluation returns 200 when cached evaluation exists")
    void testGetLatestHoldingEvaluation_found() {
        HoldingAiEvaluationDto mockHoldingEval = new HoldingAiEvaluationDto(
                instrumentId, "AAPL", "Apple Inc.", "STOCK",
                new BigDecimal("10"), new BigDecimal("180.00"), new BigDecimal("150.00"),
                new BigDecimal("300.00"), 20.0, 15.5,
                AiStance.HOLD, 4, AiRiskLevel.MODERATE,
                "High quality compounder.",
                List.of("High ROIC"), List.of("Valuation"),
                "Hold for earnings compounding.",
                new HoldingFinancialMetricsDto(30.0, 26.0, 2.0, 40.0, 0.005, 1.2, 1.4, 199.0, 140.0, 2800000000000.0, null, "STOCK", "USD"),
                "GEMINI",
                Instant.now()
        );

        when(aiEvaluationService.getLatestHoldingEvaluation(portfolioId, instrumentId))
                .thenReturn(Optional.of(mockHoldingEval));

        ResponseEntity<HoldingAiEvaluationDto> response =
                controller.getLatestHoldingEvaluation(portfolioId, instrumentId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("GEMINI", response.getBody().modelUsed());
        verify(aiEvaluationService).getLatestHoldingEvaluation(portfolioId, instrumentId);
    }

    @Test
    @DisplayName("getLatestHoldingEvaluation returns 204 when no evaluation exists")
    void testGetLatestHoldingEvaluation_notFound() {
        when(aiEvaluationService.getLatestHoldingEvaluation(portfolioId, instrumentId))
                .thenReturn(Optional.empty());

        ResponseEntity<HoldingAiEvaluationDto> response =
                controller.getLatestHoldingEvaluation(portfolioId, instrumentId);

        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
        verify(aiEvaluationService).getLatestHoldingEvaluation(portfolioId, instrumentId);
    }

    @Test
    @DisplayName("getLatestHoldingEvaluations returns list of all cached holding evaluations")
    void testGetLatestHoldingEvaluations() {
        HoldingAiEvaluationDto eval1 = new HoldingAiEvaluationDto(
                instrumentId, "AAPL", "Apple Inc.", "STOCK",
                new BigDecimal("10"), new BigDecimal("180.00"), new BigDecimal("150.00"),
                new BigDecimal("300.00"), 20.0, 15.5,
                AiStance.HOLD, 4, AiRiskLevel.MODERATE,
                "Summary", List.of(), List.of(), "Tradeoff",
                new HoldingFinancialMetricsDto(null, null, null, null, null, null, null, null, null, null, null, "STOCK", "USD"),
                "ANTHROPIC", Instant.now()
        );
        when(aiEvaluationService.getLatestHoldingEvaluations(portfolioId))
                .thenReturn(List.of(eval1));

        ResponseEntity<List<HoldingAiEvaluationDto>> response =
                controller.getLatestHoldingEvaluations(portfolioId);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("ANTHROPIC", response.getBody().get(0).modelUsed());
        verify(aiEvaluationService).getLatestHoldingEvaluations(portfolioId);
    }
}

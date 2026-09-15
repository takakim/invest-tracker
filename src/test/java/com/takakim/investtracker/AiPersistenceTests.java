package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.takakim.investtracker.domain.HoldingAiEvaluation;
import com.takakim.investtracker.domain.PortfolioAiEvaluation;
import com.takakim.investtracker.repository.HoldingAiEvaluationRepository;
import com.takakim.investtracker.repository.PortfolioAiEvaluationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AiPersistenceTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioAiEvaluationRepository portfolioAiRepo;

    @Autowired
    private HoldingAiEvaluationRepository holdingAiRepo;

    @Test
    @DisplayName("Persist, update, retrieve, and delete portfolio AI evaluation via repository")
    void testPortfolioAiEvaluationLifecycle() throws Exception {
        // Create portfolio
        String createPortfolioJson = """
                {"name":"AI Test Portfolio","baseCurrency":"USD","costBasisMethod":"FIFO","returnMethod":"TWR"}
                """;
        String res = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPortfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID portfolioId = UUID.fromString(JsonPath.read(res, "$.id"));

        // GET endpoint before evaluation returns 204 No Content
        mockMvc.perform(get("/api/v1/portfolios/{id}/ai/evaluation", portfolioId))
                .andExpect(status().isNoContent());

        // Save evaluation entity
        String sampleJson = """
                {"portfolioId":"%s","portfolioName":"AI Test Portfolio","baseCurrency":"USD","overallRiskScore":4,"overallRiskLevel":"MODERATE","executiveSummary":"Well diversified","diversificationAssessment":"Good balance","concentrationRisks":[],"taxAndLocationOptimization":[],"topRecommendations":[],"macroStressScenarios":[],"topHoldingEvaluations":[],"modelUsed":"OPENAI","evaluatedAt":"2026-09-15T00:00:00Z"}
                """.formatted(portfolioId);

        PortfolioAiEvaluation eval = new PortfolioAiEvaluation(
                portfolioId, "OPENAI", "gpt-4o-mini", 4, "MODERATE",
                "Well diversified", sampleJson, Instant.now()
        );
        PortfolioAiEvaluation saved = portfolioAiRepo.save(eval);
        assertNotNull(saved.getId());

        // Query repository
        Optional<PortfolioAiEvaluation> retrieved = portfolioAiRepo.findByPortfolioId(portfolioId);
        assertTrue(retrieved.isPresent());
        assertEquals("OPENAI", retrieved.get().getProvider());
        assertEquals("gpt-4o-mini", retrieved.get().getModelUsed());
        assertEquals(4, retrieved.get().getOverallRiskScore());
        assertEquals("MODERATE", retrieved.get().getOverallRiskLevel());

        // GET endpoint now returns 200 with cached result
        mockMvc.perform(get("/api/v1/portfolios/{id}/ai/evaluation", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value(portfolioId.toString()))
                .andExpect(jsonPath("$.overallRiskScore").value(4))
                .andExpect(jsonPath("$.modelUsed").value("OPENAI"));

        // Update evaluation (upsert logic)
        retrieved.get().setProvider("GEMINI");
        retrieved.get().setModelUsed("gemini-2.0-flash");
        retrieved.get().setOverallRiskScore(3);
        portfolioAiRepo.save(retrieved.get());

        Optional<PortfolioAiEvaluation> updated = portfolioAiRepo.findByPortfolioId(portfolioId);
        assertTrue(updated.isPresent());
        assertEquals("GEMINI", updated.get().getProvider());
        assertEquals(3, updated.get().getOverallRiskScore());

        // Cleanup
        portfolioAiRepo.deleteByPortfolioId(portfolioId);
        assertFalse(portfolioAiRepo.findByPortfolioId(portfolioId).isPresent());
    }

    @Test
    @DisplayName("Persist, update, retrieve, and delete holding AI evaluation via repository")
    void testHoldingAiEvaluationLifecycle() throws Exception {
        // Create portfolio
        String createPortfolioJson = """
                {"name":"AI Holding Test Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"TWR"}
                """;
        String pRes = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPortfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID portfolioId = UUID.fromString(JsonPath.read(pRes, "$.id"));

        // Create instrument
        String createInstrumentJson = """
                {"name":"Microsoft Corp","assetClass":"STOCK","ticker":"MSFT","isin":"US5949181045","exchange":"NASDAQ","currency":"USD"}
                """;
        String iRes = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createInstrumentJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID instrumentId = UUID.fromString(JsonPath.read(iRes, "$.id"));

        // GET endpoint before evaluation returns 204 No Content
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/holdings/{instrumentId}/ai/evaluation", portfolioId, instrumentId))
                .andExpect(status().isNoContent());

        // Save holding evaluation entity
        String sampleJson = """
                {"instrumentId":"%s","symbol":"MSFT","name":"Microsoft Corp","assetClass":"STOCK","quantity":10,"currentPrice":420.50,"averageCostBasis":380.00,"unrealizedGainLoss":405.00,"unrealizedGainLossPercentage":10.66,"portfolioWeightPercentage":15.5,"stance":"ACCUMULATE","riskScore":3,"riskLevel":"LOW","executiveSummary":"Strong moat in cloud and enterprise","strengths":["Azure growth","Cash flow"],"risks":["Valuation"],"holdingVsSellingTradeoff":"Holding preferred over selling","fundamentalMetrics":null,"modelUsed":"ANTHROPIC","evaluatedAt":"2026-09-15T00:00:00Z"}
                """.formatted(instrumentId);

        HoldingAiEvaluation holdingEval = new HoldingAiEvaluation(
                portfolioId, instrumentId, "ANTHROPIC", "claude-3-5-haiku-latest",
                "ACCUMULATE", 3, "LOW", "Strong moat in cloud and enterprise",
                sampleJson, Instant.now()
        );
        HoldingAiEvaluation saved = holdingAiRepo.save(holdingEval);
        assertNotNull(saved.getId());

        // Find by portfolio + instrument
        Optional<HoldingAiEvaluation> retrieved = holdingAiRepo.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId);
        assertTrue(retrieved.isPresent());
        assertEquals("ANTHROPIC", retrieved.get().getProvider());
        assertEquals("ACCUMULATE", retrieved.get().getStance());
        assertEquals(3, retrieved.get().getRiskScore());

        // Find all by portfolio
        List<HoldingAiEvaluation> allForPortfolio = holdingAiRepo.findByPortfolioId(portfolioId);
        assertEquals(1, allForPortfolio.size());

        // GET endpoint returns 200 with cached result
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/holdings/{instrumentId}/ai/evaluation", portfolioId, instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("MSFT"))
                .andExpect(jsonPath("$.stance").value("ACCUMULATE"))
                .andExpect(jsonPath("$.modelUsed").value("ANTHROPIC"));

        // Cleanup
        holdingAiRepo.deleteByPortfolioIdAndInstrumentId(portfolioId, instrumentId);
        assertFalse(holdingAiRepo.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId).isPresent());
    }
}

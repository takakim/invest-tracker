package com.takakim.investtracker.api;

import com.takakim.investtracker.service.ai.AiEvaluationService;
import com.takakim.investtracker.service.ai.dto.AiConfigRequest;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import com.takakim.investtracker.service.ai.dto.HoldingAiEvaluationDto;
import com.takakim.investtracker.service.ai.dto.PortfolioAiEvaluationDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AiController {

    private final AiEvaluationService aiEvaluationService;

    public AiController(AiEvaluationService aiEvaluationService) {
        this.aiEvaluationService = aiEvaluationService;
    }

    @GetMapping("/ai/status")
    public ResponseEntity<AiStatusDto> getStatus() {
        return ResponseEntity.ok(aiEvaluationService.getStatus());
    }

    /** Dynamically update AI settings such as request timeout. */
    @PutMapping("/ai/config")
    public ResponseEntity<AiStatusDto> updateConfig(@Valid @RequestBody AiConfigRequest request) {
        AiStatusDto updated = aiEvaluationService.updateConfig(request);
        return ResponseEntity.ok(updated);
    }


    /** Trigger a new portfolio-level AI evaluation (calls the LLM). */
    @PostMapping("/portfolios/{portfolioId}/ai/evaluate")
    public ResponseEntity<PortfolioAiEvaluationDto> evaluatePortfolio(@PathVariable UUID portfolioId) {
        PortfolioAiEvaluationDto result = aiEvaluationService.evaluatePortfolio(portfolioId);
        return ResponseEntity.ok(result);
    }

    /** Retrieve the latest persisted portfolio-level AI evaluation (no LLM call). */
    @GetMapping("/portfolios/{portfolioId}/ai/evaluation")
    public ResponseEntity<PortfolioAiEvaluationDto> getLatestPortfolioEvaluation(@PathVariable UUID portfolioId) {
        return aiEvaluationService.getLatestPortfolioEvaluation(portfolioId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** Trigger a new holding-level AI evaluation for a specific instrument (calls the LLM). */
    @PostMapping("/portfolios/{portfolioId}/holdings/{instrumentId}/ai/evaluate")
    public ResponseEntity<HoldingAiEvaluationDto> evaluateHolding(
            @PathVariable UUID portfolioId,
            @PathVariable UUID instrumentId
    ) {
        HoldingAiEvaluationDto result = aiEvaluationService.evaluateHolding(portfolioId, instrumentId);
        return ResponseEntity.ok(result);
    }

    /** Retrieve the latest persisted holding-level AI evaluation for a specific instrument (no LLM call). */
    @GetMapping("/portfolios/{portfolioId}/holdings/{instrumentId}/ai/evaluation")
    public ResponseEntity<HoldingAiEvaluationDto> getLatestHoldingEvaluation(
            @PathVariable UUID portfolioId,
            @PathVariable UUID instrumentId
    ) {
        return aiEvaluationService.getLatestHoldingEvaluation(portfolioId, instrumentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** Retrieve all persisted holding-level AI evaluations for the portfolio (no LLM call). */
    @GetMapping("/portfolios/{portfolioId}/holdings/ai/evaluations")
    public ResponseEntity<List<HoldingAiEvaluationDto>> getLatestHoldingEvaluations(@PathVariable UUID portfolioId) {
        List<HoldingAiEvaluationDto> results = aiEvaluationService.getLatestHoldingEvaluations(portfolioId);
        return ResponseEntity.ok(results);
    }
}

package com.takakim.investtracker.api;

import com.takakim.investtracker.service.ai.AiEvaluationService;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import com.takakim.investtracker.service.ai.dto.HoldingAiEvaluationDto;
import com.takakim.investtracker.service.ai.dto.PortfolioAiEvaluationDto;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/portfolios/{portfolioId}/ai/evaluate")
    public ResponseEntity<PortfolioAiEvaluationDto> evaluatePortfolio(@PathVariable UUID portfolioId) {
        PortfolioAiEvaluationDto result = aiEvaluationService.evaluatePortfolio(portfolioId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/portfolios/{portfolioId}/holdings/{instrumentId}/ai/evaluate")
    public ResponseEntity<HoldingAiEvaluationDto> evaluateHolding(
            @PathVariable UUID portfolioId,
            @PathVariable UUID instrumentId
    ) {
        HoldingAiEvaluationDto result = aiEvaluationService.evaluateHolding(portfolioId, instrumentId);
        return ResponseEntity.ok(result);
    }
}

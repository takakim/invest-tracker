package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.ApplyCorporateActionRequest;
import com.takakim.investtracker.api.ApiDtos.CorporateActionResponse;
import com.takakim.investtracker.api.ApiDtos.ScanCorporateActionsResponse;
import com.takakim.investtracker.domain.CorporateActionStatus;
import com.takakim.investtracker.service.CorporateActionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CorporateActionController {

    private final CorporateActionService corporateActionService;

    public CorporateActionController(CorporateActionService corporateActionService) {
        this.corporateActionService = corporateActionService;
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/corporate-actions")
    public List<CorporateActionResponse> getCorporateActions(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) CorporateActionStatus status) {
        return corporateActionService.getPortfolioCorporateActions(portfolioId, status);
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/corporate-actions/scan")
    public ScanCorporateActionsResponse scanCorporateActions(@PathVariable UUID portfolioId) {
        return corporateActionService.scanPortfolio(portfolioId);
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/corporate-actions/{actionId}/apply")
    public CorporateActionResponse applyCorporateAction(
            @PathVariable UUID portfolioId,
            @PathVariable UUID actionId,
            @Valid @RequestBody ApplyCorporateActionRequest request) {
        return corporateActionService.applyAction(portfolioId, actionId, request);
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/corporate-actions/{actionId}/dismiss")
    public CorporateActionResponse dismissCorporateAction(
            @PathVariable UUID portfolioId,
            @PathVariable UUID actionId) {
        return corporateActionService.dismissAction(portfolioId, actionId);
    }
}

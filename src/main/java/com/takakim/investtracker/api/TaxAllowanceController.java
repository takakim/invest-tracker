package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.AvailableTaxYearsResponse;
import com.takakim.investtracker.api.ApiDtos.TaxReportResponse;
import com.takakim.investtracker.api.ApiDtos.TaxSettingsRequest;
import com.takakim.investtracker.api.ApiDtos.TaxSettingsResponse;
import com.takakim.investtracker.domain.TaxRegime;
import com.takakim.investtracker.service.tax.TaxAllowanceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/tax-allowances")
public class TaxAllowanceController {

    private final TaxAllowanceService taxAllowanceService;

    public TaxAllowanceController(TaxAllowanceService taxAllowanceService) {
        this.taxAllowanceService = taxAllowanceService;
    }

    @GetMapping
    public TaxReportResponse getTaxReport(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String taxYear,
            @RequestParam(required = false) TaxRegime regime) {
        return taxAllowanceService.generateTaxReport(portfolioId, taxYear, regime);
    }

    @GetMapping("/available-years")
    public AvailableTaxYearsResponse getAvailableTaxYears(@PathVariable UUID portfolioId) {
        return taxAllowanceService.getAvailableTaxYears(portfolioId);
    }

    @PutMapping("/settings")
    public TaxSettingsResponse updateTaxSettings(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody TaxSettingsRequest request) {
        return taxAllowanceService.saveTaxSettings(portfolioId, request);
    }
}

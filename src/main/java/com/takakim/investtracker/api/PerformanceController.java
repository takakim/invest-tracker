package com.takakim.investtracker.api;

import com.takakim.investtracker.service.performance.AccountPerformanceSummary;
import com.takakim.investtracker.service.performance.PerformanceEngine;
import com.takakim.investtracker.service.performance.PerformanceResult;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing portfolio performance analytics.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/performance")
public class PerformanceController {

    private final PerformanceEngine performanceEngine;

    public PerformanceController(PerformanceEngine performanceEngine) {
        this.performanceEngine = performanceEngine;
    }

    /**
     * Calculates and returns portfolio performance as of now.
     *
     * <p>Returns TWR or MWR depending on the portfolio's configured {@code returnMethod}.
     * XIRR returns HTTP 501 until market data is available in a future phase.
     *
     * @param portfolioId the portfolio UUID
     * @return 200 with PerformanceResultResponse, or 501 for XIRR portfolios
     */
    @GetMapping
    public ResponseEntity<ApiDtos.PerformanceResultResponse> getPerformance(
            @PathVariable UUID portfolioId) {
        PerformanceResult result = performanceEngine.calculate(portfolioId);
        return ResponseEntity.ok(toResponse(result));
    }

    // ----------------------------------------------------------------
    // Mapping
    // ----------------------------------------------------------------

    private ApiDtos.PerformanceResultResponse toResponse(PerformanceResult result) {
        return new ApiDtos.PerformanceResultResponse(
                result.portfolioId(),
                result.asOf(),
                result.returnMethod(),
                result.twrReturn(),
                result.twrAnnualized(),
                result.mwrReturn(),
                result.totalRealizedGainLoss(),
                result.totalDividendIncome(),
                result.totalInterestIncome(),
                result.totalFees(),
                result.totalTaxes(),
                result.totalNetIncome(),
                result.totalCostBasis(),
                result.currency(),
                result.valuationBasis(),
                result.byAccount().stream().map(this::toAccountResponse).toList()
        );
    }

    private ApiDtos.AccountPerformanceSummaryResponse toAccountResponse(AccountPerformanceSummary s) {
        return new ApiDtos.AccountPerformanceSummaryResponse(
                s.accountId(),
                s.accountName(),
                s.realizedGainLoss(),
                s.dividendIncome(),
                s.interestIncome(),
                s.fees(),
                s.taxes(),
                s.costBasis(),
                s.currency()
        );
    }
}

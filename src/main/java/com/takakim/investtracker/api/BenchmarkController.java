package com.takakim.investtracker.api;

import com.takakim.investtracker.service.benchmark.BenchmarkComparisonResult;
import com.takakim.investtracker.service.benchmark.BenchmarkEngine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BenchmarkController {

    private final BenchmarkEngine benchmarkEngine;

    public BenchmarkController(BenchmarkEngine benchmarkEngine) {
        this.benchmarkEngine = benchmarkEngine;
    }

    @GetMapping("/portfolios/{portfolioId}/benchmark-comparison")
    public ResponseEntity<ApiDtos.BenchmarkComparisonResponse> comparePortfolioToBenchmark(
            @PathVariable UUID portfolioId,
            @RequestParam UUID benchmarkInstrumentId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Instant asOf) {

        BenchmarkComparisonResult result = benchmarkEngine.comparePortfolioToBenchmark(
                portfolioId, benchmarkInstrumentId, period, asOf);

        return ResponseEntity.ok(toResponse(result));
    }

    @GetMapping("/benchmarks")
    public ResponseEntity<List<ApiDtos.BenchmarkInstrumentResponse>> getAvailableBenchmarks() {
        var instruments = benchmarkEngine.getAvailableBenchmarks();
        var response = instruments.stream()
                .map(i -> new ApiDtos.BenchmarkInstrumentResponse(
                        i.getId(),
                        i.getName(),
                        i.getTicker(),
                        i.getIsin(),
                        i.getAssetClass().name(),
                        i.getCurrency().code()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    private ApiDtos.BenchmarkComparisonResponse toResponse(BenchmarkComparisonResult r) {
        return new ApiDtos.BenchmarkComparisonResponse(
                r.portfolioId(),
                r.benchmarkInstrumentId(),
                r.benchmarkName(),
                r.benchmarkTicker(),
                r.periodStart(),
                r.periodEnd(),
                r.portfolioReturn(),
                r.benchmarkReturn(),
                r.excessReturn(),
                r.annualizedPortfolioReturn(),
                r.annualizedBenchmarkReturn(),
                r.annualizedExcessReturn(),
                r.outperforming(),
                r.baseCurrency(),
                r.warnings()
        );
    }
}

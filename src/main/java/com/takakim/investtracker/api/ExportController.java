package com.takakim.investtracker.api;

import com.takakim.investtracker.service.export.PortfolioExportService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final PortfolioExportService exportService;

    public ExportController(PortfolioExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping(value = "/api/v1/portfolios/{portfolioId}/export/positions.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportPositionsCsv(@PathVariable UUID portfolioId) {
        String csv = exportService.exportPositionsCsv(portfolioId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"positions-" + portfolioId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping(value = "/api/v1/portfolios/{portfolioId}/export/transactions.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportTransactionsCsv(@PathVariable UUID portfolioId) {
        String csv = exportService.exportTransactionsCsv(portfolioId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions-" + portfolioId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}

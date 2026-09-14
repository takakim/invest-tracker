package com.takakim.investtracker.api;

import com.takakim.investtracker.domain.TaxRegime;
import com.takakim.investtracker.service.export.ExecutiveSummaryPdfService;
import com.takakim.investtracker.service.export.PortfolioExportService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final PortfolioExportService exportService;
    private final ExecutiveSummaryPdfService executiveSummaryPdfService;

    public ExportController(PortfolioExportService exportService, ExecutiveSummaryPdfService executiveSummaryPdfService) {
        this.exportService = exportService;
        this.executiveSummaryPdfService = executiveSummaryPdfService;
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

    @GetMapping(value = "/api/v1/portfolios/{portfolioId}/export/cash-flows.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCashFlowsCsv(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String groupBy) {
        String csv = exportService.exportCashFlowsCsv(portfolioId, period, groupBy);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cash-flows-" + portfolioId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping(value = "/api/v1/portfolios/{portfolioId}/export/executive-summary.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> exportExecutiveSummaryPdf(@PathVariable UUID portfolioId) {
        byte[] pdfBytes = executiveSummaryPdfService.generateExecutiveSummaryPdf(portfolioId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"executive-summary-" + portfolioId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping(value = "/api/v1/portfolios/{portfolioId}/export/tax-report.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> exportTaxReportPdf(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String taxYear,
            @RequestParam(required = false) TaxRegime regime) {
        byte[] pdfBytes = executiveSummaryPdfService.generateTaxReportPdf(portfolioId, taxYear, regime);
        String safeTaxYear = taxYear != null ? taxYear.replace('/', '-') : "current";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tax-report-" + safeTaxYear + "-" + portfolioId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}

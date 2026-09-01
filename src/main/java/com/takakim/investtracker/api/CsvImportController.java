package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.CsvImportPreviewResponse;
import com.takakim.investtracker.api.ApiDtos.CsvImportRequest;
import com.takakim.investtracker.api.ApiDtos.ImportBatchResponse;
import com.takakim.investtracker.api.ApiDtos.PreviewRowResponse;
import com.takakim.investtracker.domain.ImportBatch;
import com.takakim.investtracker.service.csv.CsvImportService;
import com.takakim.investtracker.service.csv.CsvImportService.PreviewResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsvImportController {

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @GetMapping("/api/v1/imports/supported-brokers")
    public List<String> getSupportedBrokers() {
        return csvImportService.getSupportedBrokers();
    }

    @PostMapping("/api/v1/imports/detect-broker")
    public ApiDtos.BrokerDetectionResponse detectBroker(@Valid @RequestBody ApiDtos.BrokerDetectionRequest request) {
        CsvImportService.BrokerDetectionResult res = csvImportService.detectBroker(request.csvContent());
        return new ApiDtos.BrokerDetectionResponse(
                res.brokerName(),
                res.confidence(),
                res.isSupported(),
                res.supportedBrokers()
        );
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/imports/preview")
    public CsvImportPreviewResponse previewImport(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @Valid @RequestBody CsvImportRequest request) {

        PreviewResult preview = csvImportService.previewImport(
                portfolioId, accountId, request.fileName(), request.csvContent(), request.overrideBroker());

        List<PreviewRowResponse> rows = preview.rows().stream()
                .map(r -> new PreviewRowResponse(
                        r.rowNumber(),
                        r.rawType(),
                        r.mappedType(),
                        r.instrumentTitle(),
                        r.ticker(),
                        r.isin(),
                        r.quantity(),
                        r.price(),
                        r.grossAmount(),
                        r.feeAmount(),
                        r.taxAmount(),
                        r.currency(),
                        r.isDuplicate(),
                        r.isIgnored(),
                        r.diagnosticMessage()
                ))
                .toList();

        return new CsvImportPreviewResponse(
                preview.brokerName(),
                preview.fileName(),
                preview.totalRows(),
                preview.importableRows(),
                preview.duplicateRows(),
                preview.ignoredRows(),
                rows
        );
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/imports")
    public ResponseEntity<ImportBatchResponse> executeImport(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @Valid @RequestBody CsvImportRequest request) {

        ImportBatch batch = csvImportService.executeImport(
                portfolioId, accountId, request.fileName(), request.csvContent(), request.overrideBroker());

        ImportBatchResponse response = toResponse(batch);
        URI location = URI.create(String.format("/api/v1/portfolios/%s/accounts/%s/imports/%s",
                portfolioId, accountId, batch.getId()));
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/imports")
    public List<ImportBatchResponse> listImports(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId) {

        return csvImportService.listImportBatches(portfolioId, accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/imports/{batchId}")
    public ResponseEntity<Void> deleteImportBatch(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @PathVariable UUID batchId) {

        csvImportService.deleteImportBatch(portfolioId, accountId, batchId);
        return ResponseEntity.noContent().build();
    }

    private ImportBatchResponse toResponse(ImportBatch b) {
        return new ImportBatchResponse(
                b.getId(),
                b.getAccount().getId(),
                b.getFileName(),
                b.getBrokerType(),
                b.getStatus().name(),
                b.getTotalRows(),
                b.getImportedRows(),
                b.getSkippedRows(),
                b.getCreatedAt()
        );
    }
}

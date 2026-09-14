package com.takakim.investtracker;

import com.takakim.investtracker.api.ExportController;
import com.takakim.investtracker.domain.TaxRegime;
import com.takakim.investtracker.service.export.ExecutiveSummaryPdfService;
import com.takakim.investtracker.service.export.PortfolioExportService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportControllerTests {

    @Mock
    private PortfolioExportService exportService;

    @Mock
    private ExecutiveSummaryPdfService executiveSummaryPdfService;

    private ExportController controller;
    private UUID portfolioId;

    @BeforeEach
    void setUp() {
        controller = new ExportController(exportService, executiveSummaryPdfService);
        portfolioId = UUID.randomUUID();
    }

    @Test
    @DisplayName("exportPositionsCsv returns CSV attachment")
    void testExportPositionsCsv() {
        when(exportService.exportPositionsCsv(portfolioId)).thenReturn("header\nval");

        ResponseEntity<byte[]> response = controller.exportPositionsCsv(portfolioId);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("positions-" + portfolioId + ".csv"));
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertArrayEquals("header\nval".getBytes(StandardCharsets.UTF_8), response.getBody());
        verify(exportService).exportPositionsCsv(portfolioId);
    }

    @Test
    @DisplayName("exportTransactionsCsv returns CSV attachment")
    void testExportTransactionsCsv() {
        when(exportService.exportTransactionsCsv(portfolioId)).thenReturn("tx_id,type");

        ResponseEntity<byte[]> response = controller.exportTransactionsCsv(portfolioId);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("transactions-" + portfolioId + ".csv"));
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertArrayEquals("tx_id,type".getBytes(StandardCharsets.UTF_8), response.getBody());
        verify(exportService).exportTransactionsCsv(portfolioId);
    }

    @Test
    @DisplayName("exportCashFlowsCsv returns CSV attachment with optional params")
    void testExportCashFlowsCsv() {
        when(exportService.exportCashFlowsCsv(portfolioId, "YTD", "MONTH")).thenReturn("period,inflow");

        ResponseEntity<byte[]> response = controller.exportCashFlowsCsv(portfolioId, "YTD", "MONTH");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("cash-flows-" + portfolioId + ".csv"));
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertArrayEquals("period,inflow".getBytes(StandardCharsets.UTF_8), response.getBody());
        verify(exportService).exportCashFlowsCsv(portfolioId, "YTD", "MONTH");
    }

    @Test
    @DisplayName("exportExecutiveSummaryPdf returns PDF attachment")
    void testExportExecutiveSummaryPdf() {
        byte[] samplePdf = "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8);
        when(executiveSummaryPdfService.generateExecutiveSummaryPdf(portfolioId)).thenReturn(samplePdf);

        ResponseEntity<byte[]> response = controller.exportExecutiveSummaryPdf(portfolioId);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("executive-summary-" + portfolioId + ".pdf"));
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertArrayEquals(samplePdf, response.getBody());
        verify(executiveSummaryPdfService).generateExecutiveSummaryPdf(portfolioId);
    }

    @Test
    @DisplayName("exportTaxReportPdf returns PDF attachment with sanitized filename")
    void testExportTaxReportPdf() {
        byte[] samplePdf = "%PDF-1.4 tax report".getBytes(StandardCharsets.UTF_8);
        when(executiveSummaryPdfService.generateTaxReportPdf(portfolioId, "2024/2025", TaxRegime.UK_HMRC)).thenReturn(samplePdf);

        ResponseEntity<byte[]> response = controller.exportTaxReportPdf(portfolioId, "2024/2025", TaxRegime.UK_HMRC);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("tax-report-2024-2025-" + portfolioId + ".pdf"));
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertArrayEquals(samplePdf, response.getBody());
        verify(executiveSummaryPdfService).generateTaxReportPdf(portfolioId, "2024/2025", TaxRegime.UK_HMRC);
    }

    @Test
    @DisplayName("exportTaxReportPdf handles null taxYear parameter with default current label")
    void testExportTaxReportPdfWithNullTaxYear() {
        byte[] samplePdf = "%PDF-1.4 tax report".getBytes(StandardCharsets.UTF_8);
        when(executiveSummaryPdfService.generateTaxReportPdf(portfolioId, null, null)).thenReturn(samplePdf);

        ResponseEntity<byte[]> response = controller.exportTaxReportPdf(portfolioId, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("tax-report-current-" + portfolioId + ".pdf"));
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertArrayEquals(samplePdf, response.getBody());
        verify(executiveSummaryPdfService).generateTaxReportPdf(portfolioId, null, null);
    }
}

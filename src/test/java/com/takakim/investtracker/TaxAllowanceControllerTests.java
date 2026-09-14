package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.*;
import com.takakim.investtracker.api.TaxAllowanceController;
import com.takakim.investtracker.domain.TaxRegime;
import com.takakim.investtracker.service.tax.TaxAllowanceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TaxAllowanceControllerTests {

    @Test
    @DisplayName("TaxAllowanceController delegates appropriately to TaxAllowanceService")
    void testControllerDelegation() {
        TaxAllowanceService service = mock(TaxAllowanceService.class);
        TaxAllowanceController controller = new TaxAllowanceController(service);

        UUID portfolioId = UUID.randomUUID();

        CapitalGainsTaxSummaryDto cgtDto = new CapitalGainsTaxSummaryDto(
                new BigDecimal("10000.00"), new BigDecimal("6000.00"),
                new BigDecimal("4500.00"), new BigDecimal("500.00"),
                new BigDecimal("4000.00"), BigDecimal.ZERO,
                new BigDecimal("4000.00"), new BigDecimal("3000.00"),
                new BigDecimal("3000.00"), BigDecimal.ZERO,
                new BigDecimal("1000.00"), new BigDecimal("180.00"),
                new BigDecimal("240.00"), new BigDecimal("18.00"),
                new BigDecimal("24.00"), 2
        );

        DividendTaxSummaryDto divDto = new DividendTaxSummaryDto(
                new BigDecimal("800.00"), BigDecimal.ZERO, new BigDecimal("800.00"),
                new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO,
                new BigDecimal("300.00"), new BigDecimal("26.25"), new BigDecimal("101.25"),
                new BigDecimal("118.05"), new BigDecimal("8.75"), new BigDecimal("33.75"),
                new BigDecimal("39.35"), 3
        );

        TaxShelteredSummaryDto shelteredDto = new TaxShelteredSummaryDto(
                new BigDecimal("2000.00"), BigDecimal.ZERO, new BigDecimal("400.00"),
                new BigDecimal("400.00"), new BigDecimal("35.00"), new BigDecimal("435.00")
        );

        TaxReportResponse report = new TaxReportResponse(
                portfolioId, "Main Portfolio", "GBP", "2024/25",
                TaxRegime.UK_HMRC, Instant.now(), Instant.now(),
                cgtDto, divDto, shelteredDto,
                List.of(), List.of(), List.of(), List.of()
        );

        when(service.generateTaxReport(portfolioId, "2024/25", TaxRegime.UK_HMRC))
                .thenReturn(report);

        AvailableTaxYearsResponse availableYears = new AvailableTaxYearsResponse(
                List.of("2024/25", "2023/24"), List.of("2025", "2024"),
                "2024/25", "2025"
        );
        when(service.getAvailableTaxYears(portfolioId)).thenReturn(availableYears);

        TaxSettingsRequest req = new TaxSettingsRequest(
                "2024/25", TaxRegime.UK_HMRC, new BigDecimal("3000.00"),
                new BigDecimal("500.00"), new BigDecimal("1000.00"), "Notes"
        );
        TaxSettingsResponse settingsResp = new TaxSettingsResponse(
                UUID.randomUUID(), portfolioId, "2024/25", TaxRegime.UK_HMRC,
                new BigDecimal("3000.00"), new BigDecimal("500.00"),
                new BigDecimal("1000.00"), "Notes", Instant.now()
        );
        when(service.saveTaxSettings(portfolioId, req)).thenReturn(settingsResp);

        assertEquals(report, controller.getTaxReport(portfolioId, "2024/25", TaxRegime.UK_HMRC));
        assertEquals(availableYears, controller.getAvailableTaxYears(portfolioId));
        assertEquals(settingsResp, controller.updateTaxSettings(portfolioId, req));

        verify(service).generateTaxReport(portfolioId, "2024/25", TaxRegime.UK_HMRC);
        verify(service).getAvailableTaxYears(portfolioId);
        verify(service).saveTaxSettings(portfolioId, req);
    }
}

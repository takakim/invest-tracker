package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.ApplyCorporateActionRequest;
import com.takakim.investtracker.api.ApiDtos.CorporateActionResponse;
import com.takakim.investtracker.api.ApiDtos.ScanCorporateActionsResponse;
import com.takakim.investtracker.api.CorporateActionController;
import com.takakim.investtracker.domain.CorporateActionStatus;
import com.takakim.investtracker.service.CorporateActionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CorporateActionControllerTests {

    @Test
    void testControllerDelegation() {
        CorporateActionService service = mock(CorporateActionService.class);
        CorporateActionController controller = new CorporateActionController(service);

        UUID portfolioId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        CorporateActionResponse response = new CorporateActionResponse(
                actionId, instrumentId, "Apple Inc.", "AAPL", "US0378331005", "STOCK",
                "STOCK_SPLIT", "PENDING", Instant.now(), null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, "USD", "10:1 split", "YAHOO",
                BigDecimal.TEN, new BigDecimal("90.00"), null, accountId, "Trading Account",
                null, Instant.now(), Instant.now()
        );

        ScanCorporateActionsResponse scanResponse = new ScanCorporateActionsResponse(
                portfolioId, 1, 2, 1, List.of("Scan complete")
        );

        when(service.getPortfolioCorporateActions(portfolioId, CorporateActionStatus.PENDING))
                .thenReturn(List.of(response));
        when(service.scanPortfolio(portfolioId)).thenReturn(scanResponse);

        ApplyCorporateActionRequest applyReq = new ApplyCorporateActionRequest(accountId, new BigDecimal("90.00"), null, null, "Note");
        when(service.applyAction(portfolioId, actionId, applyReq)).thenReturn(response);
        when(service.dismissAction(portfolioId, actionId)).thenReturn(response);

        assertEquals(List.of(response), controller.getCorporateActions(portfolioId, CorporateActionStatus.PENDING));
        assertEquals(scanResponse, controller.scanCorporateActions(portfolioId));
        assertEquals(response, controller.applyCorporateAction(portfolioId, actionId, applyReq));
        assertEquals(response, controller.dismissCorporateAction(portfolioId, actionId));
    }
}

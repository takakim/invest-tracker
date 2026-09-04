package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.api.InstrumentController;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.service.InstrumentService;
import com.takakim.investtracker.service.market.queue.MarketDataRefreshQueueService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstrumentControllerTests {

    @Test
    @DisplayName("InstrumentController delegates endpoints to InstrumentService")
    void testControllerDelegation() {
        InstrumentService service = mock(InstrumentService.class);
        InstrumentController controller = new InstrumentController(service);

        UUID id = UUID.randomUUID();
        ApiDtos.InstrumentResponse response = new ApiDtos.InstrumentResponse(
                id, "Apple", AssetClass.STOCK, "AAPL", null, null, "USD", false,
                Instant.now(), Instant.now(), null, null, null, null
        );

        when(service.create(any())).thenReturn(response);
        when(service.update(eq(id), any())).thenReturn(response);
        when(service.get(id)).thenReturn(response);
        when(service.list()).thenReturn(List.of(response));
        when(service.refreshPrice(id)).thenReturn(response);
        when(service.refreshAllPrices()).thenReturn(List.of(response));
        when(service.getQueueStatus()).thenReturn(new MarketDataRefreshQueueService.QueueStatus(3, 1, 0));

        ApiDtos.InstrumentRequest req = new ApiDtos.InstrumentRequest("Apple", AssetClass.STOCK, "AAPL", null, null, "USD");

        assertEquals(response, controller.create(req));
        assertEquals(response, controller.update(id, req));
        assertEquals(response, controller.get(id));
        assertEquals(1, controller.list().size());
        assertEquals(response, controller.refreshPrice(id));
        assertEquals(1, controller.refreshAllPrices().size());

        var queueStatus = controller.getQueueStatus();
        assertEquals(3, queueStatus.pendingCount());
        assertEquals(1, queueStatus.processingCount());
        assertEquals(0, queueStatus.failedCount());
    }
}

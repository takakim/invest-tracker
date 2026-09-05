package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.api.FxController;
import com.takakim.investtracker.domain.FxObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.service.currency.FxRateQuote;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.queue.MarketDataRefreshQueueService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FxControllerTests {

    @Test
    @DisplayName("FxController delegates getRate to FxRateService")
    void testGetRate() {
        FxRateService service = mock(FxRateService.class);
        MarketDataRefreshQueueService queueService = mock(MarketDataRefreshQueueService.class);
        FxController controller = new FxController(service, queueService);

        FxRateQuote quote = new FxRateQuote(
                "USD", "GBP", new BigDecimal("0.74"), Instant.now(),
                ObservationSourceType.PROVIDER, "TWELVE_DATA", false, null
        );
        when(service.getRate("USD", "GBP", null)).thenReturn(quote);

        ApiDtos.FxRateQuoteResponse response = controller.getRate("USD", "GBP", null);
        assertEquals("USD", response.baseCurrency());
        assertEquals("GBP", response.quoteCurrency());
        assertEquals(new BigDecimal("0.74"), response.rate());
    }

    @Test
    @DisplayName("FxController delegates recordRateOverride to FxRateService")
    void testRecordRateOverride() {
        FxRateService service = mock(FxRateService.class);
        FxController controller = new FxController(service);

        Instant now = Instant.now();
        FxObservation obs = new FxObservation("USD", "GBP", new BigDecimal("0.75"), now, ObservationSourceType.MANUAL, "Test Override");
        when(service.recordManualOverride(eq("USD"), eq("GBP"), eq(new BigDecimal("0.75")), any(), eq("Test Override")))
                .thenReturn(obs);

        ApiDtos.FxRateOverrideRequest req = new ApiDtos.FxRateOverrideRequest("USD", "GBP", new BigDecimal("0.75"), now, "Test Override");
        ResponseEntity<ApiDtos.FxObservationResponse> response = controller.recordRateOverride(req);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("USD", response.getBody().baseCurrency());
        assertEquals(new BigDecimal("0.75"), response.getBody().rate());
    }

    @Test
    @DisplayName("FxController delegates getRateHistory to FxRateService")
    void testGetRateHistory() {
        FxRateService service = mock(FxRateService.class);
        FxController controller = new FxController(service);

        FxObservation obs = new FxObservation("USD", "GBP", new BigDecimal("0.75"), Instant.now(), ObservationSourceType.PROVIDER, "TWELVE_DATA");
        when(service.getHistoricalRates(eq("USD"), eq("GBP"), any(), any())).thenReturn(List.of(obs));

        List<ApiDtos.FxObservationResponse> history = controller.getRateHistory("USD", "GBP", null, null);
        assertEquals(1, history.size());
        assertEquals("USD", history.getFirst().baseCurrency());
    }

    @Test
    @DisplayName("POST /currencies/rates/refresh enqueues single pair when base and quote provided")
    void testRefreshRatesSinglePair() {
        FxRateService service = mock(FxRateService.class);
        MarketDataRefreshQueueService queueService = mock(MarketDataRefreshQueueService.class);
        FxController controller = new FxController(service, queueService);

        when(queueService.enqueueFxRate("USD", "GBP")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.refreshRates("USD", "GBP");
        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("ENQUEUED", response.getBody().get("status"));
        assertEquals("USD", response.getBody().get("baseCurrency"));
        assertEquals("GBP", response.getBody().get("quoteCurrency"));
        assertEquals(true, response.getBody().get("enqueued"));
    }

    @Test
    @DisplayName("POST /currencies/rates/refresh enqueues active pairs when no currency specified")
    void testRefreshRatesAllActivePairs() {
        FxRateService service = mock(FxRateService.class);
        MarketDataRefreshQueueService queueService = mock(MarketDataRefreshQueueService.class);
        FxController controller = new FxController(service, queueService);

        when(queueService.enqueueActiveFxPairs()).thenReturn(4);

        ResponseEntity<Map<String, Object>> response = controller.refreshRates(null, null);
        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("ENQUEUED", response.getBody().get("status"));
        assertEquals(4, response.getBody().get("enqueuedCount"));
    }

    @Test
    @DisplayName("POST /currencies/rates/refresh handles null queueService safely")
    void testRefreshRatesNullQueueService() {
        FxRateService service = mock(FxRateService.class);
        FxController controller = new FxController(service);

        ResponseEntity<Map<String, Object>> response = controller.refreshRates(null, null);
        assertEquals(202, response.getStatusCode().value());
        assertEquals(0, response.getBody().get("enqueuedCount"));

        ResponseEntity<Map<String, Object>> pairResponse = controller.refreshRates("USD", "GBP");
        assertEquals(202, pairResponse.getStatusCode().value());
        assertEquals(false, pairResponse.getBody().get("enqueued"));
    }

    @Test
    @DisplayName("POST /currencies/rates/refresh with partial parameters enqueues active pairs")
    void testRefreshRatesPartialParams() {
        FxRateService service = mock(FxRateService.class);
        MarketDataRefreshQueueService queueService = mock(MarketDataRefreshQueueService.class);
        FxController controller = new FxController(service, queueService);

        when(queueService.enqueueActiveFxPairs()).thenReturn(2);

        ResponseEntity<Map<String, Object>> r1 = controller.refreshRates("USD", null);
        assertEquals(202, r1.getStatusCode().value());
        assertEquals(2, r1.getBody().get("enqueuedCount"));

        ResponseEntity<Map<String, Object>> r2 = controller.refreshRates(null, "GBP");
        assertEquals(202, r2.getStatusCode().value());
        assertEquals(2, r2.getBody().get("enqueuedCount"));
    }
}

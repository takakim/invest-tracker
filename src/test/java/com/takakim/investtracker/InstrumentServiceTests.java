package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.service.ConflictException;
import com.takakim.investtracker.service.InstrumentService;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.market.MarketDataService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTests {

    @Mock private InstrumentRepository repository;
    @Mock private MarketObservationRepository marketObservationRepository;
    @Mock private MarketDataService marketDataService;

    private InstrumentService service;

    @BeforeEach
    void setUp() {
        service = new InstrumentService(repository, marketObservationRepository, marketDataService);
    }

    @Test
    @DisplayName("create saves and returns response with manualPriceOnly")
    void createInstrument() {
        ApiDtos.InstrumentRequest req = new ApiDtos.InstrumentRequest(
                "Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", "USD", true
        );
        Instrument saved = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"), true);
        when(repository.existsByIsinIgnoreCase("US0378331005")).thenReturn(false);
        when(repository.save(any(Instrument.class))).thenReturn(saved);

        ApiDtos.InstrumentResponse res = service.create(req);
        assertNotNull(res);
        assertEquals("Apple Inc", res.name());
        assertEquals("AAPL", res.ticker());
        assertTrue(res.manualPriceOnly());
    }

    @Test
    @DisplayName("create throws ConflictException when ISIN exists")
    void createDuplicateIsinThrows() {
        ApiDtos.InstrumentRequest req = new ApiDtos.InstrumentRequest(
                "Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", "USD"
        );
        when(repository.existsByIsinIgnoreCase("US0378331005")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(req));
    }

    @Test
    @DisplayName("update modifies existing instrument")
    void updateInstrument() {
        UUID id = UUID.randomUUID();
        Instrument inst = new Instrument("Old Name", AssetClass.STOCK, "OLD", null, null, new Currency("USD"));
        when(repository.findById(id)).thenReturn(Optional.of(inst));
        when(repository.findByIsin("ISIN123")).thenReturn(Optional.empty());
        when(repository.save(any(Instrument.class))).thenReturn(inst);

        ApiDtos.InstrumentRequest req = new ApiDtos.InstrumentRequest(
                "New Name", AssetClass.ETF, "NEW", "ISIN123", "LSE", "USD"
        );

        ApiDtos.InstrumentResponse res = service.update(id, req);
        assertEquals("New Name", res.name());
        assertEquals(AssetClass.ETF, res.assetClass());
        assertEquals("NEW", res.ticker());
    }

    @Test
    @DisplayName("update throws ResourceNotFoundException if missing")
    void updateMissingThrows() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.update(id, new ApiDtos.InstrumentRequest("A", AssetClass.STOCK, null, null, null, "USD")));
    }

    @Test
    @DisplayName("refreshAllPrices prioritizes stalest instruments and updates quotes")
    void refreshAllPricesPrioritizesStalest() {
        Instrument inst1 = new Instrument("Apple", AssetClass.STOCK, "AAPL", null, null, new Currency("USD"));
        Instrument inst2 = new Instrument("Microsoft", AssetClass.STOCK, "MSFT", null, null, new Currency("USD"));

        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(inst1, inst2));

        // inst1 has recent observation, inst2 has NO observation (should be prioritized first)
        MarketObservation obs1 = new MarketObservation(inst1, new BigDecimal("200.00"), "USD", Instant.now(), ObservationSourceType.PROVIDER, "FEED");
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(inst1.getId()))
                .thenReturn(Optional.of(obs1));
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(inst2.getId()))
                .thenReturn(Optional.empty());

        List<ApiDtos.InstrumentResponse> result = service.refreshAllPrices();
        assertNotNull(result);
        verify(marketDataService).refreshPrice(eq(inst2.getId()));
        verify(marketDataService).refreshPrice(eq(inst1.getId()));
    }

    @Test
    @DisplayName("refreshPrice updates single instrument")
    void refreshSinglePrice() {
        UUID id = UUID.randomUUID();
        Instrument inst = new Instrument("Apple", AssetClass.STOCK, "AAPL", null, null, new Currency("USD"));
        when(repository.findById(id)).thenReturn(Optional.of(inst));

        ApiDtos.InstrumentResponse res = service.refreshPrice(id);
        assertNotNull(res);
        verify(marketDataService).refreshPrice(eq(inst.getId()));
    }

    @Test
    @DisplayName("refreshAllPrices handles null marketObservationRepository")
    void refreshAllPricesNullObsRepo() {
        Instrument inst = new Instrument("Apple", AssetClass.STOCK, "AAPL", null, null, new Currency("USD"));
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(inst));

        InstrumentService noRepoService = new InstrumentService(repository, null, marketDataService);
        List<ApiDtos.InstrumentResponse> res = noRepoService.refreshAllPrices();
        assertNotNull(res);
    }

    @Test
    @DisplayName("update with manualPriceOnly true skips market data refresh")
    void updateManualPriceOnlySkipsRefresh() {
        UUID id = UUID.randomUUID();
        Instrument inst = new Instrument("Manual Asset", AssetClass.STOCK, "MAN", null, null, new Currency("USD"), false);
        when(repository.findById(id)).thenReturn(Optional.of(inst));
        when(repository.save(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiDtos.InstrumentRequest req = new ApiDtos.InstrumentRequest(
                "Manual Asset Updated", AssetClass.STOCK, "MAN", null, null, "USD", true
        );

        ApiDtos.InstrumentResponse res = service.update(id, req);
        assertTrue(res.manualPriceOnly());
    }

    @Test
    @DisplayName("toResponse ignores non-positive or null price in observation")
    void toResponseIgnoresNonPositivePrice() {
        UUID id = UUID.randomUUID();
        Instrument inst = new Instrument("Zero Asset", AssetClass.STOCK, "ZERO", null, null, new Currency("USD"));
        when(repository.findById(id)).thenReturn(Optional.of(inst));

        MarketObservation zeroObs = new MarketObservation(inst, BigDecimal.ZERO, "USD", Instant.now(), ObservationSourceType.PROVIDER, "TEST");
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(inst.getId()))
                .thenReturn(Optional.of(zeroObs));

        ApiDtos.InstrumentResponse res = service.get(id);
        org.junit.jupiter.api.Assertions.assertNull(res.latestPrice());
    }

    @Test
    @DisplayName("refreshAllPrices enqueues all non-manual instruments when refreshQueueService is present")
    void refreshAllPricesWithQueue() {
        var queueService = mock(com.takakim.investtracker.service.market.queue.MarketDataRefreshQueueService.class);
        InstrumentService serviceWithQueue = new InstrumentService(repository, marketObservationRepository, marketDataService, queueService);

        Instrument inst1 = new Instrument("Apple", AssetClass.STOCK, "AAPL", null, null, new Currency("USD"), false);
        Instrument inst2 = new Instrument("Manual Bond", AssetClass.BOND, null, null, null, new Currency("USD"), true);
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(inst1, inst2));

        List<ApiDtos.InstrumentResponse> result = serviceWithQueue.refreshAllPrices();

        assertNotNull(result);
        verify(queueService).enqueueAll(List.of(inst1.getId()));
        verifyNoInteractions(marketDataService);
    }

    @Test
    @DisplayName("refreshPrice enqueues instrument when refreshQueueService is present")
    void refreshPriceWithQueue() {
        var queueService = mock(com.takakim.investtracker.service.market.queue.MarketDataRefreshQueueService.class);
        InstrumentService serviceWithQueue = new InstrumentService(repository, marketObservationRepository, marketDataService, queueService);

        Instrument inst = new Instrument("Apple", AssetClass.STOCK, "AAPL", null, null, new Currency("USD"), false);
        UUID id = inst.getId();
        when(repository.findById(id)).thenReturn(Optional.of(inst));

        ApiDtos.InstrumentResponse res = serviceWithQueue.refreshPrice(id);

        assertNotNull(res);
        verify(queueService).enqueue(id);
        verifyNoInteractions(marketDataService);
    }

    @Test
    @DisplayName("getQueueStatus delegates to refreshQueueService")
    void testGetQueueStatus() {
        var queueService = mock(com.takakim.investtracker.service.market.queue.MarketDataRefreshQueueService.class);
        when(queueService.getQueueStatus()).thenReturn(new com.takakim.investtracker.service.market.queue.MarketDataRefreshQueueService.QueueStatus(3, 1, 0));
        InstrumentService serviceWithQueue = new InstrumentService(repository, marketObservationRepository, marketDataService, queueService);

        var status = serviceWithQueue.getQueueStatus();
        assertEquals(3, status.pendingCount());
        assertEquals(1, status.processingCount());
        assertEquals(0, status.failedCount());

        // When queue service is null
        var fallbackStatus = service.getQueueStatus();
        assertEquals(0, fallbackStatus.pendingCount());
    }
}

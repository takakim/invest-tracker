package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketDataRefreshTask;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.domain.RefreshTaskStatus;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketDataRefreshTaskRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.market.queue.MarketDataRefreshQueueService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MarketDataRefreshQueueServiceTests {

    private MarketDataRefreshTaskRepository repository;
    private InstrumentRepository instrumentRepository;
    private MarketDataService marketDataService;
    private MarketDataProperties properties;
    private MarketDataRefreshQueueService queueService;

    private Instrument aapl;
    private Instrument manualInst;

    @BeforeEach
    void setUp() {
        repository = mock(MarketDataRefreshTaskRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        marketDataService = mock(MarketDataService.class);
        properties = new MarketDataProperties();

        queueService = new MarketDataRefreshQueueService(repository, instrumentRepository, marketDataService, properties);

        aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"), false);
        manualInst = new Instrument("Private Bond", AssetClass.BOND, null, null, null, new Currency("USD"), true);
    }

    @Test
    @DisplayName("Successfully enqueues instrument when no active task exists")
    void testEnqueueSuccess() {
        UUID id = aapl.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(aapl));
        when(repository.existsActiveByInstrumentId(eq(id), any())).thenReturn(false);

        boolean enqueued = queueService.enqueue(id);

        assertTrue(enqueued);
        ArgumentCaptor<MarketDataRefreshTask> captor = ArgumentCaptor.forClass(MarketDataRefreshTask.class);
        verify(repository).save(captor.capture());

        MarketDataRefreshTask task = captor.getValue();
        assertEquals(aapl, task.getInstrument());
        assertEquals(RefreshTaskStatus.PENDING, task.getStatus());
        assertEquals(0, task.getAttemptCount());
        assertEquals(5, task.getMaxAttempts());
        assertNotNull(task.getScheduledAt());
    }

    @Test
    @DisplayName("Ignores duplicate enqueue when active task already exists in queue")
    void testEnqueueDuplicateIgnored() {
        UUID id = aapl.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(aapl));
        when(repository.existsActiveByInstrumentId(eq(id), any())).thenReturn(true);

        boolean enqueued = queueService.enqueue(id);

        assertFalse(enqueued);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Skips enqueue for manual price only instrument")
    void testEnqueueManualPriceOnlySkipped() {
        UUID id = manualInst.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(manualInst));

        boolean enqueued = queueService.enqueue(id);

        assertFalse(enqueued);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Enqueue with null id returns false")
    void testEnqueueNullReturnsFalse() {
        assertFalse(queueService.enqueue(null));
        verifyNoInteractions(instrumentRepository);
    }

    @Test
    @DisplayName("Enqueue with unknown instrument throws ResourceNotFoundException")
    void testEnqueueUnknownInstrumentThrows() {
        UUID unknown = UUID.randomUUID();
        when(instrumentRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> queueService.enqueue(unknown));
    }

    @Test
    @DisplayName("enqueueAll enqueues collection of instruments")
    void testEnqueueAll() {
        Instrument msft = new Instrument("Microsoft", AssetClass.STOCK, "MSFT", null, null, new Currency("USD"), false);
        when(instrumentRepository.findById(aapl.getId())).thenReturn(Optional.of(aapl));
        when(instrumentRepository.findById(msft.getId())).thenReturn(Optional.of(msft));
        when(repository.existsActiveByInstrumentId(any(), any())).thenReturn(false);

        int count = queueService.enqueueAll(List.of(aapl.getId(), msft.getId()));
        assertEquals(2, count);
        verify(repository, times(2)).save(any(MarketDataRefreshTask.class));

        assertEquals(0, queueService.enqueueAll(null));
        assertEquals(0, queueService.enqueueAll(List.of()));
    }

    @Test
    @DisplayName("processNextDueTask returns false when queue is empty")
    void testProcessNextDueTaskEmpty() {
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.empty());

        boolean processed = queueService.processNextDueTask();

        assertFalse(processed);
        verifyNoInteractions(marketDataService);
    }

    @Test
    @DisplayName("processNextDueTask deletes task upon successful live quote retrieval")
    void testProcessNextDueTaskSuccessDeletesTask() {
        MarketDataRefreshTask task = new MarketDataRefreshTask(aapl, Instant.now().minusSeconds(10));
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);

        PriceQuote liveQuote = new PriceQuote(
                aapl.getId(), new BigDecimal("225.50"), "USD",
                Instant.now(), ObservationSourceType.PROVIDER, "TWELVE_DATA", false, null
        );
        when(marketDataService.refreshLivePrice(aapl.getId())).thenReturn(Optional.of(liveQuote));

        boolean result = queueService.processNextDueTask();

        assertTrue(result);
        verify(repository).delete(task);
        verify(repository, never()).save(task);
    }

    @Test
    @DisplayName("processNextDueTask reschedules task with 30s delay when live quote is throttled")
    void testProcessNextDueTaskThrottledReschedulesWith30sDelay() {
        Instant before = Instant.now();
        MarketDataRefreshTask task = new MarketDataRefreshTask(aapl, before.minusSeconds(10));
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);

        // Simulated rate-limited/throttled response
        when(marketDataService.refreshLivePrice(aapl.getId())).thenReturn(Optional.empty());

        boolean result = queueService.processNextDueTask();

        assertTrue(result);
        verify(repository, never()).delete(task);
        verify(repository).save(task);

        assertEquals(1, task.getAttemptCount());
        assertEquals(RefreshTaskStatus.PENDING, task.getStatus());
        assertTrue(task.getScheduledAt().isAfter(before.plusSeconds(25)));
        assertTrue(task.getScheduledAt().isBefore(before.plusSeconds(35)));
        assertTrue(task.getLastError().contains("throttled"));
    }

    @Test
    @DisplayName("processNextDueTask marks task as FAILED when max attempts are exceeded")
    void testProcessNextDueTaskMaxAttemptsMarksFailed() {
        MarketDataRefreshTask task = new MarketDataRefreshTask(aapl, Instant.now().minusSeconds(10), 3);
        task.setAttemptCount(2); // this attempt will make it 3 (maxAttempts)
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);

        when(marketDataService.refreshLivePrice(aapl.getId())).thenReturn(Optional.empty());

        boolean result = queueService.processNextDueTask();

        assertTrue(result);
        verify(repository, never()).delete(task);
        verify(repository).save(task);

        assertEquals(3, task.getAttemptCount());
        assertEquals(RefreshTaskStatus.FAILED, task.getStatus());
        assertTrue(task.getLastError().contains("Max retry attempts (3) reached"));
    }

    @Test
    @DisplayName("processNextDueTask handles unexpected exception by rescheduling with backoff")
    void testProcessNextDueTaskExceptionReschedules() {
        MarketDataRefreshTask task = new MarketDataRefreshTask(aapl, Instant.now().minusSeconds(10));
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);

        when(marketDataService.refreshLivePrice(aapl.getId())).thenThrow(new RuntimeException("Connection reset"));

        boolean result = queueService.processNextDueTask();

        assertTrue(result);
        verify(repository, never()).delete(task);
        verify(repository).save(task);

        assertEquals(1, task.getAttemptCount());
        assertEquals(RefreshTaskStatus.PENDING, task.getStatus());
        assertTrue(task.getLastError().contains("Connection reset"));
    }

    @Test
    @DisplayName("getQueueStatus returns counts from repository")
    void testGetQueueStatus() {
        when(repository.countByStatus(RefreshTaskStatus.PENDING)).thenReturn(5L);
        when(repository.countByStatus(RefreshTaskStatus.PROCESSING)).thenReturn(1L);
        when(repository.countByStatus(RefreshTaskStatus.FAILED)).thenReturn(2L);

        var status = queueService.getQueueStatus();

        assertEquals(5L, status.pendingCount());
        assertEquals(1L, status.processingCount());
        assertEquals(2L, status.failedCount());
    }

    @Test
    @DisplayName("processNextDueTask uses instrument name when ticker is null or blank")
    void testProcessNextDueTaskInstrumentWithoutTicker() {
        Instrument noTicker = new Instrument("Gold Bullion", AssetClass.ETF, "  ", null, null, new Currency("USD"), false);
        MarketDataRefreshTask task = new MarketDataRefreshTask(noTicker, Instant.now().minusSeconds(10));
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);
        when(marketDataService.refreshLivePrice(noTicker.getId())).thenReturn(Optional.empty());

        boolean result = queueService.processNextDueTask();

        assertTrue(result);
        verify(repository).save(task);
        assertEquals(1, task.getAttemptCount());
    }

    @Test
    @DisplayName("enqueue and failure retry use default values when refreshQueue properties is null")
    void testEnqueueAndRetryWithNullProperties() {
        MarketDataProperties nullProps = new MarketDataProperties();
        nullProps.setRefreshQueue(null);
        MarketDataRefreshQueueService serviceWithNullProps = new MarketDataRefreshQueueService(
                repository, instrumentRepository, marketDataService, nullProps);

        UUID id = aapl.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(aapl));
        when(repository.existsActiveByInstrumentId(eq(id), any())).thenReturn(false);

        boolean enqueued = serviceWithNullProps.enqueue(id);
        assertTrue(enqueued);

        MarketDataRefreshTask task = new MarketDataRefreshTask(aapl, Instant.now().minusSeconds(10));
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);
        when(marketDataService.refreshLivePrice(aapl.getId())).thenReturn(Optional.empty());

        boolean processed = serviceWithNullProps.processNextDueTask();
        assertTrue(processed);
        assertEquals(RefreshTaskStatus.PENDING, task.getStatus());
    }
}

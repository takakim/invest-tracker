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
import java.util.Set;
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
    private com.takakim.investtracker.service.currency.FxRateService fxRateService;
    private com.takakim.investtracker.repository.PortfolioRepository portfolioRepository;
    private com.takakim.investtracker.repository.AccountRepository accountRepository;
    private MarketDataRefreshQueueService queueService;

    private Instrument aapl;
    private Instrument manualInst;

    @BeforeEach
    void setUp() {
        repository = mock(MarketDataRefreshTaskRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        marketDataService = mock(MarketDataService.class);
        properties = new MarketDataProperties();
        fxRateService = mock(com.takakim.investtracker.service.currency.FxRateService.class);
        portfolioRepository = mock(com.takakim.investtracker.repository.PortfolioRepository.class);
        accountRepository = mock(com.takakim.investtracker.repository.AccountRepository.class);

        queueService = new MarketDataRefreshQueueService(
                repository, instrumentRepository, marketDataService, properties,
                fxRateService, portfolioRepository, accountRepository);

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

    @Test
    @DisplayName("enqueueFxRate successfully creates task for valid currency pair")
    void testEnqueueFxRateSuccess() {
        when(repository.existsActiveByCurrencyPair(eq("USD"), eq("GBP"), any())).thenReturn(false);

        boolean result = queueService.enqueueFxRate("USD", "GBP");

        assertTrue(result);
        verify(repository).save(argThat(task ->
                "USD".equals(task.getBaseCurrency()) &&
                "GBP".equals(task.getQuoteCurrency()) &&
                task.isFxTask() &&
                task.getInstrument() == null
        ));
    }

    @Test
    @DisplayName("enqueueFxRate returns false when currencies are null, identical, or GBX")
    void testEnqueueFxRateSkipped() {
        assertFalse(queueService.enqueueFxRate(null, "GBP"));
        assertFalse(queueService.enqueueFxRate("USD", null));
        assertFalse(queueService.enqueueFxRate("USD", "USD"));
        assertFalse(queueService.enqueueFxRate("GBX", "GBP"));
        assertFalse(queueService.enqueueFxRate("GBP", "GBX"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("enqueueFxRate skips when active task already exists in queue")
    void testEnqueueFxRateDuplicate() {
        when(repository.existsActiveByCurrencyPair(eq("USD"), eq("GBP"), any())).thenReturn(true);

        boolean result = queueService.enqueueFxRate("USD", "GBP");

        assertFalse(result);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("enqueueActiveFxPairs enqueues unique pairs across portfolios, instruments, and accounts")
    void testEnqueueActiveFxPairs() {
        when(portfolioRepository.findDistinctBaseCurrencies()).thenReturn(List.of("GBP"));
        when(instrumentRepository.findDistinctCurrencies()).thenReturn(List.of("USD", "GBX", "GBP"));
        when(accountRepository.findDistinctAccountCurrencies()).thenReturn(List.of("EUR", "GBP"));
        when(repository.existsActiveByCurrencyPair(anyString(), anyString(), any())).thenReturn(false);

        int count = queueService.enqueueActiveFxPairs();

        // USD/GBP, GBP/USD, EUR/GBP, GBP/EUR = 4 tasks
        assertEquals(4, count);
        verify(repository, times(4)).save(any());
    }

    @Test
    @DisplayName("enqueueStaleActiveFxPairs enqueues only pairs that are not fresh")
    void testEnqueueStaleActiveFxPairs() {
        when(portfolioRepository.findDistinctBaseCurrencies()).thenReturn(List.of("GBP"));
        when(instrumentRepository.findDistinctCurrencies()).thenReturn(List.of("USD"));
        when(accountRepository.findDistinctAccountCurrencies()).thenReturn(List.of());

        // USD/GBP is stale (false), GBP/USD is fresh (true)
        when(fxRateService.isRateFresh(eq("USD"), eq("GBP"), any())).thenReturn(false);
        when(fxRateService.isRateFresh(eq("GBP"), eq("USD"), any())).thenReturn(true);
        when(repository.existsActiveByCurrencyPair(eq("USD"), eq("GBP"), any())).thenReturn(false);

        int count = queueService.enqueueStaleActiveFxPairs(java.time.Duration.ofMinutes(15));

        assertEquals(1, count);
        verify(repository, times(1)).save(any());
    }

    @Test
    @DisplayName("processNextDueTask successfully executes FX task and deletes it on success")
    void testProcessNextDueTaskFxSuccess() {
        MarketDataRefreshTask task = new MarketDataRefreshTask("USD", "GBP", Instant.now().minusSeconds(10));
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);

        com.takakim.investtracker.service.currency.FxRateQuote quote = new com.takakim.investtracker.service.currency.FxRateQuote(
                "USD", "GBP", new BigDecimal("0.74"), Instant.now(),
                com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "TWELVE_DATA", false, null
        );
        when(fxRateService.refreshRate("USD", "GBP")).thenReturn(Optional.of(quote));

        boolean processed = queueService.processNextDueTask();

        assertTrue(processed);
        verify(repository).delete(task);
    }

    @Test
    @DisplayName("processNextDueTask schedules retry when FX provider returns empty or fails")
    void testProcessNextDueTaskFxFailureAndRetry() {
        MarketDataRefreshTask task = new MarketDataRefreshTask("USD", "GBP", Instant.now().minusSeconds(10), 3);
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);
        when(fxRateService.refreshRate("USD", "GBP")).thenReturn(Optional.empty());

        boolean processed = queueService.processNextDueTask();

        assertTrue(processed);
        verify(repository).save(task);
        assertEquals(1, task.getAttemptCount());
        assertEquals(RefreshTaskStatus.PENDING, task.getStatus());
    }

    @Test
    @DisplayName("processNextDueTask marks FX task as FAILED when max attempts reached")
    void testProcessNextDueTaskFxMaxAttemptsExceeded() {
        MarketDataRefreshTask task = new MarketDataRefreshTask("USD", "GBP", Instant.now().minusSeconds(10), 1);
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);
        when(fxRateService.refreshRate("USD", "GBP")).thenThrow(new RuntimeException("API error"));

        boolean processed = queueService.processNextDueTask();

        assertTrue(processed);
        verify(repository).save(task);
        assertEquals(1, task.getAttemptCount());
        assertEquals(RefreshTaskStatus.FAILED, task.getStatus());
    }

    @Test
    @DisplayName("enqueueStaleActiveFxPairs returns 0 when fxRateService is null")
    void testEnqueueStaleActiveFxPairsNullFxService() {
        MarketDataRefreshQueueService serviceWithNulls = new MarketDataRefreshQueueService(
                repository, instrumentRepository, marketDataService, properties);

        int result = serviceWithNulls.enqueueStaleActiveFxPairs(java.time.Duration.ofMinutes(15));
        assertEquals(0, result);
    }

    @Test
    @DisplayName("processNextDueTask handles FX task when fxRateService is null")
    void testProcessNextDueTaskFxNullService() {
        MarketDataRefreshQueueService serviceWithNulls = new MarketDataRefreshQueueService(
                repository, instrumentRepository, marketDataService, properties);

        MarketDataRefreshTask task = new MarketDataRefreshTask("USD", "GBP", Instant.now().minusSeconds(10));
        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);

        boolean processed = serviceWithNulls.processNextDueTask();
        assertTrue(processed);
        verify(repository).save(task);
        assertEquals(1, task.getAttemptCount());
    }

    @Test
    @DisplayName("processNextDueTask handles instrument task when instrument is null")
    void testProcessNextDueTaskNullInstrument() {
        MarketDataRefreshTask task = mock(MarketDataRefreshTask.class);
        when(task.isFxTask()).thenReturn(false);
        when(task.getInstrument()).thenReturn(null);

        when(repository.findNextDueTaskForUpdate(any())).thenReturn(Optional.of(task));
        when(repository.saveAndFlush(any())).thenReturn(task);

        boolean processed = queueService.processNextDueTask();
        assertTrue(processed);
        verify(repository).delete(task);
    }

    @Test
    @DisplayName("getActiveCurrencyPairs handles null and blank entries and GBX base currency")
    void testGetActiveCurrencyPairsBlankAndSubUnit() {
        when(portfolioRepository.findDistinctBaseCurrencies()).thenReturn(new java.util.ArrayList<>(java.util.Arrays.asList("GBX", " ", null, "GBP")));
        when(instrumentRepository.findDistinctCurrencies()).thenReturn(new java.util.ArrayList<>(java.util.Arrays.asList("USD", " ", null, "GBP")));
        when(accountRepository.findDistinctAccountCurrencies()).thenReturn(new java.util.ArrayList<>(java.util.Arrays.asList("EUR")));

        Set<MarketDataRefreshQueueService.CurrencyPair> pairs = queueService.getActiveCurrencyPairs();
        assertNotNull(pairs);
        assertFalse(pairs.isEmpty());
    }

    @Test
    @DisplayName("getActiveCurrencyPairs uses default GBP when portfolioRepository is null")
    void testGetActiveCurrencyPairsNullPortfolioRepo() {
        MarketDataRefreshQueueService serviceWithNulls = new MarketDataRefreshQueueService(
                repository, instrumentRepository, marketDataService, properties);

        Set<MarketDataRefreshQueueService.CurrencyPair> pairs = serviceWithNulls.getActiveCurrencyPairs();
        assertNotNull(pairs);
        assertTrue(pairs.isEmpty());
    }
}

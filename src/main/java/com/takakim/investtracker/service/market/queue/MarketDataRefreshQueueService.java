package com.takakim.investtracker.service.market.queue;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketDataRefreshTask;
import com.takakim.investtracker.domain.RefreshTaskStatus;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketDataRefreshTaskRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketDataRefreshQueueService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataRefreshQueueService.class);
    private static final Set<RefreshTaskStatus> ACTIVE_STATUSES = Set.of(RefreshTaskStatus.PENDING, RefreshTaskStatus.PROCESSING);

    private final MarketDataRefreshTaskRepository repository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataService marketDataService;
    private final MarketDataProperties properties;

    public record QueueStatus(long pendingCount, long processingCount, long failedCount) {}

    public MarketDataRefreshQueueService(
            MarketDataRefreshTaskRepository repository,
            InstrumentRepository instrumentRepository,
            MarketDataService marketDataService,
            MarketDataProperties properties) {
        this.repository = repository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataService = marketDataService;
        this.properties = properties;
    }

    @Transactional
    public boolean enqueue(UUID instrumentId) {
        if (instrumentId == null) {
            return false;
        }
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));

        if (instrument.isManualPriceOnly()) {
            log.debug("Skipping enqueue for instrument '{}' marked as manual price only", instrument.getName());
            return false;
        }

        if (repository.existsActiveByInstrumentId(instrumentId, ACTIVE_STATUSES)) {
            log.debug("Instrument '{}' already has an active refresh task in queue; skipping duplicate", instrument.getName());
            return false;
        }

        int maxAttempts = properties.getRefreshQueue() != null ? properties.getRefreshQueue().getMaxAttempts() : MarketDataRefreshTask.DEFAULT_MAX_ATTEMPTS;
        MarketDataRefreshTask task = new MarketDataRefreshTask(instrument, Instant.now(), maxAttempts);
        repository.save(task);
        log.info("Enqueued market data refresh task for instrument '{}' (symbol: '{}')", instrument.getName(), instrument.getTicker());
        return true;
    }

    @Transactional
    public int enqueueAll(Collection<UUID> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (UUID id : instrumentIds) {
            if (enqueue(id)) {
                count++;
            }
        }
        return count;
    }

    @Transactional
    public boolean processNextDueTask() {
        Optional<MarketDataRefreshTask> taskOpt = repository.findNextDueTaskForUpdate(Instant.now());
        if (taskOpt.isEmpty()) {
            return false;
        }

        MarketDataRefreshTask task = taskOpt.get();
        task.setStatus(RefreshTaskStatus.PROCESSING);
        task = repository.saveAndFlush(task);

        Instrument instrument = task.getInstrument();
        UUID instrumentId = instrument.getId();
        String symbol = instrument.getTicker() != null && !instrument.getTicker().isBlank() ? instrument.getTicker() : instrument.getName();

        try {
            Optional<PriceQuote> quoteOpt = marketDataService.refreshLivePrice(instrumentId);
            if (quoteOpt.isPresent()) {
                PriceQuote quote = quoteOpt.get();
                repository.delete(task);
                log.info("Successfully refreshed live market price for instrument '{}' ({}) via {}: {} {}. Task completed and removed from queue.",
                        instrument.getName(), symbol, quote.sourceReference(), quote.price(), quote.currency());
                return true;
            } else {
                handleTaskFailure(task, symbol, "Providers throttled or returned no live data");
                return true;
            }
        } catch (Exception ex) {
            handleTaskFailure(task, symbol, "Exception during quote fetch: " + ex.getMessage());
            return true;
        }
    }

    private void handleTaskFailure(MarketDataRefreshTask task, String symbol, String reason) {
        task.incrementAttemptCount();
        int maxAttempts = task.getMaxAttempts();

        if (task.getAttemptCount() >= maxAttempts) {
            task.setStatus(RefreshTaskStatus.FAILED);
            task.setLastError("Max retry attempts (" + maxAttempts + ") reached: " + reason);
            repository.save(task);
            log.warn("Market data refresh task for instrument '{}' exceeded max attempts ({}). Marked as FAILED: {}",
                    symbol, maxAttempts, reason);
        } else {
            int delaySeconds = properties.getRefreshQueue() != null ? properties.getRefreshQueue().getRetryDelaySeconds() : 30;
            task.setStatus(RefreshTaskStatus.PENDING);
            task.setScheduledAt(Instant.now().plus(Duration.ofSeconds(delaySeconds)));
            task.setLastError(reason);
            repository.save(task);
            log.warn("Live quote unavailable/throttled for instrument '{}' (attempt {}/{}). Delayed retry scheduled in {}s: {}",
                    symbol, task.getAttemptCount(), maxAttempts, delaySeconds, reason);
        }
    }

    @Transactional(readOnly = true)
    public QueueStatus getQueueStatus() {
        long pending = repository.countByStatus(RefreshTaskStatus.PENDING);
        long processing = repository.countByStatus(RefreshTaskStatus.PROCESSING);
        long failed = repository.countByStatus(RefreshTaskStatus.FAILED);
        return new QueueStatus(pending, processing, failed);
    }
}

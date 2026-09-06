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
    private final com.takakim.investtracker.service.currency.FxRateService fxRateService;
    private final com.takakim.investtracker.repository.PortfolioRepository portfolioRepository;
    private final com.takakim.investtracker.repository.AccountRepository accountRepository;

    public record QueueStatus(long pendingCount, long processingCount, long failedCount) {}
    public record CurrencyPair(String baseCurrency, String quoteCurrency) {}

    @org.springframework.beans.factory.annotation.Autowired
    public MarketDataRefreshQueueService(
            MarketDataRefreshTaskRepository repository,
            InstrumentRepository instrumentRepository,
            MarketDataService marketDataService,
            MarketDataProperties properties,
            com.takakim.investtracker.service.currency.FxRateService fxRateService,
            com.takakim.investtracker.repository.PortfolioRepository portfolioRepository,
            com.takakim.investtracker.repository.AccountRepository accountRepository) {
        this.repository = repository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataService = marketDataService;
        this.properties = properties;
        this.fxRateService = fxRateService;
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
    }

    public MarketDataRefreshQueueService(
            MarketDataRefreshTaskRepository repository,
            InstrumentRepository instrumentRepository,
            MarketDataService marketDataService,
            MarketDataProperties properties) {
        this(repository, instrumentRepository, marketDataService, properties, null, null, null);
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
    public boolean enqueueFxRate(String baseCurrency, String quoteCurrency) {
        if (baseCurrency == null || quoteCurrency == null) {
            return false;
        }
        String base = baseCurrency.trim().toUpperCase();
        String quote = quoteCurrency.trim().toUpperCase();
        if (base.equals(quote) || "GBX".equals(base) || "GBX".equals(quote)) {
            return false;
        }

        if (repository.existsActiveByCurrencyPair(base, quote, ACTIVE_STATUSES)) {
            log.debug("FX pair '{}/{}' already has an active refresh task in queue; skipping duplicate", base, quote);
            return false;
        }

        int maxAttempts = properties.getRefreshQueue() != null ? properties.getRefreshQueue().getMaxAttempts() : MarketDataRefreshTask.DEFAULT_MAX_ATTEMPTS;
        MarketDataRefreshTask task = new MarketDataRefreshTask(base, quote, Instant.now(), maxAttempts);
        repository.save(task);
        log.info("Enqueued FX rate refresh task for pair '{}/{}'", base, quote);
        return true;
    }

    public Set<CurrencyPair> getActiveCurrencyPairs() {
        Set<String> baseCurrencies = new java.util.HashSet<>();
        if (portfolioRepository != null) {
            baseCurrencies.addAll(portfolioRepository.findDistinctBaseCurrencies());
        }
        if (baseCurrencies.isEmpty()) {
            baseCurrencies.add("GBP");
        }

        Set<String> foreignCurrencies = new java.util.HashSet<>();
        if (instrumentRepository != null) {
            foreignCurrencies.addAll(instrumentRepository.findDistinctCurrencies());
        }
        if (accountRepository != null) {
            foreignCurrencies.addAll(accountRepository.findDistinctAccountCurrencies());
        }

        Set<CurrencyPair> activePairs = new java.util.LinkedHashSet<>();
        for (String base : baseCurrencies) {
            if (base == null || base.isBlank()) continue;
            String normalizedBase = base.trim().toUpperCase();
            for (String foreign : foreignCurrencies) {
                if (foreign == null || foreign.isBlank()) continue;
                String normalizedForeign = foreign.trim().toUpperCase();
                if (normalizedForeign.equals(normalizedBase)) continue;
                if ("GBX".equals(normalizedForeign) || "GBX".equals(normalizedBase)) continue;

                activePairs.add(new CurrencyPair(normalizedForeign, normalizedBase));
                activePairs.add(new CurrencyPair(normalizedBase, normalizedForeign));
            }
        }
        return activePairs;
    }

    @Transactional
    public int enqueueActiveFxPairs() {
        Set<CurrencyPair> pairs = getActiveCurrencyPairs();
        int count = 0;
        for (CurrencyPair pair : pairs) {
            if (enqueueFxRate(pair.baseCurrency(), pair.quoteCurrency())) {
                count++;
            }
        }
        return count;
    }

    @Transactional
    public int enqueueStaleActiveFxPairs(Duration maxAge) {
        if (fxRateService == null) {
            return 0;
        }
        Set<CurrencyPair> pairs = getActiveCurrencyPairs();
        int count = 0;
        for (CurrencyPair pair : pairs) {
            if (!fxRateService.isRateFresh(pair.baseCurrency(), pair.quoteCurrency(), maxAge)) {
                if (enqueueFxRate(pair.baseCurrency(), pair.quoteCurrency())) {
                    count++;
                }
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

        if (task.isFxTask()) {
            return processFxTask(task);
        } else {
            return processInstrumentTask(task);
        }
    }

    private boolean processFxTask(MarketDataRefreshTask task) {
        String base = task.getBaseCurrency();
        String quote = task.getQuoteCurrency();
        String pair = base + "/" + quote;

        if (fxRateService == null) {
            handleTaskFailure(task, pair, "FxRateService is not available");
            return true;
        }

        try {
            Optional<com.takakim.investtracker.service.currency.FxRateQuote> quoteOpt = fxRateService.refreshRate(base, quote);
            if (quoteOpt.isPresent()) {
                com.takakim.investtracker.service.currency.FxRateQuote q = quoteOpt.get();
                repository.delete(task);
                log.info("Successfully refreshed live FX rate for pair '{}' via {}: {}. Task completed and removed from queue.",
                        pair, q.sourceReference(), q.rate());
                return true;
            } else {
                handleTaskFailure(task, pair, "FX provider returned no live rate or was throttled");
                return true;
            }
        } catch (Exception ex) {
            handleTaskFailure(task, pair, "Exception during FX quote fetch: " + ex.getMessage());
            return true;
        }
    }

    private boolean processInstrumentTask(MarketDataRefreshTask task) {
        Instrument instrument = task.getInstrument();
        if (instrument == null) {
            repository.delete(task);
            return true;
        }
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
            log.warn("Market data refresh task for '{}' exceeded max attempts ({}). Marked as FAILED: {}",
                    symbol, maxAttempts, reason);
        } else {
            int delaySeconds = properties.getRefreshQueue() != null ? properties.getRefreshQueue().getRetryDelaySeconds() : 30;
            task.setStatus(RefreshTaskStatus.PENDING);
            task.setScheduledAt(Instant.now().plus(Duration.ofSeconds(delaySeconds)));
            task.setLastError(reason);
            repository.save(task);
            log.warn("Live quote unavailable/throttled for '{}' (attempt {}/{}). Delayed retry scheduled in {}s: {}",
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

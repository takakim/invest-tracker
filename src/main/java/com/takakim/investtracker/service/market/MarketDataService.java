package com.takakim.investtracker.service.market;

import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(noRollbackFor = {ResourceNotFoundException.class})
public class MarketDataService {

    private static final Duration STALE_THRESHOLD = Duration.ofHours(24);
    private static final Duration FRESHNESS_THRESHOLD = Duration.ofMinutes(5);

    private final MarketObservationRepository marketObservationRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final com.takakim.investtracker.repository.TransactionRepository transactionRepository;
    private final ObservationStorageService observationStorageService;

    @org.springframework.beans.factory.annotation.Autowired
    public MarketDataService(
            MarketObservationRepository marketObservationRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            com.takakim.investtracker.repository.TransactionRepository transactionRepository,
            ObservationStorageService observationStorageService) {
        this.marketObservationRepository = marketObservationRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.transactionRepository = transactionRepository;
        this.observationStorageService = observationStorageService;
    }

    public MarketDataService(
            MarketObservationRepository marketObservationRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            com.takakim.investtracker.repository.TransactionRepository transactionRepository) {
        this(marketObservationRepository, instrumentRepository, marketDataProvider, transactionRepository, null);
    }

    public static boolean isWeekendMarketClosed(Instant targetTime, com.takakim.investtracker.domain.AssetClass assetClass) {
        if (assetClass == com.takakim.investtracker.domain.AssetClass.CRYPTO) {
            return false; // Crypto trades 24/7/365
        }
        java.time.ZonedDateTime zdt = (targetTime != null ? targetTime : Instant.now()).atZone(java.time.ZoneOffset.UTC);
        java.time.DayOfWeek day = zdt.getDayOfWeek();
        return day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY || (day == java.time.DayOfWeek.MONDAY && zdt.getHour() < 8);
    }

    public static boolean isObservationFresh(Instant observedAt, Instant targetTime, com.takakim.investtracker.domain.AssetClass assetClass) {
        if (observedAt == null || targetTime == null) {
            return false;
        }
        Duration diff = Duration.between(observedAt, targetTime).abs();
        if (diff.compareTo(FRESHNESS_THRESHOLD) <= 0) {
            return true;
        }
        if (isWeekendMarketClosed(targetTime, assetClass)) {
            return diff.compareTo(Duration.ofHours(80)) <= 0;
        }
        return false;
    }

    public static boolean isObservationStale(Instant observedAt, Instant targetTime, com.takakim.investtracker.domain.AssetClass assetClass) {
        if (observedAt == null || targetTime == null) {
            return true;
        }
        Duration diff = Duration.between(observedAt, targetTime).abs();
        if (isWeekendMarketClosed(targetTime, assetClass)) {
            return diff.compareTo(Duration.ofHours(80)) > 0;
        }
        return diff.compareTo(STALE_THRESHOLD) > 0;
    }

    public PriceQuote getLatestPrice(UUID instrumentId, Instant asOf) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));

        Instant targetTime = asOf != null ? asOf : Instant.now();

        // 1. Check for manual override first
        Optional<MarketObservation> manualOpt = marketObservationRepository
                .findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(instrumentId, ObservationSourceType.MANUAL);

        if (manualOpt.isPresent()) {
            MarketObservation manual = manualOpt.get();
            boolean isStale = isObservationStale(manual.getObservedAt(), targetTime, instrument.getAssetClass());
            String warning = isStale ? "Manual price override is older than 24 hours" : null;
            return new PriceQuote(
                    instrumentId,
                    manual.getPrice(),
                    manual.getCurrency(),
                    manual.getObservedAt(),
                    ObservationSourceType.MANUAL,
                    manual.getSourceReference() != null ? manual.getSourceReference() : "MANUAL_OVERRIDE",
                    isStale,
                    warning
            );
        }

        // 2. Check for latest persisted observation in DB on or before targetTime
        Optional<MarketObservation> latestPersisted = marketObservationRepository
                .findFirstByInstrumentIdOrderByObservedAtDesc(instrumentId);

        if (latestPersisted.isPresent()) {
            MarketObservation obs = latestPersisted.get();
            if (asOf != null && obs.getObservedAt().isAfter(targetTime)) {
                latestPersisted = marketObservationRepository.findFirstByInstrumentIdAndObservedAtBefore(instrumentId, targetTime);
                obs = latestPersisted.orElse(null);
            }
            if (obs != null && obs.getPrice() != null && obs.getPrice().compareTo(BigDecimal.ZERO) > 0
                    && (instrument.getCurrency() == null || instrument.getCurrency().code().equalsIgnoreCase(obs.getCurrency()))) {
                boolean isStale = isObservationStale(obs.getObservedAt(), targetTime, instrument.getAssetClass());
                String warning = isStale ? "Observation is older than 24 hours (last observed " + obs.getObservedAt() + ")" : null;
                BigDecimal effectivePrice = adjustPriceForCorporateActions(instrumentId, obs.getPrice(), obs.getObservedAt(), targetTime);
                return new PriceQuote(
                        instrumentId,
                        effectivePrice,
                        obs.getCurrency(),
                        obs.getObservedAt(),
                        obs.getSourceType(),
                        obs.getSourceReference(),
                        isStale,
                        warning
                );
            }
        }

        // 3. Fallback to latest transaction trade price on or before targetTime if available
        List<com.takakim.investtracker.domain.Transaction> txs = transactionRepository.findByInstrumentIdOrderByTradeDateDesc(instrumentId);
        for (com.takakim.investtracker.domain.Transaction tx : txs) {
            if (tx.getPrice() != null && tx.getPrice().compareTo(BigDecimal.ZERO) > 0
                    && (asOf == null || !tx.getTradeDate().isAfter(targetTime))) {
                boolean isStale = isObservationStale(tx.getTradeDate(), targetTime, instrument.getAssetClass());
                String warning = "Live price unavailable; using last transaction trade price from " + tx.getTradeDate();
                BigDecimal effectivePrice = adjustPriceForCorporateActions(instrumentId, tx.getPrice(), tx.getTradeDate(), targetTime);
                PriceQuote quote = new PriceQuote(
                        instrumentId,
                        effectivePrice,
                        tx.getCurrency(),
                        tx.getTradeDate(),
                        ObservationSourceType.PROVIDER,
                        "LAST_TRANSACTION_TRADE_PRICE",
                        isStale,
                        warning
                );
                try {
                    MarketObservation obs = new MarketObservation(
                            instrument,
                            tx.getPrice(),
                            quote.currency(),
                            quote.asOf(),
                            ObservationSourceType.PROVIDER,
                            "LAST_TRANSACTION_TRADE_PRICE"
                    );
                    marketObservationRepository.save(obs);
                } catch (Exception ignored) {
                }
                return quote;
            }
        }

        // 4. If historical targetTime requested but no past observation existed, fallback to overall latest observation
        if (asOf != null) {
            Optional<MarketObservation> anyPersisted = marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(instrumentId);
            if (anyPersisted.isPresent()) {
                MarketObservation obs = anyPersisted.get();
                if (obs.getPrice() != null && obs.getPrice().compareTo(BigDecimal.ZERO) > 0
                        && (instrument.getCurrency() == null || instrument.getCurrency().code().equalsIgnoreCase(obs.getCurrency()))) {
                    BigDecimal effectivePrice = adjustPriceForCorporateActions(instrumentId, obs.getPrice(), obs.getObservedAt(), targetTime);
                    return new PriceQuote(
                            instrumentId,
                            effectivePrice,
                            obs.getCurrency(),
                            obs.getObservedAt(),
                            obs.getSourceType(),
                            obs.getSourceReference(),
                            true,
                            "Historical price unavailable at " + targetTime + "; using closest known observation"
                    );
                }
            }
        }

        // 4. If brand new instrument with zero observations and zero transactions, fetch initial quote if provider available
        if (!instrument.isManualPriceOnly()) {
            try {
                Optional<PriceQuote> providerQuoteOpt = marketDataProvider.fetchQuote(instrument, targetTime);
                if (providerQuoteOpt.isPresent()) {
                    PriceQuote quote = providerQuoteOpt.get();
                    if (quote.price() != null && quote.price().compareTo(BigDecimal.ZERO) > 0) {
                        MarketObservation obs = new MarketObservation(
                                instrument,
                                quote.price(),
                                quote.currency(),
                                quote.asOf(),
                                ObservationSourceType.PROVIDER,
                                quote.sourceReference()
                        );
                        if (observationStorageService != null) {
                            observationStorageService.saveMarketObservation(obs);
                        } else {
                            marketObservationRepository.save(obs);
                        }
                        return quote;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        throw new ResourceNotFoundException(
                "No market price available for instrument: " + instrument.getTicker() + " (" + instrumentId + ")");
    }

    public Optional<PriceQuote> refreshLivePrice(UUID instrumentId) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));

        if (instrument.isManualPriceOnly()) {
            return Optional.of(getLatestPrice(instrumentId, Instant.now()));
        }

        Instant targetTime = Instant.now();
        Optional<PriceQuote> providerQuoteOpt = marketDataProvider.fetchQuote(instrument, targetTime);
        if (providerQuoteOpt.isPresent()) {
            PriceQuote quote = providerQuoteOpt.get();
            boolean isDefaultMock = "DEFAULT_PROVIDER".equalsIgnoreCase(quote.sourceReference());
            boolean isCachedFallback = "TWELVE_DATA_CACHED".equalsIgnoreCase(quote.sourceReference());

            if (quote.price() != null && quote.price().compareTo(BigDecimal.ZERO) > 0 && !isDefaultMock && !isCachedFallback) {
                MarketObservation obs = new MarketObservation(
                        instrument,
                        quote.price(),
                        quote.currency(),
                        quote.asOf(),
                        ObservationSourceType.PROVIDER,
                        quote.sourceReference()
                );
                if (observationStorageService != null) {
                    observationStorageService.saveMarketObservation(obs);
                } else {
                    marketObservationRepository.save(obs);
                }
                return Optional.of(quote);
            }
        }
        return Optional.empty();
    }

    public PriceQuote refreshPrice(UUID instrumentId) {
        try {
            Optional<PriceQuote> live = refreshLivePrice(instrumentId);
            if (live.isPresent()) {
                return live.get();
            }
        } catch (Exception ignored) {
        }

        return getLatestPrice(instrumentId, Instant.now());
    }

    public MarketObservation recordManualOverride(
            UUID instrumentId,
            BigDecimal price,
            String currency,
            Instant observedAt,
            String reason) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));

        Objects.requireNonNull(price, "Price must not be null");
        String curr = currency != null ? currency : instrument.getCurrency().code();
        Instant obsTime = observedAt != null ? observedAt : Instant.now();

        MarketObservation observation = new MarketObservation(
                instrument,
                price,
                curr,
                obsTime,
                ObservationSourceType.MANUAL,
                reason != null && !reason.isBlank() ? reason : "MANUAL_OVERRIDE"
        );

        return marketObservationRepository.save(observation);
    }

    @Transactional(readOnly = true)
    public List<MarketObservation> getHistoricalPrices(UUID instrumentId, Instant from, Instant to) {
        if (!instrumentRepository.existsById(instrumentId)) {
            throw new ResourceNotFoundException("Instrument not found: " + instrumentId);
        }
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now().plus(Duration.ofMinutes(5));
        return marketObservationRepository.findByInstrumentIdAndObservedAtBetweenOrderByObservedAtAsc(instrumentId, start, end);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketDataService.class);

    public record BackfillInstrumentSummary(
            UUID instrumentId,
            String ticker,
            String name,
            int pointsSaved,
            String status
    ) {}

    public record PortfolioBackfillResult(
            UUID portfolioId,
            int instrumentsProcessed,
            int totalObservationsSynced,
            List<BackfillInstrumentSummary> instrumentSummaries
    ) {}

    public long getObservationCount(UUID instrumentId) {
        if (instrumentId == null) {
            return 0;
        }
        return marketObservationRepository.countByInstrumentId(instrumentId);
    }

    public boolean hasHistoricalObservationBefore(UUID instrumentId, Instant threshold) {
        if (instrumentId == null || threshold == null) {
            return false;
        }
        return marketObservationRepository.findFirstByInstrumentIdAndObservedAtBefore(instrumentId, threshold).isPresent();
    }

    public boolean hasSufficientHistoricalCoverage(UUID instrumentId, Instant from, Instant to, int minPoints) {
        if (instrumentId == null) {
            return false;
        }
        Instant start = from != null ? from : Instant.now().minus(Duration.ofDays(365));
        Instant end = to != null ? to : Instant.now();
        if (start.isAfter(end)) {
            Instant tmp = start;
            start = end;
            end = tmp;
        }
        long count = marketObservationRepository.countByInstrumentIdAndObservedAtBetween(instrumentId, start, end);
        return count >= minPoints;
    }

    @Transactional
    public List<MarketObservation> syncHistoricalPrices(UUID instrumentId, Instant from, Instant to) {
        return backfillHistoricalPrices(instrumentId, from, to);
    }

    @Transactional
    public List<MarketObservation> backfillHistoricalPrices(UUID instrumentId, Instant from, Instant to) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));
        List<PriceQuote> quotes = marketDataProvider.fetchHistoricalQuotes(instrument, from, to);
        List<MarketObservation> saved = new java.util.ArrayList<>();
        for (PriceQuote q : quotes) {
            if (!marketObservationRepository.existsByInstrumentIdAndObservedAt(instrumentId, q.asOf())) {
                MarketObservation obs = new MarketObservation(
                        instrument,
                        q.price(),
                        q.currency(),
                        q.asOf(),
                        ObservationSourceType.PROVIDER,
                        q.sourceReference()
                );
                saved.add(marketObservationRepository.save(obs));
            }
        }
        return saved;
    }

    @Transactional
    public PortfolioBackfillResult backfillPortfolioInstrumentsHistory(UUID portfolioId, Instant from, Instant to) {
        if (portfolioId == null) {
            throw new IllegalArgumentException("Portfolio ID must not be null");
        }

        List<Transaction> txs = transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId);
        Map<UUID, Instrument> instruments = new LinkedHashMap<>();
        Map<UUID, Instant> earliestDates = new java.util.HashMap<>();

        for (Transaction tx : txs) {
            if (tx.getStatus() != com.takakim.investtracker.domain.TransactionStatus.COMPLETED) {
                continue;
            }
            Instrument inst = tx.getInstrument();
            if (inst != null && inst.getId() != null && !inst.isManualPriceOnly() && inst.getTicker() != null && !inst.getTicker().isBlank()) {
                instruments.put(inst.getId(), inst);
                Instant tradeDate = tx.getTradeDate();
                if (tradeDate != null) {
                    earliestDates.merge(inst.getId(), tradeDate, (a, b) -> a.isBefore(b) ? a : b);
                }
            }
        }

        Instant defaultFrom = from != null ? from : Instant.now().minus(Duration.ofDays(365 * 2));
        Instant defaultTo = to != null ? to : Instant.now();

        int totalSynced = 0;
        List<BackfillInstrumentSummary> summaries = new ArrayList<>();

        for (Instrument inst : instruments.values()) {
            UUID instId = inst.getId();
            Instant instEarliest = earliestDates.get(instId);
            Instant rangeStart = (instEarliest != null && instEarliest.isBefore(defaultFrom))
                    ? instEarliest
                    : defaultFrom;

            try {
                List<MarketObservation> saved = backfillHistoricalPrices(instId, rangeStart, defaultTo);
                totalSynced += saved.size();
                summaries.add(new BackfillInstrumentSummary(
                        instId, inst.getTicker(), inst.getName(), saved.size(), "SUCCESS"
                ));
            } catch (Exception e) {
                log.warn("Failed to backfill historical prices for instrument '{}' ({}): {}",
                        inst.getName(), inst.getTicker(), e.getMessage());
                summaries.add(new BackfillInstrumentSummary(
                        instId, inst.getTicker(), inst.getName(), 0, "FAILED: " + e.getMessage()
                ));
            }
        }

        return new PortfolioBackfillResult(portfolioId, instruments.size(), totalSynced, summaries);
    }

    public BigDecimal adjustPriceForCorporateActions(
            UUID instrumentId,
            BigDecimal price,
            Instant priceTime,
            Instant targetTime) {
        if (price == null || priceTime == null || targetTime == null || priceTime.equals(targetTime)) {
            return price;
        }

        List<Transaction> allTxs = transactionRepository.findByInstrumentIdOrderByTradeDateDesc(instrumentId);
        if (allTxs.isEmpty()) {
            return price;
        }

        List<Transaction> splitTxs = allTxs.stream()
                .filter(t -> t.getType() == TransactionType.STOCK_SPLIT || t.getType() == TransactionType.REVERSE_STOCK_SPLIT)
                .toList();

        if (splitTxs.isEmpty()) {
            return price;
        }

        boolean priceBeforeTarget = priceTime.isBefore(targetTime);
        Instant windowStart = priceBeforeTarget ? priceTime : targetTime;
        Instant windowEnd = priceBeforeTarget ? targetTime : priceTime;

        List<Transaction> relevantSplits = splitTxs.stream()
                .filter(t -> t.getTradeDate().isAfter(windowStart) && !t.getTradeDate().isAfter(windowEnd))
                .sorted(Comparator.comparing(Transaction::getTradeDate))
                .toList();

        if (relevantSplits.isEmpty()) {
            return price;
        }

        Map<String, List<Transaction>> splitsByEvent = new LinkedHashMap<>();
        for (Transaction split : relevantSplits) {
            String eventKey = split.getTradeDate().truncatedTo(ChronoUnit.HOURS).toString() + "_" + split.getType();
            splitsByEvent.computeIfAbsent(eventKey, k -> new ArrayList<>()).add(split);
        }

        BigDecimal cumulativeRatio = BigDecimal.ONE;

        for (List<Transaction> eventSplits : splitsByEvent.values()) {
            BigDecimal eventRatio = null;
            for (Transaction splitTx : eventSplits) {
                BigDecimal preQty = computeAccountPreQuantity(
                        splitTx.getAccount().getId(), instrumentId, splitTx.getTradeDate());

                if (preQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal splitQty = splitTx.getQuantity();
                    BigDecimal postQty = splitTx.getType() == TransactionType.STOCK_SPLIT
                            ? preQty.add(splitQty)
                            : preQty.subtract(splitQty);
                    if (postQty.compareTo(BigDecimal.ZERO) > 0) {
                        eventRatio = postQty.divide(preQty, 8, RoundingMode.HALF_UP);
                        break;
                    }
                }
            }

            if (eventRatio != null) {
                cumulativeRatio = cumulativeRatio.multiply(eventRatio);
            }
        }

        if (cumulativeRatio.compareTo(BigDecimal.ONE) == 0) {
            return price;
        }

        return priceBeforeTarget
                ? price.divide(cumulativeRatio, 8, RoundingMode.HALF_UP)
                : price.multiply(cumulativeRatio).setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal computeAccountPreQuantity(UUID accountId, UUID instrumentId, Instant splitDate) {
        List<Transaction> accTxs = transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(accountId, instrumentId);
        BigDecimal qty = BigDecimal.ZERO;
        for (Transaction t : accTxs) {
            if (t.getTradeDate().isBefore(splitDate)) {
                TransactionType type = t.getType();
                if (type == TransactionType.BUY || type == TransactionType.STOCK_SPLIT) {
                    qty = qty.add(t.getQuantity());
                } else if (type == TransactionType.SELL || type == TransactionType.REVERSE_STOCK_SPLIT) {
                    qty = qty.subtract(t.getQuantity());
                }
            }
        }
        return qty;
    }
}

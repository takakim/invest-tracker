package com.takakim.investtracker.service.market;

import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.takakim.investtracker.service.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(noRollbackFor = {ResourceNotFoundException.class})
public class MarketDataService {

    private static final Duration STALE_THRESHOLD = Duration.ofHours(24);

    private final MarketObservationRepository marketObservationRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;
    private final com.takakim.investtracker.repository.TransactionRepository transactionRepository;

    public MarketDataService(
            MarketObservationRepository marketObservationRepository,
            InstrumentRepository instrumentRepository,
            MarketDataProvider marketDataProvider,
            com.takakim.investtracker.repository.TransactionRepository transactionRepository) {
        this.marketObservationRepository = marketObservationRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.transactionRepository = transactionRepository;
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
            boolean isStale = Duration.between(manual.getObservedAt(), targetTime).abs().compareTo(STALE_THRESHOLD) > 0;
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

        // 2. Fetch from market data provider
        try {
            Optional<PriceQuote> providerQuoteOpt = marketDataProvider.fetchQuote(instrument, targetTime);
            if (providerQuoteOpt.isPresent()) {
                PriceQuote quote = providerQuoteOpt.get();
                // Cache/persist observation
                MarketObservation obs = new MarketObservation(
                        instrument,
                        quote.price(),
                        quote.currency(),
                        quote.asOf(),
                        ObservationSourceType.PROVIDER,
                        quote.sourceReference()
                );
                marketObservationRepository.save(obs);
                return quote;
            }
        } catch (Exception ignored) {
        }

        // 3. Fallback to latest persisted observation
        Optional<MarketObservation> latestPersisted = marketObservationRepository
                .findFirstByInstrumentIdOrderByObservedAtDesc(instrumentId);

        if (latestPersisted.isPresent()) {
            MarketObservation obs = latestPersisted.get();
            boolean isStale = Duration.between(obs.getObservedAt(), targetTime).abs().compareTo(STALE_THRESHOLD) > 0;
            String warning = "Live price unavailable; using latest persisted observation from " + obs.getObservedAt();
            return new PriceQuote(
                    instrumentId,
                    obs.getPrice(),
                    obs.getCurrency(),
                    obs.getObservedAt(),
                    obs.getSourceType(),
                    obs.getSourceReference(),
                    isStale,
                    warning
            );
        }

        // 4. Fallback to latest transaction trade price if available
        List<com.takakim.investtracker.domain.Transaction> txs = transactionRepository.findByInstrumentIdOrderByTradeDateDesc(instrumentId);
        for (com.takakim.investtracker.domain.Transaction tx : txs) {
            if (tx.getPrice() != null && tx.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                boolean isStale = Duration.between(tx.getTradeDate(), targetTime).abs().compareTo(STALE_THRESHOLD) > 0;
                String warning = "Live price unavailable; using last transaction trade price from " + tx.getTradeDate();
                PriceQuote quote = new PriceQuote(
                        instrumentId,
                        tx.getPrice(),
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
                            quote.price(),
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

        throw new ResourceNotFoundException(
                "No market price available for instrument: " + instrument.getTicker() + " (" + instrumentId + ")");
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

    public List<MarketObservation> syncHistoricalPrices(UUID instrumentId, Instant from, Instant to) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));
        List<PriceQuote> quotes = marketDataProvider.fetchHistoricalQuotes(instrument, from, to);
        List<MarketObservation> saved = new java.util.ArrayList<>();
        for (PriceQuote q : quotes) {
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
        return saved;
    }
}

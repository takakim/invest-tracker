package com.takakim.investtracker.service.currency;

import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.FxObservation;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.repository.FxObservationRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.takakim.investtracker.service.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(noRollbackFor = {ResourceNotFoundException.class})
public class FxRateService {

    private static final Duration STALE_THRESHOLD = Duration.ofHours(24);
    private static final int FX_SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final FxObservationRepository fxObservationRepository;
    private final FxRateProvider fxRateProvider;

    public FxRateService(
            FxObservationRepository fxObservationRepository,
            FxRateProvider fxRateProvider) {
        this.fxObservationRepository = fxObservationRepository;
        this.fxRateProvider = fxRateProvider;
    }

    public FxRateQuote getRate(String baseCurrency, String quoteCurrency, Instant asOf) {
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");

        String base = baseCurrency.trim().toUpperCase();
        String quote = quoteCurrency.trim().toUpperCase();
        Instant targetTime = asOf != null ? asOf : Instant.now();

        // 1. Same currency identity
        if (base.equals(quote)) {
            return new FxRateQuote(
                    base, quote, BigDecimal.ONE.setScale(FX_SCALE, ROUNDING),
                    targetTime, ObservationSourceType.PROVIDER, "IDENTITY", false, null
            );
        }

        // Sub-unit pence sterling conversion (100 GBX = 1 GBP)
        if (base.equals("GBX") && quote.equals("GBP")) {
            return new FxRateQuote(
                    base, quote, new BigDecimal("0.01000000"),
                    targetTime, ObservationSourceType.PROVIDER, "SUB_UNIT", false, null
            );
        }
        if (base.equals("GBP") && quote.equals("GBX")) {
            return new FxRateQuote(
                    base, quote, new BigDecimal("100.00000000"),
                    targetTime, ObservationSourceType.PROVIDER, "SUB_UNIT", false, null
            );
        }

        // 2. Direct manual override (base -> quote)
        Optional<FxObservation> directManual = fxObservationRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc(base, quote, ObservationSourceType.MANUAL);
        if (directManual.isPresent()) {
            return toQuote(directManual.get(), targetTime, false);
        }

        // 3. Inverse manual override (quote -> base)
        Optional<FxObservation> inverseManual = fxObservationRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc(quote, base, ObservationSourceType.MANUAL);
        if (inverseManual.isPresent()) {
            BigDecimal invertedRate = BigDecimal.ONE.divide(inverseManual.get().getRate(), FX_SCALE, ROUNDING);
            boolean isStale = Duration.between(inverseManual.get().getObservedAt(), targetTime).abs().compareTo(STALE_THRESHOLD) > 0;
            String warning = isStale ? "Manual FX override is older than 24 hours" : null;
            return new FxRateQuote(
                    base, quote, invertedRate, inverseManual.get().getObservedAt(),
                    ObservationSourceType.MANUAL, "INVERTED_MANUAL_OVERRIDE", true, warning
            );
        }

        // 4. Provider direct or inverse rate
        Optional<FxRateQuote> providerQuote = fxRateProvider.fetchRate(base, quote, targetTime);
        if (providerQuote.isPresent()) {
            FxRateQuote q = providerQuote.get();
            // Cache provider observation
            FxObservation obs = new FxObservation(
                    base, quote, q.rate(), q.asOf(), ObservationSourceType.PROVIDER, q.sourceReference()
            );
            fxObservationRepository.save(obs);
            return q;
        }

        // 5. Triangulation through USD (if neither base nor quote is USD)
        if (!"USD".equals(base) && !"USD".equals(quote)) {
            Optional<FxRateQuote> baseToUsd = fxRateProvider.fetchRate(base, "USD", targetTime);
            Optional<FxRateQuote> quoteToUsd = fxRateProvider.fetchRate(quote, "USD", targetTime);
            if (baseToUsd.isPresent() && quoteToUsd.isPresent()) {
                BigDecimal triangulatedRate = baseToUsd.get().rate()
                        .divide(quoteToUsd.get().rate(), FX_SCALE, ROUNDING);
                return new FxRateQuote(
                        base, quote, triangulatedRate, targetTime,
                        ObservationSourceType.PROVIDER, "TRIANGULATED_USD", true, null
                );
            }
        }

        // 6. Fallback to latest persisted observation
        Optional<FxObservation> latestPersisted = fxObservationRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByObservedAtDesc(base, quote);
        if (latestPersisted.isPresent()) {
            FxObservation obs = latestPersisted.get();
            boolean isStale = Duration.between(obs.getObservedAt(), targetTime).abs().compareTo(STALE_THRESHOLD) > 0;
            String warning = "Live FX rate unavailable; using latest persisted observation from " + obs.getObservedAt();
            return new FxRateQuote(
                    base, quote, obs.getRate(), obs.getObservedAt(),
                    obs.getSourceType(), obs.getSourceReference(), false, warning
            );
        }

        throw new ResourceNotFoundException("No FX rate available for currency pair: " + base + "/" + quote);
    }

    public FxObservation recordManualOverride(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            Instant observedAt,
            String reason) {
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");
        Objects.requireNonNull(rate, "rate must not be null");

        String base = baseCurrency.trim().toUpperCase();
        String quote = quoteCurrency.trim().toUpperCase();
        if (base.equals(quote)) {
            throw new IllegalArgumentException("Cannot record FX override for identical currencies: " + base);
        }

        Instant obsTime = observedAt != null ? observedAt : Instant.now();
        FxObservation observation = new FxObservation(
                base, quote, rate, obsTime,
                ObservationSourceType.MANUAL,
                reason != null && !reason.isBlank() ? reason : "MANUAL_OVERRIDE"
        );

        return fxObservationRepository.save(observation);
    }

    @Transactional(readOnly = true)
    public Money convert(Money amount, Currency targetCurrency, Instant asOf) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(targetCurrency, "targetCurrency must not be null");

        if (amount.currency().equals(targetCurrency)) {
            return amount;
        }

        FxRateQuote quote = getRate(amount.currency().code(), targetCurrency.code(), asOf);
        BigDecimal converted = amount.amount().multiply(quote.rate()).setScale(4, ROUNDING);
        return new Money(converted, targetCurrency);
    }

    @Transactional(readOnly = true)
    public List<FxObservation> getHistoricalRates(String baseCurrency, String quoteCurrency, Instant from, Instant to) {
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now().plus(Duration.ofMinutes(5));
        return fxObservationRepository.findByBaseCurrencyAndQuoteCurrencyAndObservedAtBetweenOrderByObservedAtAsc(
                baseCurrency.toUpperCase(), quoteCurrency.toUpperCase(), start, end);
    }

    private FxRateQuote toQuote(FxObservation obs, Instant targetTime, boolean isDerived) {
        boolean isStale = Duration.between(obs.getObservedAt(), targetTime).abs().compareTo(STALE_THRESHOLD) > 0;
        String warning = isStale ? "Manual FX override is older than 24 hours" : null;
        return new FxRateQuote(
                obs.getBaseCurrency(),
                obs.getQuoteCurrency(),
                obs.getRate(),
                obs.getObservedAt(),
                obs.getSourceType(),
                obs.getSourceReference() != null ? obs.getSourceReference() : "MANUAL_OVERRIDE",
                isDerived,
                warning
        );
    }
}

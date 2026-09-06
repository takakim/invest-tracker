package com.takakim.investtracker.service.currency;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataDtos;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataGateway;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class TwelveDataFxRateProvider implements FxRateProvider {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataFxRateProvider.class);
    private static final int FX_SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final MarketDataProperties properties;
    private final DefaultFxRateProvider fallbackProvider;
    private final TwelveDataGateway gateway;

    private record CachedFxQuote(FxRateQuote quote, Instant expiresAt) {}
    private final Map<String, CachedFxQuote> fxCache = new ConcurrentHashMap<>();

    @Autowired
    public TwelveDataFxRateProvider(
            MarketDataProperties properties,
            DefaultFxRateProvider fallbackProvider,
            TwelveDataGateway gateway) {
        this.properties = properties;
        this.fallbackProvider = fallbackProvider;
        this.gateway = gateway;
    }

    @PostConstruct
    public void init() {
        if (isServiceConfigured()) {
            log.info("Twelve Data FX rate provider is ENABLED. Base URL: '{}'",
                    properties.getTwelvedata().getBaseUrl());
        } else {
            log.info("Twelve Data FX rate provider is running in OFFLINE/FALLBACK mode. Delegating to DefaultFxRateProvider.");
        }
    }

    @Override
    public Optional<FxRateQuote> fetchRate(String baseCurrency, String quoteCurrency, Instant asOf) {
        if (baseCurrency == null || quoteCurrency == null) {
            return Optional.empty();
        }
        String base = baseCurrency.trim().toUpperCase();
        String quote = quoteCurrency.trim().toUpperCase();

        if (base.equals(quote)) {
            return Optional.of(new FxRateQuote(
                    base, quote, BigDecimal.ONE.setScale(FX_SCALE, ROUNDING),
                    asOf != null ? asOf : Instant.now(),
                    ObservationSourceType.PROVIDER, "IDENTITY", false, null
            ));
        }

        if (!isServiceConfigured()) {
            log.debug("Twelve Data is not configured; delegating FX rate for '{}/{}' to fallback provider", base, quote);
            return fallbackProvider.fetchRate(base, quote, asOf);
        }

        String pairSymbol = base + "/" + quote;

        // 1. Check in-memory TTL cache to prevent 429 rate limit errors on rapid concurrent requests
        CachedFxQuote cached = fxCache.get(pairSymbol);
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            log.debug("Returning cached Twelve Data FX rate for pair '{}': {}", pairSymbol, cached.quote().rate());
            return Optional.of(cached.quote());
        }

        // 2. Fetch from Twelve Data via Rate-Limiting Gateway
        log.debug("Requesting FX rate from Twelve Data Gateway for pair: '{}'", pairSymbol);
        Optional<TwelveDataDtos.ExchangeRateResponse> responseOpt = gateway.fetchExchangeRate(pairSymbol);

        if (responseOpt.isPresent()) {
            TwelveDataDtos.ExchangeRateResponse response = responseOpt.get();
            if (response.isSuccess()) {
                Instant timestamp = asOf != null ? asOf : (response.timestamp() != null ? Instant.ofEpochSecond(response.timestamp()) : Instant.now());

                BigDecimal rate = response.rate().setScale(FX_SCALE, ROUNDING);
                FxRateQuote fxQuote = new FxRateQuote(
                        base,
                        quote,
                        rate,
                        timestamp,
                        ObservationSourceType.PROVIDER,
                        "TWELVE_DATA",
                        false,
                        null
                );

                int ttl = properties.getTwelvedata() != null ? properties.getTwelvedata().getCacheTtlSeconds() : 300;
                fxCache.put(pairSymbol, new CachedFxQuote(fxQuote, Instant.now().plusSeconds(ttl)));
                log.info("Successfully fetched Twelve Data FX rate for pair '{}': {}", pairSymbol, rate);

                return Optional.of(fxQuote);
            } else {
                log.warn("Twelve Data exchange_rate API returned no data or error for pair '{}'", pairSymbol);
            }
        }

        // 3. Fallback to cached quote if available
        if (cached != null) {
            log.warn("Twelve Data FX rate call rate limited or failed; using last cached rate for pair '{}'", pairSymbol);
            return Optional.of(cached.quote());
        }

        return fallbackProvider.fetchRate(base, quote, asOf);
    }

    @Override
    public List<FxRateQuote> fetchHistoricalRates(String baseCurrency, String quoteCurrency, Instant from, Instant to) {
        Optional<FxRateQuote> quote = fetchRate(baseCurrency, quoteCurrency, to);
        if (quote.isPresent()) {
            return List.of(quote.get());
        }
        return fallbackProvider.fetchHistoricalRates(baseCurrency, quoteCurrency, from, to);
    }

    @Override
    public String getProviderId() {
        return "TWELVE_DATA";
    }

    @Override
    public void invalidateCache(String baseCurrency, String quoteCurrency) {
        if (baseCurrency != null && quoteCurrency != null) {
            String base = baseCurrency.trim().toUpperCase();
            String quote = quoteCurrency.trim().toUpperCase();
            fxCache.remove(base + "/" + quote);
            fxCache.remove(quote + "/" + base);
            log.debug("Invalidated in-memory FX cache for '{}/{}'", base, quote);
        }
    }

    private boolean isServiceConfigured() {
        MarketDataProperties.TwelveDataProperties config = properties != null ? properties.getTwelvedata() : null;
        return config != null && config.isEnabled() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }
}

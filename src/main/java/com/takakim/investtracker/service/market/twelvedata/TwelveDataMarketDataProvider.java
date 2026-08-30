package com.takakim.investtracker.service.market.twelvedata;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.service.market.DefaultMarketDataProvider;
import com.takakim.investtracker.service.market.MarketDataProvider;
import com.takakim.investtracker.service.market.PriceQuote;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
public class TwelveDataMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataMarketDataProvider.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MarketDataProperties properties;
    private final DefaultMarketDataProvider fallbackProvider;
    private final TwelveDataGateway gateway;

    private record CachedQuote(PriceQuote quote, Instant expiresAt) {}
    private final Map<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();

    @Autowired
    public TwelveDataMarketDataProvider(
            MarketDataProperties properties,
            DefaultMarketDataProvider fallbackProvider,
            TwelveDataGateway gateway) {
        this.properties = properties;
        this.fallbackProvider = fallbackProvider;
        this.gateway = gateway;
    }

    @PostConstruct
    public void init() {
        if (isServiceConfigured()) {
            log.info("Twelve Data market data provider is ENABLED. Base URL: '{}'",
                    properties.getTwelvedata().getBaseUrl());
        } else {
            log.info("Twelve Data market data provider is running in OFFLINE/FALLBACK mode. Delegating to DefaultMarketDataProvider.");
        }
    }

    @Override
    public Optional<PriceQuote> fetchQuote(Instrument instrument, Instant asOf) {
        if (instrument == null) {
            return Optional.empty();
        }

        if (!isServiceConfigured()) {
            log.debug("Twelve Data is not configured or missing API key; delegating quote for instrument '{}' to fallback provider", instrument.getName());
            return fallbackProvider.fetchQuote(instrument, asOf);
        }

        String symbol = resolveSymbol(instrument);
        if (symbol == null) {
            log.debug("Instrument '{}' has no ticker; delegating to fallback provider", instrument.getName());
            return fallbackProvider.fetchQuote(instrument, asOf);
        }

        String exchange = resolveExchange(instrument);
        String cacheKey = symbol + (exchange != null ? ":" + exchange : "");

        // 1. Check in-memory TTL cache to prevent exhausting rate limits on concurrent requests
        CachedQuote cached = quoteCache.get(cacheKey);
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            log.debug("Returning cached Twelve Data quote for symbol '{}': {}", symbol, cached.quote().price());
            return Optional.of(cached.quote());
        }

        // 2. Fetch from Twelve Data via Rate-Limiting Gateway
        log.debug("Requesting real-time quote from Twelve Data Gateway for symbol: '{}', exchange: '{}'", symbol, exchange);
        Optional<TwelveDataDtos.QuoteResponse> responseOpt = gateway.fetchQuote(symbol, exchange);

        if (responseOpt.isPresent()) {
            TwelveDataDtos.QuoteResponse response = responseOpt.get();
            if (response.isSuccess()) {
                BigDecimal price = new BigDecimal(response.close());
                String responseCurrency = response.currency() != null ? response.currency().toUpperCase() : instrument.getCurrency().code();

                if ("GBX".equalsIgnoreCase(responseCurrency)) {
                    price = price.divide(new BigDecimal("100"), 4, RoundingMode.HALF_EVEN);
                    responseCurrency = "GBP";
                }

                Instant timestamp = asOf != null ? asOf : (response.timestamp() != null ? Instant.ofEpochSecond(response.timestamp()) : Instant.now());

                PriceQuote quote = new PriceQuote(
                        instrument.getId(),
                        price.setScale(4, RoundingMode.HALF_EVEN),
                        responseCurrency,
                        timestamp,
                        ObservationSourceType.PROVIDER,
                        "TWELVE_DATA",
                        false,
                        null
                );

                int ttl = properties.getTwelvedata() != null ? properties.getTwelvedata().getCacheTtlSeconds() : 300;
                quoteCache.put(cacheKey, new CachedQuote(quote, Instant.now().plusSeconds(ttl)));
                log.info("Successfully fetched Twelve Data quote for symbol '{}': {} {}", symbol, price, responseCurrency);

                return Optional.of(quote);
            } else {
                log.warn("Twelve Data quote API returned no data or error for symbol '{}'", symbol);
            }
        }

        // 3. If rate limited or failed, check if we have any cached quote first
        if (cached != null) {
            log.warn("Twelve Data call rate limited or failed; using last cached quote for symbol '{}'", symbol);
            return Optional.of(cached.quote());
        }

        return fallbackProvider.fetchQuote(instrument, asOf);
    }

    @Override
    public List<PriceQuote> fetchHistoricalQuotes(Instrument instrument, Instant from, Instant to) {
        if (instrument == null) {
            return List.of();
        }

        if (!isServiceConfigured()) {
            log.debug("Twelve Data is not configured; delegating historical quotes for instrument '{}' to fallback provider", instrument.getName());
            return fallbackProvider.fetchHistoricalQuotes(instrument, from, to);
        }

        String symbol = resolveSymbol(instrument);
        if (symbol == null) {
            log.debug("Instrument '{}' has no ticker; delegating historical quotes to fallback provider", instrument.getName());
            return fallbackProvider.fetchHistoricalQuotes(instrument, from, to);
        }

        String exchange = resolveExchange(instrument);
        Instant start = from != null ? from : Instant.now().minus(Duration.ofDays(365));
        Instant end = to != null ? to : Instant.now();
        if (start.isAfter(end)) {
            start = end.minus(Duration.ofDays(30));
        }

        String startDate = LocalDate.ofInstant(start, ZoneOffset.UTC).format(DATE_FORMATTER);
        String endDate = LocalDate.ofInstant(end, ZoneOffset.UTC).format(DATE_FORMATTER);

        log.debug("Fetching historical time series from Twelve Data Gateway for symbol: '{}', exchange: '{}', range: {} to {}",
                symbol, exchange, startDate, endDate);

        Optional<TwelveDataDtos.TimeSeriesResponse> responseOpt = gateway.fetchTimeSeries(symbol, exchange, startDate, endDate);

        if (responseOpt.isPresent()) {
            TwelveDataDtos.TimeSeriesResponse response = responseOpt.get();
            if (response.isSuccess()) {
                List<PriceQuote> quotes = new ArrayList<>();
                for (TwelveDataDtos.TimeSeriesValue val : response.values()) {
                    if (val.close() != null && val.datetime() != null) {
                        BigDecimal price = new BigDecimal(val.close()).setScale(4, RoundingMode.HALF_EVEN);
                        LocalDate date = LocalDate.parse(val.datetime().substring(0, 10), DATE_FORMATTER);
                        Instant pointTime = date.atStartOfDay(ZoneOffset.UTC).toInstant();
                        quotes.add(new PriceQuote(
                                instrument.getId(),
                                price,
                                instrument.getCurrency().code(),
                                pointTime,
                                ObservationSourceType.PROVIDER,
                                "TWELVE_DATA",
                                false,
                                null
                        ));
                    }
                }
                Collections.reverse(quotes);
                if (!quotes.isEmpty()) {
                    log.info("Successfully fetched {} historical price points from Twelve Data for symbol '{}'", quotes.size(), symbol);
                    return quotes;
                }
            }
            log.warn("Twelve Data time_series API returned no data or error for symbol '{}'", symbol);
        }

        return fallbackProvider.fetchHistoricalQuotes(instrument, from, to);
    }

    @Override
    public String getProviderId() {
        return "TWELVE_DATA";
    }

    public boolean isConfigured() {
        return isServiceConfigured();
    }

    public boolean isServiceConfigured() {
        MarketDataProperties.TwelveDataProperties config = properties != null ? properties.getTwelvedata() : null;
        return config != null && config.isEnabled() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    public String resolveSymbol(Instrument instrument) {
        if (instrument.getTicker() != null && !instrument.getTicker().isBlank()) {
            String ticker = instrument.getTicker().trim().toUpperCase();
            if (ticker.endsWith(".")) {
                ticker = ticker.substring(0, ticker.length() - 1);
            }
            if ("BRK.B".equals(ticker)) {
                return "BRK/B";
            }
            return !ticker.isBlank() ? ticker : null;
        }
        return null;
    }

    public String resolveExchange(Instrument instrument) {
        if (instrument.getExchange() != null && !instrument.getExchange().isBlank()) {
            String ex = instrument.getExchange().trim().toUpperCase();
            return (ex.contains("LON") || ex.contains("LSE")) ? "LSE" : ex;
        }
        if (instrument.getIsin() != null && instrument.getIsin().startsWith("GB")) {
            return "LSE";
        }
        if (instrument.getCurrency() != null && "GBP".equalsIgnoreCase(instrument.getCurrency().code())) {
            return "LSE";
        }
        return null;
    }
}

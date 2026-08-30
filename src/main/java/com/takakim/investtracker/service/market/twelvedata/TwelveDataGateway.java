package com.takakim.investtracker.service.market.twelvedata;

import com.takakim.investtracker.config.MarketDataProperties;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

@Component
public class TwelveDataGateway {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataGateway.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 8;
    private static final Duration WINDOW_DURATION = Duration.ofSeconds(60);
    private static final Duration DEFAULT_429_COOLDOWN = Duration.ofSeconds(60);

    private final MarketDataProperties properties;
    private final RestClient restClient;

    private final Deque<Instant> requestTimestamps = new ArrayDeque<>();
    private final AtomicReference<Instant> blockedUntil = new AtomicReference<>(null);

    @Autowired
    public TwelveDataGateway(MarketDataProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;

        MarketDataProperties.TwelveDataProperties config = properties.getTwelvedata();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(config != null ? config.getConnectTimeoutSeconds() : 5));
        requestFactory.setReadTimeout(Duration.ofSeconds(config != null ? config.getReadTimeoutSeconds() : 10));

        String baseUrl = config != null && config.getBaseUrl() != null ? config.getBaseUrl() : "https://api.twelvedata.com";

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public TwelveDataGateway(MarketDataProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            log.info("Twelve Data Gateway initialized. Max rate: {} calls/min. Base URL: '{}'",
                    MAX_REQUESTS_PER_MINUTE, properties.getTwelvedata().getBaseUrl());
        } else {
            log.info("Twelve Data Gateway is inactive (no API key configured or disabled).");
        }
    }

    public boolean isConfigured() {
        MarketDataProperties.TwelveDataProperties config = properties != null ? properties.getTwelvedata() : null;
        return config != null && config.isEnabled() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    public synchronized boolean isRateLimited() {
        Instant now = Instant.now();

        // 1. Check active 429 lockout cooldown
        Instant blocked = blockedUntil.get();
        if (blocked != null) {
            if (now.isBefore(blocked)) {
                long remainingSeconds = Duration.between(now, blocked).getSeconds() + 1;
                log.debug("Twelve Data gateway is cooling down after 429 rate limit. Resumes in {}s", remainingSeconds);
                return true;
            } else {
                blockedUntil.set(null);
                log.info("Twelve Data 429 cooldown period elapsed. Resuming external API calls.");
            }
        }

        // 2. Clean sliding window
        cleanSlidingWindow(now);

        // 3. Check if budget of 8 req/min is exhausted
        if (requestTimestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            Instant oldest = requestTimestamps.peekFirst();
            long waitSeconds = oldest != null ? Duration.between(now, oldest.plus(WINDOW_DURATION)).getSeconds() + 1 : 10;
            log.warn("Twelve Data rate limit budget reached ({} calls in last 60s). Pacing request for {}s.",
                    requestTimestamps.size(), Math.max(1, waitSeconds));
            return true;
        }

        return false;
    }

    public Optional<TwelveDataDtos.QuoteResponse> fetchQuote(String symbol, String exchange) {
        if (!isConfigured() || symbol == null || isRateLimited()) {
            return Optional.empty();
        }

        try {
            recordRequest();
            TwelveDataDtos.QuoteResponse response = restClient.get()
                    .uri(uriBuilder -> buildQuoteUri(uriBuilder, symbol, exchange))
                    .retrieve()
                    .body(TwelveDataDtos.QuoteResponse.class);

            if (response != null && response.code() != null && response.code() == 429) {
                handle429(null, response.message());
                return Optional.empty();
            }

            return Optional.ofNullable(response);
        } catch (RestClientResponseException e) {
            handleException(e, "quote", symbol);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error executing Twelve Data quote API for symbol '{}': {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<TwelveDataDtos.ExchangeRateResponse> fetchExchangeRate(String pairSymbol) {
        if (!isConfigured() || pairSymbol == null || isRateLimited()) {
            return Optional.empty();
        }

        try {
            recordRequest();
            TwelveDataDtos.ExchangeRateResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/exchange_rate")
                            .queryParam("symbol", pairSymbol)
                            .queryParam("apikey", properties.getTwelvedata().getApiKey())
                            .build())
                    .retrieve()
                    .body(TwelveDataDtos.ExchangeRateResponse.class);

            if (response != null && response.code() != null && response.code() == 429) {
                handle429(null, response.message());
                return Optional.empty();
            }

            return Optional.ofNullable(response);
        } catch (RestClientResponseException e) {
            handleException(e, "exchange_rate", pairSymbol);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error executing Twelve Data exchange_rate API for pair '{}': {}", pairSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<TwelveDataDtos.TimeSeriesResponse> fetchTimeSeries(String symbol, String exchange, String start, String end) {
        if (!isConfigured() || symbol == null || isRateLimited()) {
            return Optional.empty();
        }

        try {
            recordRequest();
            TwelveDataDtos.TimeSeriesResponse response = restClient.get()
                    .uri(uriBuilder -> buildTimeSeriesUri(uriBuilder, symbol, exchange, start, end))
                    .retrieve()
                    .body(TwelveDataDtos.TimeSeriesResponse.class);

            if (response != null && response.code() != null && response.code() == 429) {
                handle429(null, response.message());
                return Optional.empty();
            }

            return Optional.ofNullable(response);
        } catch (RestClientResponseException e) {
            handleException(e, "time_series", symbol);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error executing Twelve Data time_series API for symbol '{}': {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    public Instant getBlockedUntil() {
        return blockedUntil.get();
    }

    public void setBlockedUntil(Instant blockedUntil) {
        this.blockedUntil.set(blockedUntil);
    }

    public synchronized void resetRateLimiter() {
        requestTimestamps.clear();
        blockedUntil.set(null);
    }

    private synchronized void recordRequest() {
        cleanSlidingWindow(Instant.now());
        requestTimestamps.addLast(Instant.now());
    }

    private void cleanSlidingWindow(Instant now) {
        Instant cutoff = now.minus(WINDOW_DURATION);
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst().isBefore(cutoff)) {
            requestTimestamps.pollFirst();
        }
    }

    private void handleException(RestClientResponseException e, String endpoint, String target) {
        if (e.getStatusCode().value() == 429) {
            handle429(e.getResponseHeaders(), e.getResponseBodyAsString());
        } else if (e.getStatusCode().value() == 404) {
            log.warn("Twelve Data API for {} ('{}') returned HTTP 404 Not Found (symbol may require higher tier plan or specific exchange)",
                    endpoint, target);
        } else {
            log.error("Twelve Data API error for {} ('{}'): HTTP {} - {}",
                    endpoint, target, e.getStatusCode(), e.getMessage());
        }
    }

    public void handle429(HttpHeaders headers, String body) {
        Duration cooldown = parseRetryAfter(headers, body);
        Instant resumeAt = Instant.now().plus(cooldown);
        blockedUntil.set(resumeAt);
        log.warn("Twelve Data 429 rate limit detected. Backing off for {} seconds until {}",
                cooldown.getSeconds(), resumeAt);
    }

    private Duration parseRetryAfter(HttpHeaders headers, String body) {
        if (headers != null) {
            // 1. Standard Retry-After header (seconds)
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null && !retryAfter.isBlank()) {
                try {
                    long seconds = Long.parseLong(retryAfter.trim());
                    if (seconds > 0) {
                        return Duration.ofSeconds(seconds);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // 2. Twelve Data custom header: api-credits-resets-in (seconds)
            String resetIn = headers.getFirst("api-credits-resets-in");
            if (resetIn != null && !resetIn.isBlank()) {
                try {
                    long seconds = Long.parseLong(resetIn.trim());
                    if (seconds > 0) {
                        return Duration.ofSeconds(seconds);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 3. Inspect JSON body message for minute hint
        if (body != null && body.toLowerCase().contains("minute")) {
            return Duration.ofSeconds(60);
        }

        return DEFAULT_429_COOLDOWN;
    }

    private URI buildQuoteUri(UriBuilder uriBuilder, String symbol, String exchange) {
        UriBuilder b = uriBuilder.path("/quote")
                .queryParam("symbol", symbol)
                .queryParam("apikey", properties.getTwelvedata().getApiKey());
        if (exchange != null) {
            b.queryParam("exchange", exchange);
        }
        return b.build();
    }

    private URI buildTimeSeriesUri(UriBuilder uriBuilder, String symbol, String exchange, String start, String end) {
        UriBuilder b = uriBuilder.path("/time_series")
                .queryParam("symbol", symbol)
                .queryParam("interval", "1day")
                .queryParam("start_date", start)
                .queryParam("end_date", end)
                .queryParam("apikey", properties.getTwelvedata().getApiKey());
        if (exchange != null) {
            b.queryParam("exchange", exchange);
        }
        return b.build();
    }
}

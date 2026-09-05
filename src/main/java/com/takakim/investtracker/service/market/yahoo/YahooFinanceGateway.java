package com.takakim.investtracker.service.market.yahoo;

import com.takakim.investtracker.config.MarketDataProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class YahooFinanceGateway {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceGateway.class);

    private final MarketDataProperties properties;
    private final RestClient restClient;
    private final int maxRequestsPerMinute;
    private final Deque<Instant> requestTimestamps = new ArrayDeque<>();
    private Instant blockedUntil = Instant.EPOCH;

    @Autowired
    public YahooFinanceGateway(MarketDataProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        MarketDataProperties.YahooProperties yahoo = properties != null ? properties.getYahoo() : null;

        int connectTimeout = (yahoo != null && yahoo.getConnectTimeoutSeconds() > 0)
                ? yahoo.getConnectTimeoutSeconds()
                : 5;
        int readTimeout = (yahoo != null && yahoo.getReadTimeoutSeconds() > 0)
                ? yahoo.getReadTimeoutSeconds()
                : 10;
        this.maxRequestsPerMinute = (yahoo != null && yahoo.getMaxRequestsPerMinute() > 0)
                ? yahoo.getMaxRequestsPerMinute()
                : 30;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeout));

        String baseUrl = (yahoo != null && yahoo.getBaseUrl() != null && !yahoo.getBaseUrl().isBlank())
                ? yahoo.getBaseUrl()
                : "https://query1.finance.yahoo.com";
        String userAgent = (yahoo != null && yahoo.getUserAgent() != null && !yahoo.getUserAgent().isBlank())
                ? yahoo.getUserAgent()
                : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();

        if (isConfigured()) {
            log.info("Yahoo Finance Gateway initialized. Max rate: {} calls/min. Base URL: '{}'",
                    this.maxRequestsPerMinute, baseUrl);
        } else {
            log.info("Yahoo Finance Gateway is inactive (disabled in configuration).");
        }
    }

    public YahooFinanceGateway(MarketDataProperties properties) {
        this(properties, (RestClient.Builder) null);
    }

    public YahooFinanceGateway(MarketDataProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        MarketDataProperties.YahooProperties yahoo = properties != null ? properties.getYahoo() : null;
        this.maxRequestsPerMinute = (yahoo != null && yahoo.getMaxRequestsPerMinute() > 0)
                ? yahoo.getMaxRequestsPerMinute()
                : 30;
    }

    public boolean isConfigured() {
        MarketDataProperties.YahooProperties yahoo = properties != null ? properties.getYahoo() : null;
        return yahoo != null && yahoo.isEnabled() && yahoo.getBaseUrl() != null && !yahoo.getBaseUrl().isBlank();
    }

    public synchronized boolean isRateLimited() {
        return Instant.now().isBefore(blockedUntil);
    }

    private synchronized boolean acquirePermit() {
        Instant now = Instant.now();
        if (now.isBefore(blockedUntil)) {
            long remaining = Duration.between(now, blockedUntil).toSeconds();
            log.debug("Yahoo Finance gateway is cooling down after 429 rate limit. Resumes in {}s", remaining);
            return false;
        }

        // Clean timestamps older than 60s
        Instant windowStart = now.minusSeconds(60);
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst().isBefore(windowStart)) {
            requestTimestamps.pollFirst();
        }

        if (requestTimestamps.size() >= maxRequestsPerMinute) {
            long waitSeconds = 60 - Duration.between(requestTimestamps.peekFirst(), now).toSeconds();
            log.warn("Yahoo Finance rate limit budget reached ({} calls in last 60s). Pacing request for {}s.",
                    maxRequestsPerMinute, Math.max(1, waitSeconds));
            return false;
        }

        requestTimestamps.addLast(now);
        return true;
    }

    public Optional<YahooFinanceDtos.ChartEntry> fetchChart(String symbol, String interval, String range) {
        if (!isConfigured() || symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        if (!acquirePermit()) {
            return Optional.empty();
        }

        String safeInterval = (interval != null && !interval.isBlank()) ? interval : "1d";
        String safeRange = (range != null && !range.isBlank()) ? range : "1d";

        try {
            YahooFinanceDtos.ChartResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", safeInterval)
                            .queryParam("range", safeRange)
                            .build(symbol))
                    .retrieve()
                    .body(YahooFinanceDtos.ChartResponse.class);

            if (response != null && response.chart() != null) {
                if (response.chart().error() != null) {
                    log.warn("Yahoo Finance returned error for symbol '{}': {} - {}",
                            symbol, response.chart().error().code(), response.chart().error().description());
                    return Optional.empty();
                }
                if (response.chart().result() != null && !response.chart().result().isEmpty()) {
                    YahooFinanceDtos.ChartEntry entry = response.chart().result().get(0);
                    if (entry != null && entry.meta() != null) {
                        return Optional.of(entry);
                    }
                }
            }
            return Optional.empty();
        } catch (RestClientResponseException ex) {
            handleException(ex, "chart", symbol);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Unexpected error requesting Yahoo Finance chart for symbol '{}': {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    public record HistoricalPriceBar(Instant timestamp, BigDecimal closePrice, String currency) {}

    public List<HistoricalPriceBar> fetchHistoricalDailyPrices(String symbol, String range) {
        Optional<YahooFinanceDtos.ChartEntry> chartOpt = fetchChart(symbol, "1d", range);
        if (chartOpt.isEmpty()) {
            return List.of();
        }

        YahooFinanceDtos.ChartEntry entry = chartOpt.get();
        String currency = entry.meta().currency() != null
                ? entry.meta().currency()
                : "USD";

        List<Long> timestamps = entry.timestamp();
        if (timestamps == null || timestamps.isEmpty()
                || entry.indicators() == null
                || entry.indicators().quote() == null
                || entry.indicators().quote().isEmpty()) {
            return List.of();
        }

        List<BigDecimal> closes = entry.indicators().quote().get(0).close();
        if (closes == null || closes.isEmpty()) {
            return List.of();
        }

        List<HistoricalPriceBar> bars = new ArrayList<>();
        int count = Math.min(timestamps.size(), closes.size());
        for (int i = 0; i < count; i++) {
            Long ts = timestamps.get(i);
            BigDecimal close = closes.get(i);
            if (ts != null && close != null && close.compareTo(BigDecimal.ZERO) > 0) {
                bars.add(new HistoricalPriceBar(Instant.ofEpochSecond(ts), close, currency));
            }
        }
        return bars;
    }

    public void handleException(RestClientResponseException ex, String operation, String target) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.value() == 429) {
            handle429(ex.getResponseHeaders(), ex.getResponseBodyAsString());
        } else if (status.value() == 404) {
            log.warn("Yahoo Finance API for {} ('{}') returned HTTP 404 Not Found", operation, target);
        } else {
            log.error("Yahoo Finance API error for {} ('{}'): HTTP {} - {}", operation, target, status, ex.getMessage());
        }
    }

    public void handle429(HttpHeaders headers, String body) {
        int backoffSeconds = 60;
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null && !retryAfter.isBlank()) {
                try {
                    backoffSeconds = Math.max(5, Integer.parseInt(retryAfter.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        synchronized (this) {
            this.blockedUntil = Instant.now().plusSeconds(backoffSeconds);
            log.warn("Yahoo Finance 429 rate limit detected. Backing off for {} seconds until {}", backoffSeconds, blockedUntil);
        }
    }

    public synchronized void resetRateLimiter() {
        this.blockedUntil = Instant.EPOCH;
        this.requestTimestamps.clear();
    }

    public synchronized void setBlockedUntil(Instant blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public synchronized void addRequestTimestamp(Instant timestamp) {
        this.requestTimestamps.add(timestamp);
    }
}

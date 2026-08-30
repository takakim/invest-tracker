package com.takakim.investtracker.service.market.fmp;

import com.takakim.investtracker.config.MarketDataProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FmpGateway {

    private static final Logger log = LoggerFactory.getLogger(FmpGateway.class);

    private final MarketDataProperties properties;
    private final RestClient restClient;

    // Rate limiting
    private final Deque<Instant> requestTimestamps = new ArrayDeque<>();
    private Instant blockedUntil = Instant.MIN;
    private int maxRequestsPerMinute = 5;

    @org.springframework.beans.factory.annotation.Autowired
    public FmpGateway(MarketDataProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        MarketDataProperties.FmpProperties fmp = properties != null ? properties.getFmp() : null;

        int connectTimeout = (fmp != null && fmp.getConnectTimeoutSeconds() > 0)
                ? fmp.getConnectTimeoutSeconds()
                : 5;
        int readTimeout = (fmp != null && fmp.getReadTimeoutSeconds() > 0)
                ? fmp.getReadTimeoutSeconds()
                : 10;
        this.maxRequestsPerMinute = (fmp != null && fmp.getMaxRequestsPerMinute() > 0)
                ? fmp.getMaxRequestsPerMinute()
                : 5;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeout));

        String baseUrl = (fmp != null && fmp.getBaseUrl() != null && !fmp.getBaseUrl().isBlank())
                ? fmp.getBaseUrl()
                : "https://financialmodelingprep.com";

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        if (isConfigured()) {
            log.info("FMP Gateway initialized. Max rate: {} calls/min. Base URL: '{}'", maxRequestsPerMinute, baseUrl);
        } else {
            log.info("FMP Gateway is inactive (no API key configured or disabled).");
        }
    }

    public FmpGateway(MarketDataProperties properties) {
        this(properties, (RestClient.Builder) null);
    }

    public FmpGateway(MarketDataProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        MarketDataProperties.FmpProperties fmp = properties != null ? properties.getFmp() : null;
        this.maxRequestsPerMinute = (fmp != null && fmp.getMaxRequestsPerMinute() > 0)
                ? fmp.getMaxRequestsPerMinute()
                : 5;
    }

    public boolean isConfigured() {
        MarketDataProperties.FmpProperties fmp = properties != null ? properties.getFmp() : null;
        return fmp != null && fmp.isEnabled() && fmp.getApiKey() != null && !fmp.getApiKey().isBlank();
    }

    public synchronized boolean isRateLimited() {
        if (Instant.now().isBefore(blockedUntil)) {
            return true;
        }
        return false;
    }

    private synchronized boolean acquirePermit() {
        Instant now = Instant.now();
        if (now.isBefore(blockedUntil)) {
            long remaining = Duration.between(now, blockedUntil).toSeconds();
            log.debug("FMP gateway is cooling down after 429 rate limit. Resumes in {}s", remaining);
            return false;
        }

        // Clean timestamps older than 60s
        Instant windowStart = now.minusSeconds(60);
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst().isBefore(windowStart)) {
            requestTimestamps.pollFirst();
        }

        if (requestTimestamps.size() >= maxRequestsPerMinute) {
            long waitSeconds = 60 - Duration.between(requestTimestamps.peekFirst(), now).toSeconds();
            log.warn("FMP rate limit budget reached ({} calls in last 60s). Pacing request for {}s.",
                    maxRequestsPerMinute, Math.max(1, waitSeconds));
            return false;
        }

        requestTimestamps.addLast(now);
        return true;
    }

    public Optional<FmpDtos.QuoteResponse> fetchQuote(String symbol) {
        if (!isConfigured() || symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        if (!acquirePermit()) {
            return Optional.empty();
        }

        MarketDataProperties.FmpProperties fmp = properties.getFmp();
        try {
            List<FmpDtos.QuoteResponse> responses = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stable/quote")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", fmp.getApiKey())
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<FmpDtos.QuoteResponse>>() {
                    });

            if (responses != null && !responses.isEmpty() && responses.get(0).price() != null) {
                return Optional.of(responses.get(0));
            }
            return Optional.empty();
        } catch (RestClientResponseException ex) {
            handleException(ex, "quote", symbol);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Unexpected error requesting FMP quote for symbol '{}': {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    public void handleException(RestClientResponseException ex, String operation, String target) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.value() == 429) {
            handle429(ex.getResponseHeaders(), ex.getResponseBodyAsString());
        } else if (status.value() == 404) {
            log.warn("FMP API for {} ('{}') returned HTTP 404 Not Found", operation, target);
        } else {
            log.error("FMP API error for {} ('{}'): HTTP {} - {}", operation, target, status, ex.getMessage());
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
            log.warn("FMP 429 rate limit detected. Backing off for {} seconds until {}", backoffSeconds, blockedUntil);
        }
    }

    public synchronized void resetRateLimiter() {
        this.requestTimestamps.clear();
        this.blockedUntil = Instant.MIN;
    }

    public synchronized void setBlockedUntil(Instant blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public synchronized void addRequestTimestamp(Instant timestamp) {
        this.requestTimestamps.add(timestamp);
    }
}

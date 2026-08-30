package com.takakim.investtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataDtos;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TwelveDataGatewayTests {

    private MarketDataProperties properties;
    private TwelveDataGateway gateway;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        MarketDataProperties.TwelveDataProperties td = new MarketDataProperties.TwelveDataProperties();
        td.setEnabled(true);
        td.setApiKey("test-api-key");
        td.setBaseUrl("https://api.twelvedata.com");
        td.setCacheTtlSeconds(300);
        properties.setTwelvedata(td);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.twelvedata.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        gateway = new TwelveDataGateway(properties, restClient);
    }

    @Test
    @DisplayName("isConfigured returns true when enabled and key is present")
    void testIsConfigured() {
        assertTrue(gateway.isConfigured());
        gateway.init();

        properties.getTwelvedata().setApiKey("");
        assertFalse(gateway.isConfigured());
        gateway.init();

        properties.getTwelvedata().setApiKey("   ");
        assertFalse(gateway.isConfigured());

        properties.getTwelvedata().setEnabled(false);
        assertFalse(gateway.isConfigured());

        properties.setTwelvedata(null);
        assertFalse(gateway.isConfigured());

        // Test primary constructor with null twelvedata
        MarketDataProperties nullProps = new MarketDataProperties();
        nullProps.setTwelvedata(null);
        TwelveDataGateway gwNull = new TwelveDataGateway(nullProps, RestClient.builder());
        assertNotNull(gwNull);
    }

    @Test
    @DisplayName("fetchQuote successfully returns QuoteResponse")
    void testFetchQuoteSuccess() {
        String json = """
            {
                "symbol": "AAPL",
                "name": "Apple Inc",
                "exchange": "NASDAQ",
                "currency": "USD",
                "close": "224.23000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("AAPL", "NASDAQ");
        mockServer.verify();
        assertTrue(res.isPresent());
        assertEquals("AAPL", res.get().symbol());
        assertEquals("224.23000", res.get().close());
    }

    @Test
    @DisplayName("fetchQuote handles 429 response body with code 429")
    void testFetchQuote429InBody() {
        String json = """
            {
                "code": 429,
                "message": "You have run out of API credits for the current minute. Wait for the next minute.",
                "status": "error"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("AAPL", null);
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
        assertNotNull(gateway.getBlockedUntil());
    }

    @Test
    @DisplayName("fetchQuote handles HTTP 429 with Retry-After header")
    void testFetchQuote429HttpHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "45");

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("AAPL", null);
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
    }

    @Test
    @DisplayName("fetchQuote handles HTTP 429 with api-credits-resets-in header")
    void testFetchQuote429CustomHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("api-credits-resets-in", "30");

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("AAPL", null);
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
    }

    @Test
    @DisplayName("fetchExchangeRate successfully returns ExchangeRateResponse")
    void testFetchExchangeRateSuccess() {
        String json = """
            {
                "symbol": "GBP/USD",
                "rate": 1.31500000,
                "timestamp": 1724982000,
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.ExchangeRateResponse> res = gateway.fetchExchangeRate("GBP/USD");
        mockServer.verify();
        assertTrue(res.isPresent());
        assertEquals(new BigDecimal("1.31500000"), res.get().rate());
    }

    @Test
    @DisplayName("fetchTimeSeries successfully returns TimeSeriesResponse")
    void testFetchTimeSeriesSuccess() {
        String json = """
            {
                "status": "ok",
                "values": [
                    { "datetime": "2026-08-28", "close": "190.25000" }
                ]
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/time_series?symbol=AAPL&interval=1day&start_date=2026-08-01&end_date=2026-08-28&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.TimeSeriesResponse> res = gateway.fetchTimeSeries("AAPL", "NASDAQ", "2026-08-01", "2026-08-28");
        mockServer.verify();
        assertTrue(res.isPresent());
        assertEquals(1, res.get().values().size());
    }

    @Test
    @DisplayName("fetchQuote handles 200 response with code 429 in body")
    void testFetchQuoteBody429() {
        String json = "{\"code\": 429, \"message\": \"API rate limit exceeded\", \"status\": \"error\"}";
        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("AAPL", null);
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
        gateway.resetRateLimiter();
    }

    @Test
    @DisplayName("fetchExchangeRate handles 200 response with code 429 in body")
    void testFetchExchangeRateBody429() {
        String json = "{\"code\": 429, \"message\": \"API rate limit exceeded\", \"status\": \"error\"}";
        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.ExchangeRateResponse> res = gateway.fetchExchangeRate("GBP/USD");
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
        gateway.resetRateLimiter();
    }

    @Test
    @DisplayName("fetchTimeSeries handles 200 response with code 429 in body")
    void testFetchTimeSeriesBody429() {
        String json = "{\"code\": 429, \"message\": \"API rate limit exceeded\", \"status\": \"error\"}";
        mockServer.expect(requestTo("https://api.twelvedata.com/time_series?symbol=AAPL&interval=1day&start_date=2026-08-01&end_date=2026-08-28&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.TimeSeriesResponse> res = gateway.fetchTimeSeries("AAPL", "NASDAQ", "2026-08-01", "2026-08-28");
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
        gateway.resetRateLimiter();
    }

    @Test
    @DisplayName("fetchQuote, fetchExchangeRate, and fetchTimeSeries return empty when unconfigured, null input or rate limited")
    void testUnconfiguredOrNullInputs() {
        // Null inputs
        assertTrue(gateway.fetchQuote(null, null).isEmpty());
        assertTrue(gateway.fetchExchangeRate(null).isEmpty());
        assertTrue(gateway.fetchTimeSeries(null, "NASDAQ", "2026-01-01", "2026-01-02").isEmpty());

        // Unconfigured
        properties.getTwelvedata().setEnabled(false);
        assertTrue(gateway.fetchQuote("AAPL", null).isEmpty());
        assertTrue(gateway.fetchExchangeRate("GBP/USD").isEmpty());
        assertTrue(gateway.fetchTimeSeries("AAPL", "NASDAQ", "2026-01-01", "2026-01-02").isEmpty());

        // Rate limited
        properties.getTwelvedata().setEnabled(true);
        gateway.setBlockedUntil(Instant.now().plusSeconds(60));
        assertTrue(gateway.fetchQuote("AAPL", null).isEmpty());
        assertTrue(gateway.fetchExchangeRate("GBP/USD").isEmpty());
        assertTrue(gateway.fetchTimeSeries("AAPL", "NASDAQ", "2026-01-01", "2026-01-02").isEmpty());
        gateway.resetRateLimiter();
    }

    @Test
    @DisplayName("Twelve Data handles api-credits-resets-in header")
    void testApiCreditsResetsInHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("api-credits-resets-in", "25");

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("AAPL", null);
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
        gateway.resetRateLimiter();
    }

    @Test
    @DisplayName("Sliding window rate limits after 8 calls in 60s")
    void testSlidingWindowRateLimit() {
        String json = """
            {
                "symbol": "AAPL",
                "currency": "USD",
                "close": "200.00",
                "status": "ok"
            }
            """;

        for (int i = 0; i < 8; i++) {
            mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        }

        // Fire 8 calls
        for (int i = 0; i < 8; i++) {
            Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("AAPL", null);
            assertTrue(res.isPresent());
        }
        mockServer.verify();

        // 9th call should be proactively rate limited by the gateway sliding window without sending HTTP call
        assertTrue(gateway.isRateLimited());
        Optional<TwelveDataDtos.QuoteResponse> ninth = gateway.fetchQuote("AAPL", null);
        assertTrue(ninth.isEmpty());

        // Reset limiter
        gateway.resetRateLimiter();
        assertFalse(gateway.isRateLimited());
    }

    @Test
    @DisplayName("BlockedUntil in the past automatically unblocks")
    void testBlockedUntilInPast() {
        gateway.setBlockedUntil(Instant.now().minusSeconds(5));
        assertFalse(gateway.isRateLimited());
        assertNull(gateway.getBlockedUntil());
    }

    @Test
    @DisplayName("Null inputs return Optional.empty")
    void testNullInputs() {
        assertTrue(gateway.fetchQuote(null, null).isEmpty());
        assertTrue(gateway.fetchExchangeRate(null).isEmpty());
        assertTrue(gateway.fetchTimeSeries(null, null, null, null).isEmpty());
    }

    @Test
    @DisplayName("fetchQuote handles HTTP 500 server error")
    void testFetchQuoteHttp500() {
        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("AAPL", null);
        mockServer.verify();
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("fetchQuote handles HTTP 404 not found")
    void testFetchQuoteHttp404() {
        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=RPI&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("RPI", null);
        mockServer.verify();
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("fetchExchangeRate handles 429 in body")
    void testFetchExchangeRate429() {
        String json429 = """
            {
                "code": 429,
                "message": "Minute limit exceeded",
                "status": "error"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json429, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.ExchangeRateResponse> res = gateway.fetchExchangeRate("GBP/USD");
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
    }

    @Test
    @DisplayName("fetchExchangeRate handles HTTP 500")
    void testFetchExchangeRate500() {
        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<TwelveDataDtos.ExchangeRateResponse> res500 = gateway.fetchExchangeRate("GBP/USD");
        mockServer.verify();
        assertTrue(res500.isEmpty());
    }

    @Test
    @DisplayName("fetchTimeSeries handles 429")
    void testFetchTimeSeries429() {
        String json429 = """
            {
                "code": 429,
                "message": "Minute limit exceeded",
                "status": "error"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/time_series?symbol=AAPL&interval=1day&start_date=2026-08-01&end_date=2026-08-28&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json429, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.TimeSeriesResponse> res = gateway.fetchTimeSeries("AAPL", null, "2026-08-01", "2026-08-28");
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());
    }

    @Test
    @DisplayName("fetchTimeSeries handles HTTP 500")
    void testFetchTimeSeries500() {
        mockServer.expect(requestTo("https://api.twelvedata.com/time_series?symbol=AAPL&interval=1day&start_date=2026-08-01&end_date=2026-08-28&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<TwelveDataDtos.TimeSeriesResponse> res500 = gateway.fetchTimeSeries("AAPL", null, "2026-08-01", "2026-08-28");
        mockServer.verify();
        assertTrue(res500.isEmpty());
    }

    @Test
    @DisplayName("handle429 parsing edge cases: unparseable headers, non-positive, plain body")
    void testHandle429EdgeCases() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "invalid_num");
        headers.set("api-credits-resets-in", "-5");

        gateway.handle429(headers, "Daily limit reached, no hint");
        assertTrue(gateway.isRateLimited());

        gateway.handle429(null, null);
        assertTrue(gateway.isRateLimited());
    }

    @Test
    @DisplayName("fetchQuote handles non-429 error code in response body")
    void testFetchQuoteNon429CodeInBody() {
        String json = """
            {
                "code": 400,
                "message": "Symbol not found",
                "status": "error"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=BAD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.QuoteResponse> res = gateway.fetchQuote("BAD", null);
        mockServer.verify();
        assertTrue(res.isPresent());
        assertEquals(400, res.get().code());
    }

    @Test
    @DisplayName("fetchExchangeRate handles non-429 error code in response body")
    void testFetchExchangeRateNon429CodeInBody() {
        String json = """
            {
                "code": 400,
                "message": "Pair not found",
                "status": "error"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=BAD/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.ExchangeRateResponse> res = gateway.fetchExchangeRate("BAD/USD");
        mockServer.verify();
        assertTrue(res.isPresent());
        assertEquals(400, res.get().code());
    }

    @Test
    @DisplayName("fetchTimeSeries handles non-429 error code in response body")
    void testFetchTimeSeriesNon429CodeInBody() {
        String json = """
            {
                "code": 400,
                "message": "Invalid range",
                "status": "error"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/time_series?symbol=BAD&interval=1day&start_date=2026-08-01&end_date=2026-08-28&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<TwelveDataDtos.TimeSeriesResponse> res = gateway.fetchTimeSeries("BAD", null, "2026-08-01", "2026-08-28");
        mockServer.verify();
        assertTrue(res.isPresent());
        assertEquals(400, res.get().code());
    }

    @Test
    @DisplayName("handle429 parses positive Retry-After and api-credits-resets-in headers")
    void testHandle429PositiveHeaders() {
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set(HttpHeaders.RETRY_AFTER, "120");
        gateway.handle429(headers1, null);
        assertTrue(gateway.isRateLimited());

        gateway.resetRateLimiter();

        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("api-credits-resets-in", "45");
        gateway.handle429(headers2, null);
        assertTrue(gateway.isRateLimited());

        gateway.resetRateLimiter();

        HttpHeaders blankHeaders = new HttpHeaders();
        blankHeaders.set(HttpHeaders.RETRY_AFTER, "   ");
        blankHeaders.set("api-credits-resets-in", "   ");
        gateway.handle429(blankHeaders, "Standard cooldown without minute keyword");
        assertTrue(gateway.isRateLimited());
    }

    @Test
    @DisplayName("fetchExchangeRate and fetchTimeSeries return empty when unconfigured or rate-limited")
    void testUnconfiguredAndRateLimitedEdgeCases() {
        properties.getTwelvedata().setEnabled(false);
        assertTrue(gateway.fetchExchangeRate("GBP/USD").isEmpty());
        assertTrue(gateway.fetchTimeSeries("AAPL", null, "2026-01-01", "2026-01-02").isEmpty());

        properties.getTwelvedata().setEnabled(true);
        gateway.setBlockedUntil(Instant.now().plusSeconds(60));
        assertTrue(gateway.fetchExchangeRate("GBP/USD").isEmpty());
        assertTrue(gateway.fetchTimeSeries("AAPL", null, "2026-01-01", "2026-01-02").isEmpty());
    }
}

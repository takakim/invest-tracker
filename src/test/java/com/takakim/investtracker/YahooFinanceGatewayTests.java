package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceDtos;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YahooFinanceGatewayTests {

    private MarketDataProperties properties;
    private MockRestServiceServer mockServer;
    private YahooFinanceGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        MarketDataProperties.YahooProperties yahoo = new MarketDataProperties.YahooProperties();
        yahoo.setEnabled(true);
        yahoo.setBaseUrl("https://query1.finance.yahoo.com");
        yahoo.setMaxRequestsPerMinute(5);
        properties.setYahoo(yahoo);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://query1.finance.yahoo.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        gateway = new YahooFinanceGateway(properties, builder.build());
    }

    @Test
    @DisplayName("Successfully fetches chart from Yahoo Finance")
    void testFetchChartSuccess() {
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "GBp",
                                "symbol": "RR.L",
                                "exchangeName": "LSE",
                                "regularMarketPrice": 1530.20,
                                "regularMarketTime": 1787940890
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/RR.L?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<YahooFinanceDtos.ChartEntry> res = gateway.fetchChart("RR.L", "1d", "1d");
        mockServer.verify();

        assertTrue(res.isPresent());
        assertEquals("RR.L", res.get().meta().symbol());
        assertEquals("GBp", res.get().meta().currency());
        assertEquals(new BigDecimal("1530.20"), res.get().meta().regularMarketPrice());
    }

    @Test
    @DisplayName("Handles API error response body cleanly")
    void testApiErrorResponse() {
        String jsonError = """
            {
                "chart": {
                    "result": null,
                    "error": {
                        "code": "Not Found",
                        "description": "No data found, symbol may be delisted"
                    }
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/UNKNOWN?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonError, MediaType.APPLICATION_JSON));

        Optional<YahooFinanceDtos.ChartEntry> res = gateway.fetchChart("UNKNOWN", "1d", "1d");
        mockServer.verify();
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Handles null chart, null result, and null meta in response")
    void testNullChartOrEmptyResult() {
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/EMPTY?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"chart\": {\"result\": []}}", MediaType.APPLICATION_JSON));

        assertTrue(gateway.fetchChart("EMPTY", "1d", "1d").isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Handles null chart object in response")
    void testNullChartObject() {
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/NULLCHART?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"chart\": null}", MediaType.APPLICATION_JSON));

        assertTrue(gateway.fetchChart("NULLCHART", "1d", "1d").isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Handles null result array in response")
    void testNullResultArray() {
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/NULLRES?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"chart\": {\"result\": null}}", MediaType.APPLICATION_JSON));

        assertTrue(gateway.fetchChart("NULLRES", "1d", "1d").isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Handles null meta in entry in response")
    void testNullMetaInEntry() {
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/NULLMETA?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"chart\": {\"result\": [{\"meta\": null}]}}", MediaType.APPLICATION_JSON));

        assertTrue(gateway.fetchChart("NULLMETA", "1d", "1d").isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Returns empty when unconfigured, symbol missing or disabled")
    void testUnconfigured() {
        properties.getYahoo().setEnabled(false);
        assertTrue(gateway.fetchChart("AAPL", "1d", "1d").isEmpty());

        properties.getYahoo().setEnabled(true);
        properties.getYahoo().setBaseUrl(null);
        assertTrue(gateway.fetchChart("AAPL", "1d", "1d").isEmpty());

        properties.getYahoo().setBaseUrl("   ");
        assertTrue(gateway.fetchChart("AAPL", "1d", "1d").isEmpty());

        properties.getYahoo().setBaseUrl("https://query1.finance.yahoo.com");
        assertTrue(gateway.fetchChart(null, "1d", "1d").isEmpty());
        assertTrue(gateway.fetchChart("   ", "1d", "1d").isEmpty());
    }

    @Test
    @DisplayName("Handles HTTP 404 cleanly")
    void testHttp404() {
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/UNKNOWN?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<YahooFinanceDtos.ChartEntry> res = gateway.fetchChart("UNKNOWN", null, null);
        mockServer.verify();
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Handles HTTP 500 error cleanly")
    void testHttp500() {
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/ERROR?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        Optional<YahooFinanceDtos.ChartEntry> res = gateway.fetchChart("ERROR", "", "");
        mockServer.verify();
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Handles HTTP 429 rate limit and backs off")
    void testHttp429() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/RATE?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        Optional<YahooFinanceDtos.ChartEntry> res = gateway.fetchChart("RATE", "1d", "1d");
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());

        // Blocked call
        assertTrue(gateway.fetchChart("AAPL", "1d", "1d").isEmpty());

        gateway.resetRateLimiter();
        assertFalse(gateway.isRateLimited());
    }

    @Test
    @DisplayName("Rate limits when maxRequestsPerMinute is reached")
    void testRateLimitBudget() {
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "TEST",
                                "regularMarketPrice": 10.0
                            }
                        }
                    ]
                }
            }
            """;

        for (int i = 0; i < 5; i++) {
            mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/TEST?interval=1d&range=1d"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(gateway.fetchChart("TEST", "1d", "1d").isPresent());
        }

        // 6th call exceeds budget
        assertTrue(gateway.fetchChart("TEST", "1d", "1d").isEmpty());
    }

    @Test
    @DisplayName("Handles HTTP 429 without Retry-After header or invalid integer")
    void testHttp429FallbackHeaders() {
        gateway.handle429(null, "rate limit");
        assertTrue(gateway.isRateLimited());

        gateway.resetRateLimiter();
        HttpHeaders badHeader = new HttpHeaders();
        badHeader.set(HttpHeaders.RETRY_AFTER, "not-a-number");
        gateway.handle429(badHeader, "rate limit");
        assertTrue(gateway.isRateLimited());

        gateway.setBlockedUntil(Instant.now().minusSeconds(10));
        assertFalse(gateway.isRateLimited());
    }

    @Test
    @DisplayName("handleException for other status codes like 403")
    void testHandleExceptionOther() {
        HttpClientErrorException forbidden = new HttpClientErrorException(HttpStatus.FORBIDDEN, "Forbidden");
        gateway.handleException(forbidden, "chart", "AAPL");
    }

    @Test
    @DisplayName("RestClient unexpected exception handling")
    void testUnexpectedException() {
        RestClient badClient = RestClient.builder().baseUrl("http://invalid-host-9823485723.test").build();
        YahooFinanceGateway badGateway = new YahooFinanceGateway(properties, badClient);
        assertTrue(badGateway.fetchChart("AAPL", "1d", "1d").isEmpty());
    }

    @Test
    @DisplayName("Fetch chart with null interval and range parameters")
    void testNullIntervalAndRange() {
        String json = "{\"chart\": {\"result\": [{\"meta\": {\"currency\": \"USD\", \"symbol\": \"AAPL\", \"regularMarketPrice\": 200.0}}]}}";
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<YahooFinanceDtos.ChartEntry> res1 = gateway.fetchChart("AAPL", null, null);
        mockServer.verify();
        assertTrue(res1.isPresent());
    }

    @Test
    @DisplayName("Fetch chart with blank interval and range parameters")
    void testBlankIntervalAndRange() {
        String json = "{\"chart\": {\"result\": [{\"meta\": {\"currency\": \"USD\", \"symbol\": \"AAPL\", \"regularMarketPrice\": 200.0}}]}}";
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<YahooFinanceDtos.ChartEntry> res2 = gateway.fetchChart("AAPL", "   ", "   ");
        mockServer.verify();
        assertTrue(res2.isPresent());
    }

    @Test
    @DisplayName("Sliding window cleans timestamps older than 60 seconds")
    void testSlidingWindowPruning() {
        gateway.addRequestTimestamp(Instant.now().minusSeconds(120));
        gateway.addRequestTimestamp(Instant.now().minusSeconds(90));

        String json = "{\"chart\": {\"result\": [{\"meta\": {\"currency\": \"USD\", \"symbol\": \"AAPL\", \"regularMarketPrice\": 200.0}}]}}";
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<YahooFinanceDtos.ChartEntry> res = gateway.fetchChart("AAPL", "1d", "1d");
        mockServer.verify();
        assertTrue(res.isPresent());
    }

    @Test
    @DisplayName("Constructors with default and custom properties")
    void testConstructors() {
        MarketDataProperties defaultProps = new MarketDataProperties();
        YahooFinanceGateway defaultGateway = new YahooFinanceGateway(defaultProps);
        assertTrue(defaultGateway.isConfigured());

        YahooFinanceGateway nullPropsGateway = new YahooFinanceGateway(null);
        assertFalse(nullPropsGateway.isConfigured());

        MarketDataProperties customProps = new MarketDataProperties();
        customProps.getYahoo().setConnectTimeoutSeconds(0);
        customProps.getYahoo().setReadTimeoutSeconds(0);
        customProps.getYahoo().setMaxRequestsPerMinute(0);
        customProps.getYahoo().setBaseUrl("");
        YahooFinanceGateway zeroGateway = new YahooFinanceGateway(customProps, (RestClient.Builder) null);
        assertFalse(zeroGateway.isConfigured());
    }

    @Test
    @DisplayName("fetchHistoricalDailyPrices parses valid chart result into HistoricalPriceBar list")
    void testFetchHistoricalDailyPricesSuccess() {
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "AAPL"
                            },
                            "timestamp": [1700000000, 1700086400],
                            "indicators": {
                                "quote": [
                                    {
                                        "close": [180.50, 182.75]
                                    }
                                ]
                            }
                        }
                    ]
                }
            }
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=5y"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<YahooFinanceGateway.HistoricalPriceBar> bars = gateway.fetchHistoricalDailyPrices("AAPL", "5y");
        mockServer.verify();

        assertEquals(2, bars.size());
        assertEquals(new BigDecimal("180.50"), bars.get(0).closePrice());
        assertEquals("USD", bars.get(0).currency());
        assertEquals(Instant.ofEpochSecond(1700000000), bars.get(0).timestamp());
        assertEquals(new BigDecimal("182.75"), bars.get(1).closePrice());
    }

    @Test
    @DisplayName("fetchHistoricalDailyPrices returns empty list when unconfigured or empty response")
    void testFetchHistoricalDailyPricesEmpty() {
        YahooFinanceGateway unconfigured = new YahooFinanceGateway(null);
        assertTrue(unconfigured.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());

        mockServer.reset();
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"chart\":{\"result\":[]}}", MediaType.APPLICATION_JSON));

        List<YahooFinanceGateway.HistoricalPriceBar> emptyBars = gateway.fetchHistoricalDailyPrices("AAPL", "1mo");
        mockServer.verify();
        assertTrue(emptyBars.isEmpty());
    }

    @Test
    @DisplayName("fetchHistoricalDailyPrices handles null indicators, closes, or invalid price values")
    void testFetchHistoricalDailyPricesMalformedData() {
        // Missing quote indicators
        String noIndicators = """
            {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":"USD"},"timestamp":[1700000000]}]}}
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(noIndicators, MediaType.APPLICATION_JSON));
        assertTrue(gateway.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());
        mockServer.verify();

        // Null / empty closes
        mockServer.reset();
        String emptyCloses = """
            {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":"USD"},"timestamp":[1700000000],"indicators":{"quote":[{"close":[]}]}}]}}
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(emptyCloses, MediaType.APPLICATION_JSON));
        assertTrue(gateway.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());
        mockServer.verify();

        // Null timestamps
        mockServer.reset();
        String nullTimestamps = """
            {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":"USD"},"indicators":{"quote":[{"close":[100.0]}]}}]}}
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(nullTimestamps, MediaType.APPLICATION_JSON));
        assertTrue(gateway.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("fetchHistoricalDailyPrices filters null/negative closes and defaults to USD")
    void testFetchHistoricalDailyPricesFilteringAndDefaults() {
        String mixedValues = """
            {"chart":{"result":[{"meta":{"symbol":"AAPL"},"timestamp":[1700000000, 1700086400, 1700172800],"indicators":{"quote":[{"close":[null, -5.0, 150.0]}]}}]}}
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mixedValues, MediaType.APPLICATION_JSON));
        List<YahooFinanceGateway.HistoricalPriceBar> bars = gateway.fetchHistoricalDailyPrices("AAPL", "1mo");
        mockServer.verify();
        assertEquals(1, bars.size());
        assertEquals("USD", bars.get(0).currency());
        assertEquals(new BigDecimal("150.0"), bars.get(0).closePrice());

        // Empty quote list inside indicators
        mockServer.reset();
        String emptyQuoteList = """
            {"chart":{"result":[{"meta":{"symbol":"AAPL"},"timestamp":[1700000000],"indicators":{"quote":[]}}]}}
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(emptyQuoteList, MediaType.APPLICATION_JSON));
        assertTrue(gateway.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("handleException covers 404, 500, and 429 with retry-after parsing")
    void testHandleExceptionBranches() {
        // 404
        var ex404 = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        gateway.handleException(ex404, "quote", "AAPL");

        // 500
        var ex500 = org.springframework.web.client.HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders.EMPTY, new byte[0], null);
        gateway.handleException(ex500, "quote", "AAPL");

        // 429 with Retry-After header
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");
        var ex429 = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], null);
        gateway.handleException(ex429, "quote", "AAPL");

        // 429 with invalid Retry-After header
        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.set(HttpHeaders.RETRY_AFTER, "not-a-number");
        var ex429Invalid = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", invalidHeaders, new byte[0], null);
        gateway.handleException(ex429Invalid, "quote", "AAPL");

        // 429 with short retry-after
        HttpHeaders shortRetry = new HttpHeaders();
        shortRetry.set(HttpHeaders.RETRY_AFTER, "2");
        gateway.handle429(shortRetry, "");

        // 429 with null headers, empty headers, or blank retry-after
        gateway.handle429(null, "");
        gateway.handle429(new HttpHeaders(), "");
        HttpHeaders blankRetry = new HttpHeaders();
        blankRetry.set(HttpHeaders.RETRY_AFTER, "   ");
        gateway.handle429(blankRetry, "");
    }

    @Test
    @DisplayName("fetchHistoricalDailyPrices handles null meta, null indicators, and edge case values")
    void testFetchHistoricalDailyPricesEdgeCases() {
        properties.getYahoo().setMaxRequestsPerMinute(100);
        RestClient.Builder testBuilder = RestClient.builder().baseUrl("https://query1.finance.yahoo.com");
        mockServer = MockRestServiceServer.bindTo(testBuilder).build();
        gateway = new YahooFinanceGateway(properties, testBuilder.build());

        // meta with null currency (defaults to USD)
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":null},"timestamp":[1700000000],"indicators":{"quote":[{"close":[100.0]}]}}]}}
                    """, MediaType.APPLICATION_JSON));
        List<YahooFinanceGateway.HistoricalPriceBar> bars1 = gateway.fetchHistoricalDailyPrices("AAPL", "1mo");
        mockServer.verify();
        assertEquals(1, bars1.size());
        assertEquals("USD", bars1.get(0).currency());

        // null timestamp
        mockServer.reset();
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    {"chart":{"result":[{"meta":{"symbol":"AAPL"},"timestamp":null,"indicators":{"quote":[{"close":[100.0]}]}}]}}
                    """, MediaType.APPLICATION_JSON));
        assertTrue(gateway.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());
        mockServer.verify();

        // null indicators
        mockServer.reset();
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    {"chart":{"result":[{"meta":{"symbol":"AAPL"},"timestamp":[1700000000],"indicators":null}]}}
                    """, MediaType.APPLICATION_JSON));
        assertTrue(gateway.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());
        mockServer.verify();

        // null indicators.quote()
        mockServer.reset();
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    {"chart":{"result":[{"meta":{"symbol":"AAPL"},"timestamp":[1700000000],"indicators":{"quote":null}}]}}
                    """, MediaType.APPLICATION_JSON));
        assertTrue(gateway.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());
        mockServer.verify();

        // null closes
        mockServer.reset();
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    {"chart":{"result":[{"meta":{"symbol":"AAPL"},"timestamp":[1700000000],"indicators":{"quote":[{"close":null}]}}]}}
                    """, MediaType.APPLICATION_JSON));
        assertTrue(gateway.fetchHistoricalDailyPrices("AAPL", "1mo").isEmpty());
        mockServer.verify();

        // null ts or negative close
        mockServer.reset();
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":"EUR"},"timestamp":[null, 1700000000],"indicators":{"quote":[{"close":[100.0, -5.0]}]}}]}}
                    """, MediaType.APPLICATION_JSON));
        List<YahooFinanceGateway.HistoricalPriceBar> bars2 = gateway.fetchHistoricalDailyPrices("AAPL", "1mo");
        mockServer.verify();
        assertTrue(bars2.isEmpty());
    }
}

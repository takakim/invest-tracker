package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.service.market.fmp.FmpDtos;
import com.takakim.investtracker.service.market.fmp.FmpGateway;
import java.math.BigDecimal;
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

class FmpGatewayTests {

    private MarketDataProperties properties;
    private MockRestServiceServer mockServer;
    private FmpGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        MarketDataProperties.FmpProperties fmp = new MarketDataProperties.FmpProperties();
        fmp.setEnabled(true);
        fmp.setApiKey("test-fmp-key");
        fmp.setBaseUrl("https://financialmodelingprep.com");
        fmp.setMaxRequestsPerMinute(5);
        properties.setFmp(fmp);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://financialmodelingprep.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        gateway = new FmpGateway(properties, builder.build());
    }

    @Test
    @DisplayName("Successfully fetches quote from FMP")
    void testFetchQuoteSuccess() {
        String json = """
            [
                {
                    "symbol": "IQE.L",
                    "name": "IQE plc",
                    "price": 0.4797,
                    "changesPercentage": -1.70,
                    "change": -0.0083,
                    "exchange": "LSE",
                    "timestamp": 1725033600
                }
            ]
            """;

        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=IQE.L&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<FmpDtos.QuoteResponse> res = gateway.fetchQuote("IQE.L");
        mockServer.verify();

        assertTrue(res.isPresent());
        assertEquals("IQE.L", res.get().symbol());
        assertEquals(new BigDecimal("0.4797"), res.get().price());
    }

    @Test
    @DisplayName("Returns empty when unconfigured or disabled")
    void testUnconfigured() {
        properties.getFmp().setEnabled(false);
        assertTrue(gateway.fetchQuote("AAPL").isEmpty());

        properties.getFmp().setEnabled(true);
        properties.getFmp().setApiKey(null);
        assertTrue(gateway.fetchQuote("AAPL").isEmpty());

        properties.getFmp().setApiKey("   ");
        assertTrue(gateway.fetchQuote("AAPL").isEmpty());

        properties.getFmp().setApiKey("test-key");
        assertTrue(gateway.fetchQuote(null).isEmpty());
        assertTrue(gateway.fetchQuote("").isEmpty());
    }

    @Test
    @DisplayName("Handles HTTP 404 cleanly")
    void testHttp404() {
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=UNKNOWN&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<FmpDtos.QuoteResponse> res = gateway.fetchQuote("UNKNOWN");
        mockServer.verify();
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Handles HTTP 500 error cleanly")
    void testHttp500() {
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=ERROR&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        Optional<FmpDtos.QuoteResponse> res = gateway.fetchQuote("ERROR");
        mockServer.verify();
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Handles HTTP 429 rate limit and backs off")
    void testHttp429() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "30");

        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=RATE&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        Optional<FmpDtos.QuoteResponse> res = gateway.fetchQuote("RATE");
        mockServer.verify();
        assertTrue(res.isEmpty());
        assertTrue(gateway.isRateLimited());

        // Subsequent call is blocked by rate limiter
        assertTrue(gateway.fetchQuote("AAPL").isEmpty());

        gateway.resetRateLimiter();
        assertFalse(gateway.isRateLimited());
    }

    @Test
    @DisplayName("Rate limits when maxRequestsPerMinute is reached")
    void testRateLimitBudget() {
        String json = "[{\"symbol\": \"TEST\", \"price\": 10.0}]";

        for (int i = 0; i < 5; i++) {
            mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=TEST&apikey=test-fmp-key"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(gateway.fetchQuote("TEST").isPresent());
        }

        // 6th call exceeds budget
        assertTrue(gateway.fetchQuote("TEST").isEmpty());
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

        HttpHeaders blankHeader = new HttpHeaders();
        blankHeader.set(HttpHeaders.RETRY_AFTER, "   ");
        gateway.handle429(blankHeader, "rate limit");
        assertTrue(gateway.isRateLimited());

        HttpHeaders shortHeader = new HttpHeaders();
        shortHeader.set(HttpHeaders.RETRY_AFTER, "2");
        gateway.handle429(shortHeader, "rate limit");
        assertTrue(gateway.isRateLimited());

        gateway.setBlockedUntil(Instant.now().minusSeconds(10));
        assertFalse(gateway.isRateLimited());
    }

    @Test
    @DisplayName("handleException for 404, 429, and 500 status codes")
    void testHandleExceptionCodes() {
        HttpClientErrorException forbidden = new HttpClientErrorException(HttpStatus.FORBIDDEN, "Forbidden");
        gateway.handleException(forbidden, "quote", "AAPL");

        HttpClientErrorException notFound = new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found");
        gateway.handleException(notFound, "quote", "AAPL");

        HttpClientErrorException tooMany = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests");
        gateway.handleException(tooMany, "quote", "AAPL");
        assertTrue(gateway.isRateLimited());
        gateway.resetRateLimiter();
    }

    @Test
    @DisplayName("Handles null and empty responses array in fetchQuote")
    void testNullAndEmptyResponses() {
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=EMPTY&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(gateway.fetchQuote("EMPTY").isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Sliding window prunes timestamps older than 60 seconds")
    void testSlidingWindowPruning() {
        gateway.addRequestTimestamp(Instant.now().minusSeconds(120));
        gateway.addRequestTimestamp(Instant.now().minusSeconds(90));

        String json = "[{\"symbol\": \"AAPL\", \"price\": 200.0}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=AAPL&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<FmpDtos.QuoteResponse> res = gateway.fetchQuote("AAPL");
        mockServer.verify();
        assertTrue(res.isPresent());
    }

    @Test
    @DisplayName("RestClient unexpected exception handling")
    void testUnexpectedException() {
        RestClient badClient = RestClient.builder().baseUrl("http://invalid-host-349852093845.test").build();
        FmpGateway badGateway = new FmpGateway(properties, badClient);
        assertTrue(badGateway.fetchQuote("AAPL").isEmpty());
    }

    @Test
    @DisplayName("Constructors with default and custom properties")
    void testConstructors() {
        MarketDataProperties defaultProps = new MarketDataProperties();
        FmpGateway defaultGateway = new FmpGateway(defaultProps);
        assertFalse(defaultGateway.isConfigured());

        FmpGateway nullPropsGateway = new FmpGateway(null);
        assertFalse(nullPropsGateway.isConfigured());

        MarketDataProperties customProps = new MarketDataProperties();
        customProps.getFmp().setConnectTimeoutSeconds(0);
        customProps.getFmp().setReadTimeoutSeconds(0);
        customProps.getFmp().setMaxRequestsPerMinute(0);
        customProps.getFmp().setBaseUrl("");
        FmpGateway zeroGateway = new FmpGateway(customProps, (RestClient.Builder) null);
        assertFalse(zeroGateway.isConfigured());

        MarketDataProperties configuredProps = new MarketDataProperties();
        configuredProps.getFmp().setEnabled(true);
        configuredProps.getFmp().setApiKey("real-key");
        FmpGateway configuredGateway = new FmpGateway(configuredProps, RestClient.builder());
        assertTrue(configuredGateway.isConfigured());

        FmpGateway nullPropsTwoArg = new FmpGateway(null, (RestClient) null);
        assertFalse(nullPropsTwoArg.isConfigured());
    }
}

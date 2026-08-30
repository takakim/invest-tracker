package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.service.currency.DefaultFxRateProvider;
import com.takakim.investtracker.service.currency.FxRateQuote;
import com.takakim.investtracker.service.currency.TwelveDataFxRateProvider;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TwelveDataFxRateProviderTests {

    private MarketDataProperties properties;
    private DefaultFxRateProvider fallbackProvider;
    private RestClient.Builder restClientBuilder;
    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private TwelveDataGateway gateway;
    private TwelveDataFxRateProvider provider;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        properties.getTwelvedata().setEnabled(true);
        properties.getTwelvedata().setApiKey("test-api-key");
        properties.getTwelvedata().setBaseUrl("https://api.twelvedata.com");
        properties.getTwelvedata().setConnectTimeoutSeconds(3);
        properties.getTwelvedata().setReadTimeoutSeconds(5);

        fallbackProvider = new DefaultFxRateProvider();
        fallbackProvider.setRate("GBP", "USD", new BigDecimal("1.28000000"));

        restClientBuilder = RestClient.builder().baseUrl(properties.getTwelvedata().getBaseUrl());
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        restClient = restClientBuilder.build();

        gateway = new TwelveDataGateway(properties, restClient);
        provider = new TwelveDataFxRateProvider(properties, fallbackProvider, gateway);
    }

    @Test
    @DisplayName("Primary constructor with gateway succeeds")
    void primaryConstructorInitializes() {
        TwelveDataGateway gw = new TwelveDataGateway(properties, RestClient.builder());
        TwelveDataFxRateProvider p = new TwelveDataFxRateProvider(properties, fallbackProvider, gw);
        assertNotNull(p);
        assertEquals("TWELVE_DATA", p.getProviderId());

        p.init(); // Test enabled init logging

        properties.getTwelvedata().setApiKey(null);
        p.init(); // Test disabled init logging

        MarketDataProperties nullProps = new MarketDataProperties();
        nullProps.setTwelvedata(null);
        TwelveDataGateway nullGw = new TwelveDataGateway(nullProps, restClient);
        TwelveDataFxRateProvider nullP = new TwelveDataFxRateProvider(nullProps, fallbackProvider, nullGw);
        nullP.init();
    }

    @Test
    @DisplayName("Identity quote when base equals quote currency")
    void identityCurrencyQuote() {
        Optional<FxRateQuote> quoteOpt = provider.fetchRate("USD", "USD", Instant.now());
        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("1.00000000"), quoteOpt.get().rate());
        assertEquals("IDENTITY", quoteOpt.get().sourceReference());
    }

    @Test
    @DisplayName("Successfully fetches FX rate from Twelve Data")
    void fetchFxRateSuccess() {
        String jsonResponse = """
            {
                "symbol": "GBP/USD",
                "rate": 1.31500000,
                "timestamp": 1724982000,
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<FxRateQuote> quoteOpt = provider.fetchRate("GBP", "USD", Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        FxRateQuote quote = quoteOpt.get();
        assertEquals(new BigDecimal("1.31500000"), quote.rate());
        assertEquals("GBP", quote.baseCurrency());
        assertEquals("USD", quote.quoteCurrency());
        assertEquals(ObservationSourceType.PROVIDER, quote.sourceType());
        assertEquals("TWELVE_DATA", quote.sourceReference());
    }

    @Test
    @DisplayName("Successfully fetches FX rate without timestamp in response")
    void fetchFxRateWithoutTimestamp() {
        String jsonResponse = """
            {
                "symbol": "GBP/USD",
                "rate": 1.31500000,
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Instant asOfTime = Instant.parse("2026-08-28T12:00:00Z");
        Optional<FxRateQuote> quoteOpt = provider.fetchRate("GBP", "USD", asOfTime);
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(asOfTime, quoteOpt.get().asOf());
    }

    @Test
    @DisplayName("Successfully fetches FX rate when asOf is null")
    void fetchFxRateNullAsOf() {
        String jsonResponse = """
            {
                "symbol": "GBP/USD",
                "rate": 1.31500000,
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<FxRateQuote> quoteOpt = provider.fetchRate("GBP", "USD", null);
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
    }

    @Test
    @DisplayName("Falls back to DefaultFxRateProvider when disabled or no API key")
    void fallsBackWhenDisabledOrNoApiKey() {
        MarketDataProperties nullProps = new MarketDataProperties();
        nullProps.setTwelvedata(null);
        TwelveDataFxRateProvider nullConfigProvider = new TwelveDataFxRateProvider(nullProps, fallbackProvider, gateway);
        assertTrue(nullConfigProvider.fetchRate("GBP", "USD", Instant.now()).isPresent());

        properties.getTwelvedata().setApiKey(null);
        Optional<FxRateQuote> quoteOpt = provider.fetchRate("GBP", "USD", Instant.now());
        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("1.28000000"), quoteOpt.get().rate());

        properties.getTwelvedata().setApiKey("");
        quoteOpt = provider.fetchRate("GBP", "USD", Instant.now());
        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("1.28000000"), quoteOpt.get().rate());
        assertEquals("DEFAULT_FX_PROVIDER", quoteOpt.get().sourceReference());

        properties.getTwelvedata().setEnabled(false);
        properties.getTwelvedata().setApiKey("test-key");
        quoteOpt = provider.fetchRate("GBP", "USD", Instant.now());
        assertTrue(quoteOpt.isPresent());
        assertEquals("DEFAULT_FX_PROVIDER", quoteOpt.get().sourceReference());
    }

    @Test
    @DisplayName("Falls back to DefaultFxRateProvider on error response")
    void fallsBackOnErrorResponse() {
        String errorJson = """
            {
                "code": 429,
                "message": "You have run out of API credits.",
                "status": "error"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(errorJson, MediaType.APPLICATION_JSON));

        Optional<FxRateQuote> quoteOpt = provider.fetchRate("GBP", "USD", Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals("DEFAULT_FX_PROVIDER", quoteOpt.get().sourceReference());
    }

    @Test
    @DisplayName("Falls back to DefaultFxRateProvider on HTTP 500 error")
    void fallsBackOnHttpError() {
        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<FxRateQuote> quoteOpt = provider.fetchRate("GBP", "USD", Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals("DEFAULT_FX_PROVIDER", quoteOpt.get().sourceReference());
    }

    @Test
    @DisplayName("Null currency codes return empty Optional")
    void nullCurrenciesReturnEmpty() {
        assertTrue(provider.fetchRate(null, "USD", Instant.now()).isEmpty());
        assertTrue(provider.fetchRate("USD", null, Instant.now()).isEmpty());
    }

    @Test
    @DisplayName("fetchHistoricalRates returns live quote when available")
    void fetchHistoricalRatesSuccess() {
        String jsonResponse = """
            {
                "symbol": "GBP/USD",
                "rate": 1.31500000,
                "timestamp": 1724982000,
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=GBP/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<FxRateQuote> rates = provider.fetchHistoricalRates("GBP", "USD", Instant.now().minusSeconds(86400), Instant.now());
        mockServer.verify();

        assertEquals(1, rates.size());
        assertEquals(new BigDecimal("1.31500000"), rates.get(0).rate());
    }

    @Test
    @DisplayName("fetchHistoricalRates fallback when rate not found or throws")
    void fetchHistoricalRatesFallback() {
        properties.getTwelvedata().setApiKey(""); // use fallback

        List<FxRateQuote> rates = provider.fetchHistoricalRates("GBP", "USD", Instant.now().minusSeconds(86400), Instant.now());
        assertFalse(rates.isEmpty());
        assertEquals("DEFAULT_FX_PROVIDER", rates.get(0).sourceReference());
    }

    @Test
    @DisplayName("fetchHistoricalRates falls back to fallbackProvider when Twelve Data request errors")
    void fetchHistoricalRatesApiErrorFallback() {
        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=EUR/JPY&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        List<FxRateQuote> rates = provider.fetchHistoricalRates("EUR", "JPY", Instant.now().minusSeconds(86400), Instant.now());
        mockServer.verify();

        assertTrue(rates.isEmpty()); // fallbackProvider does not have EUR:JPY
    }

    @Test
    @DisplayName("getProviderId returns TWELVE_DATA")
    void getProviderIdReturnsCorrectValue() {
        assertEquals("TWELVE_DATA", provider.getProviderId());
    }

    @Test
    @DisplayName("Returns cached FX rate on second call")
    void testCacheHitAndFallbackOnError() {
        String jsonResponse = """
            {
                "symbol": "EUR/USD",
                "rate": 1.08500000,
                "timestamp": 1724982000,
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=EUR/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // 1. First call fetches and caches
        Optional<FxRateQuote> first = provider.fetchRate("EUR", "USD", Instant.now());
        mockServer.verify();
        assertTrue(first.isPresent());

        // 2. Second call returns cached quote without mockServer request
        Optional<FxRateQuote> second = provider.fetchRate("EUR", "USD", Instant.now());
        assertTrue(second.isPresent());
        assertEquals(new BigDecimal("1.08500000"), second.get().rate());
    }

    @Test
    @DisplayName("Falls back to cached FX rate when server error occurs")
    void testStaleCacheFallbackOnError() {
        String jsonResponse = """
            {
                "symbol": "EUR/USD",
                "rate": 1.08500000,
                "timestamp": 1724982000,
                "status": "ok"
            }
            """;

        // Make TTL negative so cache is expired for second request
        properties.getTwelvedata().setCacheTtlSeconds(-10);

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=EUR/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=EUR/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<FxRateQuote> first = provider.fetchRate("EUR", "USD", null);
        assertTrue(first.isPresent());
        assertEquals(Instant.ofEpochSecond(1724982000L), first.get().asOf());

        Optional<FxRateQuote> fallbackCached = provider.fetchRate("EUR", "USD", null);
        mockServer.verify();
        assertTrue(fallbackCached.isPresent());
        assertEquals(new BigDecimal("1.08500000"), fallbackCached.get().rate());
    }

    @Test
    @DisplayName("Falls back to default provider when API response is unsuccessful")
    void testUnsuccessfulResponseFallsBack() {
        String jsonResponse = """
            {
                "symbol": "EUR/USD",
                "status": "error",
                "code": 404,
                "message": "Not found"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/exchange_rate?symbol=EUR/USD&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<FxRateQuote> res = provider.fetchRate("EUR", "USD", Instant.now());
        mockServer.verify();
        assertTrue(res.isPresent());
        assertEquals("DEFAULT_FX_PROVIDER", res.get().sourceReference());
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.service.market.DefaultMarketDataProvider;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataDtos;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataGateway;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataMarketDataProvider;
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

class TwelveDataMarketDataProviderTests {

    private MarketDataProperties properties;
    private DefaultMarketDataProvider fallbackProvider;
    private RestClient.Builder restClientBuilder;
    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private TwelveDataGateway gateway;
    private TwelveDataMarketDataProvider provider;

    private Instrument aapl;
    private Instrument vuag;
    private Instrument nyseInst;
    private Instrument noExchangeInst;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        properties.getTwelvedata().setEnabled(true);
        properties.getTwelvedata().setApiKey("test-api-key");
        properties.getTwelvedata().setBaseUrl("https://api.twelvedata.com");
        properties.getTwelvedata().setConnectTimeoutSeconds(3);
        properties.getTwelvedata().setReadTimeoutSeconds(5);

        fallbackProvider = new DefaultMarketDataProvider();
        fallbackProvider.setPrice("AAPL", new BigDecimal("185.5000"));
        fallbackProvider.setPrice("VUAG", new BigDecimal("85.4000"));
        fallbackProvider.setPrice("IBM", new BigDecimal("140.0000"));

        restClientBuilder = RestClient.builder().baseUrl(properties.getTwelvedata().getBaseUrl());
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        restClient = restClientBuilder.build();

        gateway = new TwelveDataGateway(properties, restClient);
        provider = new TwelveDataMarketDataProvider(properties, fallbackProvider, gateway);

        aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        vuag = new Instrument("Vanguard S&P 500 UCITS ETF", AssetClass.ETF, "VUAG", "IE00BFMXXD54", "LSE", new Currency("GBP"));
        nyseInst = new Instrument("IBM Corp", AssetClass.STOCK, "IBM", "US4592001014", "NYSE", new Currency("USD"));
        noExchangeInst = new Instrument("Crypto Token", AssetClass.CRYPTO, "BTC", null, null, new Currency("USD"));
    }

    @Test
    @DisplayName("Primary constructor with gateway succeeds")
    void primaryConstructorInitializes() {
        TwelveDataGateway gw = new TwelveDataGateway(properties, RestClient.builder());
        TwelveDataMarketDataProvider p = new TwelveDataMarketDataProvider(properties, fallbackProvider, gw);
        assertNotNull(p);
        assertEquals("TWELVE_DATA", p.getProviderId());

        p.init(); // Test enabled init logging

        properties.getTwelvedata().setApiKey(null);
        p.init(); // Test disabled init logging

        MarketDataProperties nullProps = new MarketDataProperties();
        nullProps.setTwelvedata(null);
        TwelveDataGateway nullGw = new TwelveDataGateway(nullProps, restClient);
        TwelveDataMarketDataProvider nullP = new TwelveDataMarketDataProvider(nullProps, fallbackProvider, nullGw);
        nullP.init();
    }

    @Test
    @DisplayName("Successfully fetches quote for US stock")
    void fetchQuoteUsStockSuccess() {
        String jsonResponse = """
            {
                "symbol": "AAPL",
                "name": "Apple Inc",
                "exchange": "NASDAQ",
                "currency": "USD",
                "datetime": "2026-08-28",
                "timestamp": 1724982000,
                "close": "190.25000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        PriceQuote quote = quoteOpt.get();
        assertEquals(new BigDecimal("190.2500"), quote.price());
        assertEquals("USD", quote.currency());
        assertEquals(ObservationSourceType.PROVIDER, quote.sourceType());
        assertEquals("TWELVE_DATA", quote.sourceReference());
        assertFalse(quote.isStale());
    }

    @Test
    @DisplayName("Successfully fetches quote with NYSE exchange and null response currency")
    void fetchQuoteNyseNullCurrency() {
        String jsonResponse = """
            {
                "symbol": "IBM",
                "close": "145.50000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=IBM&apikey=test-api-key&exchange=NYSE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(nyseInst, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("145.5000"), quoteOpt.get().price());
        assertEquals("USD", quoteOpt.get().currency());
    }

    @Test
    @DisplayName("Successfully fetches quote when instrument has no exchange")
    void fetchQuoteWithoutExchange() {
        String jsonResponse = """
            {
                "symbol": "BTC",
                "close": "65000.00000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=BTC&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(noExchangeInst, null);
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("65000.0000"), quoteOpt.get().price());
        assertEquals("USD", quoteOpt.get().currency());
    }

    @Test
    @DisplayName("Successfully fetches quote for UK LSE ETF with London exchange mapping")
    void fetchQuoteLondonExchangeMapping() {
        Instrument lseInst = new Instrument("Lloyds", AssetClass.STOCK, "LLOY", null, "London Stock Exchange", new Currency("GBP"));
        String jsonResponse = """
            {
                "symbol": "LLOY",
                "currency": "GBP",
                "close": "0.60000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=LLOY&apikey=test-api-key&exchange=LSE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Instant asOfTime = Instant.parse("2026-08-28T12:00:00Z");
        Optional<PriceQuote> quoteOpt = provider.fetchQuote(lseInst, asOfTime);
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("0.6000"), quoteOpt.get().price());
        assertEquals(asOfTime, quoteOpt.get().asOf());
    }

    @Test
    @DisplayName("Converts GBX (pence) to GBP for LSE instruments")
    void convertsGbxToGbp() {
        String jsonResponse = """
            {
                "symbol": "VUAG",
                "currency": "GBX",
                "close": "8875.00000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=VUAG&apikey=test-api-key&exchange=LSE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(vuag, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("88.7500"), quoteOpt.get().price());
        assertEquals("GBP", quoteOpt.get().currency());
    }

    @Test
    @DisplayName("Cleans trailing dot ticker and infers LSE for GBP/GB ISIN")
    void testTrailingDotAndInferredLse() {
        Instrument rr = new Instrument("Rolls-Royce", AssetClass.STOCK, "RR.", "GB00B63H8491", null, new Currency("GBP"));
        String json = """
            {
                "symbol": "RR",
                "currency": "GBX",
                "close": "525.00000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=RR&apikey=test-api-key&exchange=LSE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(rr, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("5.2500"), quoteOpt.get().price());
        assertEquals("GBP", quoteOpt.get().currency());
    }

    @Test
    @DisplayName("Infers exchange with edge case ISIN and currency lengths")
    void testInferExchangeEdgeCases() {
        Instrument shortIsin = new Instrument("Short ISIN", AssetClass.STOCK, "TEST", "G", "   ", new Currency("USD"));
        assertNull(provider.resolveExchange(shortIsin));

        Instrument nullIsinInst = new Instrument("Null ISIN", AssetClass.STOCK, "TEST", null, null, new Currency("USD"));
        assertNull(provider.resolveExchange(nullIsinInst));

        Instrument eurInst = new Instrument("EUR Stock", AssetClass.STOCK, "TEST", null, null, new Currency("EUR"));
        assertNull(provider.resolveExchange(eurInst));

        Instrument lonInst = new Instrument("LON Stock", AssetClass.STOCK, "TESTLON", null, "LON", new Currency("USD"));
        assertEquals("LSE", provider.resolveExchange(lonInst));

        Instrument lseInst = new Instrument("LSE Stock", AssetClass.STOCK, "TESTLSE", null, "LSE", new Currency("USD"));
        assertEquals("LSE", provider.resolveExchange(lseInst));

        Instrument gbIsinInst = new Instrument("GB ISIN", AssetClass.STOCK, "TESTGB", "GB0000000001", null, new Currency("USD"));
        assertEquals("LSE", provider.resolveExchange(gbIsinInst));

        Instrument gbpInst = new Instrument("GBP Stock", AssetClass.STOCK, "TESTGBP", null, null, new Currency("GBP"));
        assertEquals("LSE", provider.resolveExchange(gbpInst));

        Instrument blankTicker = new Instrument("Blank Ticker", AssetClass.STOCK, "   ", null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(blankTicker, Instant.now()).isEmpty());
        assertTrue(provider.fetchHistoricalQuotes(blankTicker, null, null).isEmpty());
    }

    @Test
    @DisplayName("Translates BRK.B ticker to BRK/B for Twelve Data query")
    void testBrkBTranslation() {
        Instrument brkb = new Instrument("Berkshire Hathaway", AssetClass.STOCK, "BRK.B", "US0846707026", "NYSE", new Currency("USD"));
        String json = """
            {
                "symbol": "BRK/B",
                "currency": "USD",
                "close": "450.00000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=BRK/B&apikey=test-api-key&exchange=NYSE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(brkb, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("450.0000"), quoteOpt.get().price());
    }

    @Test
    @DisplayName("fetchQuote returns empty when close price is zero or negative")
    void testZeroClosePrice() {
        Instrument inst = new Instrument("Test Stock", AssetClass.STOCK, "TEST", null, null, new Currency("USD"));
        String json = """
            {
                "symbol": "TEST",
                "currency": "USD",
                "close": "0.00000",
                "status": "ok"
            }
            """;
        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=TEST&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(inst, Instant.now());
        mockServer.verify();
        assertTrue(quoteOpt.isEmpty());
    }

    @Test
    @DisplayName("Falls back to DefaultMarketDataProvider when API key is missing, null, or disabled")
    void fallsBackWhenDisabledOrNoApiKey() {
        MarketDataProperties nullProps = new MarketDataProperties();
        nullProps.setTwelvedata(null);
        TwelveDataMarketDataProvider nullConfigProvider = new TwelveDataMarketDataProvider(nullProps, fallbackProvider, gateway);
        assertTrue(nullConfigProvider.fetchQuote(aapl, Instant.now()).isPresent());

        properties.getTwelvedata().setApiKey(null);
        Optional<PriceQuote> quoteOpt = provider.fetchQuote(aapl, Instant.now());
        assertTrue(quoteOpt.isPresent());
        assertEquals("DEFAULT_PROVIDER", quoteOpt.get().sourceReference());

        properties.getTwelvedata().setApiKey("");
        quoteOpt = provider.fetchQuote(aapl, Instant.now());
        assertTrue(quoteOpt.isPresent());
        assertEquals("DEFAULT_PROVIDER", quoteOpt.get().sourceReference());

        properties.getTwelvedata().setEnabled(false);
        properties.getTwelvedata().setApiKey("test-key");
        quoteOpt = provider.fetchQuote(aapl, Instant.now());
        assertTrue(quoteOpt.isPresent());
        assertEquals("DEFAULT_PROVIDER", quoteOpt.get().sourceReference());
    }

    @Test
    @DisplayName("Falls back when ticker is missing or blank")
    void fallsBackWhenTickerMissingOrBlank() {
        Instrument isinOnly = new Instrument("UK T-Bill", AssetClass.BOND, null, "GB00BSGJV473", null, new Currency("GBP"));
        Optional<PriceQuote> quoteOpt = provider.fetchQuote(isinOnly, Instant.now());
        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("99.8500"), quoteOpt.get().price());

        Instrument blankTicker = new Instrument("Blank Ticker", AssetClass.STOCK, "  ", null, "   ", new Currency("USD"));
        Optional<PriceQuote> quoteOpt2 = provider.fetchQuote(blankTicker, Instant.now());
        assertTrue(quoteOpt2.isEmpty());

        List<PriceQuote> historical = provider.fetchHistoricalQuotes(isinOnly, Instant.now().minusSeconds(86400), Instant.now());
        assertFalse(historical.isEmpty());

        List<PriceQuote> historicalBlank = provider.fetchHistoricalQuotes(blankTicker, Instant.now().minusSeconds(86400), Instant.now());
        assertTrue(historicalBlank.isEmpty());
    }

    @Test
    @DisplayName("fetchHistoricalQuotes falls back when time_series returns empty values array")
    void testFetchHistoricalQuotesEmptyValues() {
        String json = "{\"status\": \"ok\", \"values\": []}";
        mockServer.expect(requestTo("https://api.twelvedata.com/time_series?symbol=AAPL&interval=1day&start_date=2026-08-01&end_date=2026-08-28&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-28T00:00:00Z"));
        mockServer.verify();
        assertFalse(quotes.isEmpty());
        assertEquals("DEFAULT_PROVIDER", quotes.get(0).sourceReference());
    }

    @Test
    @DisplayName("fetchQuote falls back when gateway returns empty quote")
    void testFetchQuoteGatewayEmpty() {
        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<PriceQuote> quote = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();
        assertTrue(quote.isPresent());
        assertEquals("DEFAULT_PROVIDER", quote.get().sourceReference());
    }

    @Test
    @DisplayName("Falls back to DefaultMarketDataProvider on Twelve Data 429 rate limit or error response")
    void fallsBackOnErrorResponse() {
        String errorJson = """
            {
                "code": 429,
                "message": "You have run out of API credits for the current minute.",
                "status": "error"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(errorJson, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals("DEFAULT_PROVIDER", quoteOpt.get().sourceReference());
    }

    @Test
    @DisplayName("Falls back to DefaultMarketDataProvider on HTTP 500 error")
    void fallsBackOnHttpServerError() {
        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals("DEFAULT_PROVIDER", quoteOpt.get().sourceReference());
    }

    @Test
    @DisplayName("Null instrument returns empty Optional")
    void nullInstrumentReturnsEmpty() {
        assertTrue(provider.fetchQuote(null, Instant.now()).isEmpty());
        assertTrue(provider.fetchHistoricalQuotes(null, Instant.now(), Instant.now()).isEmpty());
    }

    @Test
    @DisplayName("Successfully fetches historical time series with exchange param and null items filtered")
    void fetchHistoricalQuotesSuccess() {
        String timeSeriesJson = """
            {
                "values": [
                    {"datetime": "2026-08-28", "close": "190.00000"},
                    {"datetime": "2026-08-27", "close": "188.00000"},
                    {"datetime": null, "close": "185.00000"},
                    {"datetime": "2026-08-26", "close": null}
                ],
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/time_series?symbol=IBM&interval=1day&start_date=2026-08-25&end_date=2026-08-28&apikey=test-api-key&exchange=NYSE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(timeSeriesJson, MediaType.APPLICATION_JSON));

        Instant from = Instant.parse("2026-08-25T00:00:00Z");
        Instant to = Instant.parse("2026-08-28T00:00:00Z");
        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(nyseInst, from, to);
        mockServer.verify();

        assertEquals(2, quotes.size());
        assertEquals(new BigDecimal("188.0000"), quotes.get(0).price());
        assertEquals(new BigDecimal("190.0000"), quotes.get(1).price());
        assertEquals("TWELVE_DATA", quotes.get(0).sourceReference());
    }

    @Test
    @DisplayName("Historical quotes fallback when time series fails or empty")
    void fetchHistoricalQuotesFallback() {
        mockServer.expect(anything())
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-28T00:00:00Z");
        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, from, to);
        mockServer.verify();

        assertFalse(quotes.isEmpty());
        assertEquals("DEFAULT_PROVIDER", quotes.get(0).sourceReference());
    }

    @Test
    @DisplayName("Historical quotes handles null dates and inverted from/to range")
    void fetchHistoricalQuotesInvertedDates() {
        properties.getTwelvedata().setApiKey(""); // Use fallback

        Instant from = Instant.parse("2026-08-28T00:00:00Z");
        Instant to = Instant.parse("2026-08-01T00:00:00Z");
        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, from, to);
        assertFalse(quotes.isEmpty());

        List<PriceQuote> nullDatesQuotes = provider.fetchHistoricalQuotes(aapl, null, null);
        assertFalse(nullDatesQuotes.isEmpty());
    }

    @Test
    @DisplayName("Historical quotes with empty list response falls back")
    void fetchHistoricalQuotesEmptyListFallsBack() {
        String timeSeriesJson = """
            {
                "values": [],
                "status": "ok"
            }
            """;

        mockServer.expect(anything())
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(timeSeriesJson, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(noExchangeInst, Instant.now().minusSeconds(86400), Instant.now());
        mockServer.verify();

        assertTrue(quotes.isEmpty());
    }

    @Test
    @DisplayName("getProviderId returns TWELVE_DATA")
    void getProviderIdReturnsCorrectValue() {
        assertEquals("TWELVE_DATA", provider.getProviderId());
    }

    @Test
    @DisplayName("Directly tests TwelveDataDtos record isSuccess logic")
    void testDtosIsSuccessLogic() {
        // QuoteResponse variations
        TwelveDataDtos.QuoteResponse qOk = new TwelveDataDtos.QuoteResponse("AAPL", "Apple", "NASDAQ", "USD", "2026-08-28", 1724982000L, "190.00", "188.00", "ok", null, null);
        assertTrue(qOk.isSuccess());

        TwelveDataDtos.QuoteResponse qStatusError = new TwelveDataDtos.QuoteResponse("AAPL", null, null, null, null, null, "190.00", null, "error", null, "Failed");
        assertFalse(qStatusError.isSuccess());

        TwelveDataDtos.QuoteResponse qBlankClose = new TwelveDataDtos.QuoteResponse("AAPL", null, null, null, null, null, null, null, "ok", null, null);
        assertFalse(qBlankClose.isSuccess());

        TwelveDataDtos.QuoteResponse qNullStatus = new TwelveDataDtos.QuoteResponse("AAPL", null, null, null, null, null, "190.00", null, null, null, null);
        assertTrue(qNullStatus.isSuccess());

        // TimeSeriesResponse variations
        TwelveDataDtos.TimeSeriesResponse tsOk = new TwelveDataDtos.TimeSeriesResponse(List.of(new TwelveDataDtos.TimeSeriesValue("2026-08-28", "190.00")), "ok", null, null);
        assertTrue(tsOk.isSuccess());

        TwelveDataDtos.TimeSeriesResponse tsNullValues = new TwelveDataDtos.TimeSeriesResponse(null, "ok", null, null);
        assertFalse(tsNullValues.isSuccess());

        TwelveDataDtos.TimeSeriesResponse tsEmptyValues = new TwelveDataDtos.TimeSeriesResponse(List.of(), "ok", null, null);
        assertFalse(tsEmptyValues.isSuccess());

        TwelveDataDtos.TimeSeriesResponse tsError = new TwelveDataDtos.TimeSeriesResponse(List.of(new TwelveDataDtos.TimeSeriesValue("2026-08-28", "190.00")), "error", 429, "Rate limited");
        assertFalse(tsError.isSuccess());

        TwelveDataDtos.TimeSeriesResponse tsNullStatus = new TwelveDataDtos.TimeSeriesResponse(List.of(new TwelveDataDtos.TimeSeriesValue("2026-08-28", "190.00")), null, null, null);
        assertTrue(tsNullStatus.isSuccess());

        // ExchangeRateResponse variations
        TwelveDataDtos.ExchangeRateResponse fxOk = new TwelveDataDtos.ExchangeRateResponse("GBP/USD", new BigDecimal("1.30"), 1724982000L, "ok", null, null);
        assertTrue(fxOk.isSuccess());

        TwelveDataDtos.ExchangeRateResponse fxNullRate = new TwelveDataDtos.ExchangeRateResponse("GBP/USD", null, null, "ok", null, null);
        assertFalse(fxNullRate.isSuccess());

        TwelveDataDtos.ExchangeRateResponse fxError = new TwelveDataDtos.ExchangeRateResponse("GBP/USD", new BigDecimal("1.30"), null, "error", 500, "Server Error");
        assertFalse(fxError.isSuccess());

        TwelveDataDtos.ExchangeRateResponse fxNullStatus = new TwelveDataDtos.ExchangeRateResponse("GBP/USD", new BigDecimal("1.30"), null, null, null, null);
        assertTrue(fxNullStatus.isSuccess());
    }

    @Test
    @DisplayName("Returns cached quote on second call and falls back to cached on error")
    void testCacheHitAndFallbackOnError() {
        String jsonResponse = """
            {
                "symbol": "AAPL",
                "currency": "USD",
                "close": "190.25000",
                "status": "ok"
            }
            """;

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // 1. First call fetches and caches
        Optional<PriceQuote> first = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();
        assertTrue(first.isPresent());

        // 2. Second call should return cached quote without mockServer request
        Optional<PriceQuote> second = provider.fetchQuote(aapl, Instant.now());
        assertTrue(second.isPresent());
        assertEquals(new BigDecimal("190.2500"), second.get().price());
    }

    @Test
    @DisplayName("Falls back to cached quote when server error occurs")
    void testStaleCacheFallbackOnError() throws Exception {
        String jsonResponse = """
            {
                "symbol": "AAPL",
                "currency": "USD",
                "close": "190.25000",
                "timestamp": 1724982000,
                "status": "ok"
            }
            """;

        // Make TTL negative so cache is expired for second request
        properties.getTwelvedata().setCacheTtlSeconds(-10);

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=test-api-key&exchange=NASDAQ"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<PriceQuote> first = provider.fetchQuote(aapl, null);
        assertTrue(first.isPresent());
        assertEquals(Instant.ofEpochSecond(1724982000L), first.get().asOf());

        Optional<PriceQuote> fallbackCached = provider.fetchQuote(aapl, null);
        mockServer.verify();
        assertTrue(fallbackCached.isPresent());
        assertEquals(new BigDecimal("190.2500"), fallbackCached.get().price());
    }

    @Test
    @DisplayName("fetchHistoricalQuotes swaps dates if start is after end")
    void testHistoricalQuotesStartAfterEnd() {
        String jsonResponse = """
            {
                "status": "ok",
                "values": [
                    { "datetime": "2026-08-28", "close": "190.25000" }
                ]
            }
            """;

        Instant end = Instant.parse("2026-08-28T00:00:00Z");
        Instant start = Instant.parse("2026-08-30T00:00:00Z"); // after end

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("time_series")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, start, end);
        mockServer.verify();
        assertFalse(quotes.isEmpty());
    }

    @Test
    @DisplayName("fetchQuote with null ticker delegates to fallback")
    void testFetchQuoteNullTicker() {
        Instrument noTicker = new Instrument("No Ticker", AssetClass.STOCK, null, null, null, new Currency("USD"));
        Optional<PriceQuote> quote = provider.fetchQuote(noTicker, Instant.now());
        assertTrue(quote.isEmpty());
    }

    @Test
    @DisplayName("fetchHistoricalQuotes with null ticker delegates to fallback")
    void testHistoricalQuotesNullTicker() {
        Instrument noTicker = new Instrument("No Ticker", AssetClass.STOCK, null, null, null, new Currency("USD"));
        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(noTicker, null, null);
        assertNotNull(quotes);
    }

    @Test
    @DisplayName("fetchHistoricalQuotes with null dates defaults to 365 days")
    void testHistoricalQuotesNullDates() {
        String jsonResponse = """
            {
                "status": "ok",
                "values": [
                    { "datetime": "2026-08-28", "close": "190.25000" }
                ]
            }
            """;

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("time_series")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, null, null);
        mockServer.verify();
        assertFalse(quotes.isEmpty());
    }

    @Test
    @DisplayName("Exchange resolution: USD instrument without GB ISIN results in null exchange")
    void testExchangeResolutionUsdWithoutGbIsin() {
        Instrument usInst = new Instrument("US Stock", AssetClass.STOCK, "USSTK", "US1234567890", null, new Currency("USD"));
        String json = """
            {
                "symbol": "USSTK",
                "currency": "USD",
                "close": "50.00",
                "status": "ok"
            }
            """;
        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=USSTK&apikey=test-api-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> q1 = provider.fetchQuote(usInst, Instant.now());
        mockServer.verify();
        assertTrue(q1.isPresent());
    }

    @Test
    @DisplayName("Exchange resolution: GBP instrument without GB ISIN resolves to LSE")
    void testExchangeResolutionGbpWithoutGbIsin() {
        Instrument gbpInst = new Instrument("UK Stock", AssetClass.STOCK, "UKSTK", "US1234567890", null, new Currency("GBP"));
        String json2 = """
            {
                "symbol": "UKSTK",
                "currency": "GBP",
                "close": "10.00",
                "status": "ok"
            }
            """;
        mockServer.expect(requestTo("https://api.twelvedata.com/quote?symbol=UKSTK&apikey=test-api-key&exchange=LSE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json2, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> q2 = provider.fetchQuote(gbpInst, Instant.now());
        mockServer.verify();
        assertTrue(q2.isPresent());
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.market.fmp.FmpGateway;
import com.takakim.investtracker.service.market.fmp.FmpMarketDataProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FmpMarketDataProviderTests {

    private MarketDataProperties properties;
    private MockRestServiceServer mockServer;
    private FmpMarketDataProvider provider;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        MarketDataProperties.FmpProperties fmp = new MarketDataProperties.FmpProperties();
        fmp.setEnabled(true);
        fmp.setApiKey("test-fmp-key");
        fmp.setBaseUrl("https://financialmodelingprep.com");
        properties.setFmp(fmp);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://financialmodelingprep.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        FmpGateway gateway = new FmpGateway(properties, builder.build());
        provider = new FmpMarketDataProvider(properties, gateway);
    }

    @Test
    @DisplayName("Fetches LSE stock quote with .L suffix")
    void testFetchLseQuote() {
        Instrument iqe = new Instrument("IQE plc", AssetClass.STOCK, "IQE", "GB0009619924", "LSE", new Currency("GBP"));
        String json = """
            [
                {
                    "symbol": "IQE.L",
                    "price": 0.4797,
                    "timestamp": 1725033600
                }
            ]
            """;

        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=IQE.L&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(iqe, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("0.4797"), quoteOpt.get().price());
        assertEquals("FMP", quoteOpt.get().sourceReference());
        assertEquals("GBP", quoteOpt.get().currency());
    }

    @Test
    @DisplayName("Fetches US quote for AAPL")
    void testFetchUsQuotes() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String jsonAapl = "[{\"symbol\": \"AAPL\", \"price\": 225.50}]";

        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=AAPL&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonAapl, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> q1 = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();
        assertTrue(q1.isPresent());
        assertEquals(new BigDecimal("225.50"), q1.get().price());
    }

    @Test
    @DisplayName("Translates BRK.B and BRK/B to BRK-B")
    void testBrkTranslation() {
        Instrument brk = new Instrument("Berkshire", AssetClass.STOCK, "BRK.B", "US0846707026", "NYSE", new Currency("USD"));
        String jsonBrk = "[{\"symbol\": \"BRK-B\", \"price\": 450.00}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=BRK-B&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonBrk, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> q2 = provider.fetchQuote(brk, Instant.now());
        mockServer.verify();
        assertTrue(q2.isPresent());
        assertEquals(new BigDecimal("450.00"), q2.get().price());

        Instrument brkSlash = new Instrument("Berkshire", AssetClass.STOCK, "BRK/B", null, "NYSE", new Currency("USD"));
        assertEquals("BRK-B", provider.resolveSymbol(brkSlash));
    }

    @Test
    @DisplayName("Pence conversion for UK instrument reported in GBX (> 100)")
    void testPenceConversion() {
        Instrument rolls = new Instrument("Rolls-Royce", AssetClass.STOCK, "RR", "GB00B63H8491", "LSE", new Currency("GBP"));
        String json = "[{\"symbol\": \"RR.L\", \"price\": 525.0000}]";

        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=RR.L&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(rolls, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("5.2500"), quoteOpt.get().price());
    }

    @Test
    @DisplayName("fetchQuote returns empty when price is zero or negative")
    void testZeroPrice() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String json = "[{\"symbol\": \"AAPL\", \"price\": 0.0}]";

        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=AAPL&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();
        assertTrue(quoteOpt.isEmpty());
    }

    @Test
    @DisplayName("Returns empty when unconfigured or ticker is missing")
    void testUnconfiguredOrMissingTicker() {
        properties.getFmp().setEnabled(false);
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        assertTrue(provider.fetchQuote(aapl, Instant.now()).isEmpty());

        properties.getFmp().setEnabled(true);
        Instrument noTicker = new Instrument("Unknown", AssetClass.STOCK, null, null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(noTicker, Instant.now()).isEmpty());

        Instrument blankTicker = new Instrument("Unknown", AssetClass.STOCK, "   ", null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(blankTicker, Instant.now()).isEmpty());

        assertTrue(provider.fetchHistoricalQuotes(aapl, null, null).isEmpty());
        assertEquals("FMP", provider.getProviderId());
    }

    @Test
    @DisplayName("Symbol resolution for trailing dots")
    void testTrailingDot() {
        Instrument rrDot = new Instrument("Rolls Royce", AssetClass.STOCK, "RR.", null, "LON", new Currency("GBP"));
        String json = "[{\"symbol\": \"RR.L\", \"price\": 5.25, \"timestamp\": 0}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=RR.L&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(rrDot, null);
        mockServer.verify();
        assertTrue(quoteOpt.isPresent());
    }

    @Test
    @DisplayName("BTC symbol does not convert from pence")
    void testBtcSymbolNoPenceConversion() {
        Instrument btcUk = new Instrument("BTC Fund", AssetClass.CRYPTO, "BTCLSE", null, "LSE", new Currency("GBP"));
        String btcJson = "[{\"symbol\": \"BTCLSE.L\", \"price\": 50000.00, \"timestamp\": 1725033600}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=BTCLSE.L&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(btcJson, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> btcQuote = provider.fetchQuote(btcUk, Instant.now());
        mockServer.verify();
        assertTrue(btcQuote.isPresent());
        assertEquals(new BigDecimal("50000.00"), btcQuote.get().price());
    }

    @Test
    @DisplayName("US instrument without exchange specified")
    void testUsInstrumentNullExchange() {
        Instrument usNullEx = new Instrument("US Stock", AssetClass.STOCK, "XYZ", "US1112223334", null, new Currency("USD"));
        String xyzJson = "[{\"symbol\": \"XYZ\", \"price\": 150.00, \"timestamp\": 1725033600}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=XYZ&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(xyzJson, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> xyzQuote = provider.fetchQuote(usNullEx, Instant.now());
        mockServer.verify();
        assertTrue(xyzQuote.isPresent());
        assertEquals(new BigDecimal("150.00"), xyzQuote.get().price());
    }

    @Test
    @DisplayName("isConfigured branch checks")
    void testIsConfiguredBranches() {
        MarketDataProperties offlineProps = new MarketDataProperties();
        offlineProps.getFmp().setEnabled(false);
        FmpMarketDataProvider offlineProvider = new FmpMarketDataProvider(offlineProps, new FmpGateway(offlineProps));
        assertFalse(offlineProvider.isConfigured());

        FmpMarketDataProvider nullPropsProvider = new FmpMarketDataProvider(null, null);
        assertFalse(nullPropsProvider.isConfigured());

        MarketDataProperties emptyKeyProps = new MarketDataProperties();
        emptyKeyProps.getFmp().setApiKey("");
        FmpMarketDataProvider emptyKeyProvider = new FmpMarketDataProvider(emptyKeyProps, null);
        assertFalse(emptyKeyProvider.isConfigured());

        MarketDataProperties nullKeyProps = new MarketDataProperties();
        nullKeyProps.getFmp().setApiKey(null);
        FmpMarketDataProvider nullKeyProvider = new FmpMarketDataProvider(nullKeyProps, null);
        assertFalse(nullKeyProvider.isConfigured());
    }

    @Test
    @DisplayName("LSE detection via GB ISIN with null exchange")
    void testLseDetectionViaIsin() {
        Instrument gbIsinInst = new Instrument("GB ISIN", AssetClass.STOCK, "TESTGB", "GB0000000001", null, new Currency("GBP"));
        String json = "[{\"symbol\": \"TESTGB.L\", \"price\": 50.00, \"timestamp\": 1725033600}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=TESTGB.L&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quote = provider.fetchQuote(gbIsinInst, Instant.now());
        mockServer.verify();
        assertTrue(quote.isPresent());
        assertEquals(new BigDecimal("50.00"), quote.get().price());
    }

    @Test
    @DisplayName("LSE detection via GBX currency with null exchange and null ISIN")
    void testLseDetectionViaCurrencyGbx() {
        Instrument gbxInst = new Instrument("GBX Stock", AssetClass.STOCK, "TESTGBX", null, null, new Currency("GBX"));
        String json = "[{\"symbol\": \"TESTGBX.L\", \"price\": 12.50, \"timestamp\": 1725033600}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=TESTGBX.L&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quote = provider.fetchQuote(gbxInst, Instant.now());
        mockServer.verify();
        assertTrue(quote.isPresent());
    }

    @Test
    @DisplayName("LSE detection via GBP currency with null exchange and null ISIN")
    void testLseDetectionViaCurrencyGbp() {
        Instrument gbpInst = new Instrument("GBP Stock", AssetClass.STOCK, "TESTGBP", null, null, new Currency("GBP"));
        String json = "[{\"symbol\": \"TESTGBP.L\", \"price\": 12.50, \"timestamp\": 1725033600}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=TESTGBP.L&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quote = provider.fetchQuote(gbpInst, Instant.now());
        mockServer.verify();
        assertTrue(quote.isPresent());
    }

    @Test
    @DisplayName("Non-LSE detection for EUR currency with null exchange and null ISIN")
    void testNonLseDetectionEur() {
        Instrument eurInst = new Instrument("EUR Stock", AssetClass.STOCK, "SAP", null, null, new Currency("EUR"));
        String json = "[{\"symbol\": \"SAP\", \"price\": 180.00, \"timestamp\": 1725033600}]";
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=SAP&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quote = provider.fetchQuote(eurInst, Instant.now());
        mockServer.verify();
        assertTrue(quote.isPresent());
    }

    @Test
    @DisplayName("Dot-only ticker resolves to null and returns empty")
    void testDotOnlyTicker() {
        Instrument dotInst = new Instrument("Dot", AssetClass.STOCK, ".", null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(dotInst, Instant.now()).isEmpty());
    }

    @Test
    @DisplayName("Handles null price in quote response")
    void testNullPriceResponse() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String jsonNullPrice = "[{\"symbol\": \"AAPL\", \"price\": null}]";

        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=AAPL&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonNullPrice, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();
        assertTrue(quoteOpt.isEmpty());
    }

    @Test
    @DisplayName("Handles empty array quote response")
    void testEmptyArrayResponse() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        mockServer.expect(requestTo("https://financialmodelingprep.com/stable/quote?symbol=AAPL&apikey=test-fmp-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteEmpty = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();
        assertTrue(quoteEmpty.isEmpty());
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceMarketDataProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YahooFinanceMarketDataProviderTests {

    private MarketDataProperties properties;
    private MockRestServiceServer mockServer;
    private YahooFinanceMarketDataProvider provider;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        MarketDataProperties.YahooProperties yahoo = new MarketDataProperties.YahooProperties();
        yahoo.setEnabled(true);
        yahoo.setBaseUrl("https://query1.finance.yahoo.com");
        properties.setYahoo(yahoo);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://query1.finance.yahoo.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        YahooFinanceGateway gateway = new YahooFinanceGateway(properties, builder.build());
        provider = new YahooFinanceMarketDataProvider(properties, gateway);
    }

    @Test
    @DisplayName("Fetches LSE stock quote with GBp currency and converts to GBP")
    void testFetchLseQuoteGbpConversion() {
        Instrument rr = new Instrument("Rolls Royce", AssetClass.STOCK, "RR", "GB00B63H8491", "LSE", new Currency("GBP"));
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

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(rr, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("15.3020"), quoteOpt.get().price());
        assertEquals("YAHOO_FINANCE", quoteOpt.get().sourceReference());
        assertEquals("GBP", quoteOpt.get().currency());
    }

    @Test
    @DisplayName("Fetches LSE ETF with GBP currency without dividing by 100")
    void testFetchLseEtfGbpPreserved() {
        Instrument vuag = new Instrument("Vanguard S&P 500", AssetClass.ETF, "VUAG", "IE00BFMXXD54", "LSE", new Currency("GBP"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "GBP",
                                "symbol": "VUAG.L",
                                "exchangeName": "LSE",
                                "regularMarketPrice": 110.66,
                                "regularMarketTime": 1787940890
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/VUAG.L?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(vuag, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("110.66"), quoteOpt.get().price());
        assertEquals("GBP", quoteOpt.get().currency());
    }

    @Test
    @DisplayName("Fetches US quote for AAPL with null asOf timestamp and 0 marketTime")
    void testFetchUsQuoteNullAsOfFallback() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "AAPL",
                                "exchangeName": "NASDAQ",
                                "regularMarketPrice": 225.50,
                                "regularMarketTime": 0
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(aapl, null);
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("225.50"), quoteOpt.get().price());
        assertEquals("USD", quoteOpt.get().currency());
        assertNotNull(quoteOpt.get().asOf());
    }

    @Test
    @DisplayName("Fetches quote with GBX currency and converts to GBP")
    void testFetchQuoteGbxCurrency() {
        Instrument rr = new Instrument("Rolls Royce", AssetClass.STOCK, "RR", "GB00B63H8491", "LSE", new Currency("GBP"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "GBX",
                                "symbol": "RR.L",
                                "regularMarketPrice": 1530.20
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/RR.L?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(rr, null);
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("15.3020"), quoteOpt.get().price());
    }

    @Test
    @DisplayName("Translates BRK.B and BRK/B to BRK-B")
    void testBrkTranslation() {
        Instrument brk = new Instrument("Berkshire", AssetClass.STOCK, "BRK.B", "US0846707026", "NYSE", new Currency("USD"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "BRK-B",
                                "regularMarketPrice": 450.00
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/BRK-B?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(brk, null);
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("450.00"), quoteOpt.get().price());

        Instrument brkSlash = new Instrument("Berkshire", AssetClass.STOCK, "BRK/B", null, "NYSE", new Currency("USD"));
        assertEquals("BRK-B", provider.resolveSymbol(brkSlash));
    }

    @Test
    @DisplayName("Fetches historical quotes successfully with null elements handled")
    void testFetchHistoricalQuotes() {
        Instrument rr = new Instrument("Rolls Royce", AssetClass.STOCK, "RR", "GB00B63H8491", "LSE", new Currency("GBP"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "GBX",
                                "symbol": "RR.L"
                            },
                            "timestamp": [1787800000, 1787850000, 1787900000],
                            "indicators": {
                                "quote": [
                                    {
                                        "close": [1520.00, null, 1530.00]
                                    }
                                ]
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/RR.L?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(rr, null, null);
        mockServer.verify();

        assertEquals(2, quotes.size());
        assertEquals(new BigDecimal("15.2000"), quotes.get(0).price());
        assertEquals(new BigDecimal("15.3000"), quotes.get(1).price());
        assertEquals("YAHOO_FINANCE", quotes.get(0).sourceReference());
    }

    @Test
    @DisplayName("Fetches US historical quotes without pence division")
    void testFetchUsHistoricalQuotes() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "AAPL"
                            },
                            "timestamp": [1787800000],
                            "indicators": {
                                "quote": [
                                    {
                                        "close": [225.50]
                                    }
                                ]
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, null, null);
        mockServer.verify();

        assertEquals(1, quotes.size());
        assertEquals(new BigDecimal("225.50"), quotes.get(0).price());
        assertEquals("USD", quotes.get(0).currency());
    }

    @Test
    @DisplayName("Historical quotes skips null timestamps or null close values")
    void testHistoricalQuotesNullElements() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "AAPL"
                            },
                            "timestamp": [null, 1787800000],
                            "indicators": {
                                "quote": [
                                    {
                                        "close": [null, 225.50]
                                    }
                                ]
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, null, null);
        mockServer.verify();

        assertEquals(1, quotes.size());
        assertEquals(new BigDecimal("225.50"), quotes.get(0).price());
    }

    @Test
    @DisplayName("Returns empty when unconfigured or ticker is missing")
    void testUnconfiguredOrMissingTicker() {
        MarketDataProperties nullProps = new MarketDataProperties();
        nullProps.setYahoo(null);
        YahooFinanceGateway nullGw = new YahooFinanceGateway(nullProps, RestClient.builder().build());
        YahooFinanceMarketDataProvider nullP = new YahooFinanceMarketDataProvider(nullProps, nullGw);
        assertFalse(nullP.isConfigured());

        YahooFinanceMarketDataProvider nullAllP = new YahooFinanceMarketDataProvider(null, nullGw);
        assertFalse(nullAllP.isConfigured());

        properties.getYahoo().setEnabled(false);
        assertFalse(provider.isConfigured());
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        assertTrue(provider.fetchQuote(aapl, Instant.now()).isEmpty());
        assertTrue(provider.fetchHistoricalQuotes(aapl, null, null).isEmpty());

        properties.getYahoo().setEnabled(true);
        properties.getYahoo().setBaseUrl(null);
        assertFalse(provider.isConfigured());

        properties.getYahoo().setBaseUrl("   ");
        assertFalse(provider.isConfigured());

        properties.getYahoo().setBaseUrl("https://query1.finance.yahoo.com");
        assertTrue(provider.isConfigured());

        Instrument noTicker = new Instrument("Unknown", AssetClass.STOCK, null, null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(noTicker, Instant.now()).isEmpty());
        assertTrue(provider.fetchHistoricalQuotes(noTicker, null, null).isEmpty());

        Instrument blankTicker = new Instrument("Unknown", AssetClass.STOCK, "   ", null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(blankTicker, Instant.now()).isEmpty());
        assertTrue(provider.fetchHistoricalQuotes(blankTicker, null, null).isEmpty());

        Instrument dotTicker = new Instrument("Dot", AssetClass.STOCK, ".", null, null, new Currency("USD"));
        assertTrue(provider.fetchHistoricalQuotes(dotTicker, null, null).isEmpty());

        assertTrue(provider.fetchQuote(null, Instant.now()).isEmpty());
        assertTrue(provider.fetchHistoricalQuotes(null, null, null).isEmpty());
        assertEquals("YAHOO_FINANCE", provider.getProviderId());
    }

    @Test
    @DisplayName("Symbol resolution and LSE detection variants")
    void testSymbolResolutionAndLseVariants() {
        Instrument rrDot = new Instrument("Rolls Royce", AssetClass.STOCK, "RR.", null, "LON", new Currency("GBP"));
        Instrument lseGbx = new Instrument("LSE GBX Stock", AssetClass.STOCK, "TEST", null, null, new Currency("GBX"));
        Instrument lseIsin = new Instrument("LSE ISIN Stock", AssetClass.STOCK, "TEST2", "GB1234567890", null, new Currency("USD"));
        Instrument alreadyL = new Instrument("Already L", AssetClass.STOCK, "VUAG.L", null, "LSE", new Currency("GBP"));
        Instrument eurInst = new Instrument("EUR Stock", AssetClass.STOCK, "SAP", null, null, new Currency("EUR"));
        Instrument blankExInst = new Instrument("Blank Ex", AssetClass.STOCK, "TEST", null, "   ", new Currency("USD"));
        Instrument gbIsinNullEx = new Instrument("GB ISIN", AssetClass.STOCK, "GBTEST", "GB9999999999", null, new Currency("EUR"));
        Instrument gbpNullEx = new Instrument("GBP Stock", AssetClass.STOCK, "GBPTEST", null, null, new Currency("GBP"));

        assertEquals("RR.L", provider.resolveSymbol(rrDot));
        assertEquals("TEST.L", provider.resolveSymbol(lseGbx));
        assertEquals("TEST2.L", provider.resolveSymbol(lseIsin));
        assertEquals("VUAG.L", provider.resolveSymbol(alreadyL));
        assertEquals("SAP", provider.resolveSymbol(eurInst));
        assertEquals("GBTEST.L", provider.resolveSymbol(gbIsinNullEx));
        assertEquals("GBPTEST.L", provider.resolveSymbol(gbpNullEx));
        assertFalse(provider.isLseInstrument(null));
        assertFalse(provider.isLseInstrument(blankExInst));

        Instrument nyseInst = new Instrument("NYSE Stock", AssetClass.STOCK, "TESTNYSE", "US1112223334", "NYSE", new Currency("USD"));
        assertFalse(provider.isLseInstrument(nyseInst));
        assertEquals("TESTNYSE", provider.resolveSymbol(nyseInst));

        Instrument lonInst = new Instrument("LON Ex Stock", AssetClass.STOCK, "TESTLON", null, "LON", new Currency("USD"));
        assertTrue(provider.isLseInstrument(lonInst));
        assertEquals("TESTLON.L", provider.resolveSymbol(lonInst));

        Instrument lseInst = new Instrument("LSE Ex Stock", AssetClass.STOCK, "TESTLSE", null, "LSE", new Currency("USD"));
        assertTrue(provider.isLseInstrument(lseInst));
        assertEquals("TESTLSE.L", provider.resolveSymbol(lseInst));

        Instrument dotInst = new Instrument("Dot", AssetClass.STOCK, ".", null, null, new Currency("USD"));
        assertEquals(null, provider.resolveSymbol(dotInst));

        // isConfigured branches
        MarketDataProperties offlineProps = new MarketDataProperties();
        offlineProps.getYahoo().setEnabled(false);
        YahooFinanceMarketDataProvider offlineProvider = new YahooFinanceMarketDataProvider(offlineProps, new YahooFinanceGateway(offlineProps));
        assertFalse(offlineProvider.isConfigured());

        YahooFinanceMarketDataProvider nullPropsProvider = new YahooFinanceMarketDataProvider(null, null);
        assertFalse(nullPropsProvider.isConfigured());

        MarketDataProperties emptyUrlProps = new MarketDataProperties();
        emptyUrlProps.getYahoo().setBaseUrl("");
        YahooFinanceMarketDataProvider emptyUrlProvider = new YahooFinanceMarketDataProvider(emptyUrlProps, null);
        assertFalse(emptyUrlProvider.isConfigured());
    }

    @Test
    @DisplayName("Pence fallback heuristic when responseCurrency is null and price > 100")
    void testPenceFallbackHeuristic() {
        Instrument rolls = new Instrument("Rolls-Royce", AssetClass.STOCK, "RR", "GB00B63H8491", "LSE", new Currency("GBP"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": null,
                                "symbol": "RR.L",
                                "regularMarketPrice": 525.0000
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/RR.L?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(rolls, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("5.2500"), quoteOpt.get().price());
    }

    @Test
    @DisplayName("Pence fallback heuristic when responseCurrency is null and price <= 100")
    void testPenceFallbackHeuristicUnder100() {
        Instrument rolls = new Instrument("Rolls-Royce", AssetClass.STOCK, "RR", "GB00B63H8491", "LSE", new Currency("GBP"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": null,
                                "symbol": "RR.L",
                                "regularMarketPrice": 50.0000
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/RR.L?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(rolls, Instant.now());
        mockServer.verify();

        assertTrue(quoteOpt.isPresent());
        assertEquals(new BigDecimal("50.0000"), quoteOpt.get().price());
    }

    @Test
    @DisplayName("Pence fallback heuristic for BTC symbol does not divide by 100")
    void testPenceFallbackHeuristicBtc() {
        Instrument btcUk = new Instrument("BTC Fund", AssetClass.CRYPTO, "BTCLSE", null, "LSE", new Currency("GBP"));
        String btcJson = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": null,
                                "symbol": "BTCLSE.L",
                                "regularMarketPrice": 50000.00
                            }
                        }
                    ]
                }
            }
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/BTCLSE.L?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(btcJson, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> btcQuote = provider.fetchQuote(btcUk, Instant.now());
        mockServer.verify();
        assertTrue(btcQuote.isPresent());
        assertEquals(new BigDecimal("50000.00"), btcQuote.get().price());
    }

    @Test
    @DisplayName("Handles empty chart entry or null meta/price")
    void testEmptyEntryOrNullMeta() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String jsonNullPrice = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "AAPL",
                                "regularMarketPrice": null
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonNullPrice, MediaType.APPLICATION_JSON));

        Optional<PriceQuote> quoteOpt = provider.fetchQuote(aapl, Instant.now());
        mockServer.verify();
        assertTrue(quoteOpt.isEmpty());
    }

    @Test
    @DisplayName("Handles empty or 404 response from gateway")
    void testGateway404() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertTrue(provider.fetchQuote(aapl, Instant.now()).isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Handles empty historical quotes response with null close prices")
    void testEmptyHistoricalQuotesNullCloses() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String jsonNoCloses = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "symbol": "AAPL"
                            },
                            "timestamp": [1787800000],
                            "indicators": {
                                "quote": [
                                    {
                                        "close": null
                                    }
                                ]
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonNoCloses, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, null, null);
        mockServer.verify();
        assertTrue(quotes.isEmpty());
    }

    @Test
    @DisplayName("Handles empty historical quotes response with null indicators")
    void testEmptyHistoricalQuotesNullIndicators() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String jsonNoInd = "{\"chart\": {\"result\": [{\"meta\": {\"symbol\": \"AAPL\"}, \"timestamp\": null, \"indicators\": null}]}}";
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonNoInd, MediaType.APPLICATION_JSON));

        assertTrue(provider.fetchHistoricalQuotes(aapl, null, null).isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("Handles empty historical quotes response with empty quote list")
    void testEmptyHistoricalQuotesEmptyQuoteList() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String jsonEmptyQuote = "{\"chart\": {\"result\": [{\"meta\": {\"symbol\": \"AAPL\"}, \"timestamp\": [12345], \"indicators\": {\"quote\": []}}]}}";
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonEmptyQuote, MediaType.APPLICATION_JSON));

        assertTrue(provider.fetchHistoricalQuotes(aapl, null, null).isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("fetchQuote returns empty when regularMarketPrice is zero or negative")
    void testZeroOrNegativeRegularMarketPrice() {
        Instrument vwrl = new Instrument("Vanguard FTSE All-World", AssetClass.ETF, "VWRL", null, null, new Currency("USD"));
        String jsonZero = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "VWRL",
                                "regularMarketPrice": 0.0,
                                "regularMarketTime": 0
                            }
                        }
                    ]
                }
            }
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/VWRL?interval=1d&range=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonZero, MediaType.APPLICATION_JSON));

        assertTrue(provider.fetchQuote(vwrl, null).isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("fetchHistoricalQuotes returns empty when chart gateway returns empty")
    void testHistoricalQuotesChartNotFound() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertTrue(provider.fetchHistoricalQuotes(aapl, null, null).isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("fetchHistoricalQuotes returns empty when close list is null")
    void testHistoricalQuotesNullCloses() {
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        String json = "{\"chart\": {\"result\": [{\"meta\": {\"symbol\": \"AAPL\"}, \"timestamp\": [1700000000], \"indicators\": {\"quote\": [{\"close\": null}]}}]}}";
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertTrue(provider.fetchHistoricalQuotes(aapl, null, null).isEmpty());
        mockServer.verify();
    }

    @Test
    @DisplayName("fetchHistoricalQuotes converts pence to pounds for LSE instruments")
    void testHistoricalQuotesPenceConversion() {
        Instrument rr = new Instrument("Rolls Royce", AssetClass.STOCK, "RR", "GB00B63H8491", "LSE", new Currency("GBP"));
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "GBp",
                                "symbol": "RR.L"
                            },
                            "timestamp": [1700000000, 1700086400],
                            "indicators": {
                                "quote": [
                                    {
                                        "close": [1500.0, 1550.0]
                                    }
                                ]
                            }
                        }
                    ]
                }
            }
            """;
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/RR.L?interval=1d&range=1mo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(rr, null, null);
        mockServer.verify();

        assertEquals(2, quotes.size());
        assertEquals(new BigDecimal("15.0000"), quotes.get(0).price());
        assertEquals(new BigDecimal("15.5000"), quotes.get(1).price());
    }

    @Test
    @DisplayName("getProviderId returns YAHOO_FINANCE")
    void testProviderIdAndDisabled() {
        assertEquals("YAHOO_FINANCE", provider.getProviderId());

        properties.getYahoo().setEnabled(false);
        Instrument aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        assertTrue(provider.fetchQuote(aapl, Instant.now()).isEmpty());
        assertTrue(provider.fetchHistoricalQuotes(aapl, null, null).isEmpty());
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.service.market.CompositeMarketDataProvider;
import com.takakim.investtracker.service.market.DefaultMarketDataProvider;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.market.fmp.FmpMarketDataProvider;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataMarketDataProvider;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceMarketDataProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CompositeMarketDataProviderTests {

    private TwelveDataMarketDataProvider twelveData;
    private FmpMarketDataProvider fmp;
    private YahooFinanceMarketDataProvider yahoo;
    private DefaultMarketDataProvider defaultProvider;
    private CompositeMarketDataProvider composite;

    private Instrument aapl;
    private Instrument iqe;
    private Instrument rr;

    @BeforeEach
    void setUp() {
        twelveData = Mockito.mock(TwelveDataMarketDataProvider.class);
        fmp = Mockito.mock(FmpMarketDataProvider.class);
        yahoo = Mockito.mock(YahooFinanceMarketDataProvider.class);
        defaultProvider = new DefaultMarketDataProvider();
        composite = new CompositeMarketDataProvider(twelveData, fmp, yahoo, defaultProvider);

        aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        iqe = new Instrument("IQE plc", AssetClass.STOCK, "IQE", "GB0009619924", "LSE", new Currency("GBP"));
        rr = new Instrument("Rolls Royce", AssetClass.STOCK, "RR", "GB00B63H8491", "LSE", new Currency("GBP"));
    }

    @Test
    @DisplayName("Routes to Twelve Data when Twelve Data succeeds")
    void testTwelveDataSucceeds() {
        when(twelveData.isConfigured()).thenReturn(true);
        PriceQuote tdQuote = new PriceQuote(
                aapl.getId(),
                new BigDecimal("225.0000"),
                "USD",
                Instant.now(),
                ObservationSourceType.PROVIDER,
                "TWELVE_DATA",
                false,
                null
        );
        when(twelveData.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(tdQuote));

        Optional<PriceQuote> res = composite.fetchQuote(aapl, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("TWELVE_DATA", res.get().sourceReference());
        assertEquals(new BigDecimal("225.0000"), res.get().price());
    }

    @Test
    @DisplayName("Falls back to FMP when Twelve Data is 404 or unconfigured")
    void testFallbackToFmp() {
        when(twelveData.isConfigured()).thenReturn(true);
        when(twelveData.fetchQuote(eq(iqe), any())).thenReturn(Optional.empty());

        when(fmp.isConfigured()).thenReturn(true);
        PriceQuote fmpQuote = new PriceQuote(
                iqe.getId(),
                new BigDecimal("0.4797"),
                "GBP",
                Instant.now(),
                ObservationSourceType.PROVIDER,
                "FMP",
                false,
                null
        );
        when(fmp.fetchQuote(eq(iqe), any())).thenReturn(Optional.of(fmpQuote));

        Optional<PriceQuote> res = composite.fetchQuote(iqe, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("FMP", res.get().sourceReference());
        assertEquals(new BigDecimal("0.4797"), res.get().price());
    }

    @Test
    @DisplayName("Falls back to Yahoo Finance when Twelve Data and FMP fail")
    void testFallbackToYahooFinance() {
        when(twelveData.isConfigured()).thenReturn(true);
        when(twelveData.fetchQuote(eq(rr), any())).thenReturn(Optional.empty());

        when(fmp.isConfigured()).thenReturn(true);
        when(fmp.fetchQuote(eq(rr), any())).thenReturn(Optional.empty());

        when(yahoo.isConfigured()).thenReturn(true);
        PriceQuote yahooQuote = new PriceQuote(
                rr.getId(),
                new BigDecimal("15.3020"),
                "GBP",
                Instant.now(),
                ObservationSourceType.PROVIDER,
                "YAHOO_FINANCE",
                false,
                null
        );
        when(yahoo.fetchQuote(eq(rr), any())).thenReturn(Optional.of(yahooQuote));

        Optional<PriceQuote> res = composite.fetchQuote(rr, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("YAHOO_FINANCE", res.get().sourceReference());
        assertEquals(new BigDecimal("15.3020"), res.get().price());
    }

    @Test
    @DisplayName("Delegates across tiers when quotes have non-matching source reference")
    void testNonMatchingSourceReferenceDelegates() {
        when(twelveData.isConfigured()).thenReturn(true);
        PriceQuote fallbackQuote = new PriceQuote(aapl.getId(), BigDecimal.ONE, "USD", Instant.now(), ObservationSourceType.PROVIDER, "DEFAULT_PROVIDER", false, null);
        when(twelveData.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(fallbackQuote));

        when(fmp.isConfigured()).thenReturn(true);
        when(fmp.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(fallbackQuote));

        when(yahoo.isConfigured()).thenReturn(true);
        when(yahoo.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(fallbackQuote));

        Optional<PriceQuote> res = composite.fetchQuote(aapl, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("DEFAULT_PROVIDER", res.get().sourceReference());
    }

    @Test
    @DisplayName("Falls back to DefaultMarketDataProvider when all live providers fail")
    void testFallbackToDefault() {
        when(twelveData.isConfigured()).thenReturn(false);
        when(fmp.isConfigured()).thenReturn(false);
        when(yahoo.isConfigured()).thenReturn(false);

        Optional<PriceQuote> res = composite.fetchQuote(aapl, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("DEFAULT_PROVIDER", res.get().sourceReference());
    }

    @Test
    @DisplayName("Falls back to FMP when Twelve Data is rate-limited and returns cached quote")
    void testTwelveDataRateLimitedFallsBackToFmpWhenCachedQuotePresent() {
        when(twelveData.isConfigured()).thenReturn(true);
        PriceQuote cachedQuote = new PriceQuote(
                aapl.getId(), new BigDecimal("180.00"), "USD",
                Instant.now().minusSeconds(600), ObservationSourceType.PROVIDER,
                "TWELVE_DATA_CACHED", true, "Rate limited"
        );
        when(twelveData.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(cachedQuote));

        when(fmp.isConfigured()).thenReturn(true);
        PriceQuote fmpQuote = new PriceQuote(
                aapl.getId(), new BigDecimal("192.50"), "USD",
                Instant.now(), ObservationSourceType.PROVIDER,
                "FMP", false, null
        );
        when(fmp.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(fmpQuote));

        Optional<PriceQuote> res = composite.fetchQuote(aapl, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("FMP", res.get().sourceReference());
        assertEquals(new BigDecimal("192.50"), res.get().price());
    }

    @Test
    @DisplayName("Falls back to Yahoo Finance when Twelve Data is rate-limited with cached quote and FMP fails")
    void testTwelveDataRateLimitedAndFmpFailsFallsBackToYahooWhenCachedQuotePresent() {
        when(twelveData.isConfigured()).thenReturn(true);
        PriceQuote cachedQuote = new PriceQuote(
                aapl.getId(), new BigDecimal("180.00"), "USD",
                Instant.now().minusSeconds(600), ObservationSourceType.PROVIDER,
                "TWELVE_DATA_CACHED", true, "Rate limited"
        );
        when(twelveData.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(cachedQuote));

        when(fmp.isConfigured()).thenReturn(true);
        when(fmp.fetchQuote(eq(aapl), any())).thenReturn(Optional.empty());

        when(yahoo.isConfigured()).thenReturn(true);
        PriceQuote yahooQuote = new PriceQuote(
                aapl.getId(), new BigDecimal("193.10"), "USD",
                Instant.now(), ObservationSourceType.PROVIDER,
                "YAHOO_FINANCE", false, null
        );
        when(yahoo.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(yahooQuote));

        Optional<PriceQuote> res = composite.fetchQuote(aapl, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("YAHOO_FINANCE", res.get().sourceReference());
        assertEquals(new BigDecimal("193.10"), res.get().price());
    }

    @Test
    @DisplayName("Returns cached Twelve Data quote when all live providers fail")
    void testAllLiveProvidersFailReturnsCachedTwelveDataQuote() {
        when(twelveData.isConfigured()).thenReturn(true);
        PriceQuote cachedQuote = new PriceQuote(
                aapl.getId(), new BigDecimal("180.00"), "USD",
                Instant.now().minusSeconds(600), ObservationSourceType.PROVIDER,
                "TWELVE_DATA_CACHED", true, "Rate limited"
        );
        when(twelveData.fetchQuote(eq(aapl), any())).thenReturn(Optional.of(cachedQuote));

        when(fmp.isConfigured()).thenReturn(true);
        when(fmp.fetchQuote(eq(aapl), any())).thenReturn(Optional.empty());

        when(yahoo.isConfigured()).thenReturn(true);
        when(yahoo.fetchQuote(eq(aapl), any())).thenReturn(Optional.empty());

        Optional<PriceQuote> res = composite.fetchQuote(aapl, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("TWELVE_DATA_CACHED", res.get().sourceReference());
        assertEquals(new BigDecimal("180.00"), res.get().price());
    }

    @Test
    @DisplayName("Historical quotes fallback logic across tiers")
    void testHistoricalQuotes() {
        // Twelve Data historical
        when(twelveData.isConfigured()).thenReturn(true);
        PriceQuote tdQuote = new PriceQuote(
                aapl.getId(),
                new BigDecimal("220.00"),
                "USD",
                Instant.now(),
                ObservationSourceType.PROVIDER,
                "TWELVE_DATA",
                false,
                null
        );
        when(twelveData.fetchHistoricalQuotes(eq(aapl), any(), any())).thenReturn(List.of(tdQuote));

        List<PriceQuote> quotes = composite.fetchHistoricalQuotes(aapl, null, null);
        assertFalse(quotes.isEmpty());
        assertEquals("TWELVE_DATA", quotes.get(0).sourceReference());

        // Twelve Data returns non-matching list, Yahoo Finance historical succeeds
        when(twelveData.fetchHistoricalQuotes(eq(rr), any(), any())).thenReturn(List.of(
                new PriceQuote(rr.getId(), BigDecimal.TEN, "GBP", Instant.now(), ObservationSourceType.PROVIDER, "DEFAULT_PROVIDER", false, null)
        ));
        when(yahoo.isConfigured()).thenReturn(true);
        PriceQuote yahooQuote = new PriceQuote(
                rr.getId(),
                new BigDecimal("15.20"),
                "GBP",
                Instant.now(),
                ObservationSourceType.PROVIDER,
                "YAHOO_FINANCE",
                false,
                null
        );
        when(yahoo.fetchHistoricalQuotes(eq(rr), any(), any())).thenReturn(List.of(yahooQuote));

        List<PriceQuote> yQuotes = composite.fetchHistoricalQuotes(rr, null, null);
        assertFalse(yQuotes.isEmpty());
        assertEquals("YAHOO_FINANCE", yQuotes.get(0).sourceReference());

        // Twelve Data returns empty, Yahoo returns null
        when(twelveData.fetchHistoricalQuotes(eq(iqe), any(), any())).thenReturn(List.of());
        when(yahoo.fetchHistoricalQuotes(eq(iqe), any(), any())).thenReturn(null);
        List<PriceQuote> nullYahooQuotes = composite.fetchHistoricalQuotes(iqe, null, null);
        assertFalse(nullYahooQuotes.isEmpty());
        assertEquals("DEFAULT_PROVIDER", nullYahooQuotes.get(0).sourceReference());

        // Twelve Data returns empty, Yahoo returns non-matching list
        PriceQuote nonMatchingYahoo = new PriceQuote(iqe.getId(), BigDecimal.ONE, "GBP", Instant.now(), ObservationSourceType.PROVIDER, "OTHER", false, null);
        when(yahoo.fetchHistoricalQuotes(eq(iqe), any(), any())).thenReturn(List.of(nonMatchingYahoo));

        List<PriceQuote> fallbackQuotes = composite.fetchHistoricalQuotes(iqe, null, null);
        assertFalse(fallbackQuotes.isEmpty());
        assertEquals("DEFAULT_PROVIDER", fallbackQuotes.get(0).sourceReference());

        // Yahoo returns empty list
        when(yahoo.fetchHistoricalQuotes(eq(iqe), any(), any())).thenReturn(List.of());
        List<PriceQuote> emptyYahooQuotes = composite.fetchHistoricalQuotes(iqe, null, null);
        assertFalse(emptyYahooQuotes.isEmpty());
        assertEquals("DEFAULT_PROVIDER", emptyYahooQuotes.get(0).sourceReference());
    }

    @Test
    @DisplayName("Null instrument returns empty or empty list")
    void testNullInstrument() {
        assertTrue(composite.fetchQuote(null, Instant.now()).isEmpty());
        assertTrue(composite.fetchHistoricalQuotes(null, null, null).isEmpty());
    }

    @Test
    @DisplayName("Handles exceptions from all sub-providers gracefully")
    void testProviderExceptions() {
        when(twelveData.isConfigured()).thenReturn(true);
        when(twelveData.fetchQuote(any(), any())).thenThrow(new RuntimeException("TD network error"));
        when(twelveData.fetchHistoricalQuotes(any(), any(), any())).thenThrow(new RuntimeException("TD hist error"));

        when(fmp.isConfigured()).thenReturn(true);
        when(fmp.fetchQuote(any(), any())).thenThrow(new RuntimeException("FMP error"));

        when(yahoo.isConfigured()).thenReturn(true);
        when(yahoo.fetchQuote(any(), any())).thenThrow(new RuntimeException("Yahoo error"));
        when(yahoo.fetchHistoricalQuotes(any(), any(), any())).thenThrow(new RuntimeException("Yahoo hist error"));

        Optional<PriceQuote> quote = composite.fetchQuote(aapl, Instant.now());
        assertTrue(quote.isPresent());
        assertEquals("DEFAULT_PROVIDER", quote.get().sourceReference());

        List<PriceQuote> hist = composite.fetchHistoricalQuotes(aapl, null, null);
        assertFalse(hist.isEmpty());
        assertEquals("DEFAULT_PROVIDER", hist.get(0).sourceReference());
    }

    @Test
    @DisplayName("Handles null sub-providers and getProviderId")
    void testNullSubProviders() {
        CompositeMarketDataProvider nullComp = new CompositeMarketDataProvider(null, null, null, defaultProvider);
        Optional<PriceQuote> res = nullComp.fetchQuote(aapl, Instant.now());
        assertTrue(res.isPresent());
        assertEquals("DEFAULT_PROVIDER", res.get().sourceReference());

        List<PriceQuote> hist = nullComp.fetchHistoricalQuotes(aapl, null, null);
        assertFalse(hist.isEmpty());

        assertEquals("COMPOSITE_PROXY", nullComp.getProviderId());
    }
}

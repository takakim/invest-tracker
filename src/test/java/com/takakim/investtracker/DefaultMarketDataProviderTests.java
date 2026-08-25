package com.takakim.investtracker;

import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.service.market.DefaultMarketDataProvider;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMarketDataProviderTests {

    private DefaultMarketDataProvider provider;
    private Instrument aapl;

    @BeforeEach
    void setUp() {
        provider = new DefaultMarketDataProvider();
        aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
    }

    @Test
    @DisplayName("fetchQuote handles null instrument, null ticker, and unknown ticker")
    void fetchQuoteEdgeCases() {
        assertTrue(provider.fetchQuote(null, Instant.now()).isEmpty());

        Instrument noTicker = new Instrument("Private Asset", AssetClass.OTHER, null, null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(noTicker, Instant.now()).isEmpty());

        Instrument unknown = new Instrument("Unknown", AssetClass.STOCK, "UNKNOWN", null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(unknown, Instant.now()).isEmpty());
    }

    @Test
    @DisplayName("fetchQuote returns quote for known ticker with asOf or null asOf")
    void fetchQuoteKnownTicker() {
        var quoteWithAsOf = provider.fetchQuote(aapl, Instant.now());
        assertTrue(quoteWithAsOf.isPresent());
        assertEquals(new BigDecimal("185.5000"), quoteWithAsOf.get().price());

        var quoteNullAsOf = provider.fetchQuote(aapl, null);
        assertTrue(quoteNullAsOf.isPresent());
        assertEquals(new BigDecimal("185.5000"), quoteNullAsOf.get().price());
    }

    @Test
    @DisplayName("setPrice updates existing price and handles nulls")
    void setPriceUpdates() {
        provider.setPrice("AAPL", new BigDecimal("200.00"));
        var quote = provider.fetchQuote(aapl, null);
        assertTrue(quote.isPresent());
        assertEquals(new BigDecimal("200.00"), quote.get().price());

        // Null checks don't crash
        provider.setPrice(null, new BigDecimal("100.00"));
        provider.setPrice("AAPL", null);
    }

    @Test
    @DisplayName("fetchHistoricalQuotes and getProviderId")
    void historicalQuotesAndProviderId() {
        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, Instant.EPOCH, Instant.now());
        assertEquals(1, quotes.size());

        assertEquals("DEFAULT_PROVIDER", provider.getProviderId());
    }
}

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
    @DisplayName("fetchQuote handles null instrument and known/unknown tickers")
    void fetchQuoteEdgeCases() {
        assertTrue(provider.fetchQuote(null, Instant.now()).isEmpty());

        Instrument noTicker = new Instrument("Private Asset", AssetClass.OTHER, null, null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(noTicker, Instant.now()).isEmpty());

        Instrument unknown = new Instrument("Unknown Asset", AssetClass.STOCK, "XYZ999", null, null, new Currency("USD"));
        assertTrue(provider.fetchQuote(unknown, Instant.now()).isEmpty());

        Instrument byIsin = new Instrument("UK T-Bill", AssetClass.BOND, null, "GB00BSGJV473", null, new Currency("GBP"));
        var isinQuote = provider.fetchQuote(byIsin, Instant.now());
        assertTrue(isinQuote.isPresent());
        assertEquals(new BigDecimal("99.8500"), isinQuote.get().price());

        Instrument lloy = new Instrument("Lloyds", AssetClass.STOCK, "LLOY", "GB0008706128", "LSE", new Currency("GBP"));
        var lloyQuote = provider.fetchQuote(lloy, Instant.now());
        assertTrue(lloyQuote.isPresent());
        assertEquals(new BigDecimal("1.1145"), lloyQuote.get().price());
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

        // Historical quote in the past
        var pastQuote = provider.fetchQuote(aapl, Instant.now().minus(180, java.time.temporal.ChronoUnit.DAYS));
        assertTrue(pastQuote.isPresent());
        assertTrue(pastQuote.get().price().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("setPrice updates existing price and handles nulls")
    void setPriceUpdates() {
        provider.setPrice("AAPL", new BigDecimal("200.0000"));
        var quote = provider.fetchQuote(aapl, null);
        assertTrue(quote.isPresent());
        assertEquals(new BigDecimal("200.0000"), quote.get().price());

        // Null checks don't crash
        provider.setPrice(null, new BigDecimal("100.00"));
        provider.setPrice("AAPL", null);
    }

    @Test
    @DisplayName("fetchHistoricalQuotes and getProviderId")
    void historicalQuotesAndProviderId() {
        assertTrue(provider.fetchHistoricalQuotes(null, null, null).isEmpty());

        List<PriceQuote> quotes = provider.fetchHistoricalQuotes(aapl, Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS), Instant.now());
        assertFalse(quotes.isEmpty());

        // Range where start > end
        List<PriceQuote> reversed = provider.fetchHistoricalQuotes(aapl, Instant.now(), Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
        assertFalse(reversed.isEmpty());

        assertEquals("DEFAULT_PROVIDER", provider.getProviderId());
    }

    @Test
    @DisplayName("Historical price calculation edge cases (future date, ancient date, only isin or name)")
    void historicalCalculationBranches() {
        // Future date (daysDiff <= 0)
        var futureQuote = provider.fetchQuote(aapl, Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS));
        assertTrue(futureQuote.isPresent());
        assertEquals(new BigDecimal("185.5000"), futureQuote.get().price());

        // Ancient date (adjusted <= 0.01)
        var ancientQuote = provider.fetchQuote(aapl, Instant.EPOCH);
        assertTrue(ancientQuote.isPresent());
        assertTrue(ancientQuote.get().price().compareTo(BigDecimal.ZERO) > 0);

        // Instrument with known ISIN
        Instrument byIsin = new Instrument("Isin Fund", AssetClass.MUTUAL_FUND, null, "GB00BSGJV473", null, new Currency("GBP"));
        var q1 = provider.fetchQuote(byIsin, Instant.now());
        assertTrue(q1.isPresent());

        // Instrument with unknown ISIN
        Instrument unknownIsin = new Instrument("Isin Fund", AssetClass.MUTUAL_FUND, null, "GB00UNKNOWN1", null, new Currency("GBP"));
        var q2 = provider.fetchQuote(unknownIsin, Instant.now());
        assertTrue(q2.isEmpty());
    }
}

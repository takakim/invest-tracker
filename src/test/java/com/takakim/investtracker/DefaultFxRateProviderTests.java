package com.takakim.investtracker;

import com.takakim.investtracker.service.currency.DefaultFxRateProvider;
import com.takakim.investtracker.service.currency.FxRateQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultFxRateProviderTests {

    private DefaultFxRateProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultFxRateProvider();
    }

    @Test
    @DisplayName("fetchRate handles null currency codes")
    void nullCurrencyCodes() {
        assertTrue(provider.fetchRate(null, "USD", Instant.now()).isEmpty());
        assertTrue(provider.fetchRate("USD", null, Instant.now()).isEmpty());
    }

    @Test
    @DisplayName("fetchRate returns 1.0 for same currency with null or non-null asOf")
    void sameCurrencyIdentity() {
        var withAsOf = provider.fetchRate("USD", "USD", Instant.now());
        assertTrue(withAsOf.isPresent());
        assertEquals(0, BigDecimal.ONE.compareTo(withAsOf.get().rate()));

        var nullAsOf = provider.fetchRate("EUR", "EUR", null);
        assertTrue(nullAsOf.isPresent());
        assertEquals(0, BigDecimal.ONE.compareTo(nullAsOf.get().rate()));
    }

    @Test
    @DisplayName("fetchRate handles direct, inverse, and unknown rates")
    void directAndInverseRates() {
        // Direct
        var gbpUsd = provider.fetchRate("GBP", "USD", null);
        assertTrue(gbpUsd.isPresent());
        assertEquals(new BigDecimal("1.28000000"), gbpUsd.get().rate());
        assertFalse(gbpUsd.get().isDerived());

        // Inverse
        var usdGbp = provider.fetchRate("USD", "GBP", null);
        assertTrue(usdGbp.isPresent());
        assertTrue(usdGbp.get().isDerived());

        // Unknown
        var unknown = provider.fetchRate("XYZ", "ABC", null);
        assertTrue(unknown.isEmpty());
    }

    @Test
    @DisplayName("setRate updates existing rate and handles null parameters")
    void setRateUpdates() {
        provider.setRate("GBP", "USD", new BigDecimal("1.35000000"));
        var gbpUsd = provider.fetchRate("GBP", "USD", null);
        assertTrue(gbpUsd.isPresent());
        assertEquals(new BigDecimal("1.35000000"), gbpUsd.get().rate());

        // Null checks
        provider.setRate(null, "USD", BigDecimal.ONE);
        provider.setRate("GBP", null, BigDecimal.ONE);
        provider.setRate("GBP", "USD", null);
    }

    @Test
    @DisplayName("fetchHistoricalRates and getProviderId")
    void historicalRatesAndProviderId() {
        List<FxRateQuote> rates = provider.fetchHistoricalRates("GBP", "USD", Instant.EPOCH, Instant.now());
        assertEquals(1, rates.size());

        assertEquals("DEFAULT_FX_PROVIDER", provider.getProviderId());
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.FxObservation;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.repository.FxObservationRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.currency.FxRateProvider;
import com.takakim.investtracker.service.currency.FxRateQuote;
import com.takakim.investtracker.service.currency.FxRateService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FxRateServiceTests {

    @Mock private FxObservationRepository fxObservationRepository;
    @Mock private FxRateProvider fxRateProvider;

    private FxRateService fxRateService;

    @BeforeEach
    void setUp() {
        fxRateService = new FxRateService(fxObservationRepository, fxRateProvider);
    }

    @Test
    @DisplayName("Identity conversion (same currency) returns rate of 1.0")
    void sameCurrencyReturnsOne() {
        FxRateQuote quote = fxRateService.getRate("USD", "USD", null);
        assertNotNull(quote);
        assertEquals(0, BigDecimal.ONE.compareTo(quote.rate()));
        assertEquals("IDENTITY", quote.sourceReference());
        assertFalse(quote.isDerived());
        assertNull(quote.warning());
        verifyNoInteractions(fxRateProvider);
    }

    @Test
    @DisplayName("Direct manual override takes precedence")
    void directManualOverridePrecedence() {
        Instant now = Instant.now();
        FxObservation manual = new FxObservation("GBP", "USD", new BigDecimal("1.30000000"), now, ObservationSourceType.MANUAL, "Manual rate");
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("GBP", "USD", ObservationSourceType.MANUAL))
                .thenReturn(Optional.of(manual));

        FxRateQuote quote = fxRateService.getRate("GBP", "USD", now);
        assertEquals(new BigDecimal("1.30000000"), quote.rate());
        assertEquals(ObservationSourceType.MANUAL, quote.sourceType());
        assertEquals("Manual rate", quote.sourceReference());
        assertFalse(quote.isDerived());
        verifyNoInteractions(fxRateProvider);
    }

    @Test
    @DisplayName("Inverted manual override derives inverse rate when direct is not found")
    void inverseManualOverrideDerivesRate() {
        Instant now = Instant.now();
        // USD -> GBP is 0.80000000 => GBP -> USD is 1 / 0.8 = 1.25000000
        FxObservation inverse = new FxObservation("USD", "GBP", new BigDecimal("0.80000000"), now, ObservationSourceType.MANUAL, null);
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("GBP", "USD", ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("USD", "GBP", ObservationSourceType.MANUAL))
                .thenReturn(Optional.of(inverse));

        FxRateQuote quote = fxRateService.getRate("GBP", "USD", now);
        assertEquals(0, new BigDecimal("1.25000000").compareTo(quote.rate()));
        assertEquals(ObservationSourceType.MANUAL, quote.sourceType());
        assertTrue(quote.isDerived());
    }

    @Test
    @DisplayName("Provider quote is fetched and cached when no manual overrides exist")
    void providerQuoteFetchedAndCached() {
        Instant now = Instant.now();
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("EUR", "USD", ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("USD", "EUR", ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());

        FxRateQuote prov = new FxRateQuote("EUR", "USD", new BigDecimal("1.08500000"), now, ObservationSourceType.PROVIDER, "ECB", false, null);
        when(fxRateProvider.fetchRate("EUR", "USD", now)).thenReturn(Optional.of(prov));

        FxRateQuote quote = fxRateService.getRate("EUR", "USD", now);
        assertEquals(new BigDecimal("1.08500000"), quote.rate());
        verify(fxObservationRepository, times(1)).save(any(FxObservation.class));
    }

    @Test
    @DisplayName("Triangulates through USD when non-USD direct rate is not available")
    void triangulateThroughUsd() {
        Instant now = Instant.now();
        // Request GBP -> JPY where provider has GBP->USD (1.28) and JPY->USD or JPY quotes
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(fxRateProvider.fetchRate("GBP", "JPY", now)).thenReturn(Optional.empty());

        FxRateQuote gbpUsd = new FxRateQuote("GBP", "USD", new BigDecimal("1.28000000"), now, ObservationSourceType.PROVIDER, "FEED", false, null);
        FxRateQuote jpyUsd = new FxRateQuote("JPY", "USD", new BigDecimal("0.00640000"), now, ObservationSourceType.PROVIDER, "FEED", false, null);
        when(fxRateProvider.fetchRate("GBP", "USD", now)).thenReturn(Optional.of(gbpUsd));
        when(fxRateProvider.fetchRate("JPY", "USD", now)).thenReturn(Optional.of(jpyUsd));

        FxRateQuote quote = fxRateService.getRate("GBP", "JPY", now);
        assertNotNull(quote);
        // 1.28 / 0.0064 = 200.00000000
        assertEquals(0, new BigDecimal("200.00000000").compareTo(quote.rate()));
        assertTrue(quote.isDerived());
        assertEquals("TRIANGULATED_USD", quote.sourceReference());
    }

    @Test
    @DisplayName("Partial triangulation failure falls back to persisted rate")
    void partialTriangulationFallback() {
        Instant now = Instant.now();
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(fxRateProvider.fetchRate("GBP", "CHF", now)).thenReturn(Optional.empty());
        FxRateQuote gbpUsd = new FxRateQuote("GBP", "USD", new BigDecimal("1.28000000"), now, ObservationSourceType.PROVIDER, "FEED", false, null);
        when(fxRateProvider.fetchRate("GBP", "USD", now)).thenReturn(Optional.of(gbpUsd));
        when(fxRateProvider.fetchRate("CHF", "USD", now)).thenReturn(Optional.empty());

        FxObservation obs = new FxObservation("GBP", "CHF", new BigDecimal("1.15000000"), now.minus(2, ChronoUnit.HOURS), ObservationSourceType.PROVIDER, "FEED");
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByObservedAtDesc("GBP", "CHF"))
                .thenReturn(Optional.of(obs));

        FxRateQuote quote = fxRateService.getRate("GBP", "CHF", now);
        assertEquals(new BigDecimal("1.15000000"), quote.rate());
        assertNotNull(quote.warning());
    }

    @Test
    @DisplayName("Direct and inverse manual override with stale timestamp attach warning and default reference")
    void staleManualOverrides() {
        Instant now = Instant.now();
        Instant staleTime = now.minus(30, ChronoUnit.HOURS);

        // Stale direct override without reference
        FxObservation directStale = new FxObservation("EUR", "GBP", new BigDecimal("0.85000000"), staleTime, ObservationSourceType.MANUAL, null);
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("EUR", "GBP", ObservationSourceType.MANUAL))
                .thenReturn(Optional.of(directStale));

        FxRateQuote directQuote = fxRateService.getRate("EUR", "GBP", now);
        assertNotNull(directQuote.warning());
        assertEquals("MANUAL_OVERRIDE", directQuote.sourceReference());

        // Stale inverse override
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("EUR", "GBP", ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        FxObservation invStale = new FxObservation("GBP", "EUR", new BigDecimal("1.17000000"), staleTime, ObservationSourceType.MANUAL, null);
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("GBP", "EUR", ObservationSourceType.MANUAL))
                .thenReturn(Optional.of(invStale));

        FxRateQuote invQuote = fxRateService.getRate("EUR", "GBP", now);
        assertTrue(invQuote.isDerived());
        assertNotNull(invQuote.warning());
    }

    @Test
    @DisplayName("Fallback to persisted observation with warning when provider and triangulation fail")
    void fallbackToPersistedRate() {
        Instant now = Instant.now();
        Instant past = now.minus(36, ChronoUnit.HOURS);
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(fxRateProvider.fetchRate(any(), any(), any())).thenReturn(Optional.empty());

        FxObservation obs = new FxObservation("CAD", "USD", new BigDecimal("0.74000000"), past, ObservationSourceType.PROVIDER, "OLD_FEED");
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByObservedAtDesc("CAD", "USD"))
                .thenReturn(Optional.of(obs));

        FxRateQuote quote = fxRateService.getRate("CAD", "USD", now);
        assertEquals(new BigDecimal("0.74000000"), quote.rate());
        assertNotNull(quote.warning());
        assertTrue(quote.warning().contains("using latest persisted observation"));
    }


    @Test
    @DisplayName("Throws ResourceNotFoundException when no FX rate can be determined")
    void unresolvableRateThrows() {
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(fxRateProvider.fetchRate(any(), any(), any())).thenReturn(Optional.empty());
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByObservedAtDesc("XYZ", "ABC"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> fxRateService.getRate("XYZ", "ABC", null));
    }

    @Test
    @DisplayName("convert transforms Money with appropriate FX rate")
    void convertMoney() {
        Money amount = new Money(new BigDecimal("100.0000"), new Currency("GBP"));
        Currency usd = new Currency("USD");

        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("GBP", "USD", ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(fxObservationRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc("USD", "GBP", ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        FxRateQuote prov = new FxRateQuote("GBP", "USD", new BigDecimal("1.25000000"), Instant.now(), ObservationSourceType.PROVIDER, "FEED", false, null);
        when(fxRateProvider.fetchRate(eq("GBP"), eq("USD"), any())).thenReturn(Optional.of(prov));

        Money converted = fxRateService.convert(amount, usd, null);
        assertEquals(usd, converted.currency());
        assertEquals(new BigDecimal("125.0000"), converted.amount());

        // Same currency conversion returns original Money object unchanged
        Money same = fxRateService.convert(amount, new Currency("GBP"), null);
        assertEquals(amount, same);
    }

    @Test
    @DisplayName("recordManualOverride validates currencies and persists observation")
    void recordManualOverrideValidatesAndPersists() {
        when(fxObservationRepository.save(any(FxObservation.class))).thenAnswer(inv -> inv.getArgument(0));

        FxObservation obs = fxRateService.recordManualOverride("GBP", "USD", new BigDecimal("1.31000000"), null, "Broker fill rate");
        assertNotNull(obs);
        assertEquals("GBP", obs.getBaseCurrency());
        assertEquals("USD", obs.getQuoteCurrency());
        assertEquals(new BigDecimal("1.31000000"), obs.getRate());
        assertEquals(ObservationSourceType.MANUAL, obs.getSourceType());
        assertEquals("Broker fill rate", obs.getSourceReference());

        // Identical currencies throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> fxRateService.recordManualOverride("USD", "USD", BigDecimal.ONE, null, null));
    }

    @Test
    @DisplayName("getHistoricalRates queries repository with upper-cased currencies")
    void getHistoricalRatesQueries() {
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();
        FxObservation obs = new FxObservation("GBP", "USD", new BigDecimal("1.28"), from, ObservationSourceType.PROVIDER, null);
        when(fxObservationRepository.findByBaseCurrencyAndQuoteCurrencyAndObservedAtBetweenOrderByObservedAtAsc(
                eq("GBP"), eq("USD"), any(), any()))
                .thenReturn(List.of(obs));

        List<FxObservation> results = fxRateService.getHistoricalRates("gbp", "usd", from, to);
        assertEquals(1, results.size());

        assertThrows(NullPointerException.class, () -> fxRateService.getHistoricalRates(null, "USD", from, to));
        assertThrows(NullPointerException.class, () -> fxRateService.getHistoricalRates("GBP", null, from, to));
    }

    @Test
    @DisplayName("FxRateQuote and FxObservation value invariants")
    void valueInvariants() {
        Instant now = Instant.now();
        assertThrows(NullPointerException.class, () -> new FxRateQuote(null, "USD", BigDecimal.ONE, now, ObservationSourceType.PROVIDER, null, false, null));
        assertThrows(NullPointerException.class, () -> new FxRateQuote("GBP", null, BigDecimal.ONE, now, ObservationSourceType.PROVIDER, null, false, null));
        assertThrows(NullPointerException.class, () -> new FxRateQuote("GBP", "USD", null, now, ObservationSourceType.PROVIDER, null, false, null));
        assertThrows(IllegalArgumentException.class, () -> new FxRateQuote("GBP", "USD", BigDecimal.ZERO, now, ObservationSourceType.PROVIDER, null, false, null));
        assertThrows(NullPointerException.class, () -> new FxRateQuote("GBP", "USD", BigDecimal.ONE, null, ObservationSourceType.PROVIDER, null, false, null));
        assertThrows(IllegalArgumentException.class, () -> new FxObservation("GBP", "USD", new BigDecimal("-0.5"), now, ObservationSourceType.PROVIDER, null));
    }

    @Test
    @DisplayName("Sub-unit pence sterling (GBX / GBP) conversion rates")
    void penceSubUnitConversions() {
        FxRateQuote gbxToGbp = fxRateService.getRate("GBX", "GBP", null);
        assertNotNull(gbxToGbp);
        assertEquals(new BigDecimal("0.01000000"), gbxToGbp.rate());
        assertEquals("SUB_UNIT", gbxToGbp.sourceReference());

        FxRateQuote gbpToGbx = fxRateService.getRate("GBP", "GBX", null);
        assertNotNull(gbpToGbx);
        assertEquals(new BigDecimal("100.00000000"), gbpToGbx.rate());
        assertEquals("SUB_UNIT", gbpToGbx.sourceReference());
    }
}


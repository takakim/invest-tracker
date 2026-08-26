package com.takakim.investtracker;

import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.market.MarketDataProvider;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
class MarketDataServiceTests {

    @Mock private MarketObservationRepository marketObservationRepository;
    @Mock private InstrumentRepository instrumentRepository;
    @Mock private MarketDataProvider marketDataProvider;

    private MarketDataService marketDataService;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataService(
                marketObservationRepository,
                instrumentRepository,
                marketDataProvider
        );
        instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when instrument does not exist")
    void missingInstrumentThrows() {
        UUID badId = UUID.randomUUID();
        when(instrumentRepository.findById(badId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> marketDataService.getLatestPrice(badId, Instant.now()));
    }

    @Test
    @DisplayName("Manual override takes precedence over provider quote")
    void manualOverrideTakesPrecedence() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));

        MarketObservation manual = new MarketObservation(
                instrument, new BigDecimal("190.00"), "USD",
                Instant.now(), ObservationSourceType.MANUAL, "Trader manual entry"
        );
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.of(manual));

        PriceQuote quote = marketDataService.getLatestPrice(id, null);
        assertNotNull(quote);
        assertEquals(new BigDecimal("190.00"), quote.price());
        assertEquals(ObservationSourceType.MANUAL, quote.sourceType());
        assertEquals("Trader manual entry", quote.sourceReference());
        assertFalse(quote.isStale());
        assertNull(quote.warning());
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    @DisplayName("Old manual override (>24h) attaches stale warning")
    void oldManualOverrideAttachesWarning() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));

        Instant oldTime = Instant.now().minus(48, ChronoUnit.HOURS);
        MarketObservation manual = new MarketObservation(
                instrument, new BigDecimal("180.00"), "USD",
                oldTime, ObservationSourceType.MANUAL, null
        );
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.of(manual));

        PriceQuote quote = marketDataService.getLatestPrice(id, Instant.now());
        assertTrue(quote.isStale());
        assertNotNull(quote.warning());
        assertEquals("MANUAL_OVERRIDE", quote.sourceReference());
    }

    @Test
    @DisplayName("Fetches from provider when no manual override exists, and saves observation")
    void providerQuoteFetchedAndSaved() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());

        Instant now = Instant.now();
        PriceQuote provQuote = new PriceQuote(
                id, new BigDecimal("185.25"), "USD", now,
                ObservationSourceType.PROVIDER, "TEST_FEED", false, null
        );
        when(marketDataProvider.fetchQuote(instrument, now)).thenReturn(Optional.of(provQuote));

        PriceQuote quote = marketDataService.getLatestPrice(id, now);
        assertEquals(new BigDecimal("185.25"), quote.price());
        assertEquals("TEST_FEED", quote.sourceReference());
        verify(marketObservationRepository, times(1)).save(any(MarketObservation.class));
    }

    @Test
    @DisplayName("Falls back to persisted observation when provider returns empty")
    void fallbackToPersistedWhenProviderFails() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.empty());

        Instant historical = Instant.now().minus(30, ChronoUnit.HOURS);
        MarketObservation persisted = new MarketObservation(
                instrument, new BigDecimal("175.00"), "USD", historical,
                ObservationSourceType.PROVIDER, "PREV_FEED"
        );
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.of(persisted));

        PriceQuote quote = marketDataService.getLatestPrice(id, Instant.now());
        assertEquals(new BigDecimal("175.00"), quote.price());
        assertTrue(quote.isStale());
        assertTrue(quote.warning().contains("using latest persisted observation"));
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when neither provider nor persisted observation is available")
    void noPriceAvailableThrows() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.empty());
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> marketDataService.getLatestPrice(id, null));
    }

    @Test
    @DisplayName("recordManualOverride persists observation with MANUAL source type")
    void recordManualOverridePersists() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));

        Instant now = Instant.now();
        when(marketObservationRepository.save(any(MarketObservation.class))).thenAnswer(inv -> inv.getArgument(0));

        MarketObservation obs = marketDataService.recordManualOverride(
                id, new BigDecimal("205.50"), "USD", now, "Year-end audit adjustment"
        );

        assertNotNull(obs);
        assertEquals(new BigDecimal("205.50"), obs.getPrice());
        assertEquals("USD", obs.getCurrency());
        assertEquals(ObservationSourceType.MANUAL, obs.getSourceType());
        assertEquals("Year-end audit adjustment", obs.getSourceReference());

        // Defaults test (null currency, null observedAt, null reason)
        MarketObservation defaultObs = marketDataService.recordManualOverride(
                id, new BigDecimal("210.00"), null, null, null
        );
        assertEquals("USD", defaultObs.getCurrency());
        assertEquals("MANUAL_OVERRIDE", defaultObs.getSourceReference());
    }


    @Test
    @DisplayName("getHistoricalPrices validates instrument and queries repository")
    void getHistoricalPricesQueries() {
        UUID id = instrument.getId();
        when(instrumentRepository.existsById(id)).thenReturn(true);

        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant to = Instant.now();
        MarketObservation obs = new MarketObservation(instrument, new BigDecimal("180"), "USD", from, ObservationSourceType.PROVIDER, null);
        when(marketObservationRepository.findByInstrumentIdAndObservedAtBetweenOrderByObservedAtAsc(id, from, to))
                .thenReturn(List.of(obs));

        List<MarketObservation> result = marketDataService.getHistoricalPrices(id, from, to);
        assertEquals(1, result.size());

        when(instrumentRepository.existsById(id)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> marketDataService.getHistoricalPrices(id, from, to));
    }

    @Test
    @DisplayName("PriceQuote value record invariants")
    void priceQuoteInvariants() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThrows(NullPointerException.class, () -> new PriceQuote(
                null, BigDecimal.TEN, "USD", now, ObservationSourceType.PROVIDER, null, false, null
        ));
        assertThrows(NullPointerException.class, () -> new PriceQuote(
                id, null, "USD", now, ObservationSourceType.PROVIDER, null, false, null
        ));
        assertThrows(IllegalArgumentException.class, () -> new PriceQuote(
                id, new BigDecimal("-1"), "USD", now, ObservationSourceType.PROVIDER, null, false, null
        ));
        assertThrows(NullPointerException.class, () -> new PriceQuote(
                id, BigDecimal.TEN, null, now, ObservationSourceType.PROVIDER, null, false, null
        ));
        assertThrows(NullPointerException.class, () -> new PriceQuote(
                id, BigDecimal.TEN, "USD", null, ObservationSourceType.PROVIDER, null, false, null
        ));
        assertThrows(NullPointerException.class, () -> new PriceQuote(
                id, BigDecimal.TEN, "USD", now, null, null, false, null
        ));
    }

    @Test
    @DisplayName("syncHistoricalPrices fetches and saves quotes")
    void syncHistoricalPricesSaves() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        Instant now = Instant.now();
        PriceQuote q1 = new PriceQuote(id, new BigDecimal("150.00"), "USD", now.minus(30, ChronoUnit.DAYS), ObservationSourceType.PROVIDER, "FEED", false, null);
        PriceQuote q2 = new PriceQuote(id, new BigDecimal("160.00"), "USD", now, ObservationSourceType.PROVIDER, "FEED", false, null);
        when(marketDataProvider.fetchHistoricalQuotes(instrument, now.minus(30, ChronoUnit.DAYS), now))
                .thenReturn(List.of(q1, q2));
        when(marketObservationRepository.save(org.mockito.ArgumentMatchers.any(MarketObservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<MarketObservation> synced = marketDataService.syncHistoricalPrices(id, now.minus(30, ChronoUnit.DAYS), now);
        assertEquals(2, synced.size());

        when(instrumentRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> marketDataService.syncHistoricalPrices(id, now.minus(30, ChronoUnit.DAYS), now));
    }
}

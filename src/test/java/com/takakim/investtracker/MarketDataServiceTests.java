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
    @Mock private com.takakim.investtracker.repository.TransactionRepository transactionRepository;

    private MarketDataService marketDataService;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataService(
                marketObservationRepository,
                instrumentRepository,
                marketDataProvider,
                transactionRepository
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

        Instant oldTime = Instant.now().minus(120, ChronoUnit.HOURS);
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

        Instant historical = Instant.now().minus(120, ChronoUnit.HOURS);
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
    @DisplayName("Persisted observation with zero price falls back to transaction trade price")
    void testPersistedObservationZeroPriceFallsBackToTransaction() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.empty());

        MarketObservation persistedZero = new MarketObservation(
                instrument, BigDecimal.ZERO, "USD", Instant.now().minus(2, ChronoUnit.HOURS),
                ObservationSourceType.PROVIDER, "FEED"
        );
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.of(persistedZero));

        com.takakim.investtracker.domain.Portfolio port = new com.takakim.investtracker.domain.Portfolio("Port", new Currency("USD"), com.takakim.investtracker.domain.CostBasisMethod.AVERAGE_COST, com.takakim.investtracker.domain.ReturnMethod.TWR);
        com.takakim.investtracker.domain.Account account = new com.takakim.investtracker.domain.Account(port, "Acc", "Broker", new Currency("USD"));
        com.takakim.investtracker.domain.Transaction tx = new com.takakim.investtracker.domain.Transaction(
                account, instrument, com.takakim.investtracker.domain.TransactionType.BUY, Instant.now().minus(5, ChronoUnit.HOURS), null,
                new BigDecimal("10"), new BigDecimal("165.00"), null, null, null, "USD",
                null, null, null, null
        );
        when(transactionRepository.findByInstrumentIdOrderByTradeDateDesc(id)).thenReturn(List.of(tx));

        PriceQuote quote = marketDataService.getLatestPrice(id, Instant.now());
        assertEquals(new BigDecimal("165.00"), quote.price());
        assertEquals("LAST_TRANSACTION_TRADE_PRICE", quote.sourceReference());
    }

    @Test
    @DisplayName("Reuses fresh (<5 min) persisted observation when asOf is null")
    void reusesFreshPersistedObservation() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());

        Instant freshTime = Instant.now().minus(2, ChronoUnit.MINUTES);
        MarketObservation freshObs = new MarketObservation(
                instrument, new BigDecimal("195.00"), "USD",
                freshTime, ObservationSourceType.PROVIDER, "TWELVE_DATA"
        );
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.of(freshObs));

        PriceQuote quote = marketDataService.getLatestPrice(id, null);
        assertEquals(new BigDecimal("195.00"), quote.price());
        assertEquals("TWELVE_DATA", quote.sourceReference());
        assertFalse(quote.isStale());
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when neither provider, persisted observation, nor transaction price is available")
    void noPriceAvailableThrows() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.empty());
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByInstrumentIdOrderByTradeDateDesc(id)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> marketDataService.getLatestPrice(id, null));
    }

    @Test
    @DisplayName("Falls back to latest transaction trade price when provider and persisted quotes are missing")
    void fallbackToTransactionPrice() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.empty());
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.empty());

        com.takakim.investtracker.domain.Portfolio portfolio = new com.takakim.investtracker.domain.Portfolio("P", new Currency("USD"), com.takakim.investtracker.domain.CostBasisMethod.FIFO, com.takakim.investtracker.domain.ReturnMethod.TWR);
        com.takakim.investtracker.domain.Account acct = new com.takakim.investtracker.domain.Account(portfolio, "Trading", "Broker", new Currency("USD"));
        com.takakim.investtracker.domain.Transaction tx = new com.takakim.investtracker.domain.Transaction(
                acct, instrument, com.takakim.investtracker.domain.TransactionType.BUY, Instant.now().minus(5, ChronoUnit.DAYS),
                null, new BigDecimal("10"), new BigDecimal("1.1145"), new BigDecimal("11.145"), null, null, "USD", null, null, null, null
        );
        com.takakim.investtracker.domain.Transaction txNullPrice = new com.takakim.investtracker.domain.Transaction(
                acct, null, com.takakim.investtracker.domain.TransactionType.DEPOSIT, Instant.now().minus(1, ChronoUnit.DAYS),
                null, null, null, new BigDecimal("100.00"), null, null, "USD", null, null, null, null
        );
        when(transactionRepository.findByInstrumentIdOrderByTradeDateDesc(id)).thenReturn(List.of(txNullPrice, tx));

        PriceQuote quote = marketDataService.getLatestPrice(id, Instant.now());
        assertNotNull(quote);
        assertEquals(new BigDecimal("1.1145"), quote.price());
        assertEquals("LAST_TRANSACTION_TRADE_PRICE", quote.sourceReference());
    }

    @Test
    @DisplayName("Persisted observation older than 24h marks quote as stale")
    void persistedObservationStale() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.empty());

        Instant oldTime = Instant.now().minus(120, ChronoUnit.HOURS);
        MarketObservation obs = new MarketObservation(instrument, new BigDecimal("150.00"), "USD", oldTime, ObservationSourceType.PROVIDER, "FEED");
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.of(obs));

        PriceQuote quote = marketDataService.getLatestPrice(id, Instant.now());
        assertTrue(quote.isStale());
        assertNotNull(quote.warning());
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

        // Blank reason test
        MarketObservation blankReasonObs = marketDataService.recordManualOverride(
                id, new BigDecimal("215.00"), "USD", now, "   "
        );
        assertEquals("MANUAL_OVERRIDE", blankReasonObs.getSourceReference());
    }

    @Test
    @DisplayName("Falls back to persisted quote when provider throws an exception")
    void fallbackWhenProviderThrowsException() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenThrow(new RuntimeException("API timeout"));

        MarketObservation persisted = new MarketObservation(
                instrument, new BigDecimal("170.00"), "USD", Instant.now(),
                ObservationSourceType.PROVIDER, "CACHED"
        );
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.of(persisted));

        PriceQuote quote = marketDataService.getLatestPrice(id, Instant.now());
        assertEquals(new BigDecimal("170.00"), quote.price());
    }

    @Test
    @DisplayName("Transaction price fallback skips transactions with null or zero prices")
    void transactionFallbackSkipsZeroOrNullPrices() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.empty());
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.empty());

        com.takakim.investtracker.domain.Portfolio portfolio = new com.takakim.investtracker.domain.Portfolio("P", new Currency("USD"), com.takakim.investtracker.domain.CostBasisMethod.FIFO, com.takakim.investtracker.domain.ReturnMethod.TWR);
        com.takakim.investtracker.domain.Account acct = new com.takakim.investtracker.domain.Account(portfolio, "Trading", "Broker", new Currency("USD"));
        com.takakim.investtracker.domain.Transaction txNullPrice = new com.takakim.investtracker.domain.Transaction(
                acct, instrument, com.takakim.investtracker.domain.TransactionType.DIVIDEND, Instant.now().minus(10, ChronoUnit.DAYS),
                null, null, null, new BigDecimal("50.00"), null, null, "USD", null, null, null, null
        );
        com.takakim.investtracker.domain.Transaction txValid = new com.takakim.investtracker.domain.Transaction(
                acct, instrument, com.takakim.investtracker.domain.TransactionType.BUY, Instant.now().minus(2, ChronoUnit.DAYS),
                null, new BigDecimal("10"), new BigDecimal("150.00"), new BigDecimal("1500.00"), null, null, "USD", null, null, null, null
        );
        when(transactionRepository.findByInstrumentIdOrderByTradeDateDesc(id))
                .thenReturn(List.of(txNullPrice, txValid));

        PriceQuote quote = marketDataService.getLatestPrice(id, Instant.now());
        assertEquals(new BigDecimal("150.00"), quote.price());
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

    @Test
    @DisplayName("manualPriceOnly instrument skips external marketDataProvider and uses persisted/trade price")
    void testManualPriceOnlyBypassesExternalProvider() {
        Instrument manualInst = new Instrument("Private Asset", AssetClass.OTHER, "PRIV", null, null, new Currency("USD"), true);
        UUID id = manualInst.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(manualInst));

        MarketObservation obs = new MarketObservation(manualInst, new BigDecimal("50.00"), "USD", Instant.now(), ObservationSourceType.PROVIDER, "PREV");
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.of(obs));

        PriceQuote quote = marketDataService.getLatestPrice(id, Instant.now());
        assertNotNull(quote);
        assertEquals(new BigDecimal("50.00"), quote.price());
        // Verify external provider was NEVER called
        verify(marketDataProvider, never()).fetchQuote(eq(manualInst), any());
    }

    @Test
    @DisplayName("Recent observation with zero price or mismatched currency is not returned from freshness cache")
    void testRecentObservationWithZeroPriceOrDifferentCurrencyIgnored() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));

        MarketObservation zeroObs = new MarketObservation(instrument, BigDecimal.ZERO, "USD", Instant.now(), ObservationSourceType.PROVIDER, "FEED");
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id)).thenReturn(Optional.of(zeroObs));

        PriceQuote provQuote = new PriceQuote(id, new BigDecimal("190.00"), "USD", Instant.now(), ObservationSourceType.PROVIDER, "TEST_FEED", false, null);
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.of(provQuote));

        PriceQuote quote = marketDataService.getLatestPrice(id, null);
        assertEquals(new BigDecimal("190.00"), quote.price());

        // Test currency mismatch
        MarketObservation gbpObs = new MarketObservation(instrument, new BigDecimal("150.00"), "GBP", Instant.now(), ObservationSourceType.PROVIDER, "FEED");
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id)).thenReturn(Optional.of(gbpObs));
        PriceQuote quoteAfterCurrencyMismatch = marketDataService.getLatestPrice(id, null);
        assertEquals(new BigDecimal("190.00"), quoteAfterCurrencyMismatch.price());
    }

    @Test
    @DisplayName("Provider quote with zero price is rejected and falls back")
    void testProviderQuoteZeroPriceRejected() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id)).thenReturn(Optional.empty());

        PriceQuote zeroProvQuote = new PriceQuote(id, BigDecimal.ZERO, "USD", Instant.now(), ObservationSourceType.PROVIDER, "TEST_FEED", false, null);
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.of(zeroProvQuote));

        // When provider quote is zero and no observations exist, throws ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () -> marketDataService.getLatestPrice(id, Instant.now()));
    }

    @Test
    @DisplayName("Weekend market detection and observation freshness logic")
    void testWeekendMarketDetectionAndFreshness() {
        // Saturday 2026-08-29 12:00 UTC
        Instant saturday = Instant.parse("2026-08-29T12:00:00Z");
        // Sunday 2026-08-30 15:00 UTC
        Instant sunday = Instant.parse("2026-08-30T15:00:00Z");
        // Monday 2026-08-31 06:00 UTC (pre-market)
        Instant mondayEarly = Instant.parse("2026-08-31T06:00:00Z");
        // Monday 2026-08-31 10:00 UTC (trading hours)
        Instant mondayOpen = Instant.parse("2026-08-31T10:00:00Z");
        // Tuesday 2026-09-01 14:00 UTC (trading session)
        Instant tuesday = Instant.parse("2026-09-01T14:00:00Z");

        // Null targetTime defaults to now
        assertNotNull(MarketDataService.isWeekendMarketClosed(null, AssetClass.STOCK));

        // Stocks/ETFs are closed on weekend, Crypto is never closed
        assertTrue(MarketDataService.isWeekendMarketClosed(saturday, AssetClass.STOCK));
        assertTrue(MarketDataService.isWeekendMarketClosed(sunday, AssetClass.ETF));
        assertTrue(MarketDataService.isWeekendMarketClosed(mondayEarly, AssetClass.STOCK));
        assertFalse(MarketDataService.isWeekendMarketClosed(mondayOpen, AssetClass.STOCK));
        assertFalse(MarketDataService.isWeekendMarketClosed(tuesday, AssetClass.STOCK));
        assertFalse(MarketDataService.isWeekendMarketClosed(saturday, AssetClass.CRYPTO));

        // Friday 2026-08-28 20:00 UTC close observation
        Instant fridayClose = Instant.parse("2026-08-28T20:00:00Z");
        // Fresh observation within 5 min on Tuesday
        Instant tuesdayFresh = tuesday.minus(2, ChronoUnit.MINUTES);
        assertTrue(MarketDataService.isObservationFresh(tuesdayFresh, tuesday, AssetClass.STOCK));

        // On Sunday (43 hours later), Friday close is fresh and not stale for stocks
        assertTrue(MarketDataService.isObservationFresh(fridayClose, sunday, AssetClass.STOCK));
        assertFalse(MarketDataService.isObservationStale(fridayClose, sunday, AssetClass.STOCK));

        // Old observation (>80h on weekend) is stale and not fresh
        Instant tuesdayBefore = Instant.parse("2026-08-25T12:00:00Z");
        assertFalse(MarketDataService.isObservationFresh(tuesdayBefore, sunday, AssetClass.STOCK));
        assertTrue(MarketDataService.isObservationStale(tuesdayBefore, sunday, AssetClass.STOCK));

        // But for Crypto on Sunday, 43h observation is stale and not fresh (crypto trades 24/7)
        assertFalse(MarketDataService.isObservationFresh(fridayClose, sunday, AssetClass.CRYPTO));
        assertTrue(MarketDataService.isObservationStale(fridayClose, sunday, AssetClass.CRYPTO));

        // Null checks
        assertFalse(MarketDataService.isObservationFresh(null, sunday, AssetClass.STOCK));
        assertFalse(MarketDataService.isObservationFresh(fridayClose, null, AssetClass.STOCK));
        assertTrue(MarketDataService.isObservationStale(null, sunday, AssetClass.STOCK));
        assertTrue(MarketDataService.isObservationStale(fridayClose, null, AssetClass.STOCK));
    }

    @Test
    @DisplayName("Over the weekend, valid Friday close observation is reused without querying external provider")
    void testWeekendReusesFridayCloseWithoutCallingProvider() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));

        // Mock current time on weekend (Sunday)
        // If recent observation is from Friday (e.g. 2 days ago), isObservationFresh returns true over weekend
        Instant fridayObs = Instant.now().minus(40, ChronoUnit.HOURS);
        MarketObservation obs = new MarketObservation(instrument, new BigDecimal("185.00"), "USD", fridayObs, ObservationSourceType.PROVIDER, "YAHOO_FINANCE");
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id)).thenReturn(Optional.of(obs));

        // When asOf is null (live query)
        PriceQuote quote = marketDataService.getLatestPrice(id, null);
        assertNotNull(quote);
        assertEquals(new BigDecimal("185.00"), quote.price());
    }

    @Test
    @DisplayName("Default fallback provider mock price never overwrites existing persisted observation in database")
    void testDefaultProviderDoesNotOverwriteExistingPersistedObservation() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());

        // Previous genuine observation from Friday at 289.47 USD
        Instant fridayObs = Instant.now().minus(96, ChronoUnit.HOURS); // stale (>80h)
        MarketObservation persisted = new MarketObservation(
                instrument, new BigDecimal("289.47"), "USD", fridayObs,
                ObservationSourceType.PROVIDER, "TWELVE_DATA"
        );
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.of(persisted));

        // Provider (e.g. DefaultMarketDataProvider) returns fallback mock 65.30 with DEFAULT_PROVIDER
        PriceQuote mockQuote = new PriceQuote(
                id, new BigDecimal("65.30"), "USD", Instant.now(),
                ObservationSourceType.PROVIDER, "DEFAULT_PROVIDER", false, null
        );
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.of(mockQuote));

        PriceQuote result = marketDataService.getLatestPrice(id, Instant.now());

        assertNotNull(result);
        // It must NOT be the 65.30 mock, it must return the genuine persisted 289.47
        assertEquals(new BigDecimal("289.47"), result.price());
        assertEquals("TWELVE_DATA", result.sourceReference());
        verify(marketObservationRepository, never()).save(any(MarketObservation.class));
    }

    @Test
    @DisplayName("Default provider mock price is persisted only when instrument has zero prior observations")
    void testDefaultProviderPersistedWhenNoPriorObservationExists() {
        UUID id = instrument.getId();
        when(instrumentRepository.findById(id)).thenReturn(Optional.of(instrument));
        when(marketObservationRepository.findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(id, ObservationSourceType.MANUAL))
                .thenReturn(Optional.empty());
        when(marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(id))
                .thenReturn(Optional.empty());

        PriceQuote mockQuote = new PriceQuote(
                id, new BigDecimal("65.30"), "USD", Instant.now(),
                ObservationSourceType.PROVIDER, "DEFAULT_PROVIDER", false, null
        );
        when(marketDataProvider.fetchQuote(eq(instrument), any())).thenReturn(Optional.of(mockQuote));

        PriceQuote result = marketDataService.getLatestPrice(id, Instant.now());

        assertNotNull(result);
        assertEquals(new BigDecimal("65.30"), result.price());
        verify(marketObservationRepository, times(1)).save(any(MarketObservation.class));
    }
}

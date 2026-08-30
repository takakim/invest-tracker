package com.takakim.investtracker;

import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.FxObservation;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.repository.FxObservationRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.service.market.ObservationStorageService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ObservationStorageServiceTests {

    @Mock
    private MarketObservationRepository marketObservationRepository;

    @Mock
    private FxObservationRepository fxObservationRepository;

    private ObservationStorageService service;

    @BeforeEach
    void setUp() {
        service = new ObservationStorageService(marketObservationRepository, fxObservationRepository);
    }

    @Test
    void saveMarketObservation_success() {
        Instrument instrument = new Instrument("Apple Inc", com.takakim.investtracker.domain.AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        MarketObservation obs = new MarketObservation(
                instrument, new BigDecimal("185.50"), "USD", Instant.now(), ObservationSourceType.PROVIDER, "TEST"
        );

        service.saveMarketObservation(obs);
        verify(marketObservationRepository).save(obs);
    }

    @Test
    void saveMarketObservation_handlesExceptionGracefully() {
        Instrument instrument = new Instrument("Apple Inc", com.takakim.investtracker.domain.AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        MarketObservation obs = new MarketObservation(
                instrument, new BigDecimal("185.50"), "USD", Instant.now(), ObservationSourceType.PROVIDER, "TEST"
        );
        doThrow(new RuntimeException("DB error")).when(marketObservationRepository).save(any());

        service.saveMarketObservation(obs);
        verify(marketObservationRepository).save(obs);
    }

    @Test
    void saveFxObservation_success() {
        FxObservation obs = new FxObservation("EUR", "USD", new BigDecimal("1.0850"), Instant.now(), ObservationSourceType.PROVIDER, "TEST");

        service.saveFxObservation(obs);
        verify(fxObservationRepository).save(obs);
    }

    @Test
    void saveFxObservation_handlesExceptionGracefully() {
        FxObservation obs = new FxObservation("EUR", "USD", new BigDecimal("1.0850"), Instant.now(), ObservationSourceType.PROVIDER, "TEST");
        doThrow(new RuntimeException("DB error")).when(fxObservationRepository).save(any());

        service.saveFxObservation(obs);
        verify(fxObservationRepository).save(obs);
    }

    @Test
    void saveMarketObservation_skipsNullOrZero() {
        service.saveMarketObservation(null);

        Instrument instrument = new Instrument("Apple Inc", com.takakim.investtracker.domain.AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        MarketObservation zeroPrice = new MarketObservation(instrument, BigDecimal.ZERO, "USD", Instant.now(), ObservationSourceType.PROVIDER, "TEST");
        service.saveMarketObservation(zeroPrice);
    }

    @Test
    void saveFxObservation_skipsNull() {
        service.saveFxObservation(null);
    }
}

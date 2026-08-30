package com.takakim.investtracker.service.market;

import com.takakim.investtracker.domain.FxObservation;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.repository.FxObservationRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObservationStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObservationStorageService.class);

    private final MarketObservationRepository marketObservationRepository;
    private final FxObservationRepository fxObservationRepository;

    public ObservationStorageService(
            MarketObservationRepository marketObservationRepository,
            FxObservationRepository fxObservationRepository) {
        this.marketObservationRepository = marketObservationRepository;
        this.fxObservationRepository = fxObservationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMarketObservation(MarketObservation obs) {
        try {
            marketObservationRepository.save(obs);
        } catch (Exception e) {
            log.debug("Unable to persist market observation for {}: {}", obs.getInstrument().getName(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFxObservation(FxObservation obs) {
        try {
            fxObservationRepository.save(obs);
        } catch (Exception e) {
            log.debug("Unable to persist FX observation for {}/{}: {}", obs.getBaseCurrency(), obs.getQuoteCurrency(), e.getMessage());
        }
    }
}

package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketObservationRepository extends JpaRepository<MarketObservation, UUID> {

    Optional<MarketObservation> findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(
            UUID instrumentId, ObservationSourceType sourceType);

    Optional<MarketObservation> findFirstByInstrumentIdOrderByObservedAtDesc(UUID instrumentId);

    List<MarketObservation> findByInstrumentIdAndObservedAtBetweenOrderByObservedAtAsc(
            UUID instrumentId, Instant from, Instant to);
}

package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketObservationRepository extends JpaRepository<MarketObservation, UUID> {

    @Query("SELECT m FROM MarketObservation m WHERE m.instrument.id = :instrumentId AND m.sourceType = :sourceType AND m.price > 0 ORDER BY m.observedAt DESC")
    List<MarketObservation> findPositiveByInstrumentIdAndSourceTypeOrderByObservedAtDesc(
            @Param("instrumentId") UUID instrumentId, @Param("sourceType") ObservationSourceType sourceType);

    default Optional<MarketObservation> findFirstByInstrumentIdAndSourceTypeOrderByObservedAtDesc(
            UUID instrumentId, ObservationSourceType sourceType) {
        List<MarketObservation> list = findPositiveByInstrumentIdAndSourceTypeOrderByObservedAtDesc(instrumentId, sourceType);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Query("SELECT m FROM MarketObservation m WHERE m.instrument.id = :instrumentId AND m.price > 0 ORDER BY m.observedAt DESC")
    List<MarketObservation> findPositiveByInstrumentIdOrderByObservedAtDesc(@Param("instrumentId") UUID instrumentId);

    default Optional<MarketObservation> findFirstByInstrumentIdOrderByObservedAtDesc(UUID instrumentId) {
        List<MarketObservation> list = findPositiveByInstrumentIdOrderByObservedAtDesc(instrumentId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    List<MarketObservation> findByInstrumentIdAndObservedAtBetweenOrderByObservedAtAsc(
            UUID instrumentId, Instant from, Instant to);
}

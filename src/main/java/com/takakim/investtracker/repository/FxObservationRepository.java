package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.FxObservation;
import com.takakim.investtracker.domain.ObservationSourceType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FxObservationRepository extends JpaRepository<FxObservation, UUID> {

    Optional<FxObservation> findFirstByBaseCurrencyAndQuoteCurrencyAndSourceTypeOrderByObservedAtDesc(
            String baseCurrency, String quoteCurrency, ObservationSourceType sourceType);

    Optional<FxObservation> findFirstByBaseCurrencyAndQuoteCurrencyOrderByObservedAtDesc(
            String baseCurrency, String quoteCurrency);

    List<FxObservation> findByBaseCurrencyAndQuoteCurrencyAndObservedAtBetweenOrderByObservedAtAsc(
            String baseCurrency, String quoteCurrency, Instant from, Instant to);
}

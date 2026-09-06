package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.MarketDataRefreshTask;
import com.takakim.investtracker.domain.RefreshTaskStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketDataRefreshTaskRepository extends JpaRepository<MarketDataRefreshTask, UUID> {

    @Query(value = "SELECT * FROM market_data_refresh_tasks WHERE status = 'PENDING' AND scheduled_at <= :now ORDER BY scheduled_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<MarketDataRefreshTask> findNextDueTaskForUpdate(@Param("now") Instant now);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END FROM MarketDataRefreshTask t WHERE t.instrument.id = :instrumentId AND t.status IN :statuses")
    boolean existsActiveByInstrumentId(@Param("instrumentId") UUID instrumentId, @Param("statuses") Collection<RefreshTaskStatus> statuses);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END FROM MarketDataRefreshTask t WHERE t.baseCurrency = :baseCurrency AND t.quoteCurrency = :quoteCurrency AND t.status IN :statuses")
    boolean existsActiveByCurrencyPair(@Param("baseCurrency") String baseCurrency, @Param("quoteCurrency") String quoteCurrency, @Param("statuses") Collection<RefreshTaskStatus> statuses);

    long countByStatus(RefreshTaskStatus status);

    Optional<MarketDataRefreshTask> findFirstByInstrumentIdAndStatusInOrderByCreatedAtDesc(UUID instrumentId, Collection<RefreshTaskStatus> statuses);
}

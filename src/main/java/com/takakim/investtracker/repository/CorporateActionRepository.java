package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.CorporateAction;
import com.takakim.investtracker.domain.CorporateActionStatus;
import com.takakim.investtracker.domain.CorporateActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CorporateActionRepository extends JpaRepository<CorporateAction, UUID> {

    List<CorporateAction> findByInstrumentIdOrderByExDateDesc(UUID instrumentId);

    List<CorporateAction> findByInstrumentIdInOrderByExDateDesc(List<UUID> instrumentIds);

    List<CorporateAction> findByInstrumentIdInAndStatusOrderByExDateDesc(List<UUID> instrumentIds, CorporateActionStatus status);

    Optional<CorporateAction> findByInstrumentIdAndActionTypeAndExDate(
            UUID instrumentId, CorporateActionType actionType, Instant exDate);

    Optional<CorporateAction> findBySourceAndExternalId(String source, String externalId);

    @Query("SELECT ca FROM CorporateAction ca WHERE ca.instrument.id IN " +
            "(SELECT DISTINCT p.instrument.id FROM Position p WHERE p.account.portfolio.id = :portfolioId AND p.status = 'ACTIVE') " +
            "ORDER BY ca.exDate DESC")
    List<CorporateAction> findActivePortfolioCorporateActions(@Param("portfolioId") UUID portfolioId);

    @Query("SELECT ca FROM CorporateAction ca WHERE ca.instrument.id IN " +
            "(SELECT DISTINCT p.instrument.id FROM Position p WHERE p.account.portfolio.id = :portfolioId AND p.status = 'ACTIVE') " +
            "AND ca.status = :status ORDER BY ca.exDate DESC")
    List<CorporateAction> findActivePortfolioCorporateActionsByStatus(
            @Param("portfolioId") UUID portfolioId,
            @Param("status") CorporateActionStatus status);
}

package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.TargetAllocationPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TargetAllocationPlanRepository extends JpaRepository<TargetAllocationPlan, UUID> {

    @Query("SELECT p FROM TargetAllocationPlan p LEFT JOIN FETCH p.items i LEFT JOIN FETCH i.instrument WHERE p.portfolio.id = :portfolioId")
    Optional<TargetAllocationPlan> findByPortfolioIdWithItems(@Param("portfolioId") UUID portfolioId);

    Optional<TargetAllocationPlan> findByPortfolioId(UUID portfolioId);

    void deleteByPortfolioId(UUID portfolioId);
}

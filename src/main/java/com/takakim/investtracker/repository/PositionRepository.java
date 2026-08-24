package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> {

    @EntityGraph(attributePaths = {"account", "instrument"})
    @Override
    Optional<Position> findById(UUID id);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Position> findByAccountId(UUID accountId);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Position> findByAccountIdAndStatus(UUID accountId, PositionStatus status);

    @EntityGraph(attributePaths = {"account", "instrument"})
    Optional<Position> findByAccountIdAndInstrumentId(UUID accountId, UUID instrumentId);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Position> findByAccountPortfolioId(UUID portfolioId);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Position> findByAccountPortfolioIdAndStatus(UUID portfolioId, PositionStatus status);
}

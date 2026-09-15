package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.HoldingAiEvaluation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

@Repository
public interface HoldingAiEvaluationRepository extends JpaRepository<HoldingAiEvaluation, UUID> {

    List<HoldingAiEvaluation> findByPortfolioId(UUID portfolioId);

    Optional<HoldingAiEvaluation> findByPortfolioIdAndInstrumentId(UUID portfolioId, UUID instrumentId);

    @Transactional
    void deleteByPortfolioId(UUID portfolioId);

    @Transactional
    void deleteByPortfolioIdAndInstrumentId(UUID portfolioId, UUID instrumentId);
}

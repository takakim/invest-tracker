package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.PortfolioAiEvaluation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PortfolioAiEvaluationRepository extends JpaRepository<PortfolioAiEvaluation, UUID> {

    Optional<PortfolioAiEvaluation> findByPortfolioId(UUID portfolioId);

    @Transactional
    void deleteByPortfolioId(UUID portfolioId);
}

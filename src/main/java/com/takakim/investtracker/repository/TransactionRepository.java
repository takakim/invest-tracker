package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @EntityGraph(attributePaths = {"account", "instrument"})
    @Override
    Optional<Transaction> findById(UUID id);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Transaction> findByAccountIdOrderByTradeDateDesc(UUID accountId);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Transaction> findByAccountIdAndTypeOrderByTradeDateDesc(UUID accountId, TransactionType type);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Transaction> findByAccountIdAndInstrumentIdOrderByTradeDateAsc(UUID accountId, UUID instrumentId);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Transaction> findByAccountPortfolioIdOrderByTradeDateDesc(UUID portfolioId);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Transaction> findByAccountPortfolioIdAndTypeOrderByTradeDateDesc(UUID portfolioId, TransactionType type);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Transaction> findByAccountPortfolioIdAndInstrumentIdAndTypeOrderByTradeDateDesc(UUID portfolioId, UUID instrumentId, TransactionType type);

    @EntityGraph(attributePaths = {"account", "instrument"})
    List<Transaction> findByInstrumentIdOrderByTradeDateDesc(UUID instrumentId);
}

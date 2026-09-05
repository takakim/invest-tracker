package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.PortfolioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    List<Portfolio> findAllByStatusOrderByNameAsc(PortfolioStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.baseCurrency FROM Portfolio p WHERE p.status = 'ACTIVE' AND p.baseCurrency IS NOT NULL")
    List<String> findDistinctBaseCurrencies();
}

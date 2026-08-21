package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.PortfolioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    List<Portfolio> findAllByStatusOrderByNameAsc(PortfolioStatus status);
}

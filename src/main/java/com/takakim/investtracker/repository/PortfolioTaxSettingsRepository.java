package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.PortfolioTaxSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioTaxSettingsRepository extends JpaRepository<PortfolioTaxSettings, UUID> {

    Optional<PortfolioTaxSettings> findByPortfolioIdAndTaxYear(UUID portfolioId, String taxYear);

    List<PortfolioTaxSettings> findAllByPortfolioIdOrderByTaxYearDesc(UUID portfolioId);
}

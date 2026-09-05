package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {
    List<Instrument> findAllByOrderByNameAsc();
    boolean existsByIsinIgnoreCase(String isin);
    Optional<Instrument> findByTicker(String ticker);
    Optional<Instrument> findByIsin(String isin);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT i.currency FROM Instrument i WHERE i.currency IS NOT NULL")
    List<String> findDistinctCurrencies();
}

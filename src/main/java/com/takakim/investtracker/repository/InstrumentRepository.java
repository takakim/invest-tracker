package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {
    List<Instrument> findAllByOrderByNameAsc();
    boolean existsByIsinIgnoreCase(String isin);
}

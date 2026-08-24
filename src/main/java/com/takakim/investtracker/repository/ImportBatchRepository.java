package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.ImportBatch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    List<ImportBatch> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}

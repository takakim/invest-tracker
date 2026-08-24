package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.ImportRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportRecordRepository extends JpaRepository<ImportRecord, UUID> {
    boolean existsByAccountIdAndFingerprint(UUID accountId, String fingerprint);
    List<ImportRecord> findByBatchIdOrderByRowNumberAsc(UUID batchId);
}

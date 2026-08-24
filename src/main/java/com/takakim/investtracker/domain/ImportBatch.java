package com.takakim.investtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "broker_type", nullable = false, length = 64)
    private String brokerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImportBatchStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ImportBatch() {
    }

    public ImportBatch(Account account, String fileName, String brokerType, int totalRows) {
        this.id = UUID.randomUUID();
        this.account = Objects.requireNonNull(account, "Account must not be null");
        this.fileName = Objects.requireNonNull(fileName, "File name must not be null");
        this.brokerType = Objects.requireNonNull(brokerType, "Broker type must not be null");
        this.status = ImportBatchStatus.PENDING;
        this.totalRows = totalRows;
        this.importedRows = 0;
        this.skippedRows = 0;
        this.createdAt = Instant.now();
    }

    public void updateProgress(int importedRows, int skippedRows, ImportBatchStatus status) {
        this.importedRows = importedRows;
        this.skippedRows = skippedRows;
        this.status = status;
    }

    public UUID getId() { return id; }
    public Account getAccount() { return account; }
    public String getFileName() { return fileName; }
    public String getBrokerType() { return brokerType; }
    public ImportBatchStatus getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getImportedRows() { return importedRows; }
    public int getSkippedRows() { return skippedRows; }
    public Instant getCreatedAt() { return createdAt; }
}

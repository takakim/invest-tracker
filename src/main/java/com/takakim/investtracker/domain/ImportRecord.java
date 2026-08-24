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
@Table(name = "import_records")
public class ImportRecord {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImportRecordStatus status;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ImportRecord() {
    }

    public ImportRecord(
            ImportBatch batch,
            Account account,
            int rowNumber,
            String rawData,
            String fingerprint,
            ImportRecordStatus status,
            String errorMessage,
            Transaction transaction) {
        this.id = UUID.randomUUID();
        this.batch = Objects.requireNonNull(batch, "Batch must not be null");
        this.account = Objects.requireNonNull(account, "Account must not be null");
        this.rowNumber = rowNumber;
        this.rawData = rawData;
        this.fingerprint = Objects.requireNonNull(fingerprint, "Fingerprint must not be null");
        this.status = Objects.requireNonNull(status, "Status must not be null");
        this.errorMessage = errorMessage;
        this.transaction = transaction;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ImportBatch getBatch() { return batch; }
    public Account getAccount() { return account; }
    public int getRowNumber() { return rowNumber; }
    public String getRawData() { return rawData; }
    public String getFingerprint() { return fingerprint; }
    public ImportRecordStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Transaction getTransaction() { return transaction; }
    public Instant getCreatedAt() { return createdAt; }
}

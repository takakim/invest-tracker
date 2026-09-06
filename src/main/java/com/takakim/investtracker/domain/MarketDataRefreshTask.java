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
@Table(name = "market_data_refresh_tasks")
public class MarketDataRefreshTask {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Column(name = "base_currency", length = 10)
    private String baseCurrency;

    @Column(name = "quote_currency", length = 10)
    private String quoteCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefreshTaskStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MarketDataRefreshTask() {
    }

    public MarketDataRefreshTask(Instrument instrument, Instant scheduledAt, int maxAttempts) {
        this.id = UUID.randomUUID();
        this.instrument = Objects.requireNonNull(instrument, "Instrument must not be null");
        this.status = RefreshTaskStatus.PENDING;
        this.scheduledAt = scheduledAt != null ? scheduledAt : Instant.now();
        this.attemptCount = 0;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public MarketDataRefreshTask(Instrument instrument, Instant scheduledAt) {
        this(instrument, scheduledAt, DEFAULT_MAX_ATTEMPTS);
    }

    public MarketDataRefreshTask(String baseCurrency, String quoteCurrency, Instant scheduledAt, int maxAttempts) {
        this.id = UUID.randomUUID();
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency must not be null").trim().toUpperCase();
        this.quoteCurrency = Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null").trim().toUpperCase();
        this.status = RefreshTaskStatus.PENDING;
        this.scheduledAt = scheduledAt != null ? scheduledAt : Instant.now();
        this.attemptCount = 0;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public MarketDataRefreshTask(String baseCurrency, String quoteCurrency, Instant scheduledAt) {
        this(baseCurrency, quoteCurrency, scheduledAt, DEFAULT_MAX_ATTEMPTS);
    }

    public UUID getId() {
        return id;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public boolean isFxTask() {
        return baseCurrency != null && quoteCurrency != null;
    }

    public RefreshTaskStatus getStatus() {
        return status;
    }

    public void setStatus(RefreshTaskStatus status) {
        this.status = Objects.requireNonNull(status, "Status must not be null");
        this.updatedAt = Instant.now();
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "ScheduledAt must not be null");
        this.updatedAt = Instant.now();
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
        this.updatedAt = Instant.now();
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
        this.updatedAt = Instant.now();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        this.updatedAt = Instant.now();
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

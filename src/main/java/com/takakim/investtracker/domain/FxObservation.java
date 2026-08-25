package com.takakim.investtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fx_observations")
public class FxObservation {

    @Id
    private UUID id;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(name = "rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ObservationSourceType sourceType;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FxObservation() {
    }

    public FxObservation(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            Instant observedAt,
            ObservationSourceType sourceType,
            String sourceReference) {
        this.id = UUID.randomUUID();
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency must not be null").toUpperCase();
        this.quoteCurrency = Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null").toUpperCase();
        this.rate = Objects.requireNonNull(rate, "rate must not be null");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("FX rate must be strictly positive");
        }
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
        this.sourceReference = sourceReference;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getBaseCurrency() { return baseCurrency; }
    public String getQuoteCurrency() { return quoteCurrency; }
    public BigDecimal getRate() { return rate; }
    public Instant getObservedAt() { return observedAt; }
    public ObservationSourceType getSourceType() { return sourceType; }
    public String getSourceReference() { return sourceReference; }
    public Instant getCreatedAt() { return createdAt; }
}

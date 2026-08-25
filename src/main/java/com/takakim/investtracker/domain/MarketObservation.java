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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "market_observations")
public class MarketObservation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "price", nullable = false, precision = 19, scale = 8)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ObservationSourceType sourceType;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MarketObservation() {
    }

    public MarketObservation(
            Instrument instrument,
            BigDecimal price,
            String currency,
            Instant observedAt,
            ObservationSourceType sourceType,
            String sourceReference) {
        this.id = UUID.randomUUID();
        this.instrument = Objects.requireNonNull(instrument, "Instrument must not be null");
        this.price = Objects.requireNonNull(price, "Price must not be null");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        this.currency = Objects.requireNonNull(currency, "Currency must not be null").toUpperCase();
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
        this.sourceReference = sourceReference;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Instrument getInstrument() { return instrument; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public Instant getObservedAt() { return observedAt; }
    public ObservationSourceType getSourceType() { return sourceType; }
    public String getSourceReference() { return sourceReference; }
    public Instant getCreatedAt() { return createdAt; }
}

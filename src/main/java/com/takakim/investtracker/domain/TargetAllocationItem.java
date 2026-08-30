package com.takakim.investtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "target_allocation_items")
public class TargetAllocationItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private TargetAllocationPlan plan;

    @Column(name = "category_key", nullable = false, length = 100)
    private String categoryKey;

    @Column(name = "category_label", nullable = false, length = 150)
    private String categoryLabel;

    @Column(name = "target_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetPercentage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TargetAllocationItem() {
    }

    public TargetAllocationItem(
            TargetAllocationPlan plan,
            String categoryKey,
            String categoryLabel,
            BigDecimal targetPercentage,
            Instrument instrument) {
        this.id = UUID.randomUUID();
        this.plan = plan;
        this.categoryKey = requireText(categoryKey, "Category key");
        this.categoryLabel = requireText(categoryLabel, "Category label");
        this.targetPercentage = validatePercentage(targetPercentage);
        this.instrument = instrument;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String categoryLabel, BigDecimal targetPercentage, Instrument instrument) {
        this.categoryLabel = requireText(categoryLabel, "Category label");
        this.targetPercentage = validatePercentage(targetPercentage);
        this.instrument = instrument;
        this.updatedAt = Instant.now();
    }

    void setPlan(TargetAllocationPlan plan) {
        this.plan = Objects.requireNonNull(plan, "Plan must not be null");
    }

    public UUID getId() {
        return id;
    }

    public TargetAllocationPlan getPlan() {
        return plan;
    }

    public String getCategoryKey() {
        return categoryKey;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public BigDecimal getTargetPercentage() {
        return targetPercentage;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static BigDecimal validatePercentage(BigDecimal pct) {
        Objects.requireNonNull(pct, "Target percentage must not be null");
        if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Target percentage must be between 0% and 100%");
        }
        return pct;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }
}

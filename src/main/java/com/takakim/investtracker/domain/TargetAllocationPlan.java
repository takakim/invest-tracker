package com.takakim.investtracker.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "target_allocation_plans")
public class TargetAllocationPlan {

    public static final BigDecimal DEFAULT_DRIFT_TOLERANCE = new BigDecimal("5.00");

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false, unique = true)
    private Portfolio portfolio;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 30)
    private AllocationType allocationType;

    @Column(name = "drift_tolerance_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal driftTolerancePct;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TargetAllocationItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TargetAllocationPlan() {
    }

    public TargetAllocationPlan(Portfolio portfolio, String name, AllocationType allocationType, BigDecimal driftTolerancePct) {
        this.id = UUID.randomUUID();
        this.portfolio = Objects.requireNonNull(portfolio, "Portfolio must not be null");
        this.name = requireText(name, "Plan name");
        this.allocationType = Objects.requireNonNull(allocationType, "Allocation type must not be null");
        this.driftTolerancePct = driftTolerancePct != null ? driftTolerancePct : DEFAULT_DRIFT_TOLERANCE;
        if (this.driftTolerancePct.compareTo(BigDecimal.ZERO) < 0 || this.driftTolerancePct.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Drift tolerance must be between 0% and 100%");
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, AllocationType allocationType, BigDecimal driftTolerancePct) {
        this.name = requireText(name, "Plan name");
        this.allocationType = Objects.requireNonNull(allocationType, "Allocation type must not be null");
        BigDecimal tolerance = driftTolerancePct != null ? driftTolerancePct : DEFAULT_DRIFT_TOLERANCE;
        if (tolerance.compareTo(BigDecimal.ZERO) < 0 || tolerance.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Drift tolerance must be between 0% and 100%");
        }
        this.driftTolerancePct = tolerance;
        this.updatedAt = Instant.now();
    }

    public void addItem(TargetAllocationItem item) {
        Objects.requireNonNull(item, "Item must not be null");
        items.add(item);
        item.setPlan(this);
        this.updatedAt = Instant.now();
    }

    public void clearItems() {
        items.clear();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public String getName() {
        return name;
    }

    public AllocationType getAllocationType() {
        return allocationType;
    }

    public BigDecimal getDriftTolerancePct() {
        return driftTolerancePct;
    }

    public List<TargetAllocationItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }
}

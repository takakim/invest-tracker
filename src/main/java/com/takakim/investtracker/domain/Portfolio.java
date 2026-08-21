package com.takakim.investtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portfolios")
public class Portfolio {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_basis_method", nullable = false, length = 20)
    private CostBasisMethod costBasisMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_method", nullable = false, length = 10)
    private ReturnMethod returnMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PortfolioStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Portfolio() { }

    public Portfolio(String name, Currency baseCurrency, CostBasisMethod costBasisMethod, ReturnMethod returnMethod) {
        this.id = UUID.randomUUID();
        this.name = requireName(name);
        this.baseCurrency = baseCurrency.code();
        this.costBasisMethod = costBasisMethod;
        this.returnMethod = returnMethod;
        this.status = PortfolioStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, Currency baseCurrency, CostBasisMethod costBasisMethod, ReturnMethod returnMethod) {
        ensureActive();
        this.name = requireName(name);
        this.baseCurrency = baseCurrency.code();
        this.costBasisMethod = costBasisMethod;
        this.returnMethod = returnMethod;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = PortfolioStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    private void ensureActive() {
        if (status == PortfolioStatus.ARCHIVED) throw new IllegalStateException("Archived portfolio cannot be modified");
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank() || value.length() > 120) throw new IllegalArgumentException("Portfolio name is required and must be at most 120 characters");
        return value.trim();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Currency getBaseCurrency() { return new Currency(baseCurrency); }
    public CostBasisMethod getCostBasisMethod() { return costBasisMethod; }
    public ReturnMethod getReturnMethod() { return returnMethod; }
    public PortfolioStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

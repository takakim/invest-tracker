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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "positions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "instrument_id"})
)
public class Position {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "cost_basis_amount", precision = 19, scale = 4)
    private BigDecimal costBasisAmount;

    @Column(name = "cost_basis_currency", length = 3)
    private String costBasisCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PositionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Position() {
    }

    public Position(Account account, Instrument instrument, Quantity quantity, Money costBasis) {
        this.id = UUID.randomUUID();
        this.account = Objects.requireNonNull(account, "Account must not be null");
        this.instrument = Objects.requireNonNull(instrument, "Instrument must not be null");
        validateQuantity(quantity != null ? quantity.value() : null);
        this.quantity = quantity.value();

        if (costBasis != null) {
            this.costBasisAmount = costBasis.amount();
            this.costBasisCurrency = costBasis.currency().code();
        }

        this.status = PositionStatus.ACTIVE;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Quantity quantity, Money costBasis) {
        if (this.status != PositionStatus.ACTIVE) {
            throw new IllegalStateException("Cannot update archived position");
        }
        validateQuantity(quantity != null ? quantity.value() : null);
        this.quantity = quantity.value();

        if (costBasis != null) {
            this.costBasisAmount = costBasis.amount();
            this.costBasisCurrency = costBasis.currency().code();
        } else {
            this.costBasisAmount = null;
            this.costBasisCurrency = null;
        }

        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = PositionStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    private void validateQuantity(BigDecimal qty) {
        if (qty == null) {
            throw new IllegalArgumentException("Quantity must not be null");
        }
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Quantity getQuantityValueObject() {
        return new Quantity(quantity);
    }

    public BigDecimal getCostBasisAmount() {
        return costBasisAmount;
    }

    public String getCostBasisCurrency() {
        return costBasisCurrency;
    }

    public Money getCostBasisMoney() {
        if (costBasisAmount == null || costBasisCurrency == null) {
            return null;
        }
        return new Money(costBasisAmount, new Currency(costBasisCurrency));
    }

    public PositionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

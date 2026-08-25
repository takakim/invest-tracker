package com.takakim.investtracker.service.position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an open or historical acquisition lot for position cost-basis tracking.
 */
public class PositionLot {

    private final UUID id;
    private final UUID transactionId;
    private final Instant acquisitionDate;
    private final BigDecimal originalQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private final String currency;

    public PositionLot(
            UUID id,
            UUID transactionId,
            Instant acquisitionDate,
            BigDecimal originalQuantity,
            BigDecimal remainingQuantity,
            BigDecimal unitCost,
            BigDecimal totalCost,
            String currency) {
        this.id = Objects.requireNonNull(id, "Lot ID must not be null");
        this.transactionId = transactionId;
        this.acquisitionDate = Objects.requireNonNull(acquisitionDate, "Acquisition date must not be null");
        this.originalQuantity = Objects.requireNonNull(originalQuantity, "Original quantity must not be null");
        this.remainingQuantity = Objects.requireNonNull(remainingQuantity, "Remaining quantity must not be null");
        this.unitCost = Objects.requireNonNull(unitCost, "Unit cost must not be null");
        this.totalCost = Objects.requireNonNull(totalCost, "Total cost must not be null");
        this.currency = Objects.requireNonNull(currency, "Currency must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Instant getAcquisitionDate() {
        return acquisitionDate;
    }

    public BigDecimal getOriginalQuantity() {
        return originalQuantity;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(BigDecimal remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public String getCurrency() {
        return currency;
    }
}

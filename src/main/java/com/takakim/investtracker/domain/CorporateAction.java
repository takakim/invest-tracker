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
@Table(name = "corporate_actions")
public class CorporateAction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private CorporateActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CorporateActionStatus status;

    @Column(name = "ex_date", nullable = false)
    private Instant exDate;

    @Column(name = "record_date")
    private Instant recordDate;

    @Column(name = "payment_date")
    private Instant paymentDate;

    @Column(name = "ratio_from", precision = 19, scale = 8)
    private BigDecimal ratioFrom;

    @Column(name = "ratio_to", precision = 19, scale = 8)
    private BigDecimal ratioTo;

    @Column(name = "amount_per_share", precision = 19, scale = 4)
    private BigDecimal amountPerShare;

    @Column(length = 3)
    private String currency;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_transaction_id")
    private Transaction appliedTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CorporateAction() {
    }

    public CorporateAction(
            Instrument instrument,
            CorporateActionType actionType,
            Instant exDate,
            Instant recordDate,
            Instant paymentDate,
            BigDecimal ratioFrom,
            BigDecimal ratioTo,
            BigDecimal amountPerShare,
            String currency,
            String description,
            String source,
            String externalId) {
        this.id = UUID.randomUUID();
        this.instrument = Objects.requireNonNull(instrument, "Instrument must not be null");
        this.actionType = Objects.requireNonNull(actionType, "Action type must not be null");
        this.exDate = Objects.requireNonNull(exDate, "Ex-date must not be null");
        this.status = CorporateActionStatus.PENDING;
        this.recordDate = recordDate;
        this.paymentDate = paymentDate;
        this.ratioFrom = ratioFrom;
        this.ratioTo = ratioTo;
        this.amountPerShare = amountPerShare;
        this.currency = currency;
        this.description = description;
        this.source = source != null && !source.isBlank() ? source : "MANUAL";
        this.externalId = externalId;

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;

        validateActionTypeRules();
    }

    private void validateActionTypeRules() {
        if (actionType == CorporateActionType.STOCK_SPLIT || actionType == CorporateActionType.REVERSE_STOCK_SPLIT) {
            if (ratioFrom == null || ratioFrom.compareTo(BigDecimal.ZERO) <= 0
                    || ratioTo == null || ratioTo.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Split ratios (ratioFrom, ratioTo) must be positive");
            }
        } else if (actionType == CorporateActionType.DIVIDEND) {
            if (amountPerShare == null || amountPerShare.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Dividend amountPerShare must be positive");
            }
        }
    }

    public void markApplied(Transaction transaction, Account account) {
        if (this.status != CorporateActionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING corporate actions can be applied (current: " + status + ")");
        }
        this.status = CorporateActionStatus.APPLIED;
        this.appliedTransaction = transaction;
        this.account = account;
        this.updatedAt = Instant.now();
    }

    public void markDismissed() {
        if (this.status != CorporateActionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING corporate actions can be dismissed (current: " + status + ")");
        }
        this.status = CorporateActionStatus.DISMISSED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public CorporateActionType getActionType() {
        return actionType;
    }

    public CorporateActionStatus getStatus() {
        return status;
    }

    public Instant getExDate() {
        return exDate;
    }

    public Instant getRecordDate() {
        return recordDate;
    }

    public Instant getPaymentDate() {
        return paymentDate;
    }

    public BigDecimal getRatioFrom() {
        return ratioFrom;
    }

    public BigDecimal getRatioTo() {
        return ratioTo;
    }

    public BigDecimal getAmountPerShare() {
        return amountPerShare;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public String getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public Transaction getAppliedTransaction() {
        return appliedTransaction;
    }

    public Account getAccount() {
        return account;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

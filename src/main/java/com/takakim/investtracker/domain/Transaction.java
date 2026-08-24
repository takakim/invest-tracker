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
@Table(name = "transactions")
public class Transaction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Column(name = "trade_date", nullable = false)
    private Instant tradeDate;

    @Column(name = "settlement_date")
    private Instant settlementDate;

    @Column(name = "quantity", precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "fx_rate", precision = 19, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "counter_currency", length = 3)
    private String counterCurrency;

    @Column(name = "notes", length = 255)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "correction_of_transaction_id")
    private UUID correctionOfTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    public Transaction(
            Account account,
            Instrument instrument,
            TransactionType type,
            Instant tradeDate,
            Instant settlementDate,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal taxAmount,
            String currency,
            BigDecimal fxRate,
            String counterCurrency,
            String notes,
            UUID correctionOfTransactionId) {

        this.id = UUID.randomUUID();
        this.account = Objects.requireNonNull(account, "Account must not be null");
        this.type = Objects.requireNonNull(type, "Transaction type must not be null");
        this.tradeDate = Objects.requireNonNull(tradeDate, "Trade date must not be null");
        this.settlementDate = settlementDate;
        this.notes = notes;
        this.correctionOfTransactionId = correctionOfTransactionId;
        this.status = TransactionStatus.COMPLETED;

        if (currency == null || !currency.matches("^[A-Za-z]{3}$")) {
            throw new IllegalArgumentException("Valid 3-letter currency code required");
        }
        this.currency = currency.toUpperCase();
        this.counterCurrency = counterCurrency != null ? counterCurrency.toUpperCase() : null;
        this.fxRate = fxRate;

        BigDecimal fee = feeAmount != null ? feeAmount : BigDecimal.ZERO;
        BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        if (fee.compareTo(BigDecimal.ZERO) < 0 || tax.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fee and tax amounts must be non-negative");
        }
        this.feeAmount = fee;
        this.taxAmount = tax;

        validateTypeSpecificRules(type, instrument, quantity, price, grossAmount, fee, tax);

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private void validateTypeSpecificRules(
            TransactionType type,
            Instrument instrument,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal grossAmount,
            BigDecimal fee,
            BigDecimal tax) {

        boolean isTrade = (type == TransactionType.BUY || type == TransactionType.SELL);
        boolean isSplit = (type == TransactionType.STOCK_SPLIT || type == TransactionType.REVERSE_STOCK_SPLIT);

        if (isTrade || isSplit || type == TransactionType.DIVIDEND) {
            if (instrument == null) {
                throw new IllegalArgumentException("Instrument is required for transaction type " + type);
            }
        }
        this.instrument = instrument;

        if (isTrade || isSplit) {
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for " + type);
            }
        }
        this.quantity = quantity;

        if (isTrade) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Price must be positive for " + type);
            }
        }
        this.price = price;

        if (isTrade) {
            BigDecimal computedGross = quantity.multiply(price);
            this.grossAmount = grossAmount != null ? grossAmount : computedGross;
            if (type == TransactionType.BUY) {
                this.netAmount = this.grossAmount.add(fee);
            } else {
                this.netAmount = this.grossAmount.subtract(fee).subtract(tax);
            }
        } else if (type == TransactionType.DIVIDEND) {
            if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Gross amount must be positive for DIVIDEND");
            }
            this.grossAmount = grossAmount;
            this.netAmount = grossAmount.subtract(tax);
        } else if (isSplit) {
            this.grossAmount = BigDecimal.ZERO;
            this.netAmount = BigDecimal.ZERO;
        } else {
            // DEPOSIT, WITHDRAWAL, FEE, INTEREST, TRANSFER
            if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Gross amount must be non-negative for " + type);
            }
            this.grossAmount = grossAmount;
            this.netAmount = grossAmount;
        }
    }

    public void markCorrected() {
        this.status = TransactionStatus.CORRECTED;
        this.updatedAt = Instant.now();
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

    public TransactionType getType() {
        return type;
    }

    public Instant getTradeDate() {
        return tradeDate;
    }

    public Instant getSettlementDate() {
        return settlementDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public String getCounterCurrency() {
        return counterCurrency;
    }

    public String getNotes() {
        return notes;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public UUID getCorrectionOfTransactionId() {
        return correctionOfTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

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
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "broker_name", nullable = false, length = 120)
    private String brokerName;

    @Column(name = "account_currency", nullable = false, length = 3)
    private String accountCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_treatment", nullable = false, length = 20)
    private AccountTaxTreatment taxTreatment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() { }

    public Account(Portfolio portfolio, String name, String brokerName, Currency accountCurrency) {
        this(portfolio, name, brokerName, accountCurrency, AccountTaxTreatment.TAXABLE);
    }

    public Account(Portfolio portfolio, String name, String brokerName, Currency accountCurrency, AccountTaxTreatment taxTreatment) {
        this.id = UUID.randomUUID();
        this.portfolio = java.util.Objects.requireNonNull(portfolio, "portfolio");
        this.name = requireText(name, "Account name");
        this.brokerName = requireText(brokerName, "Broker name");
        this.accountCurrency = java.util.Objects.requireNonNull(accountCurrency, "accountCurrency").code();
        this.status = AccountStatus.ACTIVE;
        this.taxTreatment = taxTreatment != null ? taxTreatment : AccountTaxTreatment.TAXABLE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String brokerName, Currency accountCurrency) {
        update(name, brokerName, accountCurrency, this.taxTreatment);
    }

    public void update(String name, String brokerName, Currency accountCurrency, AccountTaxTreatment taxTreatment) {
        if (status == AccountStatus.ARCHIVED) throw new IllegalStateException("Archived account cannot be modified");
        this.name = requireText(name, "Account name");
        this.brokerName = requireText(brokerName, "Broker name");
        this.accountCurrency = java.util.Objects.requireNonNull(accountCurrency, "accountCurrency").code();
        if (taxTreatment != null) {
            this.taxTreatment = taxTreatment;
        }
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = AccountStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    public boolean isTaxExempt() {
        return this.taxTreatment == AccountTaxTreatment.TAX_EXEMPT || this.taxTreatment == AccountTaxTreatment.TAX_DEFERRED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 120) throw new IllegalArgumentException(field + " is required and must be at most 120 characters");
        return value.trim();
    }

    public UUID getId() { return id; }
    public Portfolio getPortfolio() { return portfolio; }
    public String getName() { return name; }
    public String getBrokerName() { return brokerName; }
    public Currency getAccountCurrency() { return new Currency(accountCurrency); }
    public AccountStatus getStatus() { return status; }
    public AccountTaxTreatment getTaxTreatment() { return taxTreatment != null ? taxTreatment : AccountTaxTreatment.TAXABLE; }
    public void setTaxTreatment(AccountTaxTreatment taxTreatment) { this.taxTreatment = taxTreatment != null ? taxTreatment : AccountTaxTreatment.TAXABLE; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

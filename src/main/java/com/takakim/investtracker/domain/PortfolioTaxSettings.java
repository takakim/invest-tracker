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
        name = "portfolio_tax_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_portfolio_tax_settings", columnNames = {"portfolio_id", "tax_year"})
)
public class PortfolioTaxSettings {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "tax_year", nullable = false, length = 20)
    private String taxYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", nullable = false, length = 30)
    private TaxRegime taxRegime;

    @Column(name = "cgt_allowance", precision = 18, scale = 4)
    private BigDecimal cgtAllowance;

    @Column(name = "dividend_allowance", precision = 18, scale = 4)
    private BigDecimal dividendAllowance;

    @Column(name = "loss_carryforward", precision = 18, scale = 4, nullable = false)
    private BigDecimal lossCarryforward;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PortfolioTaxSettings() { }

    public PortfolioTaxSettings(
            Portfolio portfolio,
            String taxYear,
            TaxRegime taxRegime,
            BigDecimal cgtAllowance,
            BigDecimal dividendAllowance,
            BigDecimal lossCarryforward,
            String notes
    ) {
        this.id = UUID.randomUUID();
        this.portfolio = Objects.requireNonNull(portfolio, "portfolio must not be null");
        this.taxYear = Objects.requireNonNull(taxYear, "taxYear must not be null").trim();
        this.taxRegime = taxRegime != null ? taxRegime : TaxRegime.UK_HMRC;
        this.cgtAllowance = cgtAllowance;
        this.dividendAllowance = dividendAllowance;
        this.lossCarryforward = lossCarryforward != null ? lossCarryforward : BigDecimal.ZERO;
        this.notes = notes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            TaxRegime taxRegime,
            BigDecimal cgtAllowance,
            BigDecimal dividendAllowance,
            BigDecimal lossCarryforward,
            String notes
    ) {
        if (taxRegime != null) this.taxRegime = taxRegime;
        this.cgtAllowance = cgtAllowance;
        this.dividendAllowance = dividendAllowance;
        this.lossCarryforward = lossCarryforward != null ? lossCarryforward : BigDecimal.ZERO;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Portfolio getPortfolio() { return portfolio; }
    public String getTaxYear() { return taxYear; }
    public TaxRegime getTaxRegime() { return taxRegime; }
    public BigDecimal getCgtAllowance() { return cgtAllowance; }
    public BigDecimal getDividendAllowance() { return dividendAllowance; }
    public BigDecimal getLossCarryforward() { return lossCarryforward; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

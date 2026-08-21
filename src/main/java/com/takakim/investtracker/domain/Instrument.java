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
@Table(name = "instruments")
public class Instrument {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Column(length = 32)
    private String ticker;

    @Column(length = 12)
    private String isin;

    @Column(length = 80)
    private String exchange;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Instrument() { }

    public Instrument(String name, AssetClass assetClass, String ticker, String isin, String exchange, Currency currency) {
        this.id = UUID.randomUUID();
        this.name = requireName(name);
        this.assetClass = java.util.Objects.requireNonNull(assetClass, "assetClass");
        this.ticker = normalize(ticker);
        this.isin = normalize(isin);
        this.exchange = normalize(exchange);
        this.currency = java.util.Objects.requireNonNull(currency, "currency").code();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, AssetClass assetClass, String ticker, String isin, String exchange, Currency currency) {
        this.name = requireName(name);
        this.assetClass = java.util.Objects.requireNonNull(assetClass, "assetClass");
        this.ticker = normalize(ticker);
        this.isin = normalize(isin);
        this.exchange = normalize(exchange);
        this.currency = java.util.Objects.requireNonNull(currency, "currency").code();
        this.updatedAt = Instant.now();
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank() || value.length() > 160) throw new IllegalArgumentException("Instrument name is required and must be at most 160 characters");
        return value.trim();
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public AssetClass getAssetClass() { return assetClass; }
    public String getTicker() { return ticker; }
    public String getIsin() { return isin; }
    public String getExchange() { return exchange; }
    public Currency getCurrency() { return new Currency(currency); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

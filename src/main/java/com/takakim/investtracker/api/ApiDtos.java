package com.takakim.investtracker.api;

import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.ReturnMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() { }

    public record PortfolioRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String baseCurrency,
        @NotNull CostBasisMethod costBasisMethod,
        @NotNull ReturnMethod returnMethod) { }

    public record PortfolioResponse(
        UUID id, String name, String baseCurrency, CostBasisMethod costBasisMethod,
        ReturnMethod returnMethod, String status, Instant createdAt, Instant updatedAt) { }

    public record AccountRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String brokerName,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String accountCurrency) { }

    public record AccountResponse(
        UUID id, UUID portfolioId, String name, String brokerName,
        String accountCurrency, String status, Instant createdAt, Instant updatedAt) { }

    public record InstrumentRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull AssetClass assetClass,
        @Size(max = 32) String ticker,
        @Size(max = 12) String isin,
        @Size(max = 80) String exchange,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) { }

    public record InstrumentResponse(
        UUID id, String name, AssetClass assetClass, String ticker, String isin,
        String exchange, String currency, Instant createdAt, Instant updatedAt) { }

    public record PositionRequest(
        @NotNull UUID instrumentId,
        @NotNull @jakarta.validation.constraints.DecimalMin("0.0") java.math.BigDecimal quantity,
        @jakarta.validation.constraints.DecimalMin("0.0") java.math.BigDecimal costBasisAmount,
        @Pattern(regexp = "[A-Za-z]{3}") String costBasisCurrency) { }

    public record PositionUpdateRequest(
        @NotNull @jakarta.validation.constraints.DecimalMin("0.0") java.math.BigDecimal quantity,
        @jakarta.validation.constraints.DecimalMin("0.0") java.math.BigDecimal costBasisAmount,
        @Pattern(regexp = "[A-Za-z]{3}") String costBasisCurrency) { }

    public record PositionResponse(
        UUID id, UUID accountId, UUID instrumentId, String instrumentName,
        String instrumentTicker, String instrumentIsin, AssetClass assetClass,
        java.math.BigDecimal quantity, java.math.BigDecimal costBasisAmount,
        String costBasisCurrency, String status, Instant createdAt, Instant updatedAt) { }
}

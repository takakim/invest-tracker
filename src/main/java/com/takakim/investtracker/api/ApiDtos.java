package com.takakim.investtracker.api;

import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.ReturnMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
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

    public record TransactionRequest(
        UUID instrumentId,
        @NotNull com.takakim.investtracker.domain.TransactionType type,
        @NotNull Instant tradeDate,
        Instant settlementDate,
        java.math.BigDecimal quantity,
        java.math.BigDecimal price,
        @NotNull java.math.BigDecimal grossAmount,
        java.math.BigDecimal feeAmount,
        java.math.BigDecimal taxAmount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        java.math.BigDecimal fxRate,
        @Pattern(regexp = "[A-Za-z]{3}") String counterCurrency,
        @Size(max = 255) String notes,
        UUID correctionOfTransactionId) { }

    public record TransactionCorrectionRequest(
        UUID replacementInstrumentId,
        @NotNull com.takakim.investtracker.domain.TransactionType replacementType,
        @NotNull Instant replacementTradeDate,
        Instant replacementSettlementDate,
        java.math.BigDecimal replacementQuantity,
        java.math.BigDecimal replacementPrice,
        @NotNull java.math.BigDecimal replacementGrossAmount,
        java.math.BigDecimal replacementFeeAmount,
        java.math.BigDecimal replacementTaxAmount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String replacementCurrency,
        java.math.BigDecimal replacementFxRate,
        @Pattern(regexp = "[A-Za-z]{3}") String replacementCounterCurrency,
        @Size(max = 255) String replacementNotes) { }

    public record TransactionResponse(
        UUID id, UUID accountId, UUID instrumentId, String instrumentName,
        String instrumentTicker, com.takakim.investtracker.domain.TransactionType type,
        Instant tradeDate, Instant settlementDate, java.math.BigDecimal quantity,
        java.math.BigDecimal price, java.math.BigDecimal grossAmount,
        java.math.BigDecimal feeAmount, java.math.BigDecimal taxAmount,
        java.math.BigDecimal netAmount, String currency, java.math.BigDecimal fxRate,
        String counterCurrency, String notes, String status,
        UUID correctionOfTransactionId, Instant createdAt, Instant updatedAt) { }

    public record CsvImportRequest(
        @NotBlank String fileName,
        @NotBlank String csvContent) { }

    public record PreviewRowResponse(
        int rowNumber,
        String rawType,
        String mappedType,
        String instrumentTitle,
        String ticker,
        String isin,
        java.math.BigDecimal quantity,
        java.math.BigDecimal price,
        java.math.BigDecimal grossAmount,
        java.math.BigDecimal feeAmount,
        java.math.BigDecimal taxAmount,
        String currency,
        boolean isDuplicate,
        boolean isIgnored,
        String diagnosticMessage) { }

    public record CsvImportPreviewResponse(
        String brokerName,
        String fileName,
        int totalRows,
        int importableRows,
        int duplicateRows,
        int ignoredRows,
        List<PreviewRowResponse> rows) { }

    public record ImportBatchResponse(
        UUID id,
        UUID accountId,
        String fileName,
        String brokerType,
        String status,
        int totalRows,
        int importedRows,
        int skippedRows,
        Instant createdAt) { }
}

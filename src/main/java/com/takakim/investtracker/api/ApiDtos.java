package com.takakim.investtracker.api;

import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.ReturnMethod;
import jakarta.validation.constraints.DecimalMin;
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

    public record PositionLotResponse(
        UUID lotId,
        UUID transactionId,
        Instant acquisitionDate,
        java.math.BigDecimal originalQuantity,
        java.math.BigDecimal remainingQuantity,
        java.math.BigDecimal unitCostAmount,
        java.math.BigDecimal totalCostAmount,
        String currency) { }

    public record PositionLotsDetailResponse(
        UUID positionId,
        UUID accountId,
        UUID instrumentId,
        String costBasisMethod,
        java.math.BigDecimal totalQuantity,
        java.math.BigDecimal totalCostBasisAmount,
        String currency,
        java.math.BigDecimal averageUnitCostAmount,
        java.math.BigDecimal realizedGainLossAmount,
        List<PositionLotResponse> openLots) { }

    public record PositionRecalculateResponse(
        UUID portfolioId,
        int recalculatedPositionsCount,
        String message) { }

    public record AccountPerformanceSummaryResponse(
        UUID accountId,
        String accountName,
        java.math.BigDecimal realizedGainLoss,
        java.math.BigDecimal dividendIncome,
        java.math.BigDecimal interestIncome,
        java.math.BigDecimal fees,
        java.math.BigDecimal taxes,
        java.math.BigDecimal costBasis,
        String currency) { }

    public record PerformanceResultResponse(
        UUID portfolioId,
        Instant asOf,
        String returnMethod,
        java.math.BigDecimal twrReturn,
        java.math.BigDecimal twrAnnualized,
        java.math.BigDecimal mwrReturn,
        java.math.BigDecimal totalRealizedGainLoss,
        java.math.BigDecimal totalDividendIncome,
        java.math.BigDecimal totalInterestIncome,
        java.math.BigDecimal totalFees,
        java.math.BigDecimal totalTaxes,
        java.math.BigDecimal totalNetIncome,
        java.math.BigDecimal totalCostBasis,
        String currency,
        String valuationBasis,
        List<AccountPerformanceSummaryResponse> byAccount) { }

    public record MarketPriceOverrideRequest(
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", message = "price must be non-negative")
        java.math.BigDecimal price,
        String currency,
        Instant observedAt,
        String reason) { }

    public record PriceQuoteResponse(
        UUID instrumentId,
        java.math.BigDecimal price,
        String currency,
        Instant asOf,
        String sourceType,
        String sourceReference,
        boolean isStale,
        String warning) { }

    public record MarketObservationResponse(
        UUID id,
        UUID instrumentId,
        java.math.BigDecimal price,
        String currency,
        Instant observedAt,
        String sourceType,
        String sourceReference,
        Instant createdAt) { }

    public record FxRateOverrideRequest(
        @NotBlank(message = "baseCurrency is required")
        @Size(min = 3, max = 3, message = "baseCurrency must be a 3-letter ISO code")
        String baseCurrency,
        @NotBlank(message = "quoteCurrency is required")
        @Size(min = 3, max = 3, message = "quoteCurrency must be a 3-letter ISO code")
        String quoteCurrency,
        @NotNull(message = "rate is required")
        @DecimalMin(value = "0.00000001", message = "rate must be strictly positive")
        java.math.BigDecimal rate,
        Instant observedAt,
        String reason) { }

    public record FxRateQuoteResponse(
        String baseCurrency,
        String quoteCurrency,
        java.math.BigDecimal rate,
        Instant asOf,
        String sourceType,
        String sourceReference,
        boolean isDerived,
        String warning) { }

    public record FxObservationResponse(
        UUID id,
        String baseCurrency,
        String quoteCurrency,
        java.math.BigDecimal rate,
        Instant observedAt,
        String sourceType,
        String sourceReference,
        Instant createdAt) { }

    public record AllocationItemResponse(
        String category,
        java.math.BigDecimal marketValue,
        java.math.BigDecimal percentage,
        java.math.BigDecimal costBasis,
        java.math.BigDecimal unrealizedGainLoss) { }

    public record HoldingExposureResponse(
        UUID instrumentId,
        String instrumentName,
        String ticker,
        String assetClass,
        java.math.BigDecimal quantity,
        java.math.BigDecimal currentPrice,
        java.math.BigDecimal marketValue,
        java.math.BigDecimal costBasis,
        java.math.BigDecimal unrealizedGainLoss,
        java.math.BigDecimal weightPercentage,
        String currency) { }

    public record PortfolioAnalyticsResponse(
        UUID portfolioId,
        Instant asOf,
        String baseCurrency,
        java.math.BigDecimal totalCurrentValue,
        java.math.BigDecimal totalCostBasis,
        java.math.BigDecimal totalUnrealizedGainLoss,
        java.math.BigDecimal totalUnrealizedReturnPercentage,
        java.math.BigDecimal totalRealizedGainLoss,
        java.math.BigDecimal totalCashValue,
        List<AllocationItemResponse> byAssetClass,
        List<AllocationItemResponse> byCurrency,
        List<AllocationItemResponse> byAccount,
        List<HoldingExposureResponse> topHoldings,
        List<String> warnings) { }

    public record BenchmarkInstrumentResponse(
        UUID id,
        String name,
        String ticker,
        String isin,
        String assetClass,
        String currency) { }

    public record BenchmarkComparisonResponse(
        UUID portfolioId,
        UUID benchmarkInstrumentId,
        String benchmarkName,
        String benchmarkTicker,
        Instant periodStart,
        Instant periodEnd,
        java.math.BigDecimal portfolioReturn,
        java.math.BigDecimal benchmarkReturn,
        java.math.BigDecimal excessReturn,
        java.math.BigDecimal annualizedPortfolioReturn,
        java.math.BigDecimal annualizedBenchmarkReturn,
        java.math.BigDecimal annualizedExcessReturn,
        boolean outperforming,
        String baseCurrency,
        List<String> warnings) { }

    public record DatabaseResetRequest(
        String confirmation) { }

    public record DatabaseResetResponse(
        String message,
        Instant timestamp) { }
}




package com.takakim.investtracker.api;

import com.takakim.investtracker.domain.AllocationType;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.DriftStatus;
import com.takakim.investtracker.domain.RebalanceAction;
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
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        Boolean manualPriceOnly) {
        public InstrumentRequest(
            String name, AssetClass assetClass, String ticker, String isin, String exchange, String currency
        ) {
            this(name, assetClass, ticker, isin, exchange, currency, false);
        }
    }

    public record InstrumentResponse(
        UUID id, String name, AssetClass assetClass, String ticker, String isin,
        String exchange, String currency, Boolean manualPriceOnly, Instant createdAt, Instant updatedAt,
        java.math.BigDecimal latestPrice, String priceCurrency, Instant priceAsOf,
        Boolean isStale) {
        public InstrumentResponse(
            UUID id, String name, AssetClass assetClass, String ticker, String isin,
            String exchange, String currency, Instant createdAt, Instant updatedAt
        ) {
            this(id, name, assetClass, ticker, isin, exchange, currency, false, createdAt, updatedAt, null, null, null, null);
        }
    }

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
        @NotBlank String csvContent,
        String overrideBroker) {
        public CsvImportRequest(String fileName, String csvContent) {
            this(fileName, csvContent, null);
        }
    }

    public record BrokerDetectionRequest(
        @NotBlank String csvContent) { }

    public record BrokerDetectionResponse(
        String brokerName,
        String confidence,
        boolean isSupported,
        List<String> supportedBrokers) { }

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

    public record LotDisposalResponse(
        UUID transactionId,
        UUID lotId,
        Instant disposalDate,
        java.math.BigDecimal quantity,
        java.math.BigDecimal costBasis,
        java.math.BigDecimal proceeds,
        java.math.BigDecimal realizedGainLoss,
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

    public record PositionPerformanceResponse(
        UUID positionId,
        UUID accountId,
        String accountName,
        UUID instrumentId,
        String instrumentName,
        String ticker,
        String isin,
        AssetClass assetClass,
        String status,
        java.math.BigDecimal currentQuantity,
        java.math.BigDecimal totalBoughtQuantity,
        java.math.BigDecimal totalSoldQuantity,
        java.math.BigDecimal averageBuyPrice,
        java.math.BigDecimal averageSellPrice,
        java.math.BigDecimal totalInvestedAmount,
        java.math.BigDecimal totalProceedsAmount,
        java.math.BigDecimal currentCostBasis,
        java.math.BigDecimal currentPrice,
        java.math.BigDecimal currentMarketValue,
        java.math.BigDecimal realizedGainLoss,
        java.math.BigDecimal unrealizedGainLoss,
        java.math.BigDecimal dividendIncome,
        java.math.BigDecimal fees,
        java.math.BigDecimal taxes,
        java.math.BigDecimal netTotalReturnAmount,
        java.math.BigDecimal totalReturnPercentage,
        String currency,
        java.math.BigDecimal nativePrice,
        String nativeCurrency,
        java.math.BigDecimal nativeNetTotalReturnAmount,
        java.math.BigDecimal nativeReturnPercentage,
        List<PositionLotResponse> openLots,
        List<LotDisposalResponse> disposals,
        List<TransactionResponse> transactions) { }

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
        java.math.BigDecimal totalNetDeposits,
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
        String currency,
        java.math.BigDecimal nativePrice,
        String nativeCurrency,
        java.math.BigDecimal nativeCostBasis,
        java.math.BigDecimal nativeUnrealizedGainLoss,
        java.math.BigDecimal nativeGainLossPercentage) { }

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

    public record MonthlyDividendHistoryResponse(
        String yearMonth,
        java.math.BigDecimal netAmount,
        java.math.BigDecimal grossAmount,
        java.math.BigDecimal withholdingTax,
        String currency) { }

    public record YearlyDividendHistoryResponse(
        int year,
        java.math.BigDecimal netAmount,
        java.math.BigDecimal grossAmount,
        java.math.BigDecimal withholdingTax,
        String currency) { }

    public record HoldingDividendMetricResponse(
        UUID instrumentId,
        String instrumentName,
        String ticker,
        String isin,
        AssetClass assetClass,
        java.math.BigDecimal currentShares,
        java.math.BigDecimal totalReceivedAllTime,
        java.math.BigDecimal totalReceivedYtd,
        java.math.BigDecimal totalReceivedTtm,
        java.math.BigDecimal trailingTwelveMonthsDps,
        java.math.BigDecimal projectedAnnualIncome,
        java.math.BigDecimal currentYieldPercentage,
        java.math.BigDecimal yieldOnCostPercentage,
        String currency) { }

    public record ProjectedMonthlyIncomeResponse(
        int month,
        String monthName,
        java.math.BigDecimal projectedAmount,
        String currency) { }

    public record DividendAnalyticsResponse(
        UUID portfolioId,
        Instant asOf,
        String baseCurrency,
        java.math.BigDecimal totalDividendsAllTime,
        java.math.BigDecimal totalDividendsYtd,
        java.math.BigDecimal totalDividendsTtm,
        java.math.BigDecimal totalWithholdingTaxAllTime,
        java.math.BigDecimal projectedAnnualDividendIncome,
        java.math.BigDecimal portfolioDividendYieldPercentage,
        java.math.BigDecimal portfolioYieldOnCostPercentage,
        List<MonthlyDividendHistoryResponse> monthlyHistory,
        List<YearlyDividendHistoryResponse> yearlyHistory,
        List<ProjectedMonthlyIncomeResponse> projectedMonthlyCalendar,
        List<HoldingDividendMetricResponse> holdings) { }

    public record TargetAllocationItemRequest(
        String categoryKey,
        String categoryLabel,
        java.math.BigDecimal targetPercentage,
        UUID instrumentId) { }

    public record TargetAllocationPlanRequest(
        String name,
        AllocationType allocationType,
        java.math.BigDecimal driftTolerancePercentage,
        List<TargetAllocationItemRequest> items) { }

    public record TargetAllocationItemResponse(
        UUID id,
        String categoryKey,
        String categoryLabel,
        java.math.BigDecimal targetPercentage,
        UUID instrumentId,
        String instrumentTicker,
        String instrumentName) { }

    public record TargetAllocationPlanResponse(
        UUID id,
        UUID portfolioId,
        String name,
        AllocationType allocationType,
        java.math.BigDecimal driftTolerancePercentage,
        List<TargetAllocationItemResponse> items,
        Instant updatedAt) { }

    public record RebalanceOrderItemResponse(
        String categoryKey,
        String categoryLabel,
        UUID instrumentId,
        String instrumentTicker,
        String instrumentName,
        RebalanceAction action,
        java.math.BigDecimal currentMarketValue,
        java.math.BigDecimal currentWeightPercentage,
        java.math.BigDecimal targetWeightPercentage,
        java.math.BigDecimal driftPercentage,
        DriftStatus driftStatus,
        boolean isDriftExceeded,
        java.math.BigDecimal targetValue,
        java.math.BigDecimal orderAmount,
        java.math.BigDecimal estimatedPrice,
        java.math.BigDecimal estimatedQuantity,
        java.math.BigDecimal projectedPostWeightPercentage,
        String currency) { }

    public record RebalanceAnalysisResponse(
        UUID portfolioId,
        String portfolioName,
        String baseCurrency,
        Instant asOf,
        AllocationType allocationType,
        java.math.BigDecimal totalPortfolioValue,
        java.math.BigDecimal cashInjectionAmount,
        java.math.BigDecimal totalPostRebalanceValue,
        java.math.BigDecimal driftTolerancePercentage,
        boolean hasDriftToleranceExceeded,
        List<RebalanceOrderItemResponse> items) { }

    public record HistoricalValuationPoint(
        Instant timestamp,
        java.math.BigDecimal marketValue,
        java.math.BigDecimal costBasis,
        java.math.BigDecimal cashValue,
        java.math.BigDecimal investedCapital,
        java.math.BigDecimal unrealizedGainLoss,
        java.math.BigDecimal portfolioReturnPercentage,
        java.math.BigDecimal benchmarkReturnPercentage) { }

    public record HistoricalPerformanceSummary(
        java.math.BigDecimal startingValue,
        java.math.BigDecimal endingValue,
        java.math.BigDecimal netCashFlows,
        java.math.BigDecimal totalGainLoss,
        java.math.BigDecimal portfolioReturnPercentage,
        java.math.BigDecimal benchmarkReturnPercentage,
        java.math.BigDecimal excessReturnPercentage,
        java.math.BigDecimal maxDrawdownPercentage) { }

    public record PortfolioHistoryResponse(
        UUID portfolioId,
        String portfolioName,
        String baseCurrency,
        String period,
        String interval,
        Instant periodStart,
        Instant periodEnd,
        UUID benchmarkId,
        String benchmarkTicker,
        String benchmarkName,
        HistoricalPerformanceSummary summary,
        List<HistoricalValuationPoint> dataPoints) { }
}

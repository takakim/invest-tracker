export type CostBasisMethod = 'FIFO' | 'LIFO' | 'AVERAGE_COST';
export type ReturnMethod = 'XIRR' | 'TWR' | 'MWR';
export type EntityStatus = 'ACTIVE' | 'ARCHIVED';

export type AssetClass =
  | 'STOCK'
  | 'ETF'
  | 'MUTUAL_FUND'
  | 'BOND'
  | 'REIT'
  | 'CRYPTO'
  | 'CASH'
  | 'OTHER';

export interface Portfolio {
  id: string;
  name: string;
  baseCurrency: string;
  costBasisMethod: CostBasisMethod;
  returnMethod: ReturnMethod;
  status: EntityStatus;
  createdAt: string;
  updatedAt: string;
}

export interface PortfolioCreateInput {
  name: string;
  baseCurrency: string;
  costBasisMethod: CostBasisMethod;
  returnMethod: ReturnMethod;
}

export interface Account {
  id: string;
  portfolioId: string;
  name: string;
  brokerName: string;
  accountCurrency: string;
  status: EntityStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AccountCreateInput {
  name: string;
  brokerName: string;
  accountCurrency: string;
}

export interface Instrument {
  id: string;
  name: string;
  assetClass: AssetClass;
  ticker?: string | null;
  isin?: string | null;
  exchange?: string | null;
  currency: string;
  manualPriceOnly?: boolean;
  latestPrice?: number | null;
  priceCurrency?: string | null;
  priceAsOf?: string | null;
  isStale?: boolean | null;
  createdAt: string;
  updatedAt: string;
}

export interface InstrumentCreateInput {
  name: string;
  assetClass: AssetClass;
  ticker?: string | null;
  isin?: string | null;
  exchange?: string | null;
  currency: string;
  manualPriceOnly?: boolean;
}

export interface Position {
  id: string;
  accountId: string;
  instrumentId: string;
  instrumentName: string;
  instrumentTicker?: string | null;
  instrumentIsin?: string | null;
  assetClass: AssetClass;
  quantity: number;
  costBasisAmount?: number | null;
  costBasisCurrency?: string | null;
  status: EntityStatus;
  createdAt: string;
  updatedAt: string;
}

export interface PositionCreateInput {
  instrumentId: string;
  quantity: number;
  costBasisAmount?: number | null;
  costBasisCurrency?: string | null;
}

export interface PositionUpdateInput {
  quantity: number;
  costBasisAmount?: number | null;
  costBasisCurrency?: string | null;
}

export interface PositionLot {
  lotId: string;
  transactionId?: string | null;
  acquisitionDate: string;
  originalQuantity: number;
  remainingQuantity: number;
  unitCostAmount: number;
  totalCostAmount: number;
  currency: string;
}

export interface LotDisposal {
  transactionId: string;
  lotId: string;
  disposalDate: string;
  quantity: number;
  costBasis: number;
  proceeds: number;
  realizedGainLoss: number;
  currency: string;
}

export interface PositionLotsDetail {
  positionId: string;
  accountId: string;
  instrumentId: string;
  costBasisMethod: CostBasisMethod;
  totalQuantity: number;
  totalCostBasisAmount: number;
  currency: string;
  averageUnitCostAmount: number;
  realizedGainLossAmount: number;
  openLots: PositionLot[];
}

export interface PositionPerformance {
  positionId?: string | null;
  accountId: string;
  accountName: string;
  instrumentId: string;
  instrumentName: string;
  ticker?: string | null;
  isin?: string | null;
  assetClass: AssetClass;
  status: string;
  currentQuantity: number;
  totalBoughtQuantity: number;
  totalSoldQuantity: number;
  averageBuyPrice: number;
  averageSellPrice: number;
  totalInvestedAmount: number;
  totalProceedsAmount: number;
  currentCostBasis: number;
  currentPrice: number;
  currentMarketValue: number;
  realizedGainLoss: number;
  unrealizedGainLoss: number;
  dividendIncome: number;
  fees: number;
  taxes: number;
  netTotalReturnAmount: number;
  totalReturnPercentage: number;
  currency: string;
  nativePrice?: number | null;
  nativeCurrency?: string | null;
  nativeNetTotalReturnAmount?: number | null;
  nativeReturnPercentage?: number | null;
  openLots: PositionLot[];
  disposals: LotDisposal[];
  transactions: Transaction[];
}

export interface PositionRecalculateResponse {
  portfolioId: string;
  recalculatedPositionsCount: number;
  message: string;
}

export type TransactionType =
  | 'BUY'
  | 'SELL'
  | 'DIVIDEND'
  | 'FEE'
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'INTEREST'
  | 'STOCK_SPLIT'
  | 'REVERSE_STOCK_SPLIT'
  | 'TRANSFER';

export type TransactionStatus = 'COMPLETED' | 'CORRECTED';

export interface Transaction {
  id: string;
  accountId: string;
  instrumentId?: string | null;
  instrumentName?: string | null;
  instrumentTicker?: string | null;
  type: TransactionType;
  tradeDate: string;
  settlementDate?: string | null;
  quantity?: number | null;
  price?: number | null;
  grossAmount: number;
  feeAmount?: number | null;
  taxAmount?: number | null;
  netAmount: number;
  currency: string;
  fxRate?: number | null;
  counterCurrency?: string | null;
  notes?: string | null;
  status: TransactionStatus;
  correctionOfTransactionId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionCreateInput {
  instrumentId?: string | null;
  type: TransactionType;
  tradeDate: string;
  settlementDate?: string | null;
  quantity?: number | null;
  price?: number | null;
  grossAmount: number;
  feeAmount?: number | null;
  taxAmount?: number | null;
  currency: string;
  fxRate?: number | null;
  counterCurrency?: string | null;
  notes?: string | null;
}

export interface TransactionCorrectInput {
  replacementInstrumentId?: string | null;
  replacementType: TransactionType;
  replacementTradeDate: string;
  replacementSettlementDate?: string | null;
  replacementQuantity?: number | null;
  replacementPrice?: number | null;
  replacementGrossAmount: number;
  replacementFeeAmount?: number | null;
  replacementTaxAmount?: number | null;
  replacementCurrency: string;
  replacementFxRate?: number | null;
  replacementCounterCurrency?: string | null;
  replacementNotes?: string | null;
}

export interface PreviewRow {
  rowNumber: number;
  rawType?: string | null;
  mappedType?: string | null;
  instrumentTitle?: string | null;
  ticker?: string | null;
  isin?: string | null;
  quantity?: number | null;
  price?: number | null;
  grossAmount?: number | null;
  feeAmount?: number | null;
  taxAmount?: number | null;
  currency?: string | null;
  isDuplicate: boolean;
  isIgnored: boolean;
  diagnosticMessage?: string | null;
}

export interface CsvImportPreview {
  brokerName: string;
  fileName: string;
  totalRows: number;
  importableRows: number;
  duplicateRows: number;
  ignoredRows: number;
  rows: PreviewRow[];
}

export interface ImportBatch {
  id: string;
  accountId: string;
  fileName: string;
  brokerType: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  totalRows: number;
  importedRows: number;
  skippedRows: number;
  createdAt: string;
}

export interface CsvImportInput {
  fileName: string;
  csvContent: string;
}


export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  errors?: string[];
}

export interface AccountPerformanceSummary {
  accountId: string;
  accountName: string;
  realizedGainLoss: number;
  dividendIncome: number;
  interestIncome: number;
  fees: number;
  taxes: number;
  costBasis: number;
  currency: string;
}

export interface PerformanceResult {
  portfolioId: string;
  asOf: string;
  returnMethod: ReturnMethod;
  twrReturn: number | null;
  twrAnnualized: number | null;
  mwrReturn: number | null;
  totalRealizedGainLoss: number;
  totalDividendIncome: number;
  totalInterestIncome: number;
  totalFees: number;
  totalTaxes: number;
  totalNetIncome: number;
  totalCostBasis: number;
  currency: string;
  valuationBasis: string;
  byAccount: AccountPerformanceSummary[];
}

export type ObservationSourceType = 'PROVIDER' | 'MANUAL';

export interface PriceQuote {
  instrumentId: string;
  price: number;
  currency: string;
  asOf: string;
  sourceType: ObservationSourceType;
  sourceReference?: string | null;
  isStale: boolean;
  warning?: string | null;
}

export interface MarketPriceOverrideRequest {
  price: number;
  currency?: string | null;
  observedAt?: string | null;
  reason?: string | null;
}

export interface MarketObservation {
  id: string;
  instrumentId: string;
  price: number;
  currency: string;
  observedAt: string;
  sourceType: ObservationSourceType;
  sourceReference?: string | null;
  createdAt: string;
}

export interface FxRateQuote {
  baseCurrency: string;
  quoteCurrency: string;
  rate: number;
  asOf: string;
  sourceType: ObservationSourceType;
  sourceReference?: string | null;
  isDerived: boolean;
  warning?: string | null;
}

export interface FxRateOverrideRequest {
  baseCurrency: string;
  quoteCurrency: string;
  rate: number;
  observedAt?: string | null;
  reason?: string | null;
}

export interface FxObservation {
  id: string;
  baseCurrency: string;
  quoteCurrency: string;
  rate: number;
  observedAt: string;
  sourceType: ObservationSourceType;
  sourceReference?: string | null;
  createdAt: string;
}

export interface AllocationItem {
  category: string;
  marketValue: number;
  percentage: number;
  costBasis: number;
  unrealizedGainLoss: number;
}

export interface HoldingExposure {
  instrumentId: string;
  instrumentName: string;
  ticker: string;
  assetClass: AssetClass;
  quantity: number;
  currentPrice: number;
  marketValue: number;
  costBasis: number;
  unrealizedGainLoss: number;
  weightPercentage: number;
  currency: string;
  nativePrice?: number | null;
  nativeCurrency?: string | null;
  nativeCostBasis?: number | null;
  nativeUnrealizedGainLoss?: number | null;
  nativeGainLossPercentage?: number | null;
}

export interface PortfolioAnalytics {
  portfolioId: string;
  asOf: string;
  baseCurrency: string;
  totalCurrentValue: number;
  totalCostBasis: number;
  totalUnrealizedGainLoss: number;
  totalUnrealizedReturnPercentage: number;
  totalRealizedGainLoss: number;
  totalCashValue: number;
  byAssetClass: AllocationItem[];
  byCurrency: AllocationItem[];
  byAccount: AllocationItem[];
  topHoldings: HoldingExposure[];
  warnings: string[];
}

export interface BenchmarkInstrument {
  id: string;
  name: string;
  ticker?: string | null;
  isin?: string | null;
  assetClass: AssetClass;
  currency: string;
}

export type BenchmarkPeriod = '1M' | '3M' | '6M' | '1Y' | 'YTD' | 'ALL';

export interface BenchmarkComparisonResult {
  portfolioId: string;
  benchmarkInstrumentId: string;
  benchmarkName: string;
  benchmarkTicker: string;
  periodStart: string;
  periodEnd: string;
  portfolioReturn: number;
  benchmarkReturn: number;
  excessReturn: number;
  annualizedPortfolioReturn: number;
  annualizedBenchmarkReturn: number;
  annualizedExcessReturn: number;
  outperforming: boolean;
  baseCurrency: string;
  warnings: string[];
}




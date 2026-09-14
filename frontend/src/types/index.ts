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

export type AccountTaxTreatment = 'TAXABLE' | 'TAX_EXEMPT' | 'TAX_DEFERRED';

export interface Account {
  id: string;
  portfolioId: string;
  name: string;
  brokerName: string;
  accountCurrency: string;
  taxTreatment?: AccountTaxTreatment;
  status: EntityStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AccountCreateInput {
  name: string;
  brokerName: string;
  accountCurrency: string;
  taxTreatment?: AccountTaxTreatment;
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

export interface TransactionUpdateInput {
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
  overrideBroker?: string | null;
}

export interface BrokerDetectionRequest {
  csvContent: string;
}

export interface BrokerDetectionResponse {
  brokerName: string;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
  isSupported: boolean;
  supportedBrokers: string[];
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
  totalNetDeposits?: number;
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

export interface MonthlyDividendHistory {
  yearMonth: string;
  netAmount: number;
  grossAmount: number;
  taxAmount: number;
}

export interface YearlyDividendHistory {
  year: number;
  netAmount: number;
  grossAmount: number;
  taxAmount: number;
}

export interface HoldingDividendMetric {
  instrumentId: string;
  instrumentName: string;
  ticker?: string | null;
  isin?: string | null;
  assetClass: AssetClass;
  currentShares: number;
  totalReceivedAllTime: number;
  totalReceivedYtd: number;
  totalReceivedTtm: number;
  trailingTwelveMonthsDps: number;
  projectedAnnualIncome: number;
  currentYieldPercentage: number;
  yieldOnCostPercentage: number;
  currency: string;
}

export interface ProjectedMonthlyIncome {
  month: number;
  monthName: string;
  projectedAmount: number;
}

export interface DividendAnalytics {
  portfolioId: string;
  asOf: string;
  baseCurrency: string;
  totalDividendsAllTime: number;
  totalDividendsYtd: number;
  totalDividendsTtm: number;
  totalWithholdingTaxAllTime: number;
  projectedAnnualDividendIncome: number;
  portfolioDividendYieldPercentage: number;
  portfolioYieldOnCostPercentage: number;
  monthlyHistory: MonthlyDividendHistory[];
  yearlyHistory: YearlyDividendHistory[];
  holdings: HoldingDividendMetric[];
  projectedMonthlyCalendar: ProjectedMonthlyIncome[];
  projectedCalendar?: ProjectedMonthlyIncome[];
}

export type AllocationType = 'ASSET_CLASS' | 'INSTRUMENT';
export type DriftStatus = 'IN_TOLERANCE' | 'OVERWEIGHT' | 'UNDERWEIGHT';
export type RebalanceAction = 'BUY' | 'SELL' | 'HOLD';

export interface TargetAllocationItemInput {
  categoryKey: string;
  categoryLabel: string;
  targetPercentage: number;
  instrumentId?: string | null;
}

export interface TargetAllocationPlanInput {
  name: string;
  allocationType: AllocationType;
  driftTolerancePercentage?: number;
  items: TargetAllocationItemInput[];
}

export interface TargetAllocationItem {
  id: string;
  categoryKey: string;
  categoryLabel: string;
  targetPercentage: number;
  instrumentId?: string | null;
  instrumentTicker?: string | null;
  instrumentName?: string | null;
}

export interface TargetAllocationPlan {
  id: string;
  portfolioId: string;
  name: string;
  allocationType: AllocationType;
  driftTolerancePercentage: number;
  items: TargetAllocationItem[];
  updatedAt: string;
}

export interface RebalanceOrderItem {
  categoryKey: string;
  categoryLabel: string;
  instrumentId?: string | null;
  instrumentTicker?: string | null;
  instrumentName?: string | null;
  action: RebalanceAction;
  currentMarketValue: number;
  currentWeightPercentage: number;
  targetWeightPercentage: number;
  driftPercentage: number;
  driftStatus: DriftStatus;
  isDriftExceeded: boolean;
  targetValue: number;
  orderAmount: number;
  estimatedPrice?: number | null;
  estimatedQuantity?: number | null;
  projectedPostWeightPercentage: number;
  currency: string;
}

export interface RebalanceAnalysis {
  portfolioId: string;
  portfolioName: string;
  baseCurrency: string;
  asOf: string;
  allocationType: AllocationType;
  totalPortfolioValue: number;
  cashInjectionAmount: number;
  totalPostRebalanceValue: number;
  driftTolerancePercentage: number;
  hasDriftToleranceExceeded: boolean;
  items: RebalanceOrderItem[];
}

export interface HistoricalValuationPoint {
  timestamp: string;
  marketValue: number;
  costBasis: number;
  cashValue: number;
  investedCapital: number;
  unrealizedGainLoss: number;
  portfolioReturnPercentage: number;
  benchmarkReturnPercentage?: number | null;
}

export interface HistoricalPerformanceSummary {
  startingValue: number;
  endingValue: number;
  netCashFlows: number;
  totalGainLoss: number;
  portfolioReturnPercentage: number;
  benchmarkReturnPercentage?: number | null;
  excessReturnPercentage?: number | null;
  maxDrawdownPercentage: number;
}

export interface PortfolioHistory {
  portfolioId: string;
  portfolioName: string;
  baseCurrency: string;
  period: string;
  interval: string;
  periodStart: string;
  periodEnd: string;
  benchmarkId?: string | null;
  benchmarkTicker?: string | null;
  benchmarkName?: string | null;
  summary: HistoricalPerformanceSummary;
  dataPoints: HistoricalValuationPoint[];
}

export interface CashFlowSummary {
  totalDeposits: number;
  totalWithdrawals: number;
  netContributions: number;
  totalDividends: number;
  totalInterest: number;
  totalFees: number;
  netCashFlow: number;
  avgMonthlyContribution: number;
  activeContributionMonths: number;
  cumulativeContributions: number;
  currentPortfolioValue: number;
  capitalContributionsPercentage: number;
  marketGrowthPercentage: number;
  baseCurrency: string;
}

export interface CashFlowPeriodPoint {
  periodLabel: string;
  startDate: string;
  endDate: string;
  deposits: number;
  withdrawals: number;
  netContributions: number;
  internalIncome: number;
  cumulativeNetContributions: number;
}

export interface AccountCashFlowSummary {
  accountId: string;
  accountName: string;
  brokerName: string;
  accountCurrency: string;
  currentCashBalance: number;
  currentCashBalanceInBase: number;
  totalDeposits: number;
  totalWithdrawals: number;
  netContributions: number;
}

export interface CashFlowAnalytics {
  portfolioId: string;
  portfolioName: string;
  baseCurrency: string;
  period: string;
  groupBy: 'MONTH' | 'QUARTER' | 'YEAR';
  periodStart: string;
  periodEnd: string;
  summary: CashFlowSummary;
  periods: CashFlowPeriodPoint[];
  accountBreakdown: AccountCashFlowSummary[];
  warnings: string[];
}

export type CorporateActionType = 'STOCK_SPLIT' | 'REVERSE_STOCK_SPLIT' | 'DIVIDEND';
export type CorporateActionStatus = 'PENDING' | 'APPLIED' | 'DISMISSED';

export interface CorporateAction {
  id: string;
  instrumentId: string;
  instrumentName: string;
  ticker?: string | null;
  isin?: string | null;
  assetClass: AssetClass | string;
  actionType: CorporateActionType;
  status: CorporateActionStatus;
  exDate: string;
  recordDate?: string | null;
  paymentDate?: string | null;
  ratioFrom?: number | null;
  ratioTo?: number | null;
  amountPerShare?: number | null;
  currency: string;
  description?: string | null;
  source: string;
  heldQuantityAtExDate?: number | null;
  proposedImpactQuantity?: number | null;
  proposedImpactAmount?: number | null;
  suggestedAccountId?: string | null;
  suggestedAccountName?: string | null;
  appliedTransactionId?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface ApplyCorporateActionInput {
  accountId: string;
  quantity?: number | null;
  grossAmount?: number | null;
  taxAmount?: number | null;
  notes?: string | null;
}

export interface ScanCorporateActionsResponse {
  portfolioId: string;
  scannedInstrumentsCount: number;
  discoveredActionsCount: number;
  newPendingActionsCount: number;
  messages: string[];
}

export type TaxRegime = 'UK_HMRC' | 'CALENDAR_YEAR';

export interface TaxSettingsRequest {
  taxYear?: string | null;
  taxRegime?: TaxRegime | null;
  cgtAllowance?: number | null;
  dividendAllowance?: number | null;
  lossCarryforward?: number | null;
  notes?: string | null;
}

export interface TaxSettingsResponse {
  id: string;
  portfolioId: string;
  taxYear: string;
  taxRegime: TaxRegime;
  cgtAllowance: number;
  dividendAllowance: number;
  lossCarryforward: number;
  notes?: string | null;
  updatedAt: string;
}

export interface ItemizedDisposal {
  disposalTransactionId: string;
  accountId: string;
  accountName: string;
  taxTreatment: AccountTaxTreatment;
  instrumentId: string;
  instrumentName: string;
  ticker?: string | null;
  disposalDate: string;
  quantity: number;
  proceedsNative: number;
  costBasisNative: number;
  nativeCurrency: string;
  proceedsBase: number;
  costBasisBase: number;
  realizedGainLossBase: number;
}

export interface ItemizedDividend {
  transactionId: string;
  accountId: string;
  accountName: string;
  taxTreatment: AccountTaxTreatment;
  instrumentId?: string | null;
  instrumentName: string;
  ticker?: string | null;
  paymentDate: string;
  grossAmountNative: number;
  withholdingTaxNative: number;
  nativeCurrency: string;
  grossAmountBase: number;
  withholdingTaxBase: number;
  netAmountBase: number;
}

export interface TaxLossHarvestOpportunity {
  accountId: string;
  accountName: string;
  instrumentId: string;
  instrumentName: string;
  ticker?: string | null;
  quantity: number;
  currentPrice: number;
  priceCurrency: string;
  currentMarketValueBase: number;
  totalCostBasisBase: number;
  unrealizedLossBase: number;
}

export interface CapitalGainsTaxSummary {
  totalDisposalProceeds: number;
  totalDisposalCostBasis: number;
  grossRealizedGains: number;
  grossRealizedLosses: number;
  netRealizedGainLoss: number;
  lossCarryforwardApplied: number;
  netTaxableGainBeforeAllowance: number;
  annualExemptAmount: number;
  allowanceUsed: number;
  allowanceRemaining: number;
  taxableCapitalGain: number;
  estimatedTaxBasicRate: number;
  estimatedTaxHigherRate: number;
  basicTaxRatePercentage: number;
  higherTaxRatePercentage: number;
  totalDisposalsCount: number;
}

export interface DividendTaxSummary {
  totalGrossDividends: number;
  totalWithholdingTax: number;
  netDividendsReceived: number;
  annualDividendAllowance: number;
  allowanceUsed: number;
  allowanceRemaining: number;
  taxableDividendIncome: number;
  estimatedTaxBasicRate: number;
  estimatedTaxHigherRate: number;
  estimatedTaxAdditionalRate: number;
  basicTaxRatePercentage: number;
  higherTaxRatePercentage: number;
  additionalTaxRatePercentage: number;
  totalDividendsCount: number;
}

export interface TaxShelteredSummary {
  shelteredRealizedGains: number;
  shelteredRealizedLosses: number;
  shelteredGrossDividends: number;
  estimatedCapitalGainsTaxSaved: number;
  estimatedDividendTaxSaved: number;
  totalEstimatedTaxSaved: number;
}

export interface TaxReportResponse {
  portfolioId: string;
  portfolioName: string;
  baseCurrency: string;
  taxYear: string;
  taxRegime: TaxRegime;
  periodStart: string;
  periodEnd: string;
  capitalGains: CapitalGainsTaxSummary;
  dividendIncome: DividendTaxSummary;
  shelteredSummary: TaxShelteredSummary;
  lossHarvestOpportunities: TaxLossHarvestOpportunity[];
  disposals: ItemizedDisposal[];
  dividends: ItemizedDividend[];
  warnings: string[];
}

export interface AvailableTaxYearsResponse {
  availableUkTaxYears: string[];
  availableCalendarYears: string[];
  currentUkTaxYear: string;
  currentCalendarYear: string;
}

export type AiStance = 'STRONG_BUY' | 'ACCUMULATE' | 'HOLD' | 'TRIM' | 'SELL';
export type AiRiskLevel = 'LOW' | 'MODERATE' | 'HIGH' | 'VERY_HIGH';

export interface AiStatus {
  enabled: boolean;
  connected: boolean;
  provider: string;
  baseUrl: string;
  configuredModel: string;
  availableModels: string[];
  errorMessage?: string | null;
}

export interface HoldingFinancialMetrics {
  peRatio?: number | null;
  forwardPe?: number | null;
  pegRatio?: number | null;
  priceToBook?: number | null;
  dividendYield?: number | null;
  debtToEquity?: number | null;
  returnOnEquity?: number | null;
  fiftyTwoWeekHigh?: number | null;
  fiftyTwoWeekLow?: number | null;
  marketCap?: number | null;
  expenseRatio?: number | null;
  assetClass: string;
  currency: string;
}

export interface HoldingAiEvaluation {
  instrumentId: string;
  symbol: string;
  name: string;
  assetClass: string;
  quantity: number;
  currentPrice: number;
  averageCostBasis: number;
  unrealizedGainLoss: number;
  unrealizedGainLossPercentage: number;
  portfolioWeightPercentage: number;
  stance: AiStance;
  riskScore: number;
  riskLevel: AiRiskLevel;
  executiveSummary: string;
  strengths: string[];
  risks: string[];
  holdingVsSellingTradeoff: string;
  fundamentalMetrics: HoldingFinancialMetrics;
  modelUsed: string;
  evaluatedAt: string;
}

export interface PortfolioAiEvaluation {
  portfolioId: string;
  portfolioName: string;
  baseCurrency: string;
  overallRiskScore: number;
  overallRiskLevel: AiRiskLevel;
  executiveSummary: string;
  diversificationAssessment: string;
  concentrationRisks: string[];
  taxAndLocationOptimization: string[];
  topRecommendations: string[];
  macroStressScenarios: string[];
  topHoldingEvaluations: HoldingAiEvaluation[];
  modelUsed: string;
  evaluatedAt: string;
}



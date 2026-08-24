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

export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  errors?: string[];
}

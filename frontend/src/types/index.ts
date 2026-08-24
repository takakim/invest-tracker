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

export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  errors?: string[];
}

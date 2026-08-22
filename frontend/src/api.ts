export type CostBasisMethod = 'FIFO' | 'LIFO' | 'AVERAGE_COST';
export type ReturnMethod = 'XIRR' | 'TWR' | 'MWR';

export interface Portfolio {
  id: string;
  name: string;
  baseCurrency: string;
  costBasisMethod: CostBasisMethod;
  returnMethod: ReturnMethod;
  status: string;
}

export interface Account {
  id: string;
  portfolioId: string;
  name: string;
  brokerName: string;
  accountCurrency: string;
  status: string;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({ detail: response.statusText }));
    throw new Error(problem.detail ?? 'Request failed');
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

export const api = {
  portfolios: () => request<Portfolio[]>('/api/v1/portfolios'),
  createPortfolio: (body: Omit<Portfolio, 'id' | 'status'>) => request<Portfolio>('/api/v1/portfolios', { method: 'POST', body: JSON.stringify(body) }),
  accounts: (portfolioId: string) => request<Account[]>(`/api/v1/portfolios/${portfolioId}/accounts`),
  createAccount: (portfolioId: string, body: Omit<Account, 'id' | 'portfolioId' | 'status'>) => request<Account>(`/api/v1/portfolios/${portfolioId}/accounts`, { method: 'POST', body: JSON.stringify(body) }),
};

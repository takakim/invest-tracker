import { request } from './client';
import type { Account, AccountCreateInput } from '../types';

export const accountApi = {
  list: (portfolioId: string): Promise<Account[]> =>
    request<Account[]>(`/api/v1/portfolios/${portfolioId}/accounts`),

  get: (portfolioId: string, accountId: string): Promise<Account> =>
    request<Account>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}`),

  create: (portfolioId: string, input: AccountCreateInput): Promise<Account> =>
    request<Account>(`/api/v1/portfolios/${portfolioId}/accounts`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  update: (
    portfolioId: string,
    accountId: string,
    input: AccountCreateInput,
  ): Promise<Account> =>
    request<Account>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}`, {
      method: 'PUT',
      body: JSON.stringify(input),
    }),

  archive: (portfolioId: string, accountId: string): Promise<void> =>
    request<void>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}`, {
      method: 'DELETE',
    }),
};

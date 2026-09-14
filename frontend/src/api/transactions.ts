import { request } from './client';
import type {
  Transaction,
  TransactionCreateInput,
  TransactionCorrectInput,
  TransactionUpdateInput,
  TransactionType,
} from '../types';

export const transactionApi = {
  list: (portfolioId: string, accountId: string, typeFilter?: TransactionType): Promise<Transaction[]> => {
    const url = `/api/v1/portfolios/${portfolioId}/accounts/${accountId}/transactions${
      typeFilter ? `?type=${typeFilter}` : ''
    }`;
    return request<Transaction[]>(url);
  },

  listPortfolio: (portfolioId: string): Promise<Transaction[]> =>
    request<Transaction[]>(`/api/v1/portfolios/${portfolioId}/transactions`),

  get: (portfolioId: string, accountId: string, transactionId: string): Promise<Transaction> =>
    request<Transaction>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}/transactions/${transactionId}`),

  record: (
    portfolioId: string,
    accountId: string,
    input: TransactionCreateInput,
  ): Promise<Transaction> =>
    request<Transaction>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}/transactions`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  update: (
    portfolioId: string,
    accountId: string,
    transactionId: string,
    input: TransactionUpdateInput,
  ): Promise<Transaction> =>
    request<Transaction>(
      `/api/v1/portfolios/${portfolioId}/accounts/${accountId}/transactions/${transactionId}`,
      {
        method: 'PUT',
        body: JSON.stringify(input),
      },
    ),

  correct: (
    portfolioId: string,
    accountId: string,
    transactionId: string,
    input: TransactionCorrectInput,
  ): Promise<Transaction> =>
    request<Transaction>(
      `/api/v1/portfolios/${portfolioId}/accounts/${accountId}/transactions/${transactionId}/correct`,
      {
        method: 'POST',
        body: JSON.stringify(input),
      },
    ),
};

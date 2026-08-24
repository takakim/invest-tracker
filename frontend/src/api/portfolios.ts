import { request } from './client';
import type { Portfolio, PortfolioCreateInput } from '../types';

export const portfolioApi = {
  list: (): Promise<Portfolio[]> => request<Portfolio[]>('/api/v1/portfolios'),

  get: (id: string): Promise<Portfolio> => request<Portfolio>(`/api/v1/portfolios/${id}`),

  create: (input: PortfolioCreateInput): Promise<Portfolio> =>
    request<Portfolio>('/api/v1/portfolios', {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  update: (id: string, input: PortfolioCreateInput): Promise<Portfolio> =>
    request<Portfolio>(`/api/v1/portfolios/${id}`, {
      method: 'PUT',
      body: JSON.stringify(input),
    }),

  archive: (id: string): Promise<void> =>
    request<void>(`/api/v1/portfolios/${id}`, {
      method: 'DELETE',
    }),
};

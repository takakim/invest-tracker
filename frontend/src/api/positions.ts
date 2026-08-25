import { request } from './client';
import type { Position, PositionCreateInput, PositionLotsDetail, PositionRecalculateResponse, PositionUpdateInput } from '../types';

export const positionApi = {
  list: (portfolioId: string, accountId: string): Promise<Position[]> =>
    request<Position[]>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}/positions`),

  listPortfolio: (portfolioId: string): Promise<Position[]> =>
    request<Position[]>(`/api/v1/portfolios/${portfolioId}/positions`),

  get: (portfolioId: string, accountId: string, positionId: string): Promise<Position> =>
    request<Position>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}/positions/${positionId}`),

  getLots: (portfolioId: string, accountId: string, positionId: string): Promise<PositionLotsDetail> =>
    request<PositionLotsDetail>(
      `/api/v1/portfolios/${portfolioId}/accounts/${accountId}/positions/${positionId}/lots`,
    ),

  recalculate: (portfolioId: string): Promise<PositionRecalculateResponse> =>
    request<PositionRecalculateResponse>(`/api/v1/portfolios/${portfolioId}/positions/recalculate`, {
      method: 'POST',
    }),

  create: (
    portfolioId: string,
    accountId: string,
    input: PositionCreateInput,
  ): Promise<Position> =>
    request<Position>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}/positions`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  update: (
    portfolioId: string,
    accountId: string,
    positionId: string,
    input: PositionUpdateInput,
  ): Promise<Position> =>
    request<Position>(
      `/api/v1/portfolios/${portfolioId}/accounts/${accountId}/positions/${positionId}`,
      {
        method: 'PUT',
        body: JSON.stringify(input),
      },
    ),

  archive: (portfolioId: string, accountId: string, positionId: string): Promise<void> =>
    request<void>(
      `/api/v1/portfolios/${portfolioId}/accounts/${accountId}/positions/${positionId}`,
      {
        method: 'DELETE',
      },
    ),
};


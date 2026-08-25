import { request } from './client';
import type { PerformanceResult } from '../types';

export const performanceApi = {
  get: (portfolioId: string): Promise<PerformanceResult> =>
    request<PerformanceResult>(`/api/v1/portfolios/${portfolioId}/performance`),
};

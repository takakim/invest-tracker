import { request } from './client';
import type { PortfolioAnalytics, DividendAnalytics, PortfolioHistory, CashFlowAnalytics } from '../types';

export const analyticsApi = {
  getPortfolioAnalytics: (portfolioId: string, asOf?: string): Promise<PortfolioAnalytics> => {
    const params = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return request<PortfolioAnalytics>(`/api/v1/portfolios/${portfolioId}/analytics${params}`);
  },
  getDividendAnalytics: (portfolioId: string, asOf?: string): Promise<DividendAnalytics> => {
    const params = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return request<DividendAnalytics>(`/api/v1/portfolios/${portfolioId}/analytics/dividends${params}`);
  },
  getPortfolioHistory: (
    portfolioId: string,
    params?: { period?: string; interval?: string; benchmarkId?: string }
  ): Promise<PortfolioHistory> => {
    const query = new URLSearchParams();
    if (params?.period) query.append('period', params.period);
    if (params?.interval) query.append('interval', params.interval);
    if (params?.benchmarkId) query.append('benchmarkId', params.benchmarkId);
    const queryString = query.toString() ? `?${query.toString()}` : '';
    return request<PortfolioHistory>(`/api/v1/portfolios/${portfolioId}/history${queryString}`);
  },
  getCashFlowAnalytics: (
    portfolioId: string,
    params?: { period?: string; groupBy?: string }
  ): Promise<CashFlowAnalytics> => {
    const query = new URLSearchParams();
    if (params?.period) query.append('period', params.period);
    if (params?.groupBy) query.append('groupBy', params.groupBy);
    const queryString = query.toString() ? `?${query.toString()}` : '';
    return request<CashFlowAnalytics>(`/api/v1/portfolios/${portfolioId}/cash-flows${queryString}`);
  },
};



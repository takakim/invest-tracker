import { request } from './client';
import type { PortfolioAnalytics, DividendAnalytics } from '../types';

export const analyticsApi = {
  getPortfolioAnalytics: (portfolioId: string, asOf?: string): Promise<PortfolioAnalytics> => {
    const params = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return request<PortfolioAnalytics>(`/api/v1/portfolios/${portfolioId}/analytics${params}`);
  },
  getDividendAnalytics: (portfolioId: string, asOf?: string): Promise<DividendAnalytics> => {
    const params = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return request<DividendAnalytics>(`/api/v1/portfolios/${portfolioId}/analytics/dividends${params}`);
  },
};


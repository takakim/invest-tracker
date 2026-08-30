import { useQuery } from '@tanstack/react-query';
import { analyticsApi } from '../../api';

export function usePortfolioAnalytics(portfolioId?: string, asOf?: string) {
  return useQuery({
    queryKey: ['portfolio', portfolioId, 'analytics', asOf],
    queryFn: () => analyticsApi.getPortfolioAnalytics(portfolioId!, asOf),
    enabled: !!portfolioId,
    staleTime: 30 * 1000,
  });
}

export function useDividendAnalytics(portfolioId?: string, asOf?: string) {
  return useQuery({
    queryKey: ['portfolio', portfolioId, 'analytics', 'dividends', asOf],
    queryFn: () => analyticsApi.getDividendAnalytics(portfolioId!, asOf),
    enabled: !!portfolioId,
    staleTime: 30 * 1000,
  });
}


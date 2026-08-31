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

export function usePortfolioHistory(
  portfolioId?: string,
  params?: { period?: string; interval?: string; benchmarkId?: string }
) {
  return useQuery({
    queryKey: ['portfolio', portfolioId, 'history', params?.period, params?.interval, params?.benchmarkId],
    queryFn: () => analyticsApi.getPortfolioHistory(portfolioId!, params),
    enabled: !!portfolioId,
    staleTime: 60 * 1000,
  });
}


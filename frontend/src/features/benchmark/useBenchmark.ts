import { useQuery } from '@tanstack/react-query';
import { benchmarkApi } from '../../api';
import type { BenchmarkPeriod } from '../../types';

export function useBenchmarkList() {
  return useQuery({
    queryKey: ['benchmarks'],
    queryFn: () => benchmarkApi.getAvailableBenchmarks(),
    staleTime: 60 * 1000,
  });
}

export function useBenchmarkComparison(
  portfolioId?: string,
  benchmarkInstrumentId?: string,
  period?: BenchmarkPeriod,
  asOf?: string,
) {
  return useQuery({
    queryKey: ['portfolio', portfolioId, 'benchmark-comparison', benchmarkInstrumentId, period, asOf],
    queryFn: () => benchmarkApi.comparePortfolioToBenchmark(portfolioId!, benchmarkInstrumentId!, period, asOf),
    enabled: !!portfolioId && !!benchmarkInstrumentId,
    staleTime: 30 * 1000,
  });
}

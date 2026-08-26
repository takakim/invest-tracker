import { request } from './client';
import type { BenchmarkComparisonResult, BenchmarkInstrument, BenchmarkPeriod } from '../types';

export const benchmarkApi = {
  getAvailableBenchmarks: (): Promise<BenchmarkInstrument[]> =>
    request<BenchmarkInstrument[]>('/api/v1/benchmarks'),

  comparePortfolioToBenchmark: (
    portfolioId: string,
    benchmarkInstrumentId: string,
    period?: BenchmarkPeriod,
    asOf?: string,
  ): Promise<BenchmarkComparisonResult> => {
    const params = new URLSearchParams({ benchmarkInstrumentId });
    if (period) params.append('period', period);
    if (asOf) params.append('asOf', asOf);
    return request<BenchmarkComparisonResult>(`/api/v1/portfolios/${portfolioId}/benchmark-comparison?${params.toString()}`);
  },
};

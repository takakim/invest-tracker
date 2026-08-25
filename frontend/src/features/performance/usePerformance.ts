import { useQuery } from '@tanstack/react-query';
import { performanceApi } from '../../api/performance';
import type { PerformanceResult } from '../../types';

export const usePerformance = (portfolioId: string | undefined) =>
  useQuery<PerformanceResult>({
    queryKey: ['performance', portfolioId],
    queryFn: () => performanceApi.get(portfolioId!),
    enabled: !!portfolioId,
    staleTime: 30_000,
  });

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { rebalancingApi } from '../../api';
import type { TargetAllocationPlanInput } from '../../types';

export function useTargetAllocation(portfolioId: string) {
  return useQuery({
    queryKey: ['target-allocation', portfolioId],
    queryFn: () => rebalancingApi.getTargetAllocation(portfolioId),
    enabled: Boolean(portfolioId),
  });
}

export function useSaveTargetAllocation(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: TargetAllocationPlanInput) => rebalancingApi.saveTargetAllocation(portfolioId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['target-allocation', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['rebalancing-analysis', portfolioId] });
    },
  });
}

export function useDeleteTargetAllocation(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => rebalancingApi.deleteTargetAllocation(portfolioId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['target-allocation', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['rebalancing-analysis', portfolioId] });
    },
  });
}

export function useRebalancingAnalysis(portfolioId: string, cashInjection?: number) {
  return useQuery({
    queryKey: ['rebalancing-analysis', portfolioId, cashInjection],
    queryFn: () => rebalancingApi.getRebalancingAnalysis(portfolioId, cashInjection),
    enabled: Boolean(portfolioId),
  });
}

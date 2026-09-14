import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { corporateActionApi } from '../../api';
import type { CorporateActionStatus, ApplyCorporateActionInput } from '../../types';

export function useCorporateActions(portfolioId: string, status?: CorporateActionStatus) {
  return useQuery({
    queryKey: ['corporate-actions', portfolioId, status],
    queryFn: () => corporateActionApi.list(portfolioId, status),
    enabled: Boolean(portfolioId),
  });
}

export function useScanCorporateActions(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => corporateActionApi.scan(portfolioId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['corporate-actions', portfolioId] });
    },
  });
}

export function useApplyCorporateAction(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ actionId, input }: { actionId: string; input: ApplyCorporateActionInput }) =>
      corporateActionApi.apply(portfolioId, actionId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['corporate-actions', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['positions', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['portfolio-history', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['cash-flow-analytics', portfolioId] });
    },
  });
}

export function useDismissCorporateAction(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (actionId: string) => corporateActionApi.dismiss(portfolioId, actionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['corporate-actions', portfolioId] });
    },
  });
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { portfolioApi } from '../../api';
import type { Portfolio, PortfolioCreateInput } from '../../types';

export const PORTFOLIO_QUERY_KEYS = {
  all: ['portfolios'] as const,
  detail: (id: string) => ['portfolios', id] as const,
};

export function usePortfoliosList() {
  return useQuery({
    queryKey: PORTFOLIO_QUERY_KEYS.all,
    queryFn: portfolioApi.list,
  });
}

export function usePortfolio(id: string) {
  return useQuery({
    queryKey: PORTFOLIO_QUERY_KEYS.detail(id),
    queryFn: () => portfolioApi.get(id),
    enabled: Boolean(id),
  });
}

export function useCreatePortfolio() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: PortfolioCreateInput) => portfolioApi.create(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PORTFOLIO_QUERY_KEYS.all });
    },
  });
}

export function useUpdatePortfolio() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: PortfolioCreateInput }) =>
      portfolioApi.update(id, input),
    onSuccess: (updated: Portfolio) => {
      queryClient.invalidateQueries({ queryKey: PORTFOLIO_QUERY_KEYS.all });
      queryClient.invalidateQueries({ queryKey: PORTFOLIO_QUERY_KEYS.detail(updated.id) });
    },
  });
}

export function useArchivePortfolio() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => portfolioApi.archive(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: PORTFOLIO_QUERY_KEYS.all });
      queryClient.invalidateQueries({ queryKey: PORTFOLIO_QUERY_KEYS.detail(id) });
    },
  });
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { positionApi } from '../../api';
import type { Position, PositionCreateInput, PositionUpdateInput } from '../../types';

export const POSITION_QUERY_KEYS = {
  accountList: (portfolioId: string, accountId: string) =>
    ['positions', portfolioId, accountId] as const,
  portfolioList: (portfolioId: string) => ['positions', 'portfolio', portfolioId] as const,
  detail: (portfolioId: string, accountId: string, positionId: string) =>
    ['positions', portfolioId, accountId, positionId] as const,
};

export function usePositionsList(portfolioId: string, accountId: string) {
  return useQuery({
    queryKey: POSITION_QUERY_KEYS.accountList(portfolioId, accountId),
    queryFn: () => positionApi.list(portfolioId, accountId),
    enabled: Boolean(portfolioId && accountId),
  });
}

export function usePortfolioPositionsList(portfolioId: string) {
  return useQuery({
    queryKey: POSITION_QUERY_KEYS.portfolioList(portfolioId),
    queryFn: () => positionApi.listPortfolio(portfolioId),
    enabled: Boolean(portfolioId),
  });
}

export function useCreatePosition(portfolioId: string, accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: PositionCreateInput) => positionApi.create(portfolioId, accountId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: POSITION_QUERY_KEYS.accountList(portfolioId, accountId),
      });
      queryClient.invalidateQueries({
        queryKey: POSITION_QUERY_KEYS.portfolioList(portfolioId),
      });
    },
  });
}

export function useUpdatePosition(portfolioId: string, accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      positionId,
      input,
    }: {
      positionId: string;
      input: PositionUpdateInput;
    }) => positionApi.update(portfolioId, accountId, positionId, input),
    onSuccess: (updated: Position) => {
      queryClient.invalidateQueries({
        queryKey: POSITION_QUERY_KEYS.accountList(portfolioId, accountId),
      });
      queryClient.invalidateQueries({
        queryKey: POSITION_QUERY_KEYS.portfolioList(portfolioId),
      });
      queryClient.invalidateQueries({
        queryKey: POSITION_QUERY_KEYS.detail(portfolioId, accountId, updated.id),
      });
    },
  });
}

export function useArchivePosition(portfolioId: string, accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (positionId: string) => positionApi.archive(portfolioId, accountId, positionId),
    onSuccess: (_, positionId) => {
      queryClient.invalidateQueries({
        queryKey: POSITION_QUERY_KEYS.accountList(portfolioId, accountId),
      });
      queryClient.invalidateQueries({
        queryKey: POSITION_QUERY_KEYS.portfolioList(portfolioId),
      });
      queryClient.invalidateQueries({
        queryKey: POSITION_QUERY_KEYS.detail(portfolioId, accountId, positionId),
      });
    },
  });
}

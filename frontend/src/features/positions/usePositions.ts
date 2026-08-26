import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { positionApi } from '../../api';
import type { Position, PositionCreateInput, PositionUpdateInput } from '../../types';

export const POSITION_QUERY_KEYS = {
  accountList: (portfolioId: string, accountId: string) =>
    ['positions', portfolioId, accountId] as const,
  portfolioList: (portfolioId: string) => ['positions', 'portfolio', portfolioId] as const,
  detail: (portfolioId: string, accountId: string, positionId: string) =>
    ['positions', portfolioId, accountId, positionId] as const,
  lots: (portfolioId: string, accountId: string, positionId: string) =>
    ['positions', portfolioId, accountId, positionId, 'lots'] as const,
};

export function usePositionLots(portfolioId: string, accountId: string, positionId: string) {
  return useQuery({
    queryKey: POSITION_QUERY_KEYS.lots(portfolioId, accountId, positionId),
    queryFn: () => positionApi.getLots(portfolioId, accountId, positionId),
    enabled: Boolean(portfolioId && accountId && positionId),
  });
}

export function useRecalculatePositions(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => positionApi.recalculate(portfolioId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['positions'],
      });
    },
  });
}

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
      queryClient.invalidateQueries({ queryKey: ['analytics', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['portfolios', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['performance', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['benchmark', portfolioId] });
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
      queryClient.invalidateQueries({ queryKey: ['analytics', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['portfolios', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['performance', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['benchmark', portfolioId] });
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
      queryClient.invalidateQueries({ queryKey: ['analytics', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['portfolios', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['performance', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['benchmark', portfolioId] });
    },
  });
}


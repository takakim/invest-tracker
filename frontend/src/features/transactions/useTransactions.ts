import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { transactionApi } from '../../api';
import type {
  Transaction,
  TransactionCreateInput,
  TransactionCorrectInput,
  TransactionUpdateInput,
  TransactionType,
} from '../../types';
import { POSITION_QUERY_KEYS } from '../positions/usePositions';

export const TRANSACTION_QUERY_KEYS = {
  accountList: (portfolioId: string, accountId: string, typeFilter?: TransactionType) =>
    ['transactions', portfolioId, accountId, typeFilter ?? 'ALL'] as const,
  portfolioList: (portfolioId: string) => ['transactions', 'portfolio', portfolioId] as const,
  detail: (portfolioId: string, accountId: string, transactionId: string) =>
    ['transactions', portfolioId, accountId, transactionId] as const,
};

export function useTransactionsList(
  portfolioId: string,
  accountId: string,
  typeFilter?: TransactionType,
) {
  return useQuery({
    queryKey: TRANSACTION_QUERY_KEYS.accountList(portfolioId, accountId, typeFilter),
    queryFn: () => transactionApi.list(portfolioId, accountId, typeFilter),
    enabled: Boolean(portfolioId && accountId),
  });
}

export function usePortfolioTransactionsList(portfolioId: string) {
  return useQuery({
    queryKey: TRANSACTION_QUERY_KEYS.portfolioList(portfolioId),
    queryFn: () => transactionApi.listPortfolio(portfolioId),
    enabled: Boolean(portfolioId),
  });
}

export function useCreateTransaction(portfolioId: string, accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: TransactionCreateInput) => transactionApi.record(portfolioId, accountId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['transactions'],
      });
      // Invalidate positions since trades auto-update position quantities
      queryClient.invalidateQueries({
        queryKey: ['positions'],
      });
    },
  });
}

export function useUpdateTransaction(portfolioId: string, accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      transactionId,
      input,
    }: {
      transactionId: string;
      input: TransactionUpdateInput;
    }) => transactionApi.update(portfolioId, accountId, transactionId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['positions'] });
      queryClient.invalidateQueries({ queryKey: ['portfolios'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
      queryClient.invalidateQueries({ queryKey: ['history'] });
      queryClient.invalidateQueries({ queryKey: ['performance'] });
      queryClient.invalidateQueries({ queryKey: ['dividends'] });
      queryClient.invalidateQueries({ queryKey: ['rebalancing'] });
    },
  });
}

export function useCorrectTransaction(portfolioId: string, accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      transactionId,
      input,
    }: {
      transactionId: string;
      input: TransactionCorrectInput;
    }) => transactionApi.correct(portfolioId, accountId, transactionId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['transactions'],
      });
      queryClient.invalidateQueries({
        queryKey: ['positions'],
      });
    },
  });
}

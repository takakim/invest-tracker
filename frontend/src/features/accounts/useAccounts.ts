import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountApi } from '../../api';
import type { Account, AccountCreateInput } from '../../types';

export const ACCOUNT_QUERY_KEYS = {
  list: (portfolioId: string) => ['accounts', portfolioId] as const,
  detail: (portfolioId: string, accountId: string) =>
    ['accounts', portfolioId, accountId] as const,
};

export function useAccountsList(portfolioId: string) {
  return useQuery({
    queryKey: ACCOUNT_QUERY_KEYS.list(portfolioId),
    queryFn: () => accountApi.list(portfolioId),
    enabled: Boolean(portfolioId),
  });
}

export function useAccount(portfolioId: string, accountId: string) {
  return useQuery({
    queryKey: ACCOUNT_QUERY_KEYS.detail(portfolioId, accountId),
    queryFn: () => accountApi.get(portfolioId, accountId),
    enabled: Boolean(portfolioId && accountId),
  });
}

export function useCreateAccount(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: AccountCreateInput) => accountApi.create(portfolioId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ACCOUNT_QUERY_KEYS.list(portfolioId) });
    },
  });
}

export function useUpdateAccount(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      accountId,
      input,
    }: {
      accountId: string;
      input: AccountCreateInput;
    }) => accountApi.update(portfolioId, accountId, input),
    onSuccess: (updated: Account) => {
      queryClient.invalidateQueries({ queryKey: ACCOUNT_QUERY_KEYS.list(portfolioId) });
      queryClient.invalidateQueries({
        queryKey: ACCOUNT_QUERY_KEYS.detail(portfolioId, updated.id),
      });
    },
  });
}

export function useArchiveAccount(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (accountId: string) => accountApi.archive(portfolioId, accountId),
    onSuccess: (_, accountId) => {
      queryClient.invalidateQueries({ queryKey: ACCOUNT_QUERY_KEYS.list(portfolioId) });
      queryClient.invalidateQueries({
        queryKey: ACCOUNT_QUERY_KEYS.detail(portfolioId, accountId),
      });
    },
  });
}

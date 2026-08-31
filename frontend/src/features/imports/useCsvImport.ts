import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { importApi } from '../../api';
import type { CsvImportInput } from '../../types';

export function usePreviewCsvImport(portfolioId: string, accountId: string) {
  return useMutation({
    mutationFn: (input: CsvImportInput) => importApi.preview(portfolioId, accountId, input),
  });
}

export function useExecuteCsvImport(portfolioId: string, accountId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CsvImportInput) => importApi.execute(portfolioId, accountId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['positions'] });
      queryClient.invalidateQueries({ queryKey: ['instruments'] });
      queryClient.invalidateQueries({ queryKey: ['imports', portfolioId, accountId] });
    },
  });
}

export function useSupportedBrokers() {
  return useQuery({
    queryKey: ['supportedBrokers'],
    queryFn: () => importApi.getSupportedBrokers(),
    staleTime: 60 * 60 * 1000,
  });
}

export function useDetectBroker() {
  return useMutation({
    mutationFn: (csvContent: string) => importApi.detectBroker(csvContent),
  });
}

export function useImportBatchesList(portfolioId: string, accountId: string) {
  return useQuery({
    queryKey: ['imports', portfolioId, accountId],
    queryFn: () => importApi.list(portfolioId, accountId),
    enabled: Boolean(portfolioId && accountId),
  });
}

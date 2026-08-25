import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { marketApi } from '../../api';
import { MarketPriceOverrideRequest } from '../../types';

export function useLatestQuote(instrumentId?: string, asOf?: string) {
  return useQuery({
    queryKey: ['quotes', 'latest', instrumentId, asOf],
    queryFn: () => marketApi.getLatestQuote(instrumentId!, asOf),
    enabled: !!instrumentId,
    staleTime: 60 * 1000,
  });
}

export function usePriceHistory(instrumentId?: string, from?: string, to?: string) {
  return useQuery({
    queryKey: ['quotes', 'history', instrumentId, from, to],
    queryFn: () => marketApi.getPriceHistory(instrumentId!, from, to),
    enabled: !!instrumentId,
  });
}

export function useRecordPriceOverride(instrumentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: MarketPriceOverrideRequest) => marketApi.recordPriceOverride(instrumentId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quotes', 'latest', instrumentId] });
      queryClient.invalidateQueries({ queryKey: ['quotes', 'history', instrumentId] });
      queryClient.invalidateQueries({ queryKey: ['performance'] });
    },
  });
}

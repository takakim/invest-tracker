import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fxApi } from '../../api';
import { FxRateOverrideRequest } from '../../types';

export function useFxRate(base?: string, quote?: string, asOf?: string) {
  return useQuery({
    queryKey: ['fx', 'rate', base, quote, asOf],
    queryFn: () => fxApi.getRate(base!, quote!, asOf),
    enabled: !!base && !!quote,
    staleTime: 60 * 1000,
  });
}

export function useFxRateHistory(base?: string, quote?: string, from?: string, to?: string) {
  return useQuery({
    queryKey: ['fx', 'history', base, quote, from, to],
    queryFn: () => fxApi.getRateHistory(base!, quote!, from, to),
    enabled: !!base && !!quote,
  });
}

export function useRecordFxOverride() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: FxRateOverrideRequest) => fxApi.recordRateOverride(data),
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['fx', 'rate', vars.baseCurrency, vars.quoteCurrency] });
      queryClient.invalidateQueries({ queryKey: ['fx', 'rate', vars.quoteCurrency, vars.baseCurrency] });
      queryClient.invalidateQueries({ queryKey: ['fx', 'history'] });
      queryClient.invalidateQueries({ queryKey: ['performance'] });
    },
  });
}

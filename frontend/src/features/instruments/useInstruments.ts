import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { instrumentApi } from '../../api';
import type { InstrumentCreateInput } from '../../types';

export const INSTRUMENT_QUERY_KEYS = {
  all: ['instruments'] as const,
  detail: (id: string) => ['instruments', id] as const,
};

export function useInstrumentsList() {
  return useQuery({
    queryKey: INSTRUMENT_QUERY_KEYS.all,
    queryFn: instrumentApi.list,
  });
}

export function useInstrument(id: string) {
  return useQuery({
    queryKey: INSTRUMENT_QUERY_KEYS.detail(id),
    queryFn: () => instrumentApi.get(id),
    enabled: Boolean(id),
  });
}

export function useCreateInstrument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: InstrumentCreateInput) => instrumentApi.create(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: INSTRUMENT_QUERY_KEYS.all });
    },
  });
}

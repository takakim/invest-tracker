import { useMutation, useQueryClient } from '@tanstack/react-query';
import { systemApi } from '../../api';

export function useResetDatabase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (confirmation: string = 'RESET') => systemApi.resetDatabase(confirmation),
    onSuccess: () => {
      queryClient.clear();
    },
  });
}

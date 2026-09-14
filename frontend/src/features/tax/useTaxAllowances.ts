import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { taxAllowanceApi } from '../../api';
import type { TaxRegime, TaxSettingsRequest } from '../../types';

export function useTaxReport(portfolioId: string, taxYear?: string, regime?: TaxRegime) {
  return useQuery({
    queryKey: ['tax-report', portfolioId, taxYear, regime],
    queryFn: () => taxAllowanceApi.getTaxReport(portfolioId, taxYear, regime),
    enabled: Boolean(portfolioId),
  });
}

export function useAvailableTaxYears(portfolioId: string) {
  return useQuery({
    queryKey: ['available-tax-years', portfolioId],
    queryFn: () => taxAllowanceApi.getAvailableTaxYears(portfolioId),
    enabled: Boolean(portfolioId),
  });
}

export function useUpdateTaxSettings(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: TaxSettingsRequest) =>
      taxAllowanceApi.updateSettings(portfolioId, settings),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tax-report', portfolioId] });
      queryClient.invalidateQueries({ queryKey: ['available-tax-years', portfolioId] });
    },
  });
}

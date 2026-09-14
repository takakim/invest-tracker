import { request } from './client';
import type {
  TaxReportResponse,
  AvailableTaxYearsResponse,
  TaxSettingsRequest,
  TaxSettingsResponse,
  TaxRegime,
} from '../types';

export const taxAllowanceApi = {
  getTaxReport: (
    portfolioId: string,
    taxYear?: string,
    regime?: TaxRegime
  ): Promise<TaxReportResponse> => {
    const params = new URLSearchParams();
    if (taxYear) params.append('taxYear', taxYear);
    if (regime) params.append('regime', regime);
    const query = params.toString() ? `?${params.toString()}` : '';
    return request<TaxReportResponse>(`/api/v1/portfolios/${portfolioId}/tax-allowances${query}`);
  },

  getAvailableTaxYears: (portfolioId: string): Promise<AvailableTaxYearsResponse> =>
    request<AvailableTaxYearsResponse>(`/api/v1/portfolios/${portfolioId}/tax-allowances/available-years`),

  updateSettings: (
    portfolioId: string,
    settings: TaxSettingsRequest
  ): Promise<TaxSettingsResponse> =>
    request<TaxSettingsResponse>(`/api/v1/portfolios/${portfolioId}/tax-allowances/settings`, {
      method: 'PUT',
      body: JSON.stringify(settings),
    }),
};

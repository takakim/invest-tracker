import { request } from './client';
import type {
  CorporateAction,
  CorporateActionStatus,
  ApplyCorporateActionInput,
  ScanCorporateActionsResponse,
  Transaction,
} from '../types';

export const corporateActionApi = {
  list: (portfolioId: string, status?: CorporateActionStatus): Promise<CorporateAction[]> => {
    const query = status ? `?status=${encodeURIComponent(status)}` : '';
    return request<CorporateAction[]>(`/api/v1/portfolios/${portfolioId}/corporate-actions${query}`);
  },

  scan: (portfolioId: string): Promise<ScanCorporateActionsResponse> =>
    request<ScanCorporateActionsResponse>(`/api/v1/portfolios/${portfolioId}/corporate-actions/scan`, {
      method: 'POST',
    }),

  apply: (
    portfolioId: string,
    actionId: string,
    input: ApplyCorporateActionInput
  ): Promise<CorporateAction> =>
    request<CorporateAction>(`/api/v1/portfolios/${portfolioId}/corporate-actions/${actionId}/apply`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  dismiss: (portfolioId: string, actionId: string): Promise<CorporateAction> =>
    request<CorporateAction>(`/api/v1/portfolios/${portfolioId}/corporate-actions/${actionId}/dismiss`, {
      method: 'POST',
    }),
};

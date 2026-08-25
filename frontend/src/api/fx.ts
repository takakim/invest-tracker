import { request } from './client';
import { FxRateQuote, FxRateOverrideRequest, FxObservation } from '../types';

export const fxApi = {
  getRate: (base: string, quote: string, asOf?: string): Promise<FxRateQuote> => {
    const params = new URLSearchParams({ base, quote });
    if (asOf) params.set('asOf', asOf);
    return request<FxRateQuote>(`/api/v1/currencies/rates?${params.toString()}`);
  },

  recordRateOverride: (data: FxRateOverrideRequest): Promise<FxObservation> =>
    request<FxObservation>('/api/v1/currencies/rates/override', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getRateHistory: (base: string, quote: string, from?: string, to?: string): Promise<FxObservation[]> => {
    const params = new URLSearchParams({ base, quote });
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    return request<FxObservation[]>(`/api/v1/currencies/rates/history?${params.toString()}`);
  },
};


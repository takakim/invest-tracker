import { request } from './client';
import { PriceQuote, MarketPriceOverrideRequest, MarketObservation } from '../types';

export const marketApi = {
  getLatestQuote: (instrumentId: string, asOf?: string): Promise<PriceQuote> => {
    const params = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
    return request<PriceQuote>(`/api/v1/instruments/${instrumentId}/quotes/latest${params}`);
  },

  recordPriceOverride: (instrumentId: string, data: MarketPriceOverrideRequest): Promise<MarketObservation> =>
    request<MarketObservation>(`/api/v1/instruments/${instrumentId}/quotes/override`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getPriceHistory: (instrumentId: string, from?: string, to?: string): Promise<MarketObservation[]> => {
    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    const query = params.toString() ? `?${params.toString()}` : '';
    return request<MarketObservation[]>(`/api/v1/instruments/${instrumentId}/quotes/history${query}`);
  },
};


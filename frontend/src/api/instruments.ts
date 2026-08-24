import { request } from './client';
import type { Instrument, InstrumentCreateInput } from '../types';

export const instrumentApi = {
  list: (): Promise<Instrument[]> => request<Instrument[]>('/api/v1/instruments'),

  get: (id: string): Promise<Instrument> =>
    request<Instrument>(`/api/v1/instruments/${id}`),

  create: (input: InstrumentCreateInput): Promise<Instrument> =>
    request<Instrument>('/api/v1/instruments', {
      method: 'POST',
      body: JSON.stringify(input),
    }),
};

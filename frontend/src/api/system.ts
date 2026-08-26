import { request } from './client';

export interface DatabaseResetResponse {
  message: string;
  timestamp: string;
}

export const systemApi = {
  resetDatabase: (confirmation: string = 'RESET'): Promise<DatabaseResetResponse> =>
    request<DatabaseResetResponse>('/api/v1/system/reset-database', {
      method: 'POST',
      body: JSON.stringify({ confirmation }),
    }),
};

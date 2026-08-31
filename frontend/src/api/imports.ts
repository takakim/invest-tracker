import { request } from './client';
import type {
  CsvImportPreview,
  ImportBatch,
  CsvImportInput,
  BrokerDetectionResponse,
} from '../types';

export const importApi = {
  getSupportedBrokers: (): Promise<string[]> =>
    request<string[]>('/api/v1/imports/supported-brokers'),

  detectBroker: (csvContent: string): Promise<BrokerDetectionResponse> =>
    request<BrokerDetectionResponse>('/api/v1/imports/detect-broker', {
      method: 'POST',
      body: JSON.stringify({ csvContent }),
    }),

  preview: (portfolioId: string, accountId: string, input: CsvImportInput): Promise<CsvImportPreview> =>
    request<CsvImportPreview>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}/imports/preview`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  execute: (portfolioId: string, accountId: string, input: CsvImportInput): Promise<ImportBatch> =>
    request<ImportBatch>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}/imports`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  list: (portfolioId: string, accountId: string): Promise<ImportBatch[]> =>
    request<ImportBatch[]>(`/api/v1/portfolios/${portfolioId}/accounts/${accountId}/imports`),
};

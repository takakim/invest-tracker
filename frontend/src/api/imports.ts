import { request } from './client';
import type {
  CsvImportPreview,
  ImportBatch,
  CsvImportInput,
} from '../types';

export const importApi = {
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

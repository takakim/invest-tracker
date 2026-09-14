import { request } from './client';
import type { AiStatus, HoldingAiEvaluation, PortfolioAiEvaluation } from '../types';

export async function getAiStatus(): Promise<AiStatus> {
  return request<AiStatus>('/api/v1/ai/status');
}

export async function evaluatePortfolio(portfolioId: string): Promise<PortfolioAiEvaluation> {
  return request<PortfolioAiEvaluation>(`/api/v1/portfolios/${portfolioId}/ai/evaluate`, {
    method: 'POST',
  });
}

export async function evaluateHolding(
  portfolioId: string,
  instrumentId: string
): Promise<HoldingAiEvaluation> {
  return request<HoldingAiEvaluation>(
    `/api/v1/portfolios/${portfolioId}/holdings/${instrumentId}/ai/evaluate`,
    {
      method: 'POST',
    }
  );
}

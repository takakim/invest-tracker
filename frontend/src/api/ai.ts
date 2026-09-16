import { request } from './client';
import type { AiConfigRequest, AiStatus, HoldingAiEvaluation, PortfolioAiEvaluation } from '../types';

export async function getAiStatus(): Promise<AiStatus> {
  return request<AiStatus>('/api/v1/ai/status');
}

export async function updateAiConfig(config: AiConfigRequest): Promise<AiStatus> {
  return request<AiStatus>('/api/v1/ai/config', {
    method: 'PUT',
    body: JSON.stringify(config),
  });
}


export async function evaluatePortfolio(portfolioId: string): Promise<PortfolioAiEvaluation> {
  return request<PortfolioAiEvaluation>(`/api/v1/portfolios/${portfolioId}/ai/evaluate`, {
    method: 'POST',
  });
}

export async function getLatestPortfolioEvaluation(
  portfolioId: string
): Promise<PortfolioAiEvaluation | null> {
  const resp = await fetch(`/api/v1/portfolios/${portfolioId}/ai/evaluation`);
  if (resp.status === 204) return null;
  if (!resp.ok) throw new Error(`Failed to fetch portfolio evaluation: ${resp.status}`);
  return resp.json() as Promise<PortfolioAiEvaluation>;
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

export async function getLatestHoldingEvaluation(
  portfolioId: string,
  instrumentId: string
): Promise<HoldingAiEvaluation | null> {
  const resp = await fetch(
    `/api/v1/portfolios/${portfolioId}/holdings/${instrumentId}/ai/evaluation`
  );
  if (resp.status === 204) return null;
  if (!resp.ok) throw new Error(`Failed to fetch holding evaluation: ${resp.status}`);
  return resp.json() as Promise<HoldingAiEvaluation>;
}

export async function getLatestHoldingEvaluations(
  portfolioId: string
): Promise<HoldingAiEvaluation[]> {
  return request<HoldingAiEvaluation[]>(`/api/v1/portfolios/${portfolioId}/holdings/ai/evaluations`);
}

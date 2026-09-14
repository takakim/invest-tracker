import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getAiStatus, evaluatePortfolio, evaluateHolding } from '../../api';
import type { AiStatus, HoldingAiEvaluation, PortfolioAiEvaluation } from '../../types';

export const AI_QUERY_KEYS = {
  status: ['ai', 'status'] as const,
  portfolioEvaluation: (portfolioId: string) => ['ai', 'portfolio-evaluation', portfolioId] as const,
  holdingEvaluation: (portfolioId: string, instrumentId: string) =>
    ['ai', 'holding-evaluation', portfolioId, instrumentId] as const,
};

export function useAiStatus() {
  return useQuery<AiStatus>({
    queryKey: AI_QUERY_KEYS.status,
    queryFn: getAiStatus,
    staleTime: 30000,
    refetchOnWindowFocus: false,
  });
}

export function useEvaluatePortfolio(portfolioId: string) {
  const queryClient = useQueryClient();

  return useMutation<PortfolioAiEvaluation, Error>({
    mutationFn: () => evaluatePortfolio(portfolioId),
    onSuccess: (data) => {
      queryClient.setQueryData(AI_QUERY_KEYS.portfolioEvaluation(portfolioId), data);
      // Also cache any included topHoldingEvaluations
      if (data.topHoldingEvaluations) {
        data.topHoldingEvaluations.forEach((holding) => {
          queryClient.setQueryData(
            AI_QUERY_KEYS.holdingEvaluation(portfolioId, holding.instrumentId),
            holding
          );
        });
      }
    },
  });
}

export function usePortfolioAiEvaluation(portfolioId: string) {
  return useQuery<PortfolioAiEvaluation | null>({
    queryKey: AI_QUERY_KEYS.portfolioEvaluation(portfolioId),
    queryFn: () => null, // Initialized via mutation or read from cache
    enabled: false,
    staleTime: Infinity,
  });
}

export function useEvaluateHolding(portfolioId: string) {
  const queryClient = useQueryClient();

  return useMutation<HoldingAiEvaluation, Error, string>({
    mutationFn: (instrumentId: string) => evaluateHolding(portfolioId, instrumentId),
    onSuccess: (data, instrumentId) => {
      queryClient.setQueryData(AI_QUERY_KEYS.holdingEvaluation(portfolioId, instrumentId), data);
    },
  });
}

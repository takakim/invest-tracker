import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getAiStatus,
  updateAiConfig,
  evaluatePortfolio,
  evaluateHolding,
  getLatestPortfolioEvaluation,
  getLatestHoldingEvaluation,
  getLatestHoldingEvaluations,
} from '../../api';
import type { AiConfigRequest, AiStatus, HoldingAiEvaluation, PortfolioAiEvaluation } from '../../types';


export const AI_QUERY_KEYS = {
  status: ['ai', 'status'] as const,
  portfolioEvaluation: (portfolioId: string) =>
    ['ai', 'portfolio-evaluation', portfolioId] as const,
  holdingEvaluation: (portfolioId: string, instrumentId: string) =>
    ['ai', 'holding-evaluation', portfolioId, instrumentId] as const,
  holdingEvaluations: (portfolioId: string) =>
    ['ai', 'holding-evaluations', portfolioId] as const,
};

export function useAiStatus() {
  return useQuery<AiStatus>({
    queryKey: AI_QUERY_KEYS.status,
    queryFn: getAiStatus,
    staleTime: 30000,
    refetchOnWindowFocus: false,
  });
}

/**
 * Fetches the latest persisted portfolio AI evaluation from the database (no LLM call).
 * Returns null if no evaluation has been run yet.
 */
export function useLatestPortfolioEvaluation(portfolioId: string) {
  return useQuery<PortfolioAiEvaluation | null>({
    queryKey: AI_QUERY_KEYS.portfolioEvaluation(portfolioId),
    queryFn: () => getLatestPortfolioEvaluation(portfolioId),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    enabled: !!portfolioId,
  });
}

/**
 * Triggers a new portfolio-level AI evaluation (LLM call).
 * Updates the cached portfolio evaluation on success.
 */
export function useEvaluatePortfolio(portfolioId: string) {
  const queryClient = useQueryClient();

  return useMutation<PortfolioAiEvaluation, Error>({
    mutationFn: () => evaluatePortfolio(portfolioId),
    onSuccess: (data) => {
      queryClient.setQueryData(AI_QUERY_KEYS.portfolioEvaluation(portfolioId), data);
      // Also cache any included topHoldingEvaluations
      if (data.topHoldingEvaluations && data.topHoldingEvaluations.length > 0) {
        data.topHoldingEvaluations.forEach((holding) => {
          queryClient.setQueryData(
            AI_QUERY_KEYS.holdingEvaluation(portfolioId, holding.instrumentId),
            holding
          );
        });
        queryClient.setQueryData<HoldingAiEvaluation[]>(
          AI_QUERY_KEYS.holdingEvaluations(portfolioId),
          (old = []) => {
            const map = new Map<string, HoldingAiEvaluation>();
            old.forEach((h) => map.set(h.instrumentId, h));
            data.topHoldingEvaluations.forEach((h) => map.set(h.instrumentId, h));
            return Array.from(map.values());
          }
        );
        queryClient.invalidateQueries({
          queryKey: AI_QUERY_KEYS.holdingEvaluations(portfolioId),
        });
      }
    },
  });
}

/**
 * Fetches the latest persisted holding AI evaluation for a specific instrument (no LLM call).
 * Returns null if no evaluation has been run yet.
 */
export function useLatestHoldingEvaluation(portfolioId: string, instrumentId: string) {
  return useQuery<HoldingAiEvaluation | null>({
    queryKey: AI_QUERY_KEYS.holdingEvaluation(portfolioId, instrumentId),
    queryFn: () => getLatestHoldingEvaluation(portfolioId, instrumentId),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    enabled: !!portfolioId && !!instrumentId,
  });
}

/**
 * Fetches all persisted holding AI evaluations for the portfolio (no LLM call).
 */
export function useLatestHoldingEvaluations(portfolioId: string) {
  return useQuery<HoldingAiEvaluation[]>({
    queryKey: AI_QUERY_KEYS.holdingEvaluations(portfolioId),
    queryFn: () => getLatestHoldingEvaluations(portfolioId),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    enabled: !!portfolioId,
  });
}

/**
 * Triggers a new holding-level AI evaluation for a specific instrument (LLM call).
 * Updates the cached holding evaluation on success.
 */
export function useEvaluateHolding(portfolioId: string) {
  const queryClient = useQueryClient();

  return useMutation<HoldingAiEvaluation, Error, string>({
    mutationFn: (instrumentId: string) => evaluateHolding(portfolioId, instrumentId),
    onSuccess: (data, instrumentId) => {
      // 1. Immediately cache the evaluation for this specific holding
      queryClient.setQueryData(AI_QUERY_KEYS.holdingEvaluation(portfolioId, instrumentId), data);

      // 2. Immediately update the portfolio-wide holding evaluations list in cache
      queryClient.setQueryData<HoldingAiEvaluation[]>(
        AI_QUERY_KEYS.holdingEvaluations(portfolioId),
        (old = []) => {
          const filtered = old.filter((h) => h.instrumentId !== instrumentId);
          return [...filtered, data];
        }
      );

      // 3. Invalidate holding evaluations list to ensure synchronization with DB
      queryClient.invalidateQueries({
        queryKey: AI_QUERY_KEYS.holdingEvaluations(portfolioId),
      });
    },
  });
}

/**
 * Updates AI settings (such as request timeout) dynamically.
 */
export function useUpdateAiConfig() {
  const queryClient = useQueryClient();

  return useMutation<AiStatus, Error, AiConfigRequest>({
    mutationFn: (config: AiConfigRequest) => updateAiConfig(config),
    onSuccess: (data) => {
      queryClient.setQueryData(AI_QUERY_KEYS.status, data);
    },
  });
}


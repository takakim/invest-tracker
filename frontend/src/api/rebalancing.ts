import { request } from './client';
import type { TargetAllocationPlan, TargetAllocationPlanInput, RebalanceAnalysis } from '../types';

export const rebalancingApi = {
  getTargetAllocation: async (portfolioId: string): Promise<TargetAllocationPlan | null> => {
    try {
      return await request<TargetAllocationPlan>(`/api/v1/portfolios/${portfolioId}/target-allocation`);
    } catch (err: any) {
      if (err?.status === 404) {
        return null;
      }
      throw err;
    }
  },

  saveTargetAllocation: (portfolioId: string, input: TargetAllocationPlanInput): Promise<TargetAllocationPlan> => {
    return request<TargetAllocationPlan>(`/api/v1/portfolios/${portfolioId}/target-allocation`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  },

  deleteTargetAllocation: (portfolioId: string): Promise<void> => {
    return request<void>(`/api/v1/portfolios/${portfolioId}/target-allocation`, {
      method: 'DELETE',
    });
  },

  getRebalancingAnalysis: (portfolioId: string, cashInjection?: number, asOf?: string): Promise<RebalanceAnalysis> => {
    const params = new URLSearchParams();
    if (cashInjection != null && cashInjection > 0) {
      params.append('cashInjection', cashInjection.toString());
    }
    if (asOf) {
      params.append('asOf', asOf);
    }
    const query = params.toString() ? `?${params.toString()}` : '';
    return request<RebalanceAnalysis>(`/api/v1/portfolios/${portfolioId}/rebalancing${query}`);
  },
};

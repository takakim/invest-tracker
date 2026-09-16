import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import * as aiApi from '../api/ai';
import { AiStatusIndicator } from '../features/ai/AiStatusIndicator';
import { HoldingAiEvaluationModal, getStanceChipProps, getRiskLevelColor } from '../features/ai/HoldingAiEvaluationModal';
import { PortfolioAiEvaluationCard } from '../features/ai/PortfolioAiEvaluationCard';
import { AiSettingsModal } from '../features/ai/AiSettingsModal';
import { isEvaluationStale, getStalenessDays } from '../features/ai';
import type { AiStatus, HoldingAiEvaluation, PortfolioAiEvaluation } from '../types';

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
}

function renderWithProviders(ui: React.ReactElement) {
  const testQueryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={testQueryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter>{ui}</MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

const mockAiStatusConnected: AiStatus = {
  enabled: true,
  connected: true,
  provider: 'LM_STUDIO',
  baseUrl: 'http://localhost:1234',
  configuredModel: 'gemma4-12b',
  availableModels: ['gemma4-12b', 'qwen2.5-7b'],
  timeoutSeconds: 60,
};

const mockAiStatusOffline: AiStatus = {
  enabled: true,
  connected: false,
  provider: 'LM_STUDIO',
  baseUrl: 'http://localhost:1234',
  configuredModel: 'gemma4-12b',
  availableModels: [],
  errorMessage: 'Connection refused',
  timeoutSeconds: 60,
};


const mockHoldingEvaluation: HoldingAiEvaluation = {
  instrumentId: 'inst-1',
  symbol: 'NVDA',
  name: 'NVIDIA Corporation',
  assetClass: 'EQUITY',
  quantity: 50,
  currentPrice: 130.5,
  averageCostBasis: 85.0,
  unrealizedGainLoss: 2275.0,
  unrealizedGainLossPercentage: 53.53,
  portfolioWeightPercentage: 18.25,
  stance: 'ACCUMULATE',
  riskScore: 6,
  riskLevel: 'MODERATE',
  executiveSummary: 'Dominant leader in accelerated computing and AI infrastructure with exceptional pricing power.',
  strengths: [
    'Massive moat in CUDA software ecosystem',
    'Gross margins exceeding 70% with high enterprise demand',
  ],
  risks: [
    'Customer concentration among hyperscalers',
    'Potential export restrictions and cyclical semiconductor pauses',
  ],
  holdingVsSellingTradeoff: 'Trimming triggers capital gains tax; holding captures AI secular supercycle.',
  fundamentalMetrics: {
    peRatio: 38.5,
    forwardPe: 28.2,
    pegRatio: 1.45,
    priceToBook: 22.1,
    dividendYield: 0.0003,
    debtToEquity: 0.42,
    returnOnEquity: 0.58,
    fiftyTwoWeekHigh: 140.76,
    fiftyTwoWeekLow: 75.6,
    marketCap: 3200000000000,
    expenseRatio: null,
    assetClass: 'EQUITY',
    currency: 'USD',
  },
  modelUsed: 'gemma4-12b',
  evaluatedAt: '2026-09-14T22:00:00Z',
};

const mockPortfolioEvaluation: PortfolioAiEvaluation = {
  portfolioId: 'port-1',
  portfolioName: 'Tech & Dividend Portfolio',
  baseCurrency: 'USD',
  overallRiskScore: 5,
  overallRiskLevel: 'MODERATE',
  executiveSummary: 'Well-positioned growth portfolio with healthy cash buffer and resilient dividend foundation.',
  diversificationAssessment: 'Portfolio displays strong technology concentration balanced by global dividend funds.',
  concentrationRisks: [
    'Top 3 tech holdings account for 45% of equities',
    'US dollar currency exposure exceeds 75%',
  ],
  taxAndLocationOptimization: [
    'Consider holding higher-yield dividend positions in tax-sheltered wrappers',
    'Leverage remaining capital gains tax annual exempt allowance',
  ],
  topRecommendations: [
    'Gradually diversify into international value funds',
    'Establish trailing stop-losses for mega-cap winners',
  ],
  macroStressScenarios: [
    '100 bps interest rate hike: estimated portfolio impact -4.5%',
    'Tech multiple compression: estimated drawdown -9.2%',
  ],
  topHoldingEvaluations: [mockHoldingEvaluation],
  modelUsed: 'gemma4-12b',
  evaluatedAt: '2026-09-14T22:05:00Z',
};

describe('AI Intelligence Feature Suite', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(aiApi, 'getLatestPortfolioEvaluation').mockResolvedValue(null);
    vi.spyOn(aiApi, 'getLatestHoldingEvaluation').mockResolvedValue(null);
  });

  describe('Utility Functions', () => {
    it('returns appropriate chip props for all AI stances', () => {
      expect(getStanceChipProps('STRONG_BUY').label).toBe('STRONG BUY');
      expect(getStanceChipProps('ACCUMULATE').label).toBe('ACCUMULATE');
      expect(getStanceChipProps('HOLD').label).toBe('HOLD');
      expect(getStanceChipProps('TRIM').label).toBe('TRIM');
      expect(getStanceChipProps('SELL').label).toBe('SELL');
    });

    it('returns appropriate risk colors across risk scores and levels', () => {
      expect(getRiskLevelColor('LOW', 2)).toBe('#16a34a');
      expect(getRiskLevelColor('MODERATE', 5)).toBe('#d97706');
      expect(getRiskLevelColor('HIGH', 7)).toBe('#ea580c');
      expect(getRiskLevelColor('VERY_HIGH', 10)).toBe('#dc2626');
    });

    it('determines evaluation staleness correctly for <=7d and >7d', () => {
      const now = new Date();
      const freshDate = new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000).toISOString();
      const staleDate = new Date(now.getTime() - 8 * 24 * 60 * 60 * 1000).toISOString();

      expect(isEvaluationStale(freshDate)).toBe(false);
      expect(isEvaluationStale(staleDate)).toBe(true);
      expect(isEvaluationStale(undefined)).toBe(false);
      expect(isEvaluationStale(freshDate, true)).toBe(true);
      expect(isEvaluationStale(staleDate, false)).toBe(true);
      expect(getStalenessDays(staleDate)).toBeGreaterThanOrEqual(8);
      expect(getStalenessDays(freshDate)).toBe(2);
      expect(getStalenessDays(undefined)).toBe(0);
    });
  });

  describe('AiStatusIndicator', () => {
    it('renders connected chip with provider and model name when provider is online', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);

      renderWithProviders(<AiStatusIndicator />);

      await waitFor(() => {
        expect(screen.getByText('LM STUDIO: gemma4-12b')).toBeInTheDocument();
      });
    });

    it('renders provider name and model for OpenAI provider', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue({
        enabled: true,
        connected: true,
        provider: 'OPENAI',
        baseUrl: 'https://api.openai.com',
        configuredModel: 'gpt-4o-mini',
        availableModels: ['gpt-4o-mini', 'gpt-4o'],
      });

      renderWithProviders(<AiStatusIndicator />);

      await waitFor(() => {
        expect(screen.getByText('OPENAI: gpt-4o-mini')).toBeInTheDocument();
      });
    });

    it('renders offline chip with provider name when provider is disconnected', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusOffline);

      renderWithProviders(<AiStatusIndicator />);

      await waitFor(() => {
        expect(screen.getByText('LM STUDIO: Offline')).toBeInTheDocument();
      });
    });

    it('opens AiSettingsModal when clicking status chip', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);

      renderWithProviders(<AiStatusIndicator />);

      await waitFor(() => {
        expect(screen.getByText('LM STUDIO: gemma4-12b')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByTestId('ai-status-chip'));

      await waitFor(() => {
        expect(screen.getByTestId('ai-settings-modal')).toBeInTheDocument();
        expect(screen.getByText('AI Gateway Settings')).toBeInTheDocument();
      });
    });
  });

  describe('HoldingAiEvaluationModal', () => {
    it('renders evaluation data with stance, risk score, executive summary, strengths and metrics', async () => {
      vi.spyOn(aiApi, 'evaluateHolding').mockResolvedValue(mockHoldingEvaluation);

      renderWithProviders(
        <HoldingAiEvaluationModal
          open={true}
          onClose={vi.fn()}
          portfolioId="port-1"
          instrumentId="inst-1"
          symbol="NVDA"
          instrumentName="NVIDIA Corporation"
          initialData={mockHoldingEvaluation}
        />
      );

      expect(screen.getByText(/NVDA — NVIDIA Corporation/i)).toBeInTheDocument();
      expect(screen.getByText('ACCUMULATE')).toBeInTheDocument();
      expect(screen.getByText(/Dominant leader in accelerated computing/i)).toBeInTheDocument();
      expect(screen.getByText(/Massive moat in CUDA software ecosystem/i)).toBeInTheDocument();
      expect(screen.getByText(/Customer concentration among hyperscalers/i)).toBeInTheDocument();
      expect(screen.getByText(/P\/E \(TTM\)/i)).toBeInTheDocument();
      expect(screen.getByText('38.5')).toBeInTheDocument();
      expect(screen.getByText(/Forward P\/E/i)).toBeInTheDocument();
      expect(screen.getByText('28.2')).toBeInTheDocument();
    });

    it('renders initial prompt card when opened without initialData and no cache, and only triggers on button click', async () => {
      const evaluateSpy = vi.spyOn(aiApi, 'evaluateHolding').mockResolvedValue(mockHoldingEvaluation);

      renderWithProviders(
        <HoldingAiEvaluationModal
          open={true}
          onClose={vi.fn()}
          portfolioId="port-1"
          instrumentId="inst-1"
          symbol="NVDA"
          instrumentName="NVIDIA Corporation"
        />
      );

      await waitFor(() => {
        expect(screen.getByText('Fundamental & Risk Evaluation')).toBeInTheDocument();
        expect(screen.getByTestId('evaluate-holding-btn')).toBeInTheDocument();
      });

      // Does NOT auto-trigger LLM evaluation
      expect(evaluateSpy).not.toHaveBeenCalled();

      // Trigger manually
      fireEvent.click(screen.getByTestId('evaluate-holding-btn'));

      await waitFor(() => {
        expect(evaluateSpy).toHaveBeenCalledWith('port-1', 'inst-1');
        expect(screen.getByText('ACCUMULATE')).toBeInTheDocument();
      });
    });

    it('renders cached holding evaluation immediately when available', async () => {
      vi.spyOn(aiApi, 'getLatestHoldingEvaluation').mockResolvedValue(mockHoldingEvaluation);
      const evaluateSpy = vi.spyOn(aiApi, 'evaluateHolding');

      renderWithProviders(
        <HoldingAiEvaluationModal
          open={true}
          onClose={vi.fn()}
          portfolioId="port-1"
          instrumentId="inst-1"
          symbol="NVDA"
          instrumentName="NVIDIA Corporation"
        />
      );

      await waitFor(() => {
        expect(screen.getByText('ACCUMULATE')).toBeInTheDocument();
        expect(screen.getByText(/Dominant leader in accelerated computing/i)).toBeInTheDocument();
      });
      // Cached evaluation is shown without auto-triggering a new evaluation call
      expect(evaluateSpy).not.toHaveBeenCalled();
    });

    it('handles error state gracefully with retry and adjust timeout buttons', async () => {
      vi.spyOn(aiApi, 'evaluateHolding').mockRejectedValue(new Error('LM Studio timeout'));

      renderWithProviders(
        <HoldingAiEvaluationModal
          open={true}
          onClose={vi.fn()}
          portfolioId="port-1"
          instrumentId="inst-1"
          symbol="NVDA"
          instrumentName="NVIDIA Corporation"
        />
      );

      await waitFor(() => {
        expect(screen.getByTestId('evaluate-holding-btn')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByTestId('evaluate-holding-btn'));

      await waitFor(() => {
        expect(screen.getByText(/Failed to evaluate holding/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Retry/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Adjust Timeout/i })).toBeInTheDocument();
      });

      // Clicking Adjust Timeout opens AiSettingsModal
      fireEvent.click(screen.getByRole('button', { name: /Adjust Timeout/i }));
      await waitFor(() => {
        expect(screen.getByTestId('ai-settings-modal')).toBeInTheDocument();
      });
    });

    it('opens AI settings modal from AI Settings button in dialog actions', async () => {
      renderWithProviders(
        <HoldingAiEvaluationModal
          open={true}
          onClose={vi.fn()}
          portfolioId="port-1"
          instrumentId="inst-1"
          symbol="NVDA"
          instrumentName="NVIDIA Corporation"
          initialData={mockHoldingEvaluation}
        />
      );

      fireEvent.click(screen.getByTestId('holding-ai-settings-btn'));
      await waitFor(() => {
        expect(screen.getByTestId('ai-settings-modal')).toBeInTheDocument();
      });
    });

    it('renders staleness alert and chip when evaluation is older than 7 days', async () => {
      const staleEvaluation: HoldingAiEvaluation = {
        ...mockHoldingEvaluation,
        evaluatedAt: new Date(Date.now() - 9 * 24 * 60 * 60 * 1000).toISOString(),
        isStale: true,
      };

      renderWithProviders(
        <HoldingAiEvaluationModal
          open={true}
          onClose={vi.fn()}
          portfolioId="port-1"
          instrumentId="inst-1"
          symbol="NVDA"
          instrumentName="NVIDIA Corporation"
          initialData={staleEvaluation}
        />
      );

      expect(screen.getByText('Stale (>7d)')).toBeInTheDocument();
      expect(screen.getByText(/Outdated Evaluation \(>7 days\)/i)).toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: /Re-evaluate/i }).length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('PortfolioAiEvaluationCard', () => {
    it('renders initial CTA prompt and triggers analysis when button clicked', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);
      const evaluateSpy = vi.spyOn(aiApi, 'evaluatePortfolio').mockResolvedValue(mockPortfolioEvaluation);

      renderWithProviders(
        <PortfolioAiEvaluationCard
          portfolioId="port-1"
          portfolioName="Tech & Dividend Portfolio"
          baseCurrency="USD"
        />
      );

      await waitFor(() => {
        expect(screen.getByText(/Comprehensive Portfolio Intelligence/i)).toBeInTheDocument();
      });

      const analyzeBtn = screen.getByRole('button', { name: /Analyze Tech & Dividend Portfolio/i });
      fireEvent.click(analyzeBtn);

      await waitFor(() => {
        expect(evaluateSpy).toHaveBeenCalledWith('port-1');
        expect(screen.getByText('Portfolio Executive Thesis')).toBeInTheDocument();
        expect(screen.getByText(/Well-positioned growth portfolio with healthy cash buffer/i)).toBeInTheDocument();
        expect(screen.getByText(/Diversification Assessment/i)).toBeInTheDocument();
        expect(screen.getByText(/Concentration Risks/i)).toBeInTheDocument();
        expect(screen.getByText(/Tax Wrapper & Asset Location Optimization/i)).toBeInTheDocument();
        expect(screen.getByText(/Macro Stress Scenarios/i)).toBeInTheDocument();
        expect(screen.getByText(/Strategic Action Items & Recommendations/i)).toBeInTheDocument();
        expect(screen.getByText('Top Holdings AI Stance & Risk Matrix')).toBeInTheDocument();
      });
    });

    it('renders cached portfolio evaluation immediately when present', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);
      vi.spyOn(aiApi, 'getLatestPortfolioEvaluation').mockResolvedValue(mockPortfolioEvaluation);

      renderWithProviders(
        <PortfolioAiEvaluationCard
          portfolioId="port-1"
          portfolioName="Tech & Dividend Portfolio"
          baseCurrency="USD"
        />
      );

      await waitFor(() => {
        expect(screen.getByText('Portfolio Executive Thesis')).toBeInTheDocument();
        expect(screen.getByText(/Well-positioned growth portfolio with healthy cash buffer/i)).toBeInTheDocument();
        expect(screen.getByText('GEMMA4-12B')).toBeInTheDocument();
      });
    });

    it('opens AI settings modal from header settings button and error Adjust Timeout button', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);
      vi.spyOn(aiApi, 'evaluatePortfolio').mockRejectedValue(new Error('Gateway timeout'));

      renderWithProviders(
        <PortfolioAiEvaluationCard
          portfolioId="port-1"
          portfolioName="Tech & Dividend Portfolio"
          baseCurrency="USD"
        />
      );

      // Open from header button
      const settingsBtn = await screen.findByTestId('portfolio-ai-settings-btn');
      fireEvent.click(settingsBtn);
      await waitFor(() => {
        expect(screen.getByTestId('ai-settings-modal')).toBeInTheDocument();
      });

      // Close modal
      fireEvent.click(screen.getByRole('button', { name: /Cancel/i }));
      await waitFor(() => {
        expect(screen.queryByTestId('ai-settings-modal')).not.toBeInTheDocument();
      });

      // Trigger error and test Adjust Timeout button
      const analyzeBtn = await screen.findByRole('button', { name: /Analyze Tech & Dividend Portfolio/i });
      fireEvent.click(analyzeBtn);
      await waitFor(() => {
        expect(screen.getByText(/Failed to evaluate portfolio/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Adjust Timeout/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Adjust Timeout/i }));
      await waitFor(() => {
        expect(screen.getByTestId('ai-settings-modal')).toBeInTheDocument();
      });
    });


    it('renders staleness banner, header chip, and table stale badge when portfolio evaluation is older than 7 days', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);
      const staleHolding: HoldingAiEvaluation = {
        ...mockHoldingEvaluation,
        evaluatedAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString(),
        isStale: true,
      };
      const stalePortfolio: PortfolioAiEvaluation = {
        ...mockPortfolioEvaluation,
        evaluatedAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString(),
        isStale: true,
        topHoldingEvaluations: [staleHolding],
      };
      vi.spyOn(aiApi, 'getLatestPortfolioEvaluation').mockResolvedValue(stalePortfolio);

      renderWithProviders(
        <PortfolioAiEvaluationCard
          portfolioId="port-1"
          portfolioName="Tech & Dividend Portfolio"
          baseCurrency="USD"
        />
      );

      await waitFor(() => {
        expect(screen.getByText(/Outdated Evaluation \(>7 days\)/i)).toBeInTheDocument();
        expect(screen.getAllByText('Stale (>7d)').length).toBeGreaterThanOrEqual(2);
      });
    });
  });

  describe('AiSettingsModal', () => {
    it('renders provider status and preset options', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);

      renderWithProviders(<AiSettingsModal open={true} onClose={vi.fn()} />);

      await waitFor(() => {
        expect(screen.getByText('AI Gateway Settings')).toBeInTheDocument();
        expect(screen.getByText('LM STUDIO')).toBeInTheDocument();
        expect(screen.getByText('gemma4-12b')).toBeInTheDocument();
        expect(screen.getByText('1m (60s)')).toBeInTheDocument();
        expect(screen.getByText('5m (300s)')).toBeInTheDocument();
        expect(screen.getByText('20m (1200s)')).toBeInTheDocument();
      });
    });

    it('updates timeout input when clicking presets and saves successfully', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);
      const updateConfigSpy = vi.spyOn(aiApi, 'updateAiConfig').mockResolvedValue({
        ...mockAiStatusConnected,
        timeoutSeconds: 300,
      });

      renderWithProviders(<AiSettingsModal open={true} onClose={vi.fn()} />);

      await waitFor(() => {
        expect(screen.getByText('gemma4-12b')).toBeInTheDocument();
        expect(screen.getByText('5m (300s)')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('5m (300s)'));

      const input = screen.getByTestId('ai-timeout-input') as HTMLInputElement;
      await waitFor(() => {
        expect(input.value).toBe('300');
      });

      fireEvent.click(screen.getByTestId('ai-save-settings-btn'));

      await waitFor(() => {
        expect(updateConfigSpy).toHaveBeenCalledWith({ timeoutSeconds: 300 });
        expect(screen.getByTestId('ai-settings-success-alert')).toBeInTheDocument();
      });
    });

    it('validates timeout range 5 to 3600 seconds', async () => {
      vi.spyOn(aiApi, 'getAiStatus').mockResolvedValue(mockAiStatusConnected);

      renderWithProviders(<AiSettingsModal open={true} onClose={vi.fn()} />);

      await waitFor(() => {
        expect(screen.getByText('gemma4-12b')).toBeInTheDocument();
        expect(screen.getByTestId('ai-timeout-input')).toBeInTheDocument();
      });

      const input = screen.getByTestId('ai-timeout-input');
      fireEvent.change(input, { target: { value: '2' } });

      await waitFor(() => {
        expect(screen.getByText(/Timeout must be between 5 and 3600 seconds/i)).toBeInTheDocument();
        expect(screen.getByTestId('ai-save-settings-btn')).toBeDisabled();
      });
    });
  });
});



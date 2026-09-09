import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { rebalancingApi, instrumentApi, portfolioApi } from '../api';
import {
  TargetAllocationCard,
  RebalancingCalculatorCard,
  TargetAllocationModal,
  RebalancingDetailPage,
} from '../features/rebalancing';
import type { TargetAllocationPlan, RebalanceAnalysis, Instrument, Portfolio } from '../types';

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
}

function renderWithProviders(ui: React.ReactElement, initialPath = '/') {
  const testQueryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={testQueryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[initialPath]}>{ui}</MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

const mockPlan: TargetAllocationPlan = {
  id: 'plan-1',
  portfolioId: 'port-1',
  name: '60/30/10 Core Strategy',
  allocationType: 'ASSET_CLASS',
  driftTolerancePercentage: 5.0,
  items: [
    { id: 'item-1', categoryKey: 'STOCK', categoryLabel: 'Stocks & Equities', targetPercentage: 60.0 },
    { id: 'item-2', categoryKey: 'ETF', categoryLabel: 'ETFs', targetPercentage: 30.0 },
    { id: 'item-3', categoryKey: 'CASH', categoryLabel: 'Cash Buffer', targetPercentage: 10.0 },
  ],
  updatedAt: '2026-08-30T12:00:00Z',
};

const mockAnalysis: RebalanceAnalysis = {
  portfolioId: 'port-1',
  portfolioName: 'Growth Portfolio',
  baseCurrency: 'GBP',
  asOf: '2026-08-30T12:00:00Z',
  allocationType: 'ASSET_CLASS',
  totalPortfolioValue: 10000.0,
  cashInjectionAmount: 0.0,
  totalPostRebalanceValue: 10000.0,
  driftTolerancePercentage: 5.0,
  hasDriftToleranceExceeded: true,
  items: [
    {
      categoryKey: 'STOCK',
      categoryLabel: 'Stocks & Equities',
      action: 'SELL',
      currentMarketValue: 8000.0,
      currentWeightPercentage: 80.0,
      targetWeightPercentage: 60.0,
      driftPercentage: 20.0,
      driftStatus: 'OVERWEIGHT',
      isDriftExceeded: true,
      targetValue: 6000.0,
      orderAmount: 2000.0,
      projectedPostWeightPercentage: 60.0,
      currency: 'GBP',
    },
    {
      categoryKey: 'ETF',
      categoryLabel: 'ETFs',
      action: 'BUY',
      currentMarketValue: 1000.0,
      currentWeightPercentage: 10.0,
      targetWeightPercentage: 30.0,
      driftPercentage: -20.0,
      driftStatus: 'UNDERWEIGHT',
      isDriftExceeded: true,
      targetValue: 3000.0,
      orderAmount: 2000.0,
      projectedPostWeightPercentage: 30.0,
      currency: 'GBP',
    },
    {
      categoryKey: 'CASH',
      categoryLabel: 'Cash Buffer',
      action: 'HOLD',
      currentMarketValue: 1000.0,
      currentWeightPercentage: 10.0,
      targetWeightPercentage: 10.0,
      driftPercentage: 0.0,
      driftStatus: 'IN_TOLERANCE',
      isDriftExceeded: false,
      targetValue: 1000.0,
      orderAmount: 0.0,
      projectedPostWeightPercentage: 10.0,
      currency: 'GBP',
    },
  ],
};

const mockCashInjectionAnalysis: RebalanceAnalysis = {
  ...mockAnalysis,
  cashInjectionAmount: 4000.0,
  totalPostRebalanceValue: 14000.0,
  items: [
    {
      categoryKey: 'STOCK',
      categoryLabel: 'Stocks & Equities',
      action: 'HOLD',
      currentMarketValue: 8000.0,
      currentWeightPercentage: 80.0,
      targetWeightPercentage: 60.0,
      driftPercentage: 20.0,
      driftStatus: 'OVERWEIGHT',
      isDriftExceeded: true,
      targetValue: 8400.0,
      orderAmount: 0.0,
      projectedPostWeightPercentage: 57.14,
      currency: 'GBP',
    },
    {
      categoryKey: 'ETF',
      categoryLabel: 'ETFs',
      action: 'BUY',
      currentMarketValue: 1000.0,
      currentWeightPercentage: 10.0,
      targetWeightPercentage: 30.0,
      driftPercentage: -20.0,
      driftStatus: 'UNDERWEIGHT',
      isDriftExceeded: true,
      targetValue: 4200.0,
      orderAmount: 3200.0,
      projectedPostWeightPercentage: 30.0,
      currency: 'GBP',
    },
  ],
};

describe('Target Allocation & Rebalancing UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('TargetAllocationCard', () => {
    it('renders unconfigured state with Configure Targets button', async () => {
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(null);
      vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockAnalysis);

      renderWithProviders(<TargetAllocationCard portfolioId="port-1" currency="GBP" />);

      await waitFor(() => {
        expect(screen.getByText('Target Asset Allocation Strategy')).toBeInTheDocument();
      });

      expect(screen.getByRole('button', { name: /Configure Targets/i })).toBeInTheDocument();
    });

    it('renders configured plan with category weights and drift status', async () => {
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockPlan);
      vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockAnalysis);

      renderWithProviders(<TargetAllocationCard portfolioId="port-1" currency="GBP" />);

      await waitFor(() => {
        expect(screen.getByText('60/30/10 Core Strategy')).toBeInTheDocument();
      });

      expect(screen.getByText('Rebalance Drift Detected')).toBeInTheDocument();
      expect(screen.getByText('Tolerance: ±5%')).toBeInTheDocument();
      expect(screen.getByText('Stocks & Equities')).toBeInTheDocument();
      expect(screen.getByText('+20.00%')).toBeInTheDocument();
      expect(screen.getByText('-20.00%')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Edit Targets/i })).toBeInTheDocument();
    });
  });

  describe('RebalancingCalculatorCard', () => {
    it('renders rebalancing orders in Full Rebalance mode', async () => {
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockPlan);
      vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockAnalysis);

      renderWithProviders(<RebalancingCalculatorCard portfolioId="port-1" currency="GBP" />);

      await waitFor(() => {
        expect(screen.getByText('Portfolio Rebalancing Calculator')).toBeInTheDocument();
      });

      expect(screen.getByText('10,000.00 GBP')).toBeInTheDocument();
      expect(screen.getByText('1 Buy, 1 Sell')).toBeInTheDocument();
      expect(screen.getByText('Exceeds Tolerance')).toBeInTheDocument();

      // Check buy and sell chips
      expect(screen.getByText('SELL')).toBeInTheDocument();
      expect(screen.getByText('BUY')).toBeInTheDocument();
      expect(screen.getByText('HOLD')).toBeInTheDocument();
    });

    it('switches to Cash Injection mode and triggers re-analysis', async () => {
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockPlan);
      const getAnalysisSpy = vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockCashInjectionAnalysis);

      renderWithProviders(<RebalancingCalculatorCard portfolioId="port-1" currency="GBP" />);

      await waitFor(() => {
        expect(screen.getByText('Portfolio Rebalancing Calculator')).toBeInTheDocument();
      });

      // Switch to Cash Injection mode
      fireEvent.click(screen.getByRole('button', { name: /Cash Injection/i }));

      await waitFor(() => {
        expect(screen.getByLabelText(/New Cash Deposit Amount/i)).toBeInTheDocument();
      });

      // Click quick add button +1,000
      fireEvent.click(screen.getByRole('button', { name: /\+1,000/i }));

      await waitFor(() => {
        expect(getAnalysisSpy).toHaveBeenCalledWith('port-1', 1000);
      });
    });

    it('copies rebalance plan to clipboard', async () => {
      const writeTextMock = vi.fn().mockResolvedValue(undefined);
      Object.assign(navigator, {
        clipboard: {
          writeText: writeTextMock,
        },
      });

      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockPlan);
      vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockAnalysis);

      renderWithProviders(<RebalancingCalculatorCard portfolioId="port-1" currency="GBP" />);

      await waitFor(() => {
        expect(screen.getByText('Portfolio Rebalancing Calculator')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Copy Plan/i }));

      expect(writeTextMock).toHaveBeenCalled();
      expect(await screen.findByText('Rebalancing plan copied to clipboard!')).toBeInTheDocument();
    });
  });

  describe('TargetAllocationModal', () => {
    it('validates total percentage must equal 100%', async () => {
      vi.spyOn(instrumentApi, 'list').mockResolvedValue([]);
      const saveSpy = vi.spyOn(rebalancingApi, 'saveTargetAllocation').mockResolvedValue(mockPlan);

      renderWithProviders(
        <TargetAllocationModal
          open={true}
          onClose={vi.fn()}
          portfolioId="port-1"
          existingPlan={null}
        />
      );

      expect(screen.getByText('Configure Target Asset Allocation')).toBeInTheDocument();
      // Default sum is 100.00%
      expect(screen.getByText(/100.00% ✓/i)).toBeInTheDocument();

      const saveBtn = screen.getByRole('button', { name: /Save Target Plan/i });
      expect(saveBtn).not.toBeDisabled();

      fireEvent.click(saveBtn);
      await waitFor(() => {
        expect(saveSpy).toHaveBeenCalled();
      });
    });
  });

  describe('RebalancingDetailPage Component Suite', () => {
    const mockPortfolio: Portfolio = {
      id: 'port-1',
      name: 'Growth Portfolio',
      baseCurrency: 'GBP',
      costBasisMethod: 'FIFO',
      returnMethod: 'TWR',
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };

    it('renders RebalancingDetailPage with hero stats, drift table, and trade orders', async () => {
      vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockPlan);
      vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockAnalysis);

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/rebalancing" element={<RebalancingDetailPage />} />
        </Routes>,
        '/portfolios/port-1/rebalancing'
      );

      await waitFor(() => {
        expect(screen.getByText('Strategic Target Allocation & Rebalancing Engine')).toBeInTheDocument();
      });

      // Breadcrumbs & Navigation
      expect(screen.getByText('Target Allocation & Rebalancing')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /Back to Portfolio/i })).toBeInTheDocument();

      // Hero KPIs
      expect(screen.getByText('60/30/10 Core Strategy')).toBeInTheDocument();
      expect(screen.getAllByText('±5%').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Drift Exceeded')).toBeInTheDocument();
      expect(screen.getByText(/2 Orders/i)).toBeInTheDocument();

      // Comparison table
      expect(screen.getByText('Target Allocation Model vs Current Exposure')).toBeInTheDocument();
      expect(screen.getAllByText('Stocks & Equities').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('ETFs').length).toBeGreaterThanOrEqual(1);

      // Orders ledger
      expect(screen.getByText(/Actionable Rebalancing Trade Orders/i)).toBeInTheDocument();
      expect(screen.getByText('SELL')).toBeInTheDocument();
      expect(screen.getByText('BUY')).toBeInTheDocument();
    });

    it('renders unconfigured state when no target plan exists', async () => {
      vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(null);

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/rebalancing" element={<RebalancingDetailPage />} />
        </Routes>,
        '/portfolios/port-1/rebalancing'
      );

      await waitFor(() => {
        expect(screen.getByText(/No Target Allocation Configured/i)).toBeInTheDocument();
      });
      expect(screen.getByRole('button', { name: /Configure Strategy Targets Now/i })).toBeInTheDocument();
    });

    it('allows toggling Cash Injection simulator and inputting amount', async () => {
      vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockPlan);
      const analysisSpy = vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockAnalysis);

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/rebalancing" element={<RebalancingDetailPage />} />
        </Routes>,
        '/portfolios/port-1/rebalancing'
      );

      await waitFor(() => {
        expect(screen.getByText('Strategic Target Allocation & Rebalancing Engine')).toBeInTheDocument();
      });

      // Switch to Cash Injection mode
      const cashInjBtn = screen.getByRole('button', { name: /Cash Injection \(Buy-Only\)/i });
      fireEvent.click(cashInjBtn);

      await waitFor(() => {
        expect(screen.getByPlaceholderText('e.g. 1000')).toBeInTheDocument();
      });

      // Click +£1,000 chip
      fireEvent.click(screen.getByText('+GBP 1,000'));

      await waitFor(() => {
        expect(analysisSpy).toHaveBeenCalledWith('port-1', 1000);
      });
    });

    it('handles Copy Plan and CSV Export without errors', async () => {
      vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockPlan);
      vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockAnalysis);

      const writeTextMock = vi.fn().mockResolvedValue(undefined);
      Object.assign(navigator, {
        clipboard: {
          writeText: writeTextMock,
        },
      });

      const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/rebalancing" element={<RebalancingDetailPage />} />
        </Routes>,
        '/portfolios/port-1/rebalancing'
      );

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Copy Plan/i })).toBeInTheDocument();
      });

      // Click Copy Plan
      fireEvent.click(screen.getByRole('button', { name: /Copy Plan/i }));
      expect(writeTextMock).toHaveBeenCalled();
      expect(await screen.findByText('Rebalancing plan copied to clipboard!')).toBeInTheDocument();

      // Click Export CSV
      fireEvent.click(screen.getByRole('button', { name: /Export CSV/i }));
      expect(clickSpy).toHaveBeenCalled();
    });

    it('handles Delete Target Plan with confirmation dialog', async () => {
      vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
      vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockPlan);
      vi.spyOn(rebalancingApi, 'getRebalancingAnalysis').mockResolvedValue(mockAnalysis);
      const deleteSpy = vi.spyOn(rebalancingApi, 'deleteTargetAllocation').mockResolvedValue();

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/rebalancing" element={<RebalancingDetailPage />} />
        </Routes>,
        '/portfolios/port-1/rebalancing'
      );

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Delete Plan/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Delete Plan/i }));

      await waitFor(() => {
        expect(screen.getByText('Delete Target Allocation Plan')).toBeInTheDocument();
      });

      // Confirm deletion inside dialog
      const confirmBtn = screen.getByRole('button', { name: 'Delete Plan' });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(deleteSpy).toHaveBeenCalledWith('port-1');
      });
    });
  });
});


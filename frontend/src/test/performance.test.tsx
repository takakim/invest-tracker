import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { portfolioApi, performanceApi, analyticsApi } from '../api';
import PerformanceSummaryCard from '../features/performance/PerformanceSummaryCard';
import { PerformanceDetailPage } from '../features/performance/PerformanceDetailPage';
import type { PerformanceResult, Portfolio, PortfolioHistory } from '../types';

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function renderWithProviders(ui: React.ReactElement, initialPath = '/') {
  const testQueryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={testQueryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[initialPath]}>{ui}</MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

const mockPortfolio: Portfolio = {
  id: 'port-123',
  name: 'Global Tech & Growth',
  baseCurrency: 'GBP',
  costBasisMethod: 'FIFO',
  returnMethod: 'TWR',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const mockPerformance: PerformanceResult = {
  portfolioId: 'port-123',
  asOf: '2026-09-01T12:00:00Z',
  returnMethod: 'TWR',
  twrReturn: 0.165,
  twrAnnualized: 0.142,
  mwrReturn: 0.158,
  totalRealizedGainLoss: 2400,
  totalDividendIncome: 650,
  totalInterestIncome: 120,
  totalFees: 35,
  totalTaxes: 45,
  totalNetIncome: 690,
  totalCostBasis: 18500,
  totalNetDeposits: 20000,
  currency: 'GBP',
  valuationBasis: 'COST_BASIS',
  byAccount: [
    {
      accountId: 'acc-1',
      accountName: 'Trading 212 ISA',
      costBasis: 12000,
      realizedGainLoss: 1800,
      dividendIncome: 450,
      interestIncome: 80,
      fees: 20,
      taxes: 30,
      currency: 'GBP',
    },
    {
      accountId: 'acc-2',
      accountName: 'Interactive Brokers General',
      costBasis: 6500,
      realizedGainLoss: 600,
      dividendIncome: 200,
      interestIncome: 40,
      fees: 15,
      taxes: 15,
      currency: 'GBP',
    },
  ],
};

const mockHistory1Y: PortfolioHistory = {
  portfolioId: 'port-123',
  portfolioName: 'Global Tech & Growth',
  baseCurrency: 'GBP',
  period: '1Y',
  interval: 'WEEKLY',
  periodStart: '2025-09-01T00:00:00Z',
  periodEnd: '2026-09-01T00:00:00Z',
  summary: {
    startingValue: 18000,
    endingValue: 22500,
    netCashFlows: 2000,
    totalGainLoss: 2500,
    portfolioReturnPercentage: 14.8,
    maxDrawdownPercentage: 6.2,
  },
  dataPoints: [
    {
      timestamp: '2025-09-01T00:00:00Z',
      marketValue: 18000,
      costBasis: 16000,
      cashValue: 1000,
      investedCapital: 16000,
      unrealizedGainLoss: 2000,
      portfolioReturnPercentage: 0,
    },
    {
      timestamp: '2026-03-01T00:00:00Z',
      marketValue: 20500,
      costBasis: 17500,
      cashValue: 1200,
      investedCapital: 17500,
      unrealizedGainLoss: 3000,
      portfolioReturnPercentage: 8.5,
    },
    {
      timestamp: '2026-09-01T00:00:00Z',
      marketValue: 22500,
      costBasis: 18500,
      cashValue: 1500,
      investedCapital: 18500,
      unrealizedGainLoss: 4000,
      portfolioReturnPercentage: 14.8,
    },
  ],
};

describe('Performance Feature Suite', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(performanceApi, 'get').mockResolvedValue(mockPerformance);
    vi.spyOn(analyticsApi, 'getPortfolioHistory').mockResolvedValue(mockHistory1Y);
  });

  describe('PerformanceSummaryCard Component', () => {
    it('renders card with period chips, all-time metrics, and view detail CTA', async () => {
      renderWithProviders(<PerformanceSummaryCard portfolioId="port-123" currency="GBP" />);

      await waitFor(() => {
        expect(screen.getByText('Portfolio Performance')).toBeInTheDocument();
        expect(screen.getByText('16.50%')).toBeInTheDocument();
        expect(screen.getByText('Realized Gain/Loss')).toBeInTheDocument();
        expect(screen.getByText('Net Income')).toBeInTheDocument();
      });

      // Period selector chips
      expect(screen.getByRole('button', { name: '1M' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '3M' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '6M' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'YTD' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '1Y' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'ALL' })).toBeInTheDocument();

      // CTA button
      expect(screen.getByRole('button', { name: /View Performance Detail/i })).toBeInTheDocument();
    });

    it('switches to 1Y period and displays period return', async () => {
      renderWithProviders(<PerformanceSummaryCard portfolioId="port-123" currency="GBP" />);

      await waitFor(() => {
        expect(screen.getByText('16.50%')).toBeInTheDocument();
      });

      // Click 1Y chip
      fireEvent.click(screen.getByRole('button', { name: '1Y' }));

      await waitFor(() => {
        expect(screen.getByText('14.80%')).toBeInTheDocument();
        expect(screen.getByText(/1Y Period Return/i)).toBeInTheDocument();
      });
    });
  });

  describe('PerformanceDetailPage Component', () => {
    it('renders hero return, sub-KPIs, SVG chart, attribution, and account tables', async () => {
      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/performance" element={<PerformanceDetailPage />} />
        </Routes>,
        '/portfolios/port-123/performance',
      );

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /Global Tech & Growth Performance/i })).toBeInTheDocument();
        expect(screen.getByText(/TWR Methodology/i)).toBeInTheDocument();
      });

      // Check sub KPIs
      expect(screen.getByText(/Annualized Return/i)).toBeInTheDocument();
      expect(screen.getByText('14.20%')).toBeInTheDocument();
      expect(screen.getByText(/Max Drawdown/i)).toBeInTheDocument();
      expect(screen.getByText(/-6.20%/i)).toBeInTheDocument();
      expect(screen.getByText(/Net Capital Inflow/i)).toBeInTheDocument();

      // Check Chart and Attribution
      expect(screen.getByText(/Cumulative Portfolio Return Trajectory/i)).toBeInTheDocument();
      expect(screen.getByText(/Return Attribution Breakdown/i)).toBeInTheDocument();
      expect(screen.getByText(/Income & Cost Drag Analysis/i)).toBeInTheDocument();
      expect(screen.getByText(/Account Performance Attribution/i)).toBeInTheDocument();

      // Check Account Table items
      expect(screen.getByText('Trading 212 ISA')).toBeInTheDocument();
      expect(screen.getByText('Interactive Brokers General')).toBeInTheDocument();

      // Check Methodology guide
      expect(screen.getByText(/Return Methodology & Calculation Standards/i)).toBeInTheDocument();
      expect(screen.getByText(/Internal Rate of Return \(XIRR\)/i)).toBeInTheDocument();
    });

    it('allows changing the period on the detail page', async () => {
      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/performance" element={<PerformanceDetailPage />} />
        </Routes>,
        '/portfolios/port-123/performance',
      );

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /Global Tech & Growth Performance/i })).toBeInTheDocument();
      });

      // Click ALL chip
      fireEvent.click(screen.getByRole('button', { name: 'ALL' }));

      await waitFor(() => {
        expect(screen.getByText('16.50%')).toBeInTheDocument();
        expect(screen.getByText(/Total All-Time Return/i)).toBeInTheDocument();
      });
    });
  });
});

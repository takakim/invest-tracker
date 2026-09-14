import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { analyticsApi, portfolioApi, exportApi } from '../api';
import { CashFlowSummaryCard } from '../features/analytics/CashFlowSummaryCard';
import { CashFlowDetailPage } from '../features/analytics/CashFlowDetailPage';
import type { CashFlowAnalytics, Portfolio } from '../types';

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

const mockPortfolio: Portfolio = {
  id: 'port-123',
  name: 'Growth Portfolio',
  baseCurrency: 'GBP',
  costBasisMethod: 'FIFO',
  returnMethod: 'TWR',
  status: 'ACTIVE',
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
};

const mockCashFlowsResponse: CashFlowAnalytics = {
  portfolioId: 'port-123',
  portfolioName: 'Growth Portfolio',
  baseCurrency: 'GBP',
  period: 'ALL',
  groupBy: 'MONTH',
  periodStart: '2025-01-01T00:00:00Z',
  periodEnd: '2026-03-01T00:00:00Z',
  summary: {
    totalDeposits: 12000,
    totalWithdrawals: 2000,
    netContributions: 10000,
    totalDividends: 400,
    totalInterest: 50,
    totalFees: 30,
    netCashFlow: 10420,
    avgMonthlyContribution: 2000,
    activeContributionMonths: 5,
    cumulativeContributions: 10000,
    currentPortfolioValue: 14000,
    capitalContributionsPercentage: 71.4,
    marketGrowthPercentage: 28.6,
    baseCurrency: 'GBP',
  },
  periods: [
    {
      periodLabel: '2026-01',
      startDate: '2026-01-01T00:00:00Z',
      endDate: '2026-01-31T23:59:59Z',
      deposits: 5000,
      withdrawals: 1000,
      netContributions: 4000,
      internalIncome: 100,
      cumulativeNetContributions: 4000,
    },
    {
      periodLabel: '2026-02',
      startDate: '2026-02-01T00:00:00Z',
      endDate: '2026-02-28T23:59:59Z',
      deposits: 7000,
      withdrawals: 1000,
      netContributions: 6000,
      internalIncome: 300,
      cumulativeNetContributions: 10000,
    },
  ],
  accountBreakdown: [
    {
      accountId: 'acc-1',
      accountName: 'ISA Account',
      brokerName: 'Freetrade',
      accountCurrency: 'GBP',
      currentCashBalance: 2500,
      currentCashBalanceInBase: 2500,
      totalDeposits: 8000,
      totalWithdrawals: 1000,
      netContributions: 7000,
    },
    {
      accountId: 'acc-2',
      accountName: 'GIA Account',
      brokerName: 'IBKR',
      accountCurrency: 'USD',
      currentCashBalance: 1200,
      currentCashBalanceInBase: 960,
      totalDeposits: 4000,
      totalWithdrawals: 1000,
      netContributions: 3000,
    },
  ],
  warnings: [],
};

describe('Cash Flow Feature Suite', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getCashFlowAnalytics').mockResolvedValue(mockCashFlowsResponse);
    vi.spyOn(exportApi, 'downloadCashFlowsCsv').mockResolvedValue();
  });

  describe('CashFlowSummaryCard', () => {
    it('renders overview metrics and wealth origin bar', async () => {
      renderWithProviders(
        <CashFlowSummaryCard portfolioId="port-123" currency="GBP" />
      );

      await waitFor(() => {
        expect(screen.getByText('Cash Flow & Savings')).toBeInTheDocument();
      });

      expect(screen.getByText('+GBP 12,000.00')).toBeInTheDocument();
      expect(screen.getByText('-GBP 2,000.00')).toBeInTheDocument();
      expect(screen.getByText('GBP 10,000.00')).toBeInTheDocument();
      expect(screen.getByText('5 mo')).toBeInTheDocument();
      expect(screen.getByText(/Contributions \(71.4%\) vs Growth \(28.6%\)/)).toBeInTheDocument();
      expect(screen.getByText('View Full Cash Flow Analytics & Ledger')).toBeInTheDocument();
    });
  });

  describe('CashFlowDetailPage', () => {
    it('renders full detail page with hero metrics, charts, and tables', async () => {
      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/cash-flows" element={<CashFlowDetailPage />} />
        </Routes>,
        '/portfolios/port-123/cash-flows'
      );

      await waitFor(() => {
        expect(screen.getByText('Cash Flow & Savings')).toBeInTheDocument();
      });

      expect(screen.getByText('+GBP 12,000.00')).toBeInTheDocument();
      expect(screen.getByText('-GBP 2,000.00')).toBeInTheDocument();
      expect(screen.getAllByText('GBP 10,000.00').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Where Your Wealth Came From')).toBeInTheDocument();
      expect(screen.getByText('Periodic Capital Inflow vs Outflow')).toBeInTheDocument();
      expect(screen.getByText('Periodic Cash Flow Breakdown')).toBeInTheDocument();
      expect(screen.getByText('Account Cash & Inflow Distribution')).toBeInTheDocument();

      // Check account rows
      expect(screen.getByText('ISA Account')).toBeInTheDocument();
      expect(screen.getByText('GIA Account')).toBeInTheDocument();
      expect(screen.getByText('Freetrade')).toBeInTheDocument();
      expect(screen.getByText('IBKR')).toBeInTheDocument();
    });

    it('allows toggling time horizon and grouping chips', async () => {
      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/cash-flows" element={<CashFlowDetailPage />} />
        </Routes>,
        '/portfolios/port-123/cash-flows'
      );

      await waitFor(() => {
        expect(screen.getByText('Cash Flow & Savings')).toBeInTheDocument();
      });

      // Click '1Y' period chip
      const oneYearChip = screen.getByRole('button', { name: '1Y' });
      fireEvent.click(oneYearChip);

      await waitFor(() => {
        expect(analyticsApi.getCashFlowAnalytics).toHaveBeenCalledWith('port-123', expect.objectContaining({ period: '1Y' }));
      });

      // Wait for content and click 'Quarterly' grouping chip
      await waitFor(() => {
        expect(screen.getByRole('button', { name: 'Quarterly' })).toBeInTheDocument();
      });
      const quarterlyChip = screen.getByRole('button', { name: 'Quarterly' });
      fireEvent.click(quarterlyChip);

      await waitFor(() => {
        expect(analyticsApi.getCashFlowAnalytics).toHaveBeenCalledWith('port-123', expect.objectContaining({ groupBy: 'QUARTER' }));
      });
    });

    it('triggers CSV export when Export CSV is clicked', async () => {
      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/cash-flows" element={<CashFlowDetailPage />} />
        </Routes>,
        '/portfolios/port-123/cash-flows'
      );

      await waitFor(() => {
        expect(screen.getByText('Export CSV')).toBeInTheDocument();
      });

      const exportBtn = screen.getByRole('button', { name: /Export CSV/i });
      fireEvent.click(exportBtn);

      await waitFor(() => {
        expect(exportApi.downloadCashFlowsCsv).toHaveBeenCalledWith('port-123', { period: 'ALL', groupBy: 'MONTH' });
      });
    });
  });
});

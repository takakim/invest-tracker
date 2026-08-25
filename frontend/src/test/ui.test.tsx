import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { ApiError, portfolioApi, instrumentApi, accountApi, positionApi, transactionApi, importApi, performanceApi, marketApi, fxApi } from '../api';
import { ErrorAlert, EmptyState, ConfirmDialog, Layout } from '../components';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { PortfolioListPage } from '../features/portfolios/PortfolioListPage';
import { PortfolioDetailPage } from '../features/portfolios/PortfolioDetailPage';
import { InstrumentListPage } from '../features/instruments/InstrumentListPage';
import PerformanceSummaryCard from '../features/performance/PerformanceSummaryCard';
import { MarketRatesCard } from '../features/market/MarketRatesCard';
import { ManualPriceModal } from '../features/market/ManualPriceModal';
import type { Portfolio, Instrument, Account, Position, Transaction, PerformanceResult } from '../types';

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
}

function renderWithProviders(ui: React.ReactElement, { route = '/' } = {}) {
  const testQueryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={testQueryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('UI Primitives', () => {
  it('renders ErrorAlert with ProblemDetail field errors', () => {
    const error = new ApiError({
      title: 'Validation Error',
      status: 400,
      detail: 'Request body failed validation',
      errors: ['name: must not be empty', 'baseCurrency: invalid code'],
    });

    renderWithProviders(<ErrorAlert error={error} />);

    expect(screen.getByText('Validation Error')).toBeInTheDocument();
    expect(screen.getByText('Request body failed validation')).toBeInTheDocument();
    expect(screen.getByText('name: must not be empty')).toBeInTheDocument();
    expect(screen.getByText('baseCurrency: invalid code')).toBeInTheDocument();
  });

  it('renders EmptyState with action button trigger', () => {
    const onAction = vi.fn();
    renderWithProviders(
      <EmptyState
        title="No Portfolios"
        description="Get started by creating one"
        actionLabel="Create Now"
        onAction={onAction}
      />,
    );

    expect(screen.getByText('No Portfolios')).toBeInTheDocument();
    expect(screen.getByText('Get started by creating one')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: 'Create Now' });
    fireEvent.click(btn);
    expect(onAction).toHaveBeenCalledTimes(1);
  });

  it('renders ConfirmDialog and responds to cancel and confirm', () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();

    const { rerender } = renderWithProviders(
      <ConfirmDialog
        open={true}
        title="Delete Item"
        message="Are you sure?"
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    expect(screen.getByText('Delete Item')).toBeInTheDocument();
    expect(screen.getByText('Are you sure?')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('renders Layout with branding and navigation', () => {
    renderWithProviders(
      <Layout>
        <div>Content Body</div>
      </Layout>,
    );

    expect(screen.getAllByText('Invest Tracker').length).toBeGreaterThan(0);
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Portfolios')).toBeInTheDocument();
    expect(screen.getByText('Instruments')).toBeInTheDocument();
    expect(screen.getByText('Content Body')).toBeInTheDocument();
  });
});

describe('Feature Pages', () => {
  const mockPortfolios: Portfolio[] = [
    {
      id: 'p-1',
      name: 'Retirement Growth',
      baseCurrency: 'GBP',
      costBasisMethod: 'FIFO',
      returnMethod: 'XIRR',
      status: 'ACTIVE',
      createdAt: '2026-08-20T10:00:00Z',
      updatedAt: '2026-08-20T10:00:00Z',
    },
  ];

  const mockAccounts: Account[] = [
    {
      id: 'acc-1',
      portfolioId: 'p-1',
      name: 'Interactive Brokers SIPP',
      brokerName: 'Interactive Brokers',
      accountCurrency: 'GBP',
      status: 'ACTIVE',
      createdAt: '2026-08-20T10:00:00Z',
      updatedAt: '2026-08-20T10:00:00Z',
    },
  ];

  const mockInstruments: Instrument[] = [
    {
      id: 'inst-1',
      name: 'Apple Inc',
      assetClass: 'STOCK',
      ticker: 'AAPL',
      isin: 'US0378331005',
      exchange: 'NASDAQ',
      currency: 'USD',
      createdAt: '2026-08-20T10:00:00Z',
      updatedAt: '2026-08-20T10:00:00Z',
    },
  ];

  const mockPositions: Position[] = [
    {
      id: 'pos-1',
      accountId: 'acc-1',
      instrumentId: 'inst-1',
      instrumentName: 'Apple Inc',
      instrumentTicker: 'AAPL',
      instrumentIsin: 'US0378331005',
      assetClass: 'STOCK',
      quantity: 50,
      costBasisAmount: 7500,
      costBasisCurrency: 'USD',
      status: 'ACTIVE',
      createdAt: '2026-08-20T10:00:00Z',
      updatedAt: '2026-08-20T10:00:00Z',
    },
  ];

  const mockTransactions: Transaction[] = [
    {
      id: 'tx-1',
      accountId: 'acc-1',
      instrumentId: 'inst-1',
      instrumentName: 'Apple Inc',
      instrumentTicker: 'AAPL',
      type: 'BUY',
      tradeDate: '2026-08-20T10:00:00Z',
      quantity: 50,
      price: 150,
      grossAmount: 7500,
      feeAmount: 5,
      netAmount: 7505,
      currency: 'USD',
      status: 'COMPLETED',
      createdAt: '2026-08-20T10:00:00Z',
      updatedAt: '2026-08-20T10:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.spyOn(portfolioApi, 'list').mockResolvedValue(mockPortfolios);
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolios[0]);
    vi.spyOn(instrumentApi, 'list').mockResolvedValue(mockInstruments);
    vi.spyOn(accountApi, 'list').mockResolvedValue(mockAccounts);
    vi.spyOn(positionApi, 'list').mockResolvedValue(mockPositions);
    vi.spyOn(transactionApi, 'list').mockResolvedValue(mockTransactions);
    vi.spyOn(transactionApi, 'listPortfolio').mockResolvedValue(mockTransactions);
    vi.spyOn(importApi, 'list').mockResolvedValue([]);
    vi.spyOn(positionApi, 'getLots').mockResolvedValue({
      positionId: 'pos-1',
      accountId: 'acc-1',
      instrumentId: 'inst-1',
      costBasisMethod: 'FIFO',
      totalQuantity: 10,
      totalCostBasisAmount: 1500,
      currency: 'USD',
      averageUnitCostAmount: 150,
      realizedGainLossAmount: 0,
      openLots: [],
    });
    vi.spyOn(positionApi, 'recalculate').mockResolvedValue({
      portfolioId: 'p-1',
      recalculatedPositionsCount: 1,
      message: 'Recalculated',
    });
    vi.spyOn(performanceApi, 'get').mockResolvedValue({
      portfolioId: 'p-1',
      asOf: '2026-08-20T10:00:00Z',
      returnMethod: 'TWR',
      twrReturn: 0.125,
      twrAnnualized: 0.15,
      mwrReturn: null,
      totalRealizedGainLoss: 250,
      totalDividendIncome: 50,
      totalInterestIncome: 10,
      totalFees: 5,
      totalTaxes: 8,
      totalNetIncome: 47,
      totalCostBasis: 1500,
      currency: 'GBP',
      valuationBasis: 'COST_BASIS',
      byAccount: [],
    });
    vi.spyOn(fxApi, 'getRate').mockResolvedValue({
      baseCurrency: 'GBP',
      quoteCurrency: 'USD',
      rate: 1.28,
      asOf: '2026-08-20T10:00:00Z',
      sourceType: 'PROVIDER',
      isDerived: false,
    });
    vi.spyOn(marketApi, 'getLatestQuote').mockResolvedValue({
      instrumentId: 'inst-1',
      price: 185.5,
      currency: 'USD',
      asOf: '2026-08-20T10:00:00Z',
      sourceType: 'PROVIDER',
      isStale: false,
    });
    vi.spyOn(marketApi, 'recordPriceOverride').mockResolvedValue({
      id: 'obs-1',
      instrumentId: 'inst-1',
      price: 190.0,
      currency: 'USD',
      observedAt: '2026-08-20T10:00:00Z',
      sourceType: 'MANUAL',
      createdAt: '2026-08-20T10:00:00Z',
    });
  });

  it('renders DashboardPage with active portfolios and metrics', async () => {
    renderWithProviders(<DashboardPage />);

    expect(screen.getByText('Dashboard')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Retirement Growth')).toBeInTheDocument();
    });
  });

  it('renders PortfolioListPage and opens create modal', async () => {
    renderWithProviders(<PortfolioListPage />);

    await waitFor(() => {
      expect(screen.getByText('Retirement Growth')).toBeInTheDocument();
      expect(screen.getByText('Currency: GBP')).toBeInTheDocument();
    });

    const newBtn = screen.getByRole('button', { name: 'New Portfolio' });
    fireEvent.click(newBtn);

    expect(screen.getByText('Create New Portfolio')).toBeInTheDocument();
    expect(screen.getByLabelText(/Portfolio Name/i)).toBeInTheDocument();
  });

  it('renders PortfolioDetailPage with account list and performance card', async () => {
    const testQueryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={testQueryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/portfolios/p-1']}>
            <Routes>
              <Route path="/portfolios/:id" element={<PortfolioDetailPage />} />
            </Routes>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 4, name: 'Retirement Growth' })).toBeInTheDocument();
      expect(screen.getByRole('heading', { level: 5, name: 'Accounts' })).toBeInTheDocument();
      expect(screen.getByText('Interactive Brokers SIPP')).toBeInTheDocument();
      expect(screen.getByText('Interactive Brokers')).toBeInTheDocument();
      expect(screen.getByText('Portfolio Performance')).toBeInTheDocument();
      expect(screen.getByText('12.50%')).toBeInTheDocument();
    });
  });

  it('renders PerformanceSummaryCard component with metrics', async () => {
    renderWithProviders(<PerformanceSummaryCard portfolioId="p-1" currency="GBP" />);

    await waitFor(() => {
      expect(screen.getByText('Portfolio Performance')).toBeInTheDocument();
      expect(screen.getByText('12.50%')).toBeInTheDocument();
      expect(screen.getByText('Realized Gain/Loss')).toBeInTheDocument();
      expect(screen.getByText('Net Income')).toBeInTheDocument();
      expect(screen.getByText('Cost Basis')).toBeInTheDocument();
    });
  });

  it('renders MarketRatesCard and displays exchange rates', async () => {
    renderWithProviders(<MarketRatesCard />);

    await waitFor(() => {
      expect(screen.getByText('Exchange Rates (FX)')).toBeInTheDocument();
      expect(screen.getByText('GBP/USD')).toBeInTheDocument();
      expect(screen.getAllByText('1.2800').length).toBeGreaterThan(0);
    });
  });

  it('renders ManualPriceModal and submits price override', async () => {
    const handleClose = vi.fn();
    renderWithProviders(
      <ManualPriceModal open={true} instrument={mockInstruments[0]} onClose={handleClose} />
    );

    expect(screen.getByText('Manual Price Override')).toBeInTheDocument();
    expect(screen.getByText(/Apple Inc/)).toBeInTheDocument();

    const priceInput = screen.getByLabelText(/Override Price/i);
    fireEvent.change(priceInput, { target: { value: '190.0' } });

    const submitBtn = screen.getByRole('button', { name: 'Record Override' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(marketApi.recordPriceOverride).toHaveBeenCalledWith('inst-1', {
        price: 190.0,
        currency: 'USD',
        reason: undefined,
      });
      expect(handleClose).toHaveBeenCalled();
    });
  });

  it('renders InstrumentListPage and filters instruments', async () => {
    renderWithProviders(<InstrumentListPage />);

    await waitFor(() => {
      expect(screen.getByText('Instrument Directory')).toBeInTheDocument();
      expect(screen.getByText('Apple Inc')).toBeInTheDocument();
      expect(screen.getByText('AAPL')).toBeInTheDocument();
      expect(screen.getByText('US0378331005')).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(/Search by name/i);
    fireEvent.change(searchInput, { target: { value: 'NonExistent' } });

    await waitFor(() => {
      expect(screen.getByText('No Matching Instruments')).toBeInTheDocument();
    });
  });
});


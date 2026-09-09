import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { portfolioApi, accountApi, positionApi, transactionApi, importApi } from '../api';
import { AccountDetailPage } from '../features/accounts/AccountDetailPage';
import { AccountSectionCard } from '../features/accounts/AccountSectionCard';
import type { Account, ImportBatch, Portfolio, PositionPerformance, Transaction } from '../types';

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
  name: 'Growth & Income Portfolio',
  baseCurrency: 'GBP',
  costBasisMethod: 'FIFO',
  returnMethod: 'TWR',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const mockAccount: Account = {
  id: 'acc-456',
  portfolioId: 'port-123',
  name: 'Trading 212 ISA',
  brokerName: 'Trading 212',
  accountCurrency: 'GBP',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const mockPositions: PositionPerformance[] = [
  {
    positionId: 'pos-1',
    accountId: 'acc-456',
    accountName: 'Trading 212 ISA',
    instrumentId: 'inst-1',
    instrumentName: 'Apple Inc.',
    ticker: 'AAPL',
    isin: 'US0378331005',
    assetClass: 'STOCK',
    status: 'ACTIVE',
    currentQuantity: 10,
    totalBoughtQuantity: 10,
    totalSoldQuantity: 0,
    averageBuyPrice: 150,
    averageSellPrice: 0,
    totalInvestedAmount: 1500,
    totalProceedsAmount: 0,
    currentCostBasis: 1500,
    currentPrice: 180,
    currentMarketValue: 1800,
    realizedGainLoss: 0,
    unrealizedGainLoss: 300,
    dividendIncome: 25,
    fees: 0,
    taxes: 0,
    netTotalReturnAmount: 325,
    totalReturnPercentage: 21.67,
    currency: 'GBP',
    openLots: [],
    disposals: [],
    transactions: [],
  },
  {
    positionId: 'pos-2',
    accountId: 'acc-456',
    accountName: 'Trading 212 ISA',
    instrumentId: 'inst-2',
    instrumentName: 'Vanguard FTSE All-World',
    ticker: 'VWRL',
    isin: 'IE00B3RBWM25',
    assetClass: 'ETF',
    status: 'ACTIVE',
    currentQuantity: 20,
    totalBoughtQuantity: 20,
    totalSoldQuantity: 0,
    averageBuyPrice: 90,
    averageSellPrice: 0,
    totalInvestedAmount: 1800,
    totalProceedsAmount: 0,
    currentCostBasis: 1800,
    currentPrice: 95,
    currentMarketValue: 1900,
    realizedGainLoss: 0,
    unrealizedGainLoss: 100,
    dividendIncome: 50,
    fees: 0,
    taxes: 0,
    netTotalReturnAmount: 150,
    totalReturnPercentage: 8.33,
    currency: 'GBP',
    openLots: [],
    disposals: [],
    transactions: [],
  },
];

const mockTransactions: Transaction[] = [
  {
    id: 'tx-1',
    accountId: 'acc-456',
    type: 'DEPOSIT',
    tradeDate: '2026-01-10T10:00:00Z',
    grossAmount: 5000,
    netAmount: 5000,
    currency: 'GBP',
    status: 'COMPLETED',
    createdAt: '2026-01-10T10:00:00Z',
    updatedAt: '2026-01-10T10:00:00Z',
    notes: 'Initial deposit',
  },
  {
    id: 'tx-2',
    accountId: 'acc-456',
    instrumentId: 'inst-1',
    instrumentName: 'Apple Inc.',
    instrumentTicker: 'AAPL',
    type: 'BUY',
    tradeDate: '2026-01-15T14:00:00Z',
    quantity: 10,
    price: 150,
    grossAmount: 1500,
    netAmount: 1500,
    currency: 'GBP',
    status: 'COMPLETED',
    createdAt: '2026-01-15T14:00:00Z',
    updatedAt: '2026-01-15T14:00:00Z',
  },
  {
    id: 'tx-3',
    accountId: 'acc-456',
    instrumentId: 'inst-2',
    instrumentName: 'Vanguard FTSE All-World',
    instrumentTicker: 'VWRL',
    type: 'BUY',
    tradeDate: '2026-02-01T11:00:00Z',
    quantity: 20,
    price: 90,
    grossAmount: 1800,
    netAmount: 1800,
    currency: 'GBP',
    status: 'COMPLETED',
    createdAt: '2026-02-01T11:00:00Z',
    updatedAt: '2026-02-01T11:00:00Z',
  },
  {
    id: 'tx-4',
    accountId: 'acc-456',
    instrumentId: 'inst-1',
    instrumentName: 'Apple Inc.',
    instrumentTicker: 'AAPL',
    type: 'DIVIDEND',
    tradeDate: '2026-03-01T09:00:00Z',
    grossAmount: 25,
    netAmount: 25,
    currency: 'GBP',
    status: 'COMPLETED',
    createdAt: '2026-03-01T09:00:00Z',
    updatedAt: '2026-03-01T09:00:00Z',
  },
];

const mockImportBatches: ImportBatch[] = [
  {
    id: 'batch-1',
    accountId: 'acc-456',
    fileName: 'trading212_export_2026.csv',
    brokerType: 'TRADING_212',
    status: 'COMPLETED',
    totalRows: 4,
    importedRows: 4,
    skippedRows: 0,
    createdAt: '2026-03-01T12:00:00Z',
  },
];

describe('Account Feature & Detail Page Suite', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(accountApi, 'get').mockResolvedValue(mockAccount);
    vi.spyOn(positionApi, 'listAccountPerformance').mockResolvedValue(mockPositions);
    vi.spyOn(transactionApi, 'list').mockResolvedValue(mockTransactions);
    vi.spyOn(importApi, 'list').mockResolvedValue(mockImportBatches);
  });

  it('renders AccountDetailPage with hero summary and KPIs', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/accounts/:accountId" element={<AccountDetailPage />} />
      </Routes>,
      '/portfolios/port-123/accounts/acc-456',
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Trading 212 ISA' })).toBeInTheDocument();
      expect(screen.getByText(/Growth & Income Portfolio/i)).toBeInTheDocument();
    });

    // Check KPI cards
    expect(screen.getByText(/Total Account Value/i)).toBeInTheDocument();
    expect(screen.getByText(/Available Cash/i)).toBeInTheDocument();
    expect(screen.getByText(/Holdings Value/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Unrealized P&L/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Total Dividends Earned/i)).toBeInTheDocument();

    // Check positions table rows
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument();
    expect(screen.getByText('Vanguard FTSE All-World')).toBeInTheDocument();
  });

  it('filters holdings with the PositionTable search bar', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/accounts/:accountId" element={<AccountDetailPage />} />
      </Routes>,
      '/portfolios/port-123/accounts/acc-456',
    );

    await waitFor(() => {
      expect(screen.getByText('Apple Inc.')).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(/Filter holdings by name, ticker, ISIN, asset class.../i);
    fireEvent.change(searchInput, { target: { value: 'VWRL' } });

    // Apple Inc should be filtered out, VWRL should remain
    expect(screen.queryByText('Apple Inc.')).not.toBeInTheDocument();
    expect(screen.getByText('Vanguard FTSE All-World')).toBeInTheDocument();
  });

  it('switches to Transaction Ledger tab and filters by type', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/accounts/:accountId" element={<AccountDetailPage />} />
      </Routes>,
      '/portfolios/port-123/accounts/acc-456',
    );

    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /Transactions/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('tab', { name: /Transactions/i }));

    await waitFor(() => {
      expect(screen.getByText('Initial deposit')).toBeInTheDocument();
    });

    // Click DIVIDEND chip filter
    const dividendChip = screen.getByRole('button', { name: 'DIVIDEND' });
    fireEvent.click(dividendChip);

    // Initial deposit should be filtered out
    expect(screen.queryByText('Initial deposit')).not.toBeInTheDocument();
    expect(screen.getByText(/Showing 1 of 4/i)).toBeInTheDocument();
  });

  it('switches to Import History tab and displays import batches', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/accounts/:accountId" element={<AccountDetailPage />} />
      </Routes>,
      '/portfolios/port-123/accounts/acc-456',
    );

    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /Import History/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('tab', { name: /Import History/i }));

    await waitFor(() => {
      expect(screen.getByText('trading212_export_2026.csv')).toBeInTheDocument();
      expect(screen.getByText('TRADING_212')).toBeInTheDocument();
    });
  });

  it('switches to Cash Flow Timeline tab and displays capital flows', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/accounts/:accountId" element={<AccountDetailPage />} />
      </Routes>,
      '/portfolios/port-123/accounts/acc-456',
    );

    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /Cash Flow Timeline/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('tab', { name: /Cash Flow Timeline/i }));

    await waitFor(() => {
      expect(screen.getByText(/Total Deposits/i)).toBeInTheDocument();
      expect(screen.getByText(/Net Capital Invested/i)).toBeInTheDocument();
      expect(screen.getByText('Initial deposit')).toBeInTheDocument();
    });
  });

  it('renders AccountSectionCard with 3-KPI summary strip and Account Details navigation button', async () => {
    const handleEdit = vi.fn();
    const handleArchive = vi.fn();

    renderWithProviders(
      <AccountSectionCard
        portfolio={mockPortfolio}
        account={mockAccount}
        onEdit={handleEdit}
        onArchive={handleArchive}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('AVAILABLE CASH')).toBeInTheDocument();
      expect(screen.getByText('OPEN POSITIONS')).toBeInTheDocument();
      expect(screen.getByText('UNREALIZED P&L')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Account Details/i })).toBeInTheDocument();
    });
  });
});

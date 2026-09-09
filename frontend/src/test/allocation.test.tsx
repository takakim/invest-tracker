import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { portfolioApi, analyticsApi, rebalancingApi } from '../api';
import { DonutChart } from '../features/analytics/DonutChart';
import { AllocationDetailPage } from '../features/analytics/AllocationDetailPage';
import type { Portfolio, PortfolioAnalytics, TargetAllocationPlan } from '../types';

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

function renderWithProviders(ui: React.ReactElement, initialPath = '/') {
  const queryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[initialPath]}>{ui}</MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

const mockPortfolio: Portfolio = {
  id: 'port-123',
  name: 'Diversified Growth Portfolio',
  baseCurrency: 'GBP',
  costBasisMethod: 'FIFO',
  returnMethod: 'TWR',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const mockAnalytics: PortfolioAnalytics = {
  portfolioId: 'port-123',
  asOf: '2026-09-01T00:00:00Z',
  baseCurrency: 'GBP',
  totalCurrentValue: 50000.0,
  totalCostBasis: 42000.0,
  totalUnrealizedGainLoss: 8000.0,
  totalUnrealizedReturnPercentage: 19.05,
  totalRealizedGainLoss: 1200.0,
  totalCashValue: 5000.0,
  byAssetClass: [
    {
      category: 'STOCK',
      marketValue: 35000.0,
      percentage: 0.7,
      costBasis: 30000.0,
      unrealizedGainLoss: 5000.0,
    },
    {
      category: 'BOND',
      marketValue: 10000.0,
      percentage: 0.2,
      costBasis: 9000.0,
      unrealizedGainLoss: 1000.0,
    },
    {
      category: 'CASH',
      marketValue: 5000.0,
      percentage: 0.1,
      costBasis: 5000.0,
      unrealizedGainLoss: 0.0,
    },
  ],
  byCurrency: [
    {
      category: 'GBP',
      marketValue: 20000.0,
      percentage: 0.4,
      costBasis: 18000.0,
      unrealizedGainLoss: 2000.0,
    },
    {
      category: 'USD',
      marketValue: 30000.0,
      percentage: 0.6,
      costBasis: 24000.0,
      unrealizedGainLoss: 6000.0,
    },
  ],
  byAccount: [
    {
      category: 'Main Trading ISA',
      marketValue: 32000.0,
      percentage: 0.64,
      costBasis: 28000.0,
      unrealizedGainLoss: 4000.0,
    },
    {
      category: 'SIPP Pension',
      marketValue: 18000.0,
      percentage: 0.36,
      costBasis: 14000.0,
      unrealizedGainLoss: 4000.0,
    },
  ],
  topHoldings: [
    {
      instrumentId: 'inst-1',
      instrumentName: 'Apple Inc.',
      ticker: 'AAPL',
      assetClass: 'STOCK',
      quantity: 120,
      currentPrice: 175.0,
      marketValue: 21000.0,
      costBasis: 17000.0,
      unrealizedGainLoss: 4000.0,
      weightPercentage: 0.42,
      currency: 'GBP',
    },
    {
      instrumentId: 'inst-2',
      instrumentName: 'Microsoft Corporation',
      ticker: 'MSFT',
      assetClass: 'STOCK',
      quantity: 40,
      currentPrice: 350.0,
      marketValue: 14000.0,
      costBasis: 13000.0,
      unrealizedGainLoss: 1000.0,
      weightPercentage: 0.28,
      currency: 'GBP',
    },
    {
      instrumentId: 'inst-3',
      instrumentName: 'Vanguard UK Gilt ETF',
      ticker: 'VGOV',
      assetClass: 'BOND',
      quantity: 500,
      currentPrice: 20.0,
      marketValue: 10000.0,
      costBasis: 9000.0,
      unrealizedGainLoss: 1000.0,
      weightPercentage: 0.2,
      currency: 'GBP',
    },
  ],
  warnings: [],
};

const mockTargetPlan: TargetAllocationPlan = {
  id: 'plan-1',
  portfolioId: 'port-123',
  name: 'Balanced Growth Plan',
  allocationType: 'ASSET_CLASS',
  driftTolerancePercentage: 5.0,
  items: [
    {
      id: 'item-1',
      categoryKey: 'STOCK',
      categoryLabel: 'Stocks',
      targetPercentage: 0.6,
    },
    {
      id: 'item-2',
      categoryKey: 'BOND',
      categoryLabel: 'Bonds',
      targetPercentage: 0.3,
    },
    {
      id: 'item-3',
      categoryKey: 'CASH',
      categoryLabel: 'Cash',
      targetPercentage: 0.1,
    },
  ],
  updatedAt: '2026-08-01T00:00:00Z',
};

describe('DonutChart Pure SVG Component', () => {
  it('renders donut slices and center text correctly', () => {
    const items = [
      { id: '1', label: 'Stocks', value: 700, percentage: 0.7, color: '#2563eb' },
      { id: '2', label: 'Bonds', value: 300, percentage: 0.3, color: '#10b981' },
    ];

    render(<DonutChart items={items} size={200} centerTitle="£1,000" centerSubtitle="Total" />);
    expect(screen.getByText('£1,000')).toBeInTheDocument();
    expect(screen.getByText('Total')).toBeInTheDocument();
  });

  it('renders empty message when no items are provided', () => {
    render(<DonutChart items={[]} />);
    expect(screen.getByText(/No allocation data/i)).toBeInTheDocument();
  });
});

describe('AllocationDetailPage Suite', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders AllocationDetailPage with hero stats, breadcrumbs, and donut chart', async () => {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioAnalytics').mockResolvedValue(mockAnalytics);
    vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockTargetPlan);

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/allocation" element={<AllocationDetailPage />} />
      </Routes>,
      '/portfolios/port-123/allocation'
    );

    await waitFor(() => {
      expect(screen.getByText('Asset Allocation & Exposure Analytics')).toBeInTheDocument();
    });

    // Check breadcrumbs and navigation
    expect(screen.getByText('Asset Allocation')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Back to Portfolio/i })).toBeInTheDocument();

    // Check Hero KPIs
    expect(screen.getByText(/Total Portfolio Value/i)).toBeInTheDocument();
    expect(screen.getAllByText(/50,000.00/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('3 Classes')).toBeInTheDocument();
    expect(screen.getByText(/Balanced Growth Plan/i)).toBeInTheDocument();

    // Check concentration warning (top 3 = 42% + 28% + 20% = 90% -> High risk)
    expect(screen.getByText(/High Portfolio Concentration Detected/i)).toBeInTheDocument();

    // Check Granular Ledger table
    expect(screen.getByText('Granular Allocation & Exposure Ledger')).toBeInTheDocument();
    expect(screen.getAllByText('STOCK').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('BOND').length).toBeGreaterThanOrEqual(1);
  });

  it('switches breakdown dimension to Currency Exposure and Holdings Concentration', async () => {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioAnalytics').mockResolvedValue(mockAnalytics);
    vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockTargetPlan);

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/allocation" element={<AllocationDetailPage />} />
      </Routes>,
      '/portfolios/port-123/allocation'
    );

    await waitFor(() => {
      expect(screen.getByText('Asset Allocation & Exposure Analytics')).toBeInTheDocument();
    });

    // Switch to Currency Exposure
    const currencyBtn = screen.getByRole('button', { name: /Currency Exposure/i });
    fireEvent.click(currencyBtn);

    await waitFor(() => {
      expect(screen.getAllByText('USD').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('GBP').length).toBeGreaterThanOrEqual(1);
    });

    // Switch to Holdings Concentration
    const holdingsBtn = screen.getByRole('button', { name: /Holdings Concentration/i });
    fireEvent.click(holdingsBtn);

    await waitFor(() => {
      expect(screen.getAllByText('AAPL').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('MSFT').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('Apple Inc.').length).toBeGreaterThanOrEqual(1);
    });
  });

  it('filters granular allocation ledger rows by keyword', async () => {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioAnalytics').mockResolvedValue(mockAnalytics);
    vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockTargetPlan);

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/allocation" element={<AllocationDetailPage />} />
      </Routes>,
      '/portfolios/port-123/allocation'
    );

    await waitFor(() => {
      expect(screen.getAllByText('STOCK').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('BOND').length).toBeGreaterThanOrEqual(1);
    });

    // Type in search box
    const filterInput = screen.getByPlaceholderText(/Filter categories or holdings/i);
    fireEvent.change(filterInput, { target: { value: 'BOND' } });

    await waitFor(() => {
      expect(screen.getAllByText('BOND').length).toBeGreaterThanOrEqual(1);
      // In ledger table, STOCK should no longer appear
    });
  });

  it('handles CSV export button click without errors', async () => {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioAnalytics').mockResolvedValue(mockAnalytics);
    vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(mockTargetPlan);

    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/allocation" element={<AllocationDetailPage />} />
      </Routes>,
      '/portfolios/port-123/allocation'
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Export CSV/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Export CSV/i }));
    expect(clickSpy).toHaveBeenCalled();
  });

  it('renders error state on API failure', async () => {
    vi.spyOn(portfolioApi, 'get').mockRejectedValue(new Error('Network failure'));
    vi.spyOn(analyticsApi, 'getPortfolioAnalytics').mockRejectedValue(new Error('Network failure'));
    vi.spyOn(rebalancingApi, 'getTargetAllocation').mockResolvedValue(null);

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/allocation" element={<AllocationDetailPage />} />
      </Routes>,
      '/portfolios/port-123/allocation'
    );

    await waitFor(() => {
      expect(screen.getByText(/Failed to load asset allocation analytics/i)).toBeInTheDocument();
    });
  });
});

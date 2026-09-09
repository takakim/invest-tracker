import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { analyticsApi } from '../api';
import { portfolioApi } from '../api';
import { DividendSummaryCard } from '../features/analytics/DividendSummaryCard';
import { DividendAnalyticsCard } from '../features/analytics/DividendAnalyticsCard';
import { DividendDetailPage } from '../features/analytics/DividendDetailPage';
import type { DividendAnalytics, Portfolio } from '../types';

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
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
  name: 'My Portfolio',
  baseCurrency: 'GBP',
  costBasisMethod: 'FIFO',
  returnMethod: 'TWR',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const mockDividendAnalytics: DividendAnalytics = {
  portfolioId: 'port-123',
  asOf: '2026-08-30T12:00:00Z',
  baseCurrency: 'GBP',
  totalDividendsAllTime: 450.0,
  totalDividendsYtd: 150.0,
  totalDividendsTtm: 300.0,
  totalWithholdingTaxAllTime: 45.0,
  projectedAnnualDividendIncome: 520.0,
  portfolioDividendYieldPercentage: 3.85,
  portfolioYieldOnCostPercentage: 4.50,
  holdings: [
    {
      instrumentId: 'inst-1',
      instrumentName: 'Vanguard S&P 500 ETF',
      ticker: 'VUSA',
      isin: 'IE00B3XXRP09',
      assetClass: 'ETF',
      currentShares: 100,
      totalReceivedAllTime: 300.0,
      totalReceivedYtd: 100.0,
      totalReceivedTtm: 200.0,
      trailingTwelveMonthsDps: 2.0,
      projectedAnnualIncome: 200.0,
      currentYieldPercentage: 2.50,
      yieldOnCostPercentage: 3.20,
      currency: 'GBP',
    },
    {
      instrumentId: 'inst-2',
      instrumentName: 'Apple Inc',
      ticker: 'AAPL',
      isin: 'US0378331005',
      assetClass: 'STOCK',
      currentShares: 80,
      totalReceivedAllTime: 150.0,
      totalReceivedYtd: 50.0,
      totalReceivedTtm: 100.0,
      trailingTwelveMonthsDps: 4.0,
      projectedAnnualIncome: 320.0,
      currentYieldPercentage: 1.60,
      yieldOnCostPercentage: 2.10,
      currency: 'GBP',
    },
  ],
  projectedMonthlyCalendar: [
    { month: 1, monthName: 'Jan', projectedAmount: 0 },
    { month: 2, monthName: 'Feb', projectedAmount: 50.0 },
    { month: 3, monthName: 'Mar', projectedAmount: 130.0 },
    { month: 4, monthName: 'Apr', projectedAmount: 0 },
    { month: 5, monthName: 'May', projectedAmount: 50.0 },
    { month: 6, monthName: 'Jun', projectedAmount: 130.0 },
    { month: 7, monthName: 'Jul', projectedAmount: 0 },
    { month: 8, monthName: 'Aug', projectedAmount: 50.0 },
    { month: 9, monthName: 'Sep', projectedAmount: 130.0 },
    { month: 10, monthName: 'Oct', projectedAmount: 0 },
    { month: 11, monthName: 'Nov', projectedAmount: 50.0 },
    { month: 12, monthName: 'Dec', projectedAmount: 130.0 },
  ],
  projectedCalendar: [],
  monthlyHistory: [
    { yearMonth: '2026-06', netAmount: 100.0, grossAmount: 115.0, taxAmount: 15.0 },
    { yearMonth: '2026-03', netAmount: 50.0, grossAmount: 55.0, taxAmount: 5.0 },
  ],
  yearlyHistory: [
    { year: 2026, netAmount: 150.0, grossAmount: 170.0, taxAmount: 20.0 },
    { year: 2025, netAmount: 300.0, grossAmount: 325.0, taxAmount: 25.0 },
  ],
};

// ─── DividendAnalyticsCard (legacy full card — still exported) ────────────────
describe('DividendAnalyticsCard Component', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders summary metrics and holdings correctly', async () => {
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockResolvedValue(mockDividendAnalytics);
    renderWithProviders(<DividendAnalyticsCard portfolioId="port-123" currency="GBP" />);
    await waitFor(() => {
      expect(screen.getByText('Dividend Analytics & Income Projection')).toBeInTheDocument();
    });
    expect(screen.getByText('Yield: 3.85%')).toBeInTheDocument();
    expect(screen.getByText('YOC: 4.50%')).toBeInTheDocument();
    expect(screen.getByText('520.00')).toBeInTheDocument();
    expect(screen.getByText('VUSA')).toBeInTheDocument();
    expect(screen.getByText('AAPL')).toBeInTheDocument();
  });

  it('switches between tabs (Calendar, Monthly, Yearly)', async () => {
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockResolvedValue(mockDividendAnalytics);
    renderWithProviders(<DividendAnalyticsCard portfolioId="port-123" currency="GBP" />);
    await waitFor(() => {
      expect(screen.getByText('Dividend Analytics & Income Projection')).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole('tab', { name: /12-Month Projected Calendar/i }));
    expect(screen.getByText(/Estimated monthly cashflows over the next 12 months/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: /Monthly History/i }));
    expect(screen.getByText('2026-06')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: /Yearly History/i }));
    expect(screen.getByText('2025')).toBeInTheDocument();
  });

  it('handles empty dividend data gracefully', async () => {
    const emptyAnalytics: DividendAnalytics = {
      ...mockDividendAnalytics,
      portfolioId: 'port-empty',
      holdings: [],
      projectedMonthlyCalendar: [],
      projectedCalendar: [],
      monthlyHistory: [],
      yearlyHistory: [],
      totalDividendsAllTime: 0,
      totalDividendsYtd: 0,
      totalDividendsTtm: 0,
      projectedAnnualDividendIncome: 0,
    };
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockResolvedValue(emptyAnalytics);
    renderWithProviders(<DividendAnalyticsCard portfolioId="port-empty" currency="GBP" />);
    await waitFor(() => {
      expect(screen.getByText('Dividend Analytics & Income Projection')).toBeInTheDocument();
    });
    expect(screen.getByText(/No dividend-bearing instruments or dividend payouts recorded/i)).toBeInTheDocument();
  });

  it('renders nothing when query fails', async () => {
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockRejectedValue(new Error('Network error'));
    const { container } = renderWithProviders(<DividendAnalyticsCard portfolioId="port-err" currency="GBP" />);
    await waitFor(() => {
      expect(container.querySelector('[data-testid="dividend-analytics-card"]')).toBeNull();
    });
  });
});

// ─── DividendSummaryCard ──────────────────────────────────────────────────────
describe('DividendSummaryCard Component', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders KPIs and spark bars', async () => {
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockResolvedValue(mockDividendAnalytics);
    renderWithProviders(<DividendSummaryCard portfolioId="port-123" currency="GBP" />);
    await waitFor(() => {
      expect(screen.getByTestId('dividend-summary-card')).toBeInTheDocument();
    });
    expect(screen.getByText('Dividend Income')).toBeInTheDocument();
    expect(screen.getByText(/Yield 3.85%/i)).toBeInTheDocument();
    expect(screen.getByText(/YOC 4.50%/i)).toBeInTheDocument();
    expect(screen.getByText('View Dividend Details & Projections')).toBeInTheDocument();
  });

  it('renders nothing on error', async () => {
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockRejectedValue(new Error('fail'));
    const { container } = renderWithProviders(<DividendSummaryCard portfolioId="port-err" currency="GBP" />);
    await waitFor(() => {
      expect(container.querySelector('[data-testid="dividend-summary-card"]')).toBeNull();
    });
  });
});

// ─── DividendDetailPage ───────────────────────────────────────────────────────
describe('DividendDetailPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  function renderDetailPage() {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockResolvedValue(mockDividendAnalytics);
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={qc}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/portfolios/port-123/dividends']}>
            <Routes>
              <Route path="/portfolios/:id/dividends" element={<DividendDetailPage />} />
            </Routes>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>,
    );
  }

  it('renders header, KPI strip, and breadcrumb', async () => {
    renderDetailPage();
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Dividend Analytics' })).toBeInTheDocument();
    });
    expect(screen.getByText('My Portfolio')).toBeInTheDocument();
    expect(screen.getByText('Projected Annual')).toBeInTheDocument();
    expect(screen.getByText('Current Yield')).toBeInTheDocument();
    expect(screen.getByText('All-Time Received')).toBeInTheDocument();
  });

  it('renders historical yearly data table', async () => {
    renderDetailPage();
    await waitFor(() => {
      expect(screen.getByText('Historical Dividend Income')).toBeInTheDocument();
    });
    // Use getAllByText since '2026' also appears in the SVG bar chart axis
    expect(screen.getAllByText('2026').length).toBeGreaterThan(0);
    expect(screen.getAllByText('2025').length).toBeGreaterThan(0);
  });

  it('switches to monthly history tab', async () => {
    renderDetailPage();
    await waitFor(() => {
      expect(screen.getByText('Historical Dividend Income')).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole('tab', { name: /Monthly/i }));
    await waitFor(() => {
      expect(screen.getByText('2026-06')).toBeInTheDocument();
    });
    expect(screen.getByText('2026-03')).toBeInTheDocument();
  });

  it('renders holdings breakdown table with sort controls', async () => {
    renderDetailPage();
    await waitFor(() => {
      expect(screen.getByText('Holdings Income Breakdown')).toBeInTheDocument();
    });
    expect(screen.getByText('VUSA')).toBeInTheDocument();
    expect(screen.getByText('AAPL')).toBeInTheDocument();
    // Sort by yield
    fireEvent.click(screen.getByText(/Yield %/));
    expect(screen.getByText('VUSA')).toBeInTheDocument();
  });

  it('renders DRIP projection section with sliders', async () => {
    renderDetailPage();
    await waitFor(() => {
      expect(screen.getByText(/10-Year Dividend Reinvestment Projection/i)).toBeInTheDocument();
    });
    expect(screen.getByLabelText('Dividend growth rate slider')).toBeInTheDocument();
    expect(screen.getByLabelText('Reinvestment percentage slider')).toBeInTheDocument();
    // Projection table rows should exist (Year 1–10)
    expect(screen.getByText('Year 1')).toBeInTheDocument();
    expect(screen.getByText('Year 10')).toBeInTheDocument();
  });

  it('renders strategy insights', async () => {
    renderDetailPage();
    await waitFor(() => {
      expect(screen.getByText('Strategy Insights')).toBeInTheDocument();
    });
    // Should show top yielding insight (VUSA has highest yield)
    expect(screen.getByText('Top Yielding Holding')).toBeInTheDocument();
  });
});

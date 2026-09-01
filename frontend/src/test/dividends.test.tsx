import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { analyticsApi } from '../api';
import { DividendAnalyticsCard } from '../features/analytics/DividendAnalyticsCard';
import type { DividendAnalytics } from '../types';

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
  projectedCalendar: [
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
  monthlyHistory: [
    { yearMonth: '2026-06', netAmount: 100.0, grossAmount: 115.0, taxAmount: 15.0 },
    { yearMonth: '2026-03', netAmount: 50.0, grossAmount: 55.0, taxAmount: 5.0 },
  ],
  yearlyHistory: [
    { year: 2026, netAmount: 150.0, grossAmount: 170.0, taxAmount: 20.0 },
    { year: 2025, netAmount: 300.0, grossAmount: 325.0, taxAmount: 25.0 },
  ],
};

describe('DividendAnalyticsCard Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders summary metrics and holdings correctly', async () => {
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockResolvedValue(mockDividendAnalytics);

    renderWithProviders(<DividendAnalyticsCard portfolioId="port-123" currency="GBP" />);

    // Wait for card to load
    await waitFor(() => {
      expect(screen.getByText('Dividend Analytics & Income Projection')).toBeInTheDocument();
    });

    // Check summary chips & cards
    expect(screen.getByText('Yield: 3.85%')).toBeInTheDocument();
    expect(screen.getByText('YOC: 4.50%')).toBeInTheDocument();
    expect(screen.getByText('Projected Annual Income')).toBeInTheDocument();
    expect(screen.getByText('520.00')).toBeInTheDocument();
    expect(screen.getByText('450.00')).toBeInTheDocument(); // All-time net
    expect(screen.getByText('150.00 GBP')).toBeInTheDocument(); // YTD
    expect(screen.getByText('300.00 GBP')).toBeInTheDocument(); // TTM

    // Check Holdings Breakdown table (default tab)
    expect(screen.getByText('VUSA')).toBeInTheDocument();
    expect(screen.getByText('AAPL')).toBeInTheDocument();
    expect(screen.getByText('200.00')).toBeInTheDocument(); // VUSA projected
    expect(screen.getByText('320.00')).toBeInTheDocument(); // AAPL projected
  });

  it('switches between tabs (Calendar, Monthly, Yearly)', async () => {
    vi.spyOn(analyticsApi, 'getDividendAnalytics').mockResolvedValue(mockDividendAnalytics);

    renderWithProviders(<DividendAnalyticsCard portfolioId="port-123" currency="GBP" />);

    await waitFor(() => {
      expect(screen.getByText('Dividend Analytics & Income Projection')).toBeInTheDocument();
    });

    // Switch to 12-Month Projected Calendar
    fireEvent.click(screen.getByRole('tab', { name: /12-Month Projected Calendar/i }));
    expect(screen.getByText(/Estimated monthly cashflows over the next 12 months/i)).toBeInTheDocument();
    expect(screen.getByText('Jan')).toBeInTheDocument();
    expect(screen.getByText('Mar')).toBeInTheDocument();

    // Switch to Monthly History tab
    fireEvent.click(screen.getByRole('tab', { name: /Monthly History/i }));
    expect(screen.getByText('2026-06')).toBeInTheDocument();
    expect(screen.getByText('2026-03')).toBeInTheDocument();

    // Switch to Yearly History tab
    fireEvent.click(screen.getByRole('tab', { name: /Yearly History/i }));
    expect(screen.getByText('2026')).toBeInTheDocument();
    expect(screen.getByText('2025')).toBeInTheDocument();
  });

  it('handles empty dividend data gracefully', async () => {
    const emptyAnalytics: DividendAnalytics = {
      portfolioId: 'port-empty',
      asOf: '2026-08-30T12:00:00Z',
      baseCurrency: 'GBP',
      totalDividendsAllTime: 0,
      totalDividendsYtd: 0,
      totalDividendsTtm: 0,
      totalWithholdingTaxAllTime: 0,
      projectedAnnualDividendIncome: 0,
      portfolioDividendYieldPercentage: 0,
      portfolioYieldOnCostPercentage: 0,
      holdings: [],
      projectedMonthlyCalendar: [],
      projectedCalendar: [],
      monthlyHistory: [],
      yearlyHistory: [],
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

    // Wait and verify null render
    await waitFor(() => {
      expect(container.querySelector('[data-testid="dividend-analytics-card"]')).toBeNull();
    });
  });
});

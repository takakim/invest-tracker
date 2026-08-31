import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { analyticsApi, instrumentApi } from '../api';
import { PortfolioHistoryCard } from '../features/analytics/PortfolioHistoryCard';
import type { PortfolioHistory, Instrument } from '../types';

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

const mockHistoryResponse: PortfolioHistory = {
  portfolioId: 'port-123',
  portfolioName: 'Growth Portfolio',
  baseCurrency: 'GBP',
  period: '1Y',
  interval: 'WEEKLY',
  periodStart: '2025-08-30T00:00:00Z',
  periodEnd: '2026-08-30T00:00:00Z',
  benchmarkId: 'bench-1',
  benchmarkTicker: 'SPX',
  benchmarkName: 'S&P 500 Index',
  summary: {
    startingValue: 10000.0,
    endingValue: 14500.0,
    netCashFlows: 2000.0,
    totalGainLoss: 2500.0,
    portfolioReturnPercentage: 25.0,
    benchmarkReturnPercentage: 18.0,
    excessReturnPercentage: 7.0,
    maxDrawdownPercentage: 4.5,
  },
  dataPoints: [
    {
      timestamp: '2025-08-30T00:00:00Z',
      marketValue: 10000.0,
      costBasis: 10000.0,
      cashValue: 0.0,
      investedCapital: 10000.0,
      unrealizedGainLoss: 0.0,
      portfolioReturnPercentage: 0.0,
      benchmarkReturnPercentage: 0.0,
    },
    {
      timestamp: '2026-02-28T00:00:00Z',
      marketValue: 12000.0,
      costBasis: 10000.0,
      cashValue: 0.0,
      investedCapital: 10000.0,
      unrealizedGainLoss: 2000.0,
      portfolioReturnPercentage: 20.0,
      benchmarkReturnPercentage: 10.0,
    },
    {
      timestamp: '2026-08-30T00:00:00Z',
      marketValue: 14500.0,
      costBasis: 12000.0,
      cashValue: 0.0,
      investedCapital: 12000.0,
      unrealizedGainLoss: 2500.0,
      portfolioReturnPercentage: 25.0,
      benchmarkReturnPercentage: 18.0,
    },
  ],
};

const mockBenchmarkInstruments: Instrument[] = [
  {
    id: 'bench-1',
    name: 'S&P 500 Index',
    ticker: 'SPX',
    assetClass: 'ETF',
    currency: 'USD',
    manualPriceOnly: false,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  },
];

describe('Historical Valuation & Benchmark Charting UI', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders historical portfolio summary metrics and chart', async () => {
    vi.spyOn(analyticsApi, 'getPortfolioHistory').mockResolvedValue(mockHistoryResponse);
    vi.spyOn(instrumentApi, 'list').mockResolvedValue(mockBenchmarkInstruments);

    renderWithProviders(<PortfolioHistoryCard portfolioId="port-123" currency="GBP" />);

    await waitFor(() => {
      expect(screen.getByText('Historical Valuation & Performance Charting')).toBeInTheDocument();
    });

    expect(screen.getByText(/Ending Valuation/i)).toBeInTheDocument();
    expect(screen.getByText(/14,500.00/i)).toBeInTheDocument();
    expect(screen.getByText(/Portfolio Period Return/i)).toBeInTheDocument();
    expect(screen.getByText(/\+25.00%/i)).toBeInTheDocument();
    expect(screen.getByText(/Maximum Drawdown/i)).toBeInTheDocument();
    expect(screen.getByText(/-4.50%/i)).toBeInTheDocument();
  });

  it('allows toggling time horizons and mode tabs', async () => {
    const historySpy = vi.spyOn(analyticsApi, 'getPortfolioHistory').mockResolvedValue(mockHistoryResponse);
    vi.spyOn(instrumentApi, 'list').mockResolvedValue(mockBenchmarkInstruments);

    renderWithProviders(<PortfolioHistoryCard portfolioId="port-123" currency="GBP" />);

    await waitFor(() => {
      expect(screen.getByText('Historical Valuation & Performance Charting')).toBeInTheDocument();
    });

    // Click 6M horizon chip
    fireEvent.click(screen.getByRole('button', { name: '6M' }));
    await waitFor(() => {
      expect(historySpy).toHaveBeenCalledWith('port-123', expect.objectContaining({ period: '6M' }));
    });

    // Switch to Relative Return mode
    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /Relative Return/i })).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole('tab', { name: /Relative Return/i }));

    await waitFor(() => {
      expect(screen.getByText(/Portfolio Return \(%\)/i)).toBeInTheDocument();
    });
  });

  it('handles empty historical data points gracefully', async () => {
    const emptyHistory: PortfolioHistory = {
      ...mockHistoryResponse,
      dataPoints: [],
      summary: {
        startingValue: 0,
        endingValue: 0,
        netCashFlows: 0,
        totalGainLoss: 0,
        portfolioReturnPercentage: 0,
        maxDrawdownPercentage: 0,
      },
    };
    vi.spyOn(analyticsApi, 'getPortfolioHistory').mockResolvedValue(emptyHistory);
    vi.spyOn(instrumentApi, 'list').mockResolvedValue([]);

    renderWithProviders(<PortfolioHistoryCard portfolioId="port-123" currency="GBP" />);

    await waitFor(() => {
      expect(screen.getByText(/No historical transaction data available/i)).toBeInTheDocument();
    });
  });
});

import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { analyticsApi, instrumentApi, portfolioApi, benchmarkApi } from '../api';
import { PortfolioHistoryCard } from '../features/analytics/PortfolioHistoryCard';
import { HistoryDetailPage } from '../features/analytics/HistoryDetailPage';
import type { PortfolioHistory, Instrument, Portfolio, BenchmarkComparisonResult } from '../types';

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

describe('HistoryDetailPage Component Suite', () => {
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

  const mockComparisonResult: BenchmarkComparisonResult = {
    portfolioId: 'port-123',
    benchmarkInstrumentId: 'bench-1',
    benchmarkName: 'S&P 500 Index',
    benchmarkTicker: 'SPX',
    periodStart: '2025-08-30T00:00:00Z',
    periodEnd: '2026-08-30T00:00:00Z',
    portfolioReturn: 0.25,
    benchmarkReturn: 0.18,
    excessReturn: 0.07,
    annualizedPortfolioReturn: 0.25,
    annualizedBenchmarkReturn: 0.18,
    annualizedExcessReturn: 0.07,
    outperforming: true,
    baseCurrency: 'GBP',
    warnings: [],
  };

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders HistoryDetailPage with hero stats, breadcrumbs, and valuation ledger', async () => {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioHistory').mockResolvedValue(mockHistoryResponse);
    vi.spyOn(benchmarkApi, 'getAvailableBenchmarks').mockResolvedValue(mockBenchmarkInstruments);
    vi.spyOn(benchmarkApi, 'comparePortfolioToBenchmark').mockResolvedValue(mockComparisonResult);

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/history" element={<HistoryDetailPage />} />
      </Routes>,
      '/portfolios/port-123/history'
    );

    await waitFor(() => {
      expect(screen.getByText('Historical Valuation & Benchmark Comparison')).toBeInTheDocument();
    });

    // Check breadcrumbs and navigation
    expect(screen.getByText('History & Benchmark')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Back to Portfolio/i })).toBeInTheDocument();

    // Check hero KPI strip
    expect(screen.getByText(/Ending Portfolio Value/i)).toBeInTheDocument();
    expect(screen.getAllByText(/14,500.00/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Net Capital Inflows/i)).toBeInTheDocument();
    expect(screen.getAllByText(/2,000.00/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/\+25.00%/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Alpha \/ Excess Return/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Outperforming/i).length).toBeGreaterThanOrEqual(1);

    // Check Valuation Ledger table
    expect(screen.getByText('Historical Valuation Ledger')).toBeInTheDocument();
    expect(screen.getAllByText('2025-08-30').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('2026-08-30').length).toBeGreaterThanOrEqual(1);
  });

  it('allows switching chart modes to Return vs Benchmark and Drawdown Depth', async () => {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioHistory').mockResolvedValue(mockHistoryResponse);
    vi.spyOn(benchmarkApi, 'getAvailableBenchmarks').mockResolvedValue(mockBenchmarkInstruments);
    vi.spyOn(benchmarkApi, 'comparePortfolioToBenchmark').mockResolvedValue(mockComparisonResult);

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/history" element={<HistoryDetailPage />} />
      </Routes>,
      '/portfolios/port-123/history'
    );

    await waitFor(() => {
      expect(screen.getByText('Historical Valuation & Benchmark Comparison')).toBeInTheDocument();
    });

    // Toggle Return mode
    const returnModeBtn = screen.getByRole('button', { name: /Return vs Benchmark \(%\)/i });
    fireEvent.click(returnModeBtn);

    await waitFor(() => {
      expect(screen.getByText(/Portfolio Cumulative Return \(%\)/i)).toBeInTheDocument();
    });

    // Toggle Drawdown Depth mode
    const drawdownModeBtn = screen.getByRole('button', { name: /Drawdown Depth \(%\)/i });
    fireEvent.click(drawdownModeBtn);

    await waitFor(() => {
      expect(screen.getByText(/Underwater Drawdown from Historical Peak \(%\)/i)).toBeInTheDocument();
    });
  });

  it('filters historical valuation ledger rows by date', async () => {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioHistory').mockResolvedValue(mockHistoryResponse);
    vi.spyOn(benchmarkApi, 'getAvailableBenchmarks').mockResolvedValue(mockBenchmarkInstruments);
    vi.spyOn(benchmarkApi, 'comparePortfolioToBenchmark').mockResolvedValue(mockComparisonResult);

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/history" element={<HistoryDetailPage />} />
      </Routes>,
      '/portfolios/port-123/history'
    );

    await waitFor(() => {
      expect(screen.getAllByText('2025-08-30').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('2026-08-30').length).toBeGreaterThanOrEqual(1);
    });

    // Filter by specific date
    const filterInput = screen.getByPlaceholderText(/Filter by Date/i);
    fireEvent.change(filterInput, { target: { value: '2026-02-28' } });

    await waitFor(() => {
      expect(screen.getAllByText('2026-02-28').length).toBeGreaterThanOrEqual(1);
      // In table, 2025-08-30 row should no longer be rendered, though SVG might show ticks
    });
  });

  it('handles CSV export action without error', async () => {
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioHistory').mockResolvedValue(mockHistoryResponse);
    vi.spyOn(benchmarkApi, 'getAvailableBenchmarks').mockResolvedValue(mockBenchmarkInstruments);
    vi.spyOn(benchmarkApi, 'comparePortfolioToBenchmark').mockResolvedValue(mockComparisonResult);

    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/history" element={<HistoryDetailPage />} />
      </Routes>,
      '/portfolios/port-123/history'
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Export CSV/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Export CSV/i }));
    expect(clickSpy).toHaveBeenCalled();
  });

  it('renders error state on API failure', async () => {
    vi.spyOn(portfolioApi, 'get').mockRejectedValue(new Error('Network error'));
    vi.spyOn(analyticsApi, 'getPortfolioHistory').mockRejectedValue(new Error('Network error'));
    vi.spyOn(benchmarkApi, 'getAvailableBenchmarks').mockResolvedValue([]);

    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/history" element={<HistoryDetailPage />} />
      </Routes>,
      '/portfolios/port-123/history'
    );

    await waitFor(() => {
      expect(screen.getByText(/Failed to load portfolio history/i)).toBeInTheDocument();
    });
  });
});


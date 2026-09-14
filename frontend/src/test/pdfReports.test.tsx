import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { portfolioApi, analyticsApi, performanceApi, accountApi, exportApi, taxAllowanceApi } from '../api';
import { ExportReportModal } from '../features/analytics/ExportReportModal';
import { ExecutiveSummaryReportPage } from '../features/reports/ExecutiveSummaryReportPage';
import { TaxAllowanceDetailPage } from '../features/tax/TaxAllowanceDetailPage';
import type { Portfolio, PortfolioAnalytics, PerformanceResult, Account, CashFlowAnalytics, TaxReportResponse, AvailableTaxYearsResponse } from '../types';

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
  name: 'Growth & Wealth Portfolio',
  baseCurrency: 'GBP',
  costBasisMethod: 'FIFO',
  returnMethod: 'TWR',
  status: 'ACTIVE',
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
};

const mockAnalytics: PortfolioAnalytics = {
  portfolioId: 'port-123',
  asOf: '2025-03-15T12:00:00Z',
  baseCurrency: 'GBP',
  totalCurrentValue: 55000,
  totalCostBasis: 45000,
  totalUnrealizedGainLoss: 10000,
  totalUnrealizedReturnPercentage: 0.2222,
  totalRealizedGainLoss: 3000,
  totalCashValue: 5000,
  byAssetClass: [
    { category: 'STOCK', marketValue: 40000, percentage: 0.8, costBasis: 32000, unrealizedGainLoss: 8000 },
    { category: 'CASH', marketValue: 10000, percentage: 0.2, costBasis: 10000, unrealizedGainLoss: 0 },
  ],
  byCurrency: [
    { category: 'GBP', marketValue: 45000, percentage: 0.9, costBasis: 37000, unrealizedGainLoss: 8000 },
    { category: 'USD', marketValue: 5000, percentage: 0.1, costBasis: 5000, unrealizedGainLoss: 0 },
  ],
  byAccount: [],
  topHoldings: [
    {
      instrumentId: 'inst-1',
      instrumentName: 'Apple Inc.',
      ticker: 'AAPL',
      assetClass: 'STOCK',
      quantity: 50,
      currentPrice: 180,
      marketValue: 9000,
      costBasis: 7500,
      unrealizedGainLoss: 1500,
      weightPercentage: 0.18,
      currency: 'GBP',
    },
  ],
  warnings: [],
};

const mockPerformance: PerformanceResult = {
  portfolioId: 'port-123',
  asOf: '2025-03-15T12:00:00Z',
  returnMethod: 'TWR',
  twrReturn: 0.25,
  twrAnnualized: 0.12,
  mwrReturn: 0.22,
  totalRealizedGainLoss: 3000,
  totalDividendIncome: 1200,
  totalInterestIncome: 100,
  totalFees: 50,
  totalTaxes: 0,
  totalNetIncome: 1250,
  totalCostBasis: 45000,
  totalNetDeposits: 42000,
  currency: 'GBP',
  valuationBasis: 'MARKET_VALUE',
  byAccount: [],
};

const mockCashFlows: CashFlowAnalytics = {
  portfolioId: 'port-123',
  portfolioName: 'Growth & Wealth Portfolio',
  baseCurrency: 'GBP',
  period: 'ALL',
  groupBy: 'MONTH',
  periodStart: '2025-01-01T00:00:00Z',
  periodEnd: '2025-03-15T00:00:00Z',
  summary: {
    totalDeposits: 45000,
    totalWithdrawals: 3000,
    netContributions: 42000,
    totalDividends: 1200,
    totalInterest: 100,
    totalFees: 50,
    netCashFlow: 43250,
    avgMonthlyContribution: 3500,
    activeContributionMonths: 12,
    cumulativeContributions: 42000,
    currentPortfolioValue: 55000,
    capitalContributionsPercentage: 76.4,
    marketGrowthPercentage: 23.6,
    baseCurrency: 'GBP',
  },
  periods: [],
  accountBreakdown: [
    {
      accountId: 'acc-1',
      accountName: 'Trading 212 ISA',
      brokerName: 'Trading 212',
      accountCurrency: 'GBP',
      currentCashBalance: 5000,
      currentCashBalanceInBase: 5000,
      totalDeposits: 45000,
      totalWithdrawals: 3000,
      netContributions: 42000,
    },
  ],
  warnings: [],
};

const mockAccounts: Account[] = [
  {
    id: 'acc-1',
    portfolioId: 'port-123',
    name: 'Trading 212 ISA',
    brokerName: 'Trading 212',
    accountCurrency: 'GBP',
    status: 'ACTIVE',
    taxTreatment: 'TAX_EXEMPT',
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  },
];

const mockTaxReport: TaxReportResponse = {
  portfolioId: 'port-123',
  portfolioName: 'Growth & Wealth Portfolio',
  baseCurrency: 'GBP',
  taxYear: '2024/25',
  taxRegime: 'UK_HMRC',
  periodStart: '2024-04-06T00:00:00Z',
  periodEnd: '2025-04-05T23:59:59Z',
  capitalGains: {
    totalDisposalProceeds: 15000,
    totalDisposalCostBasis: 10000,
    grossRealizedGains: 5000,
    grossRealizedLosses: 0,
    netRealizedGainLoss: 5000,
    lossCarryforwardApplied: 0,
    netTaxableGainBeforeAllowance: 5000,
    annualExemptAmount: 3000,
    allowanceUsed: 3000,
    allowanceRemaining: 0,
    taxableCapitalGain: 2000,
    estimatedTaxBasicRate: 200,
    estimatedTaxHigherRate: 400,
    basicTaxRatePercentage: 10,
    higherTaxRatePercentage: 20,
    totalDisposalsCount: 3,
  },
  dividendIncome: {
    totalGrossDividends: 1500,
    totalWithholdingTax: 0,
    netDividendsReceived: 1500,
    annualDividendAllowance: 500,
    allowanceUsed: 500,
    allowanceRemaining: 0,
    taxableDividendIncome: 1000,
    estimatedTaxBasicRate: 87.5,
    estimatedTaxHigherRate: 337.5,
    estimatedTaxAdditionalRate: 393.5,
    basicTaxRatePercentage: 8.75,
    higherTaxRatePercentage: 33.75,
    additionalTaxRatePercentage: 39.35,
    totalDividendsCount: 4,
  },
  shelteredSummary: {
    shelteredRealizedGains: 2000,
    shelteredRealizedLosses: 0,
    shelteredGrossDividends: 800,
    estimatedCapitalGainsTaxSaved: 400,
    estimatedDividendTaxSaved: 70,
    totalEstimatedTaxSaved: 470,
  },
  lossHarvestOpportunities: [],
  disposals: [],
  dividends: [],
  warnings: [],
};

const mockAvailableYears: AvailableTaxYearsResponse = {
  availableUkTaxYears: ['2024/25', '2023/24'],
  availableCalendarYears: ['2025', '2024'],
  currentUkTaxYear: '2024/25',
  currentCalendarYear: '2025',
};

describe('PDF Export & Executive Summary Feature Suite', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(analyticsApi, 'getPortfolioAnalytics').mockResolvedValue(mockAnalytics);
    vi.spyOn(analyticsApi, 'getCashFlowAnalytics').mockResolvedValue(mockCashFlows);
    vi.spyOn(performanceApi, 'get').mockResolvedValue(mockPerformance);
    vi.spyOn(accountApi, 'list').mockResolvedValue(mockAccounts);
    vi.spyOn(taxAllowanceApi, 'getTaxReport').mockResolvedValue(mockTaxReport);
    vi.spyOn(taxAllowanceApi, 'getAvailableTaxYears').mockResolvedValue(mockAvailableYears);
    vi.spyOn(exportApi, 'downloadExecutiveSummaryPdf').mockResolvedValue();
    vi.spyOn(exportApi, 'downloadTaxReportPdf').mockResolvedValue();
  });

  describe('ExportReportModal PDF Actions', () => {
    it('triggers executive PDF download and tax PDF download from modal', async () => {
      const handleClose = vi.fn();
      renderWithProviders(
        <ExportReportModal
          open={true}
          portfolioId="port-123"
          portfolioName="Growth & Wealth Portfolio"
          onClose={handleClose}
        />
      );

      expect(screen.getByText('Export Portfolio Statements')).toBeInTheDocument();
      expect(screen.getByText('Executive Summary Report')).toBeInTheDocument();
      expect(screen.getByText('Tax Year Audit Statement')).toBeInTheDocument();

      // Download Executive PDF
      const execPdfBtn = screen.getByRole('button', { name: 'Download Executive PDF' });
      fireEvent.click(execPdfBtn);
      await waitFor(() => {
        expect(exportApi.downloadExecutiveSummaryPdf).toHaveBeenCalledWith('port-123');
      });

      // Download Tax PDF
      const taxPdfBtn = screen.getByRole('button', { name: 'Download Tax PDF' });
      fireEvent.click(taxPdfBtn);
      await waitFor(() => {
        expect(exportApi.downloadTaxReportPdf).toHaveBeenCalledWith('port-123');
      });
    });

    it('navigates to summary report page when clicking View', async () => {
      const handleClose = vi.fn();
      renderWithProviders(
        <Routes>
          <Route
            path="/"
            element={
              <ExportReportModal
                open={true}
                portfolioId="port-123"
                portfolioName="Growth & Wealth Portfolio"
                onClose={handleClose}
              />
            }
          />
          <Route path="/portfolios/:id/reports/summary" element={<div>Executive Summary Page View</div>} />
        </Routes>,
        '/'
      );

      const viewBtn = screen.getByRole('button', { name: 'View' });
      fireEvent.click(viewBtn);

      await waitFor(() => {
        expect(handleClose).toHaveBeenCalled();
        expect(screen.getByText('Executive Summary Page View')).toBeInTheDocument();
      });
    });
  });

  describe('ExecutiveSummaryReportPage', () => {
    it('renders report cards, holdings matrix, accounts, and triggers download & print', async () => {
      const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {});

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/reports/summary" element={<ExecutiveSummaryReportPage />} />
        </Routes>,
        '/portfolios/port-123/reports/summary'
      );

      // Verify header and KPI values
      expect(await screen.findByText('INVEST-TRACKER • EXECUTIVE SUMMARY REPORT')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Growth & Wealth Portfolio' })).toBeInTheDocument();
      expect(screen.getByText('TOTAL PORTFOLIO VALUE')).toBeInTheDocument();
      expect(screen.getByText('NET CAPITAL INVESTED')).toBeInTheDocument();
      expect(screen.getByText('TOTAL CUMULATIVE RETURN')).toBeInTheDocument();

      // Verify holdings table
      expect(screen.getByText('Apple Inc.')).toBeInTheDocument();
      expect(screen.getByText('AAPL')).toBeInTheDocument();

      // Verify accounts table
      expect(screen.getByText('Trading 212 ISA')).toBeInTheDocument();
      expect(screen.getByText('Trading 212')).toBeInTheDocument();

      // Trigger Print
      const printBtn = screen.getByRole('button', { name: 'Print / Save PDF' });
      fireEvent.click(printBtn);
      expect(printSpy).toHaveBeenCalled();

      // Trigger Download PDF
      const downloadBtn = screen.getByRole('button', { name: 'Download Official PDF' });
      fireEvent.click(downloadBtn);
      await waitFor(() => {
        expect(exportApi.downloadExecutiveSummaryPdf).toHaveBeenCalledWith('port-123');
      });

      printSpy.mockRestore();
    });
  });

  describe('TaxAllowanceDetailPage PDF Export', () => {
    it('triggers tax report PDF download when clicking Download PDF Audit button', async () => {
      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:portfolioId/tax" element={<TaxAllowanceDetailPage />} />
        </Routes>,
        '/portfolios/port-123/tax'
      );

      const downloadPdfBtn = await screen.findByRole('button', { name: 'Download PDF Audit' });
      expect(downloadPdfBtn).toBeInTheDocument();

      fireEvent.click(downloadPdfBtn);

      await waitFor(() => {
        expect(exportApi.downloadTaxReportPdf).toHaveBeenCalledWith('port-123', {
          taxYear: '2024/25',
          regime: 'UK_HMRC',
        });
      });
    });
  });
});

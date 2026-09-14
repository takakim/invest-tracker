import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { taxAllowanceApi, portfolioApi } from '../api';
import { TaxAllowanceDetailPage } from '../features/tax/TaxAllowanceDetailPage';
import type { Portfolio, TaxReportResponse, AvailableTaxYearsResponse } from '../types';

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
  name: 'UK Taxable Portfolio',
  baseCurrency: 'GBP',
  costBasisMethod: 'FIFO',
  returnMethod: 'TWR',
  status: 'ACTIVE',
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
};

const mockAvailableYears: AvailableTaxYearsResponse = {
  availableUkTaxYears: ['2025/26', '2024/25', '2023/24'],
  availableCalendarYears: ['2025', '2024', '2023'],
  currentUkTaxYear: '2024/25',
  currentCalendarYear: '2024',
};

const mockReport: TaxReportResponse = {
  portfolioId: 'port-123',
  portfolioName: 'UK Taxable Portfolio',
  baseCurrency: 'GBP',
  taxRegime: 'UK_HMRC',
  taxYear: '2024/25',
  periodStart: '2024-04-06T00:00:00Z',
  periodEnd: '2025-04-05T23:59:59Z',
  capitalGains: {
    totalDisposalProceeds: 15000,
    totalDisposalCostBasis: 10000,
    grossRealizedGains: 5000,
    grossRealizedLosses: 500,
    netRealizedGainLoss: 4500,
    lossCarryforwardApplied: 0,
    netTaxableGainBeforeAllowance: 4500,
    annualExemptAmount: 3000,
    allowanceUsed: 3000,
    allowanceRemaining: 0,
    taxableCapitalGain: 1500,
    estimatedTaxBasicRate: 150,
    estimatedTaxHigherRate: 300,
    basicTaxRatePercentage: 10,
    higherTaxRatePercentage: 20,
    totalDisposalsCount: 2,
  },
  dividendIncome: {
    totalGrossDividends: 800,
    totalWithholdingTax: 50,
    netDividendsReceived: 750,
    annualDividendAllowance: 500,
    allowanceUsed: 500,
    allowanceRemaining: 0,
    taxableDividendIncome: 300,
    estimatedTaxBasicRate: 26.25,
    estimatedTaxHigherRate: 101.25,
    estimatedTaxAdditionalRate: 118.05,
    basicTaxRatePercentage: 8.75,
    higherTaxRatePercentage: 33.75,
    additionalTaxRatePercentage: 39.35,
    totalDividendsCount: 1,
  },
  shelteredSummary: {
    shelteredRealizedGains: 3500,
    shelteredRealizedLosses: 0,
    shelteredGrossDividends: 600,
    estimatedCapitalGainsTaxSaved: 700,
    estimatedDividendTaxSaved: 202.5,
    totalEstimatedTaxSaved: 902.5,
  },
  lossHarvestOpportunities: [
    {
      accountId: 'acc-1',
      accountName: 'General Investment Account',
      instrumentId: 'inst-3',
      instrumentName: 'Microsoft Corp',
      ticker: 'MSFT',
      quantity: 20,
      currentPrice: 350,
      priceCurrency: 'USD',
      currentMarketValueBase: 7000,
      totalCostBasisBase: 8000,
      unrealizedLossBase: 1000,
    },
  ],
  disposals: [
    {
      disposalTransactionId: 'tx-1',
      accountId: 'acc-1',
      accountName: 'General Investment Account',
      taxTreatment: 'TAXABLE',
      instrumentId: 'inst-1',
      instrumentName: 'Apple Inc',
      ticker: 'AAPL',
      disposalDate: '2024-06-15T10:00:00Z',
      quantity: 10,
      proceedsNative: 15000,
      costBasisNative: 10000,
      nativeCurrency: 'GBP',
      proceedsBase: 15000,
      costBasisBase: 10000,
      realizedGainLossBase: 5000,
    },
    {
      disposalTransactionId: 'tx-2',
      accountId: 'acc-1',
      accountName: 'General Investment Account',
      taxTreatment: 'TAXABLE',
      instrumentId: 'inst-2',
      instrumentName: 'Tesla Inc',
      ticker: 'TSLA',
      disposalDate: '2024-07-20T11:00:00Z',
      quantity: 5,
      proceedsNative: 500,
      costBasisNative: 1000,
      nativeCurrency: 'GBP',
      proceedsBase: 500,
      costBasisBase: 1000,
      realizedGainLossBase: -500,
    },
  ],
  dividends: [
    {
      transactionId: 'tx-div-1',
      accountId: 'acc-1',
      accountName: 'General Investment Account',
      taxTreatment: 'TAXABLE',
      instrumentId: 'inst-1',
      instrumentName: 'Apple Inc',
      ticker: 'AAPL',
      paymentDate: '2024-08-10T08:00:00Z',
      grossAmountNative: 800,
      withholdingTaxNative: 50,
      nativeCurrency: 'GBP',
      grossAmountBase: 800,
      withholdingTaxBase: 50,
      netAmountBase: 750,
    },
  ],
  warnings: [],
};

describe('Tax Allowance Feature Suite', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
    vi.spyOn(taxAllowanceApi, 'getAvailableTaxYears').mockResolvedValue(mockAvailableYears);
    vi.spyOn(taxAllowanceApi, 'getTaxReport').mockResolvedValue(mockReport);
    vi.spyOn(taxAllowanceApi, 'updateSettings').mockResolvedValue({
      id: 'tax-settings-1',
      portfolioId: 'port-123',
      taxYear: '2024/25',
      taxRegime: 'UK_HMRC',
      cgtAllowance: 3000,
      dividendAllowance: 500,
      lossCarryforward: 1000,
      notes: null,
      updatedAt: '2025-01-01T00:00:00Z',
    });
  });

  it('renders TaxAllowanceDetailPage with KPI summary cards and gauges', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/tax" element={<TaxAllowanceDetailPage />} />
      </Routes>,
      '/portfolios/port-123/tax'
    );

    expect(await screen.findByText('Capital Gains & Dividend Allowance Tracker')).toBeInTheDocument();
    expect(screen.getByText('UK HMRC')).toBeInTheDocument();

    // Check CGT KPI metrics
    expect(screen.getByText(/Capital Gains Tax Allowance/i)).toBeInTheDocument();
    expect(screen.getByText('Taxable Capital Gain')).toBeInTheDocument();

    // Check Dividend KPI metrics
    expect(screen.getByText(/^Dividend Allowance/i)).toBeInTheDocument();
    expect(screen.getByText('Taxable Dividend Income')).toBeInTheDocument();

    // Check Tax-Sheltered Card
    expect(screen.getByText('Tax-Sheltered Wealth Growth')).toBeInTheDocument();
    expect(screen.getByText(/Estimated Tax Saved:/i)).toBeInTheDocument();

    // Check Loss Harvesting Card
    expect(screen.getByText('Tax-Loss Harvesting Opportunities')).toBeInTheDocument();
    expect(screen.getByText('Microsoft Corp')).toBeInTheDocument();
  });

  it('switches tabs between Capital Disposals and Dividends Received', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/tax" element={<TaxAllowanceDetailPage />} />
      </Routes>,
      '/portfolios/port-123/tax'
    );

    await screen.findByText('Capital Gains & Dividend Allowance Tracker');

    // Default tab is Capital Disposals (disposals table visible)
    expect(screen.getByText('Apple Inc')).toBeInTheDocument();
    expect(screen.getByText('Tesla Inc')).toBeInTheDocument();

    // Switch to Dividends tab
    const divTab = screen.getByRole('tab', { name: /Taxable Dividends/i });
    fireEvent.click(divTab);

    await waitFor(() => {
      expect(screen.getByText('Payment Date')).toBeInTheDocument();
      expect(screen.getByText('Gross Amount (GBP)')).toBeInTheDocument();
    });
  });

  it('allows opening tax settings modal, updating settings, and saving', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/tax" element={<TaxAllowanceDetailPage />} />
      </Routes>,
      '/portfolios/port-123/tax'
    );

    await screen.findByText('Capital Gains & Dividend Allowance Tracker');

    // Click Tax Settings button
    const configBtn = screen.getByRole('button', { name: /Tax Settings/i });
    fireEvent.click(configBtn);

    expect(await screen.findByText(/Tax Settings & Custom Allowances/i)).toBeInTheDocument();

    // Update loss carryforward
    const lossInput = screen.getByLabelText(/Loss Carryforward from Prior Years/i);
    fireEvent.change(lossInput, { target: { value: '1000' } });

    // Click Save Settings
    const saveBtn = screen.getByRole('button', { name: /Save Settings/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(taxAllowanceApi.updateSettings).toHaveBeenCalledWith(
        'port-123',
        expect.objectContaining({
          lossCarryforward: 1000,
        })
      );
    });
  });

  it('switches tax year via select dropdown', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/portfolios/:id/tax" element={<TaxAllowanceDetailPage />} />
      </Routes>,
      '/portfolios/port-123/tax'
    );

    await screen.findByText('Capital Gains & Dividend Allowance Tracker');

    const yearSelect = screen.getByLabelText('Tax Year');
    fireEvent.mouseDown(yearSelect);

    const yearOption = await screen.findByRole('option', { name: '2023/24' });
    fireEvent.click(yearOption);

    await waitFor(() => {
      expect(taxAllowanceApi.getTaxReport).toHaveBeenCalledWith('port-123', '2023/24', 'UK_HMRC');
    });
  });
});

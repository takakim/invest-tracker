import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { corporateActionApi, portfolioApi, accountApi } from '../api';
import { CorporateActionsBanner } from '../features/corporate-actions/CorporateActionsBanner';
import { CorporateActionsDetailPage } from '../features/corporate-actions/CorporateActionsDetailPage';
import type { CorporateAction, Portfolio, Account, ScanCorporateActionsResponse } from '../types';

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
  id: 'port-1',
  name: 'Growth Tech Portfolio',
  baseCurrency: 'USD',
  costBasisMethod: 'FIFO',
  returnMethod: 'XIRR',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const mockAccounts: Account[] = [
  {
    id: 'acc-1',
    portfolioId: 'port-1',
    name: 'Trading Account USD',
    brokerName: 'Freetrade',
    accountCurrency: 'USD',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
];

const mockActions: CorporateAction[] = [
  {
    id: 'act-1',
    instrumentId: 'inst-1',
    instrumentName: 'Apple Inc.',
    ticker: 'AAPL',
    isin: 'US0378331005',
    assetClass: 'STOCK',
    actionType: 'STOCK_SPLIT',
    status: 'PENDING',
    exDate: '2026-06-10T00:00:00Z',
    recordDate: '2026-06-11T00:00:00Z',
    paymentDate: '2026-06-12T00:00:00Z',
    ratioFrom: 1,
    ratioTo: 4,
    amountPerShare: null,
    currency: 'USD',
    description: '4:1 Stock Split',
    source: 'YAHOO_FINANCE',
    heldQuantityAtExDate: 100,
    proposedImpactQuantity: 300,
    proposedImpactAmount: null,
    suggestedAccountId: 'acc-1',
    suggestedAccountName: 'Trading Account USD',
    appliedTransactionId: null,
    createdAt: '2026-06-01T00:00:00Z',
  },
  {
    id: 'act-2',
    instrumentId: 'inst-2',
    instrumentName: 'Microsoft Corp',
    ticker: 'MSFT',
    isin: 'US5949181045',
    assetClass: 'STOCK',
    actionType: 'DIVIDEND',
    status: 'PENDING',
    exDate: '2026-05-15T00:00:00Z',
    recordDate: '2026-05-16T00:00:00Z',
    paymentDate: '2026-06-01T00:00:00Z',
    ratioFrom: null,
    ratioTo: null,
    amountPerShare: 0.75,
    currency: 'USD',
    description: 'Cash Dividend $0.75',
    source: 'YAHOO_FINANCE',
    heldQuantityAtExDate: 50,
    proposedImpactQuantity: null,
    proposedImpactAmount: 37.5,
    suggestedAccountId: 'acc-1',
    suggestedAccountName: 'Trading Account USD',
    appliedTransactionId: null,
    createdAt: '2026-05-01T00:00:00Z',
  },
  {
    id: 'act-3',
    instrumentId: 'inst-3',
    instrumentName: 'Regional REIT Ltd',
    ticker: 'RGL.L',
    isin: 'GB00B89C3502',
    assetClass: 'REIT',
    actionType: 'REVERSE_STOCK_SPLIT',
    status: 'APPLIED',
    exDate: '2026-04-01T00:00:00Z',
    recordDate: null,
    paymentDate: null,
    ratioFrom: 10,
    ratioTo: 1,
    amountPerShare: null,
    currency: 'GBP',
    description: '1:10 Reverse Split',
    source: 'YAHOO_FINANCE',
    heldQuantityAtExDate: 1000,
    proposedImpactQuantity: 900,
    proposedImpactAmount: null,
    suggestedAccountId: 'acc-1',
    suggestedAccountName: 'Trading Account USD',
    appliedTransactionId: 'tx-split-1',
    createdAt: '2026-04-01T00:00:00Z',
  },
];

describe('Corporate Actions Feature', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('CorporateActionsBanner', () => {
    it('does not render when there are no pending actions', async () => {
      vi.spyOn(corporateActionApi, 'list').mockResolvedValue([]);

      const { container } = renderWithProviders(
        <CorporateActionsBanner portfolioId="port-1" />
      );

      await waitFor(() => {
        expect(container.firstChild).toBeNull();
      });
    });

    it('renders notification banner with counts when pending actions exist', async () => {
      vi.spyOn(corporateActionApi, 'list').mockResolvedValue(
        mockActions.filter((a) => a.status === 'PENDING')
      );

      renderWithProviders(<CorporateActionsBanner portfolioId="port-1" />);

      await waitFor(() => {
        expect(screen.getByTestId('corporate-actions-banner')).toBeInTheDocument();
      });

      expect(screen.getByText(/Pending Corporate Actions Detected \(2\)/i)).toBeInTheDocument();
      expect(screen.getByText(/1 Split/i)).toBeInTheDocument();
      expect(screen.getByText(/1 Dividend/i)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /Review & Ingest/i })).toBeInTheDocument();
    });
  });

  describe('CorporateActionsDetailPage', () => {
    beforeEach(() => {
      vi.spyOn(portfolioApi, 'get').mockResolvedValue(mockPortfolio);
      vi.spyOn(accountApi, 'list').mockResolvedValue(mockAccounts);
      vi.spyOn(corporateActionApi, 'list').mockImplementation(async (_portId, status) => {
        if (!status) return mockActions;
        return mockActions.filter((a) => a.status === status);
      });
    });

    it('renders detail page header, quick stats, and pending table', async () => {
      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/corporate-actions" element={<CorporateActionsDetailPage />} />
        </Routes>,
        '/portfolios/port-1/corporate-actions'
      );

      await waitFor(() => {
        expect(screen.getByText('Corporate Actions & Splits Feed')).toBeInTheDocument();
      });

      expect(screen.getByText('Pending Actions')).toBeInTheDocument();
      expect(screen.getByText('Applied to Ledger')).toBeInTheDocument();

      // Check AAPL split row
      expect(screen.getByText('AAPL')).toBeInTheDocument();
      expect(screen.getByText('Stock Split')).toBeInTheDocument();
      expect(screen.getByText('4:1')).toBeInTheDocument();

      // Check MSFT dividend row
      expect(screen.getByText('MSFT')).toBeInTheDocument();
      expect(screen.getByText('Dividend')).toBeInTheDocument();
      expect(screen.getByText(/0.7500 USD/i)).toBeInTheDocument();
    });

    it('triggers market scan when clicking Scan For Actions', async () => {
      const scanResult: ScanCorporateActionsResponse = {
        portfolioId: 'port-1',
        scannedInstrumentsCount: 5,
        discoveredActionsCount: 2,
        newPendingActionsCount: 1,
        messages: ['Discovered 4:1 split for AAPL'],
      };
      const scanSpy = vi.spyOn(corporateActionApi, 'scan').mockResolvedValue(scanResult);

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/corporate-actions" element={<CorporateActionsDetailPage />} />
        </Routes>,
        '/portfolios/port-1/corporate-actions'
      );

      await waitFor(() => {
        expect(screen.getByText('Scan For Actions')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('Scan For Actions'));

      await waitFor(() => {
        expect(scanSpy).toHaveBeenCalledWith('port-1');
      });

      await waitFor(() => {
        expect(screen.getByText(/Scan complete: 5 instruments scanned/i)).toBeInTheDocument();
      });
    });

    it('opens apply dialog and confirms ingestion for a split', async () => {
      const applySpy = vi.spyOn(corporateActionApi, 'apply').mockResolvedValue({
        ...mockActions[0],
        status: 'APPLIED',
        appliedTransactionId: 'tx-applied-123',
      });

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/corporate-actions" element={<CorporateActionsDetailPage />} />
        </Routes>,
        '/portfolios/port-1/corporate-actions'
      );

      await waitFor(() => {
        expect(screen.getByText('AAPL')).toBeInTheDocument();
      });

      // Click Apply button on AAPL row
      const applyButtons = screen.getAllByRole('button', { name: 'Apply' });
      fireEvent.click(applyButtons[0]);

      await waitFor(() => {
        expect(screen.getByText(/Apply Corporate Action: STOCK SPLIT/i)).toBeInTheDocument();
      });

      // Click Confirm & Ingest
      fireEvent.click(screen.getByRole('button', { name: /Confirm & Ingest/i }));

      await waitFor(() => {
        expect(applySpy).toHaveBeenCalledWith(
          'port-1',
          'act-1',
          expect.objectContaining({
            accountId: 'acc-1',
            quantity: 300,
          })
        );
      });
    });

    it('opens dismiss dialog and confirms dismissal', async () => {
      const dismissSpy = vi.spyOn(corporateActionApi, 'dismiss').mockResolvedValue({
        ...mockActions[0],
        status: 'DISMISSED',
      });

      renderWithProviders(
        <Routes>
          <Route path="/portfolios/:id/corporate-actions" element={<CorporateActionsDetailPage />} />
        </Routes>,
        '/portfolios/port-1/corporate-actions'
      );

      await waitFor(() => {
        expect(screen.getByText('AAPL')).toBeInTheDocument();
      });

      // Click Dismiss button
      const dismissButtons = screen.getAllByRole('button', { name: 'Dismiss' });
      fireEvent.click(dismissButtons[0]);

      await waitFor(() => {
        expect(screen.getByText(/Dismiss Corporate Action/i)).toBeInTheDocument();
      });

      // Confirm dismissal
      fireEvent.click(screen.getByRole('button', { name: 'Dismiss Action' }));

      await waitFor(() => {
        expect(dismissSpy).toHaveBeenCalledWith('port-1', 'act-1');
      });
    });
  });
});

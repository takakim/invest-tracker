import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';
import { theme } from '../theme';
import { CsvImportModal } from '../features/imports/CsvImportModal';
import { importApi } from '../api/imports';
import type { CsvImportPreview, ImportBatch, BrokerDetectionResponse } from '../types';

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>{ui}</ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('CsvImportModal Component', () => {
  const mockPreview: CsvImportPreview = {
    brokerName: 'Vanguard UK',
    fileName: 'vanguard_trades.csv',
    totalRows: 2,
    importableRows: 2,
    duplicateRows: 0,
    ignoredRows: 0,
    rows: [
      {
        rowNumber: 2,
        rawType: 'Buy',
        mappedType: 'BUY',
        instrumentTitle: 'Vanguard S&P 500 UCITS ETF',
        ticker: 'VUSA',
        isin: 'IE00B3XXRP09',
        quantity: 20,
        price: 100,
        grossAmount: 2000,
        feeAmount: 0,
        taxAmount: 0,
        currency: 'GBP',
        isDuplicate: false,
        isIgnored: false,
        diagnosticMessage: 'Ready for import',
      },
      {
        rowNumber: 3,
        rawType: 'Dividend',
        mappedType: 'DIVIDEND',
        instrumentTitle: 'Vanguard S&P 500 UCITS ETF',
        ticker: 'VUSA',
        isin: 'IE00B3XXRP09',
        quantity: undefined,
        price: undefined,
        grossAmount: 15.5,
        feeAmount: 0,
        taxAmount: 0,
        currency: 'GBP',
        isDuplicate: false,
        isIgnored: false,
        diagnosticMessage: 'Ready for import',
      },
    ],
  };

  const mockBatch: ImportBatch = {
    id: 'batch-1',
    accountId: 'acc-1',
    fileName: 'vanguard_trades.csv',
    brokerType: 'Vanguard UK',
    status: 'COMPLETED',
    totalRows: 2,
    importedRows: 2,
    skippedRows: 0,
    createdAt: '2026-08-31T10:00:00Z',
  };

  const mockDetection: BrokerDetectionResponse = {
    brokerName: 'Vanguard UK',
    confidence: 'HIGH',
    isSupported: true,
    supportedBrokers: [
      'AJ Bell',
      'DEGIRO',
      'Freetrade',
      'Interactive Brokers',
      'InvestEngine',
      'Trading 212',
      'Vanguard UK',
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(importApi, 'getSupportedBrokers').mockResolvedValue(mockDetection.supportedBrokers);
    vi.spyOn(importApi, 'detectBroker').mockResolvedValue(mockDetection);
    vi.spyOn(importApi, 'preview').mockResolvedValue(mockPreview);
    vi.spyOn(importApi, 'execute').mockResolvedValue(mockBatch);
  });

  it('renders initial dropzone with supported brokers description', () => {
    const handleClose = vi.fn();
    renderWithProviders(
      <CsvImportModal
        open={true}
        portfolioId="p-1"
        accountId="acc-1"
        onClose={handleClose}
      />,
    );

    expect(screen.getByText('Import Broker CSV')).toBeInTheDocument();
    expect(screen.getByText(/Select a broker CSV file to import/i)).toBeInTheDocument();
    expect(screen.getByText(/Vanguard UK, Interactive Brokers, DEGIRO, AJ Bell/i)).toBeInTheDocument();
  });

  it('handles CSV file upload, triggers auto-detection and displays preview', async () => {
    const handleClose = vi.fn();
    renderWithProviders(
      <CsvImportModal
        open={true}
        portfolioId="p-1"
        accountId="acc-1"
        onClose={handleClose}
      />,
    );

    const fileContent = 'Date,Transaction Type,Investment Name,ISIN,Units,Unit Price,Amount,Charges,Net Amount\n15/01/2026,Buy,Vanguard S&P 500,IE00B3XXRP09,20,100,2000,0,2000';
    const file = new File([fileContent], 'vanguard_trades.csv', { type: 'text/csv' });

    // Mock FileReader implementation for jsdom
    class MockFileReader {
      onload: ((evt: { target: { result: string } }) => void) | null = null;
      readAsText() {
        if (this.onload) {
          this.onload({ target: { result: fileContent } });
        }
      }
    }
    vi.stubGlobal('FileReader', MockFileReader);

    const input = screen.getByTestId('csv-file-input') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByText('Import Preview')).toBeInTheDocument();
      expect(screen.getAllByText('Vanguard UK').length).toBeGreaterThan(0);
      expect(screen.getByText('Auto-detected')).toBeInTheDocument();
      expect(screen.getByText('2 Ready')).toBeInTheDocument();
      expect(screen.getAllByText('Vanguard S&P 500 UCITS ETF').length).toBe(2);
    });

    const confirmBtn = screen.getByRole('button', { name: /Confirm & Import 2 Transactions/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(screen.getByText('Import Completed')).toBeInTheDocument();
      expect(screen.getByText(/Successfully imported 2 transaction\(s\)/i)).toBeInTheDocument();
    });
  });
});

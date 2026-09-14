import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Stack,
  Typography,
  Card,
  CardContent,
  Alert,
  Box,
  Divider,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import TableChartIcon from '@mui/icons-material/TableChart';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import RequestQuoteIcon from '@mui/icons-material/RequestQuote';
import PaymentsIcon from '@mui/icons-material/Payments';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { exportApi } from '../../api';

interface ExportReportModalProps {
  open: boolean;
  portfolioId: string;
  portfolioName: string;
  onClose: () => void;
}

export function ExportReportModal({ open, portfolioId, portfolioName, onClose }: ExportReportModalProps) {
  const navigate = useNavigate();
  const [downloading, setDownloading] = useState<'positions' | 'transactions' | 'cashFlows' | 'executivePdf' | 'taxPdf' | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleDownloadPositions = async () => {
    setDownloading('positions');
    setErrorMsg(null);
    try {
      await exportApi.downloadPositionsCsv(portfolioId);
    } catch (err: unknown) {
      const e = err as { message?: string };
      setErrorMsg(e.message || 'Failed to download positions CSV statement');
    } finally {
      setDownloading(null);
    }
  };

  const handleDownloadTransactions = async () => {
    setDownloading('transactions');
    setErrorMsg(null);
    try {
      await exportApi.downloadTransactionsCsv(portfolioId);
    } catch (err: unknown) {
      const e = err as { message?: string };
      setErrorMsg(e.message || 'Failed to download transactions CSV statement');
    } finally {
      setDownloading(null);
    }
  };

  const handleDownloadCashFlows = async () => {
    setDownloading('cashFlows');
    setErrorMsg(null);
    try {
      await exportApi.downloadCashFlowsCsv(portfolioId, { period: 'ALL', groupBy: 'MONTH' });
    } catch (err: unknown) {
      const e = err as { message?: string };
      setErrorMsg(e.message || 'Failed to download cash flows CSV statement');
    } finally {
      setDownloading(null);
    }
  };

  const handleDownloadExecutivePdf = async () => {
    setDownloading('executivePdf');
    setErrorMsg(null);
    try {
      await exportApi.downloadExecutiveSummaryPdf(portfolioId);
    } catch (err: unknown) {
      const e = err as { message?: string };
      setErrorMsg(e.message || 'Failed to download Executive Summary PDF');
    } finally {
      setDownloading(null);
    }
  };

  const handleDownloadTaxPdf = async () => {
    setDownloading('taxPdf');
    setErrorMsg(null);
    try {
      await exportApi.downloadTaxReportPdf(portfolioId);
    } catch (err: unknown) {
      const e = err as { message?: string };
      setErrorMsg(e.message || 'Failed to download Tax Report PDF');
    } finally {
      setDownloading(null);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        Export Portfolio Statements
        <Typography variant="body2" color="text.secondary">
          {portfolioName}
        </Typography>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {errorMsg && <Alert severity="error">{errorMsg}</Alert>}
          <Typography variant="body2" color="text.secondary">
            Generate printable PDF audit reports or export raw financial ledger data in CSV format for spreadsheet analysis and tax accounting.
          </Typography>

          <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary', letterSpacing: 0.8, textTransform: 'uppercase' }}>
            Official Audit Reports (PDF)
          </Typography>

          {/* Executive Summary PDF */}
          <Card variant="outlined" sx={{ bgcolor: 'rgba(15, 23, 42, 0.02)' }}>
            <CardContent>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                  <PictureAsPdfIcon color="error" />
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                      Executive Summary Report
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Valuation, TWR performance, asset allocation, cash balances, and holdings matrix.
                    </Typography>
                  </Box>
                </Stack>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="text"
                    size="small"
                    startIcon={<OpenInNewIcon />}
                    onClick={() => {
                      onClose();
                      navigate(`/portfolios/${portfolioId}/reports/summary`);
                    }}
                  >
                    View
                  </Button>
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<DownloadIcon />}
                    onClick={handleDownloadExecutivePdf}
                    loading={downloading === 'executivePdf'}
                  >
                    Download Executive PDF
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>

          {/* Tax Audit Statement PDF */}
          <Card variant="outlined" sx={{ bgcolor: 'rgba(15, 23, 42, 0.02)' }}>
            <CardContent>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                  <RequestQuoteIcon color="primary" />
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                      Tax Year Audit Statement
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      CGT & dividend allowance utilization, sheltered growth shield, and itemized disposals.
                    </Typography>
                  </Box>
                </Stack>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<DownloadIcon />}
                  onClick={handleDownloadTaxPdf}
                  loading={downloading === 'taxPdf'}
                >
                  Download Tax PDF
                </Button>
              </Stack>
            </CardContent>
          </Card>

          <Divider sx={{ my: 1 }} />

          <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary', letterSpacing: 0.8, textTransform: 'uppercase' }}>
            Data Ledger Exports (CSV)
          </Typography>

          {/* Holdings & Positions CSV */}
          <Card variant="outlined">
            <CardContent>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                  <TableChartIcon color="primary" />
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                      Holdings & Positions Statement
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Current quantities, cost bases, market values, and weight percentages.
                    </Typography>
                  </Box>
                </Stack>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<DownloadIcon />}
                  onClick={handleDownloadPositions}
                  loading={downloading === 'positions'}
                >
                  Download CSV
                </Button>
              </Stack>
            </CardContent>
          </Card>

          {/* Transaction Ledger CSV */}
          <Card variant="outlined">
            <CardContent>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                  <ReceiptLongIcon color="primary" />
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                      Transaction Ledger History
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Full audit trail of all buys, sells, dividends, deposits, and fees.
                    </Typography>
                  </Box>
                </Stack>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<DownloadIcon />}
                  onClick={handleDownloadTransactions}
                  loading={downloading === 'transactions'}
                >
                  Download CSV
                </Button>
              </Stack>
            </CardContent>
          </Card>

          {/* Cash Flows CSV */}
          <Card variant="outlined">
            <CardContent>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                  <PaymentsIcon color="primary" />
                  <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                      Cash Flow Statement
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Periodic inflows, outflows, internal income, and cumulative contributions.
                    </Typography>
                  </Box>
                </Stack>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<DownloadIcon />}
                  onClick={handleDownloadCashFlows}
                  loading={downloading === 'cashFlows'}
                >
                  Download Cash Flows
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="inherit">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

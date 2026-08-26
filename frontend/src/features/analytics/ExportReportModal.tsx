import React, { useState } from 'react';
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
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import TableChartIcon from '@mui/icons-material/TableChart';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import { exportApi } from '../../api';

interface ExportReportModalProps {
  open: boolean;
  portfolioId: string;
  portfolioName: string;
  onClose: () => void;
}

export function ExportReportModal({ open, portfolioId, portfolioName, onClose }: ExportReportModalProps) {
  const [downloading, setDownloading] = useState<'positions' | 'transactions' | null>(null);
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
            Download your raw financial data in CSV format for offline reporting, spreadsheet modeling, or tax accounting.
          </Typography>

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

import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from '@mui/material';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';

import { usePositionPerformance } from './usePositions';
import { LoadingState } from '../../components';
import type { Position } from '../../types';

interface PositionPerformanceModalProps {
  open: boolean;
  portfolioId: string;
  accountId: string;
  position: Position | null;
  onClose: () => void;
}

export function PositionPerformanceModal({
  open,
  portfolioId,
  accountId,
  position,
  onClose,
}: PositionPerformanceModalProps) {
  const [tabIndex, setTabIndex] = useState(0);

  const {
    data: performance,
    isLoading,
    error,
  } = usePositionPerformance(
    portfolioId,
    accountId,
    position?.id || '',
  );

  if (!open || !position) return null;

  const isProfitable = (performance?.netTotalReturnAmount ?? 0) >= 0;
  const returnColor = isProfitable ? 'success.main' : 'error.main';

  const isClosed = performance?.status === 'CLOSED' || performance?.currentQuantity === 0;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}>
          <Box>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="h5" sx={{ fontWeight: 700 }}>
                {position.instrumentName}
              </Typography>
              {position.instrumentTicker && (
                <Typography variant="subtitle1" color="text.secondary" sx={{ fontWeight: 600 }}>
                  ({position.instrumentTicker})
                </Typography>
              )}
            </Stack>
            <Typography variant="caption" color="text.secondary">
              Account: {performance?.accountName || 'Account'} • ISIN: {position.instrumentIsin || '—'}
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Chip
              label={isClosed ? 'CLOSED / PAST' : 'ACTIVE HOLDING'}
              size="small"
              color={isClosed ? 'default' : 'success'}
              variant={isClosed ? 'outlined' : 'filled'}
              sx={{ fontWeight: 600 }}
            />
            <Chip label={position.assetClass} size="small" variant="outlined" />
          </Stack>
        </Stack>
      </DialogTitle>

      <DialogContent dividers sx={{ p: 3 }}>
        {isLoading ? (
          <LoadingState message="Calculating position performance and ledger..." />
        ) : error ? (
          <Alert severity="error">
            Failed to calculate performance for this position: {(error as Error).message}
          </Alert>
        ) : performance ? (
          <Stack spacing={3}>
            {/* Top KPI Banner */}
            <Paper
              sx={{
                p: 2.5,
                borderRadius: 2,
                bgcolor: isProfitable ? 'rgba(46, 125, 50, 0.08)' : 'rgba(211, 47, 47, 0.08)',
                border: '1px solid',
                borderColor: isProfitable ? 'rgba(46, 125, 50, 0.2)' : 'rgba(211, 47, 47, 0.2)',
              }}
            >
              <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2 }}>
                <Box>
                  <Typography variant="overline" color="text.secondary" sx={{ fontWeight: 700, letterSpacing: 1 }}>
                    NET TOTAL RETURN (ALL-TIME)
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline' }}>
                    <Typography variant="h3" sx={{ fontWeight: 800, color: returnColor }}>
                      {performance.netTotalReturnAmount >= 0 ? '+' : ''}
                      {performance.netTotalReturnAmount.toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}{' '}
                      {performance.currency}
                    </Typography>
                    <Typography variant="h5" sx={{ fontWeight: 700, color: returnColor }}>
                      ({performance.totalReturnPercentage >= 0 ? '+' : ''}
                      {performance.totalReturnPercentage.toFixed(2)}%)
                    </Typography>
                  </Stack>
                  {performance.nativeCurrency && performance.nativeCurrency !== performance.currency && performance.nativeNetTotalReturnAmount != null && (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Native Currency: {performance.nativeNetTotalReturnAmount >= 0 ? '+' : ''}
                      {performance.nativeNetTotalReturnAmount.toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}{' '}
                      {performance.nativeCurrency} ({performance.nativeReturnPercentage != null && (performance.nativeReturnPercentage >= 0 ? '+' : '')}
                      {performance.nativeReturnPercentage?.toFixed(2)}%)
                    </Typography>
                  )}
                </Box>
                <Box sx={{ textAlign: { xs: 'left', sm: 'right' } }}>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    Current Holding Quantity
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    {performance.currentQuantity.toLocaleString(undefined, {
                      minimumFractionDigits: 0,
                      maximumFractionDigits: 6,
                    })}{' '}
                    shares
                  </Typography>
                  {performance.currentPrice > 0 && (
                    <Typography variant="body2" color="text.secondary">
                      Live Quote: {performance.currentPrice.toFixed(2)} {performance.currency}
                    </Typography>
                  )}
                </Box>
              </Stack>
            </Paper>

            {/* Financial Component Cards Grid */}
            <Grid container spacing={2}>
              {/* Realized P/L */}
              <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      REALIZED PROFIT / LOSS
                    </Typography>
                    <Typography
                      variant="h6"
                      sx={{
                        fontWeight: 700,
                        color: performance.realizedGainLoss >= 0 ? 'success.main' : 'error.main',
                      }}
                    >
                      {performance.realizedGainLoss >= 0 ? '+' : ''}
                      {performance.realizedGainLoss.toFixed(2)} {performance.currency}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      From completed sells & disposals
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>

              {/* Unrealized P/L */}
              <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      UNREALIZED P&L (OPEN)
                    </Typography>
                    <Typography
                      variant="h6"
                      sx={{
                        fontWeight: 700,
                        color: performance.unrealizedGainLoss >= 0 ? 'success.main' : 'error.main',
                      }}
                    >
                      {performance.unrealizedGainLoss >= 0 ? '+' : ''}
                      {performance.unrealizedGainLoss.toFixed(2)} {performance.currency}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Market Value: {performance.currentMarketValue.toFixed(2)} {performance.currency}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>

              {/* Dividends */}
              <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      DIVIDEND INCOME
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: 'success.main' }}>
                      +{performance.dividendIncome.toFixed(2)} {performance.currency}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Cash distributions received
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>

              {/* Total Invested */}
              <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      TOTAL CAPITAL INVESTED
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {performance.totalInvestedAmount.toFixed(2)} {performance.currency}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {performance.totalBoughtQuantity} shares @ avg {performance.averageBuyPrice.toFixed(2)}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>

              {/* Total Proceeds */}
              <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      TOTAL SELL PROCEEDS
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {performance.totalProceedsAmount.toFixed(2)} {performance.currency}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {performance.totalSoldQuantity} shares @ avg {performance.averageSellPrice.toFixed(2)}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>

              {/* Fees and Taxes */}
              <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      FEES & TAXES PAID
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                      {(performance.fees + performance.taxes).toFixed(2)} {performance.currency}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Broker fees: {performance.fees.toFixed(2)} • Taxes: {performance.taxes.toFixed(2)}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            {/* Detailed Tabs: Ledger History, Lots & Disposals */}
            <Box>
              <Tabs
                value={tabIndex}
                onChange={(_, val) => setTabIndex(val)}
                sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}
              >
                <Tab icon={<ReceiptLongOutlinedIcon fontSize="small" />} iconPosition="start" label={`Transactions (${performance.transactions.length})`} />
                <Tab icon={<LayersOutlinedIcon fontSize="small" />} iconPosition="start" label={`Open Lots (${performance.openLots.length})`} />
                <Tab icon={<AccountBalanceWalletOutlinedIcon fontSize="small" />} iconPosition="start" label={`Sell Disposals (${performance.disposals.length})`} />
              </Tabs>

              {/* Tab 0: Transactions */}
              {tabIndex === 0 && (
                <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 300 }}>
                  <Table size="small" stickyHeader>
                    <TableHead>
                      <TableRow>
                        <TableCell>Date</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell align="right">Qty</TableCell>
                        <TableCell align="right">Price</TableCell>
                        <TableCell align="right">Gross</TableCell>
                        <TableCell align="right">Fee/Tax</TableCell>
                        <TableCell align="right">Net Amount</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {performance.transactions.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={7} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                            No transaction history recorded.
                          </TableCell>
                        </TableRow>
                      ) : (
                        performance.transactions.map((tx) => (
                          <TableRow key={tx.id} hover>
                            <TableCell>{new Date(tx.tradeDate).toLocaleDateString()}</TableCell>
                            <TableCell>
                              <Chip
                                label={tx.type}
                                size="small"
                                color={
                                  tx.type === 'BUY'
                                    ? 'primary'
                                    : tx.type === 'SELL'
                                    ? 'secondary'
                                    : tx.type === 'DIVIDEND'
                                    ? 'success'
                                    : 'default'
                                }
                                sx={{ height: 20, fontSize: '0.7rem' }}
                              />
                            </TableCell>
                            <TableCell align="right">{tx.quantity != null ? Number(tx.quantity).toLocaleString() : '—'}</TableCell>
                            <TableCell align="right">{tx.price != null ? Number(tx.price).toFixed(2) : '—'}</TableCell>
                            <TableCell align="right">{tx.grossAmount != null ? Number(tx.grossAmount).toFixed(2) : '—'}</TableCell>
                            <TableCell align="right">
                              {((tx.feeAmount || 0) + (tx.taxAmount || 0)) > 0
                                ? ((tx.feeAmount || 0) + (tx.taxAmount || 0)).toFixed(2)
                                : '—'}
                            </TableCell>
                            <TableCell align="right" sx={{ fontWeight: 600 }}>
                              {Number(tx.netAmount).toFixed(2)} {tx.currency}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}

              {/* Tab 1: Open Lots */}
              {tabIndex === 1 && (
                <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 300 }}>
                  <Table size="small" stickyHeader>
                    <TableHead>
                      <TableRow>
                        <TableCell>Acquisition Date</TableCell>
                        <TableCell align="right">Original Qty</TableCell>
                        <TableCell align="right">Remaining Qty</TableCell>
                        <TableCell align="right">Unit Cost</TableCell>
                        <TableCell align="right">Total Cost Basis</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {performance.openLots.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                            No active open lots (position is fully liquidated or closed).
                          </TableCell>
                        </TableRow>
                      ) : (
                        performance.openLots.map((lot) => (
                          <TableRow key={lot.lotId} hover>
                            <TableCell>{new Date(lot.acquisitionDate).toLocaleDateString()}</TableCell>
                            <TableCell align="right">{Number(lot.originalQuantity).toLocaleString()}</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 600 }}>{Number(lot.remainingQuantity).toLocaleString()}</TableCell>
                            <TableCell align="right">{Number(lot.unitCostAmount).toFixed(4)} {lot.currency}</TableCell>
                            <TableCell align="right" sx={{ fontWeight: 600 }}>
                              {Number(lot.totalCostAmount).toFixed(2)} {lot.currency}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}

              {/* Tab 2: Sell Disposals */}
              {tabIndex === 2 && (
                <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 300 }}>
                  <Table size="small" stickyHeader>
                    <TableHead>
                      <TableRow>
                        <TableCell>Disposal Date</TableCell>
                        <TableCell align="right">Disposed Qty</TableCell>
                        <TableCell align="right">Cost Basis</TableCell>
                        <TableCell align="right">Proceeds</TableCell>
                        <TableCell align="right">Realized Gain/Loss</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {performance.disposals.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                            No historical sell lot disposals recorded.
                          </TableCell>
                        </TableRow>
                      ) : (
                        performance.disposals.map((d, idx) => (
                          <TableRow key={`${d.transactionId}-${idx}`} hover>
                            <TableCell>{new Date(d.disposalDate).toLocaleDateString()}</TableCell>
                            <TableCell align="right">{Number(d.quantity).toLocaleString()}</TableCell>
                            <TableCell align="right">{Number(d.costBasis).toFixed(2)} {d.currency}</TableCell>
                            <TableCell align="right">{Number(d.proceeds).toFixed(2)} {d.currency}</TableCell>
                            <TableCell
                              align="right"
                              sx={{
                                fontWeight: 700,
                                color: d.realizedGainLoss >= 0 ? 'success.main' : 'error.main',
                              }}
                            >
                              {d.realizedGainLoss >= 0 ? '+' : ''}
                              {Number(d.realizedGainLoss).toFixed(2)} {d.currency}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          </Stack>
        ) : null}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

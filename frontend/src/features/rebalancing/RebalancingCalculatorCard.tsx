import React, { useState } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Stack,
  Box,
  Button,
  Chip,
  Grid,
  Paper,
  TextField,
  ToggleButtonGroup,
  ToggleButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Alert,
  Skeleton,
  Tooltip,
  Snackbar,
} from '@mui/material';
import CalculateOutlinedIcon from '@mui/icons-material/CalculateOutlined';
import BalanceOutlinedIcon from '@mui/icons-material/BalanceOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { useTargetAllocation, useRebalancingAnalysis } from './useRebalancing';
import type { RebalanceAction } from '../../types';

interface RebalancingCalculatorCardProps {
  portfolioId: string;
  currency: string;
}

export function RebalancingCalculatorCard({ portfolioId, currency }: RebalancingCalculatorCardProps) {
  const { data: targetPlan } = useTargetAllocation(portfolioId);
  const [rebalanceMode, setRebalanceMode] = useState<'FULL' | 'CASH_INJECTION'>('FULL');
  const [cashInjectionInput, setCashInjectionInput] = useState<string>('');
  const [copiedSnackbar, setCopiedSnackbar] = useState(false);

  const cashInjectionAmount = rebalanceMode === 'CASH_INJECTION' ? parseFloat(cashInjectionInput) || 0 : undefined;
  const { data: analysis, isLoading, error } = useRebalancingAnalysis(portfolioId, cashInjectionAmount);

  if (!targetPlan) {
    return null;
  }

  if (isLoading) {
    return (
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Skeleton variant="text" width="30%" height={32} />
          <Skeleton variant="rectangular" height={120} sx={{ my: 1, borderRadius: 1 }} />
        </CardContent>
      </Card>
    );
  }

  if (error || !analysis) {
    return null;
  }

  const formatMoney = (val: number | string | undefined | null) => {
    return Number(val ?? 0).toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  };

  const buyOrders = analysis.items.filter((i) => i.action === 'BUY');
  const sellOrders = analysis.items.filter((i) => i.action === 'SELL');

  const handleQuickCash = (amount: number) => {
    const current = parseFloat(cashInjectionInput) || 0;
    setCashInjectionInput((current + amount).toString());
  };

  const handleCopyPlan = () => {
    const lines = [
      `Portfolio Rebalancing Plan: ${analysis.portfolioName}`,
      `Date: ${new Date(analysis.asOf).toLocaleDateString()}`,
      `Mode: ${rebalanceMode === 'CASH_INJECTION' ? 'Cash Injection (Buy-Only)' : 'Full Rebalance (Buy & Sell)'}`,
      `Total Portfolio Value: ${formatMoney(analysis.totalPortfolioValue)} ${currency}`,
    ];

    if (analysis.cashInjectionAmount > 0) {
      lines.push(`Injected Cash: ${formatMoney(analysis.cashInjectionAmount)} ${currency}`);
      lines.push(`Post-Rebalance Total: ${formatMoney(analysis.totalPostRebalanceValue)} ${currency}`);
    }

    lines.push('\nRecommended Orders:');
    for (const item of analysis.items) {
      if (item.action === 'HOLD') continue;
      const sharesStr = item.estimatedQuantity ? ` (~${item.estimatedQuantity} shares)` : '';
      lines.push(`- ${item.action} ${item.categoryLabel}: ${formatMoney(item.orderAmount)} ${currency}${sharesStr} (Drift: ${item.driftPercentage > 0 ? '+' : ''}${item.driftPercentage}%)`);
    }

    navigator.clipboard.writeText(lines.join('\n'));
    setCopiedSnackbar(true);
  };

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }} data-testid="rebalancing-calculator-card">
      <CardContent>
        <Stack spacing={3}>
          {/* Header */}
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2 }}
          >
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <CalculateOutlinedIcon color="primary" sx={{ fontSize: 28 }} />
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1.15rem' }}>
                  Portfolio Rebalancing Calculator
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Actionable trade orders to restore target allocations and eliminate drift
                </Typography>
              </Box>
            </Stack>

            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <ToggleButtonGroup
                value={rebalanceMode}
                exclusive
                onChange={(_, newMode) => {
                  if (newMode) setRebalanceMode(newMode);
                }}
                size="small"
                color="primary"
              >
                <ToggleButton value="FULL" sx={{ px: 2, fontWeight: 600 }}>
                  <BalanceOutlinedIcon sx={{ mr: 0.5, fontSize: 18 }} /> Full Rebalance (Buy/Sell)
                </ToggleButton>
                <ToggleButton value="CASH_INJECTION" sx={{ px: 2, fontWeight: 600 }}>
                  <PaymentsOutlinedIcon sx={{ mr: 0.5, fontSize: 18 }} /> Cash Injection (Buy Only)
                </ToggleButton>
              </ToggleButtonGroup>

              <Button
                variant="outlined"
                size="small"
                startIcon={<ContentCopyOutlinedIcon />}
                onClick={handleCopyPlan}
              >
                Copy Plan
              </Button>
            </Stack>
          </Stack>

          {/* Cash Injection Input Bar */}
          {rebalanceMode === 'CASH_INJECTION' && (
            <Paper
              variant="outlined"
              sx={{
                p: 2,
                borderRadius: 2,
                bgcolor: (theme) => (theme.palette.mode === 'dark' ? 'rgba(33, 150, 243, 0.08)' : '#f0f7ff'),
                border: '1px solid rgba(33, 150, 243, 0.3)',
              }}
            >
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
                <TextField
                  label="New Cash Deposit Amount"
                  size="small"
                  type="number"
                  value={cashInjectionInput}
                  onChange={(e) => setCashInjectionInput(e.target.value)}
                  placeholder="e.g. 1000.00"
                  sx={{ width: { xs: '100%', sm: 240 } }}
                  slotProps={{
                    input: {
                      endAdornment: <Typography variant="body2">{currency}</Typography>,
                    },
                  }}
                />
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center', mr: 0.5 }}>
                    Quick add:
                  </Typography>
                  <Button size="small" variant="outlined" onClick={() => handleQuickCash(250)}>
                    +250
                  </Button>
                  <Button size="small" variant="outlined" onClick={() => handleQuickCash(500)}>
                    +500
                  </Button>
                  <Button size="small" variant="outlined" onClick={() => handleQuickCash(1000)}>
                    +1,000
                  </Button>
                  <Button size="small" variant="outlined" onClick={() => handleQuickCash(5000)}>
                    +5,000
                  </Button>
                  {cashInjectionInput && (
                    <Button size="small" color="secondary" onClick={() => setCashInjectionInput('')}>
                      Clear
                    </Button>
                  )}
                </Stack>
              </Stack>
            </Paper>
          )}

          {/* Summary Metric Strip */}
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                <Typography variant="caption" color="text.secondary">
                  Current Portfolio Value
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  {formatMoney(analysis.totalPortfolioValue)} {currency}
                </Typography>
              </Paper>
            </Grid>

            {rebalanceMode === 'CASH_INJECTION' ? (
              <>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                      Injected Cash
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.main' }}>
                      +{formatMoney(analysis.cashInjectionAmount)} {currency}
                    </Typography>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                      Post-Rebalance Total
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: 'success.main' }}>
                      {formatMoney(analysis.totalPostRebalanceValue)} {currency}
                    </Typography>
                  </Paper>
                </Grid>
              </>
            ) : (
              <>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                      Proposed Rebalance Orders
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {buyOrders.length} Buy{buyOrders.length === 1 ? '' : 's'}, {sellOrders.length} Sell{sellOrders.length === 1 ? '' : 's'}
                    </Typography>
                  </Paper>
                </Grid>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                      Max Drift vs Target
                    </Typography>
                    <Typography
                      variant="h6"
                      sx={{
                        fontWeight: 700,
                        color: analysis.hasDriftToleranceExceeded ? 'warning.main' : 'success.main',
                      }}
                    >
                      {analysis.hasDriftToleranceExceeded ? 'Exceeds Tolerance' : 'Within Tolerance'}
                    </Typography>
                  </Paper>
                </Grid>
              </>
            )}
          </Grid>

          {/* Orders Table */}
          <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
            <Table size="small" aria-label="rebalance orders table">
              <TableHead sx={{ bgcolor: 'action.hover' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600 }}>Category / Asset</TableCell>
                  <TableCell align="center" sx={{ fontWeight: 600 }}>Action</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>Order Amount ({currency})</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>Est. Price & Quantity</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>Weight Shift</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {analysis.items.map((item) => {
                  const isBuy = item.action === 'BUY';
                  const isSell = item.action === 'SELL';
                  const isHold = item.action === 'HOLD';

                  return (
                    <TableRow key={item.categoryKey} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {item.categoryLabel}
                        </Typography>
                        {item.instrumentTicker && (
                          <Typography variant="caption" color="text.secondary">
                            {item.instrumentTicker}
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell align="center">
                        <Chip
                          label={item.action}
                          color={isBuy ? 'success' : isSell ? 'warning' : 'default'}
                          size="small"
                          sx={{ fontWeight: 700, minWidth: 65 }}
                        />
                      </TableCell>

                      <TableCell
                        align="right"
                        sx={{
                          fontWeight: 700,
                          fontFamily: 'monospace',
                          color: isBuy ? 'success.main' : isSell ? 'warning.dark' : 'text.disabled',
                        }}
                      >
                        {isHold ? '—' : `${formatMoney(item.orderAmount)} ${currency}`}
                      </TableCell>

                      <TableCell align="right">
                        {item.estimatedQuantity && item.estimatedPrice ? (
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: 'monospace' }}>
                              ~{Number(item.estimatedQuantity).toLocaleString(undefined, { maximumFractionDigits: 4 })} shares
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              @ {formatMoney(item.estimatedPrice)} {currency}
                            </Typography>
                          </Box>
                        ) : (
                          <Typography variant="caption" color="text.disabled">
                            —
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell align="right">
                        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', alignItems: 'center' }}>
                          <Typography variant="body2" color="text.secondary">
                            {Number(item.currentWeightPercentage).toFixed(2)}%
                          </Typography>
                          <ArrowForwardIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
                          <Typography variant="body2" sx={{ fontWeight: 700, color: 'primary.main' }}>
                            {Number(item.projectedPostWeightPercentage).toFixed(2)}%
                          </Typography>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Stack>
      </CardContent>

      <Snackbar
        open={copiedSnackbar}
        autoHideDuration={3000}
        onClose={() => setCopiedSnackbar(false)}
        message="Rebalancing plan copied to clipboard!"
      />
    </Card>
  );
}

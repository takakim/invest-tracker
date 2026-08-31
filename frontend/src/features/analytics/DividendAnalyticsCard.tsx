import React, { useState } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Stack,
  Box,
  Grid,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Tabs,
  Tab,
  LinearProgress,
  Skeleton,
  Tooltip,
} from '@mui/material';
import PaidOutlinedIcon from '@mui/icons-material/PaidOutlined';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import PercentOutlinedIcon from '@mui/icons-material/PercentOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import { useDividendAnalytics } from './useAnalytics';

interface DividendAnalyticsCardProps {
  portfolioId: string;
  currency: string;
}

export function DividendAnalyticsCard({ portfolioId, currency }: DividendAnalyticsCardProps) {
  const { data: analytics, isLoading, error } = useDividendAnalytics(portfolioId);
  const [historyTab, setHistoryTab] = useState<'holdings' | 'calendar' | 'yearly' | 'monthly'>('holdings');

  if (isLoading) {
    return (
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Skeleton variant="text" width="40%" height={32} />
          <Skeleton variant="rectangular" height={140} sx={{ my: 2, borderRadius: 1 }} />
        </CardContent>
      </Card>
    );
  }

  if (error || !analytics) {
    return null;
  }

  const formatMoney = (amount: number | string | undefined | null) => {
    const val = Number(amount ?? 0);
    return val.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  };

  const formatPct = (pct: number | string | undefined | null) => {
    const val = Number(pct ?? 0);
    return `${val.toFixed(2)}%`;
  };

  const totalAllTime = formatMoney(analytics.totalDividendsAllTime);
  const totalYtd = formatMoney(analytics.totalDividendsYtd);
  const totalTtm = formatMoney(analytics.totalDividendsTtm);
  const totalTax = formatMoney(analytics.totalWithholdingTaxAllTime);
  const projectedAnnual = formatMoney(analytics.projectedAnnualDividendIncome);
  const currentYield = formatPct(analytics.portfolioDividendYieldPercentage);
  const yieldOnCost = formatPct(analytics.portfolioYieldOnCostPercentage);

  const projectedCalendar = analytics.projectedMonthlyCalendar || analytics.projectedCalendar || [];
  const holdings = analytics.holdings || [];
  const monthlyHistory = analytics.monthlyHistory || [];
  const yearlyHistory = analytics.yearlyHistory || [];

  const maxMonthVal = Math.max(
    ...projectedCalendar.map((m) => Number(m.projectedAmount || 0)),
    1
  );

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }} data-testid="dividend-analytics-card">
      <CardContent>
        <Stack spacing={3}>
          {/* Header */}
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}
          >
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <PaidOutlinedIcon color="primary" sx={{ fontSize: 28 }} />
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1.15rem' }}>
                  Dividend Analytics & Income Projection
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Historical dividend cashflows, portfolio yield metrics, and forward 12-month projections
                </Typography>
              </Box>
            </Stack>

            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Chip
                icon={<PercentOutlinedIcon />}
                label={`Yield: ${currentYield}`}
                color="primary"
                variant="outlined"
                sx={{ fontWeight: 600 }}
              />
              <Chip
                icon={<TrendingUpOutlinedIcon />}
                label={`YOC: ${yieldOnCost}`}
                color="success"
                variant="outlined"
                sx={{ fontWeight: 600 }}
              />
            </Stack>
          </Stack>

          {/* Top Summary Metric Cards */}
          <Grid container spacing={2}>
            {/* Projected Annual Dividend Income */}
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <Paper
                variant="outlined"
                sx={{
                  p: 2,
                  borderRadius: 2,
                  bgcolor: (theme) => (theme.palette.mode === 'dark' ? 'rgba(76, 175, 80, 0.08)' : '#f1f8f3'),
                  border: '1px solid rgba(76, 175, 80, 0.3)',
                }}
              >
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
                  <SavingsOutlinedIcon sx={{ color: 'success.main', fontSize: 20 }} />
                  <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                    Projected Annual Income
                  </Typography>
                </Stack>
                <Typography variant="h5" sx={{ fontWeight: 700, color: 'success.main', my: 0.5 }}>
                  {projectedAnnual} <Typography component="span" variant="body1" color="text.secondary">{currency}</Typography>
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Based on trailing 12-month DPS × active shares
                </Typography>
              </Paper>
            </Grid>

            {/* Total Dividends Received (All-Time) */}
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <Paper
                variant="outlined"
                sx={{
                  p: 2,
                  borderRadius: 2,
                  bgcolor: 'background.default',
                }}
              >
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
                  <AccountBalanceWalletOutlinedIcon sx={{ color: 'primary.main', fontSize: 20 }} />
                  <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                    Total Received (All-Time)
                  </Typography>
                </Stack>
                <Typography variant="h5" sx={{ fontWeight: 700, color: 'text.primary', my: 0.5 }}>
                  {totalAllTime} <Typography component="span" variant="body1" color="text.secondary">{currency}</Typography>
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Withholding tax paid: {totalTax} {currency}
                </Typography>
              </Paper>
            </Grid>

            {/* YTD & TTM Dividends */}
            <Grid size={{ xs: 12, sm: 12, md: 4 }}>
              <Paper
                variant="outlined"
                sx={{
                  p: 2,
                  borderRadius: 2,
                  bgcolor: 'background.default',
                }}
              >
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
                  <CalendarMonthOutlinedIcon sx={{ color: 'info.main', fontSize: 20 }} />
                  <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                    Recent Dividend Cashflow
                  </Typography>
                </Stack>
                <Stack direction="row" spacing={3} sx={{ mt: 1 }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">
                      Year-to-Date (YTD)
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {totalYtd} {currency}
                    </Typography>
                  </Box>
                  <Box sx={{ borderLeft: '1px solid #e0e0e0', pl: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                      Trailing 12 Months (TTM)
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {totalTtm} {currency}
                    </Typography>
                  </Box>
                </Stack>
              </Paper>
            </Grid>
          </Grid>

          {/* Sub-Navigation Tabs */}
          <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tabs
              value={historyTab}
              onChange={(_, val) => setHistoryTab(val)}
              textColor="primary"
              indicatorColor="primary"
              variant="scrollable"
              scrollButtons="auto"
            >
              <Tab label={`Holdings Breakdown (${holdings.length})`} value="holdings" sx={{ textTransform: 'none', fontWeight: 600 }} />
              <Tab label="12-Month Projected Calendar" value="calendar" sx={{ textTransform: 'none', fontWeight: 600 }} />
              <Tab label="Monthly History" value="monthly" sx={{ textTransform: 'none', fontWeight: 600 }} />
              <Tab label="Yearly History" value="yearly" sx={{ textTransform: 'none', fontWeight: 600 }} />
            </Tabs>
          </Box>

          {/* Tab 1: Holdings Breakdown Table */}
          {historyTab === 'holdings' && (
            <Box>
              {holdings.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                  No dividend-bearing instruments or dividend payouts recorded for this portfolio yet.
                </Typography>
              ) : (
                <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
                  <Table size="small" aria-label="dividend holdings table">
                    <TableHead sx={{ bgcolor: 'action.hover' }}>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 600 }}>Instrument</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Asset Class</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Active Shares</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>TTM DPS ({currency})</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Projected Income ({currency})</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Yield %</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Yield on Cost %</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>All-Time Received ({currency})</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {holdings.map((h) => (
                        <TableRow key={h.instrumentId} hover>
                          <TableCell>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {h.ticker || h.instrumentName}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {h.instrumentName}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Chip label={h.assetClass} size="small" variant="outlined" />
                          </TableCell>
                          <TableCell align="right">
                            {Number(h.currentShares).toLocaleString(undefined, { maximumFractionDigits: 4 })}
                          </TableCell>
                          <TableCell align="right" sx={{ fontFamily: 'monospace' }}>
                            {formatMoney(h.trailingTwelveMonthsDps)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700, color: Number(h.projectedAnnualIncome) > 0 ? 'success.main' : 'text.primary', fontFamily: 'monospace' }}>
                            {formatMoney(h.projectedAnnualIncome)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 600, color: 'primary.main' }}>
                            {formatPct(h.currentYieldPercentage)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 600, color: 'success.main' }}>
                            {formatPct(h.yieldOnCostPercentage)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontFamily: 'monospace' }}>
                            {formatMoney(h.totalReceivedAllTime)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          )}

          {/* Tab 2: 12-Month Projected Calendar */}
          {historyTab === 'calendar' && (
            <Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Estimated monthly cashflows over the next 12 months based on historical distribution schedules:
              </Typography>
              <Grid container spacing={1.5}>
                {projectedCalendar.map((m) => {
                  const amt = Number(m.projectedAmount || 0);
                  const isPositive = amt > 0;
                  const barPct = Math.min((amt / maxMonthVal) * 100, 100);

                  return (
                    <Grid key={m.month} size={{ xs: 6, sm: 4, md: 2 }}>
                      <Paper
                        variant="outlined"
                        sx={{
                          p: 1.5,
                          borderRadius: 2,
                          textAlign: 'center',
                          bgcolor: isPositive
                            ? (theme) => (theme.palette.mode === 'dark' ? 'rgba(76, 175, 80, 0.06)' : '#f4faf5')
                            : 'background.default',
                          border: isPositive ? '1px solid rgba(76, 175, 80, 0.4)' : undefined,
                        }}
                      >
                        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                          {m.monthName}
                        </Typography>
                        <Typography
                          variant="body1"
                          sx={{
                            fontWeight: 700,
                            my: 0.5,
                            color: isPositive ? 'success.main' : 'text.disabled',
                          }}
                        >
                          {formatMoney(amt)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {currency}
                        </Typography>
                        <Box sx={{ mt: 1 }}>
                          <LinearProgress
                            variant="determinate"
                            value={barPct}
                            color={isPositive ? 'success' : 'inherit'}
                            sx={{ height: 4, borderRadius: 2 }}
                          />
                        </Box>
                      </Paper>
                    </Grid>
                  );
                })}
              </Grid>
            </Box>
          )}

          {/* Tab 3: Monthly History */}
          {historyTab === 'monthly' && (
            <Box>
              {monthlyHistory.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                  No historical dividend payments logged yet.
                </Typography>
              ) : (
                <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
                  <Table size="small" aria-label="monthly dividend table">
                    <TableHead sx={{ bgcolor: 'action.hover' }}>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 600 }}>Period (Year-Month)</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Net Received ({currency})</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Gross Dividend ({currency})</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Tax Withheld ({currency})</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {monthlyHistory.map((row) => (
                        <TableRow key={row.yearMonth} hover>
                          <TableCell sx={{ fontWeight: 600 }}>{row.yearMonth}</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700, color: 'success.main', fontFamily: 'monospace' }}>
                            {formatMoney(row.netAmount)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontFamily: 'monospace' }}>
                            {formatMoney(row.grossAmount)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
                            {formatMoney(row.taxAmount)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          )}

          {/* Tab 4: Yearly History */}
          {historyTab === 'yearly' && (
            <Box>
              {yearlyHistory.length === 0 ? (
                <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                  No historical dividend payments logged yet.
                </Typography>
              ) : (
                <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
                  <Table size="small" aria-label="yearly dividend table">
                    <TableHead sx={{ bgcolor: 'action.hover' }}>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 600 }}>Year</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Net Received ({currency})</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Gross Dividend ({currency})</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>Tax Withheld ({currency})</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {yearlyHistory.map((row) => (
                        <TableRow key={row.year} hover>
                          <TableCell sx={{ fontWeight: 700 }}>{row.year}</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700, color: 'success.main', fontFamily: 'monospace' }}>
                            {formatMoney(row.netAmount)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontFamily: 'monospace' }}>
                            {formatMoney(row.grossAmount)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
                            {formatMoney(row.taxAmount)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

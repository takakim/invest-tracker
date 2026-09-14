import React, { useState } from 'react';
import { Link as RouterLink, useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  Link,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
  Alert,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';
import SouthWestIcon from '@mui/icons-material/SouthWest';
import NorthEastIcon from '@mui/icons-material/NorthEast';
import { usePortfolio } from '../portfolios/usePortfolios';
import { useCashFlowAnalytics } from './useAnalytics';
import { exportApi } from '../../api/export';

const fmt = (v: number | undefined | null, dp = 2) =>
  Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });
const fmtPct = (v: number | undefined | null) => `${Number(v ?? 0).toFixed(1)}%`;

export function CashFlowDetailPage() {
  const { id: portfolioId = '' } = useParams();
  const navigate = useNavigate();

  const [period, setPeriod] = useState<'1M' | '3M' | '6M' | '1Y' | 'YTD' | 'ALL'>('ALL');
  const [groupBy, setGroupBy] = useState<'MONTH' | 'QUARTER' | 'YEAR'>('MONTH');
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  const { data: portfolio, isLoading: isPortfolioLoading } = usePortfolio(portfolioId);
  const {
    data: cashFlows,
    isLoading: isCashFlowsLoading,
    error,
  } = useCashFlowAnalytics(portfolioId, { period, groupBy });

  const currency = cashFlows?.baseCurrency || portfolio?.baseCurrency || 'GBP';

  const handleExportCsv = async () => {
    try {
      await exportApi.downloadCashFlowsCsv(portfolioId, { period, groupBy });
    } catch (err) {
      console.error('Failed to export cash flows CSV:', err);
    }
  };

  if (isPortfolioLoading || isCashFlowsLoading) {
    return (
      <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
        <Skeleton variant="text" width={250} height={32} />
        <Skeleton variant="rectangular" height={160} sx={{ mt: 3, borderRadius: 2 }} />
        <Skeleton variant="rectangular" height={360} sx={{ mt: 3, borderRadius: 2 }} />
      </Box>
    );
  }

  if (error || !cashFlows) {
    return (
      <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
        <Alert severity="error">
          Failed to load cash flow analytics. Please try again.
        </Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(-1)} sx={{ mt: 2 }}>
          Go Back
        </Button>
      </Box>
    );
  }

  const { summary, periods, accountBreakdown, warnings } = cashFlows;
  const capitalPct = Number(summary.capitalContributionsPercentage || 0);
  const growthPct = Number(summary.marketGrowthPercentage || 0);

  // SVG Chart Computations
  const maxFlow = Math.max(
    ...periods.map((p) => Math.max(Number(p.deposits || 0), Number(p.withdrawals || 0))),
    100
  );
  const chartHeight = 220;
  const chartWidth = 720;
  const paddingX = 40;
  const paddingY = 25;
  const plotWidth = chartWidth - paddingX * 2;
  const plotHeight = chartHeight - paddingY * 2;

  const barWidth = Math.max(Math.min(plotWidth / (periods.length * 2.5 || 1), 28), 6);

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1200, mx: 'auto' }}>
      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/" color="inherit" underline="hover">
          Home
        </Link>
        <Link component={RouterLink} to="/portfolios" color="inherit" underline="hover">
          Portfolios
        </Link>
        <Link component={RouterLink} to={`/portfolios/${portfolioId}`} color="inherit" underline="hover">
          {portfolio?.name || 'Portfolio'}
        </Link>
        <Typography color="text.primary">Cash Flows</Typography>
      </Breadcrumbs>

      {/* Header Bar */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2, mb: 3 }}
      >
        <Box>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <AccountBalanceWalletOutlinedIcon color="primary" sx={{ fontSize: 32 }} />
            <Box>
              <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.5 }}>
                Cash Flow & Savings
              </Typography>
              <Typography variant="body2" color="text.secondary">
                External capital contributions, withdrawals, savings consistency, and wealth origin.
              </Typography>
            </Box>
          </Stack>
        </Box>

        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Button
            variant="outlined"
            size="small"
            startIcon={<FileDownloadOutlinedIcon />}
            onClick={handleExportCsv}
            sx={{ textTransform: 'none', fontWeight: 600 }}
          >
            Export CSV
          </Button>
          <Button
            variant="contained"
            size="small"
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate(`/portfolios/${portfolioId}`)}
            sx={{ textTransform: 'none', fontWeight: 600 }}
          >
            Back to Portfolio
          </Button>
        </Stack>
      </Stack>

      {/* Warnings */}
      {warnings && warnings.length > 0 && (
        <Alert severity="warning" sx={{ mb: 3, borderRadius: 2 }}>
          {warnings.join(' ')}
        </Alert>
      )}

      {/* Controls Bar */}
      <Paper
        variant="outlined"
        sx={{ p: 2, mb: 3, borderRadius: 2, display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'space-between', alignItems: 'center' }}
      >
        {/* Period Selector */}
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, mr: 1 }}>
            TIME HORIZON:
          </Typography>
          {(['1M', '3M', '6M', '1Y', 'YTD', 'ALL'] as const).map((p) => (
            <Chip
              key={p}
              label={p}
              size="small"
              clickable
              color={period === p ? 'primary' : 'default'}
              variant={period === p ? 'filled' : 'outlined'}
              onClick={() => setPeriod(p)}
              sx={{ fontWeight: period === p ? 700 : 500 }}
            />
          ))}
        </Stack>

        {/* Grouping Selector */}
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, mr: 1 }}>
            GROUP BY:
          </Typography>
          {(['MONTH', 'QUARTER', 'YEAR'] as const).map((g) => (
            <Chip
              key={g}
              label={g === 'MONTH' ? 'Monthly' : g === 'QUARTER' ? 'Quarterly' : 'Yearly'}
              size="small"
              clickable
              color={groupBy === g ? 'primary' : 'default'}
              variant={groupBy === g ? 'filled' : 'outlined'}
              onClick={() => setGroupBy(g)}
              sx={{ fontWeight: groupBy === g ? 700 : 500 }}
            />
          ))}
        </Stack>
      </Paper>

      {/* 5 KPI Metric Cards */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
              <SouthWestIcon color="success" sx={{ fontSize: 18 }} />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                TOTAL DEPOSITS
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 800, color: 'success.main' }}>
              +{currency} {fmt(summary.totalDeposits)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Total cash injected
            </Typography>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
              <NorthEastIcon color="error" sx={{ fontSize: 18 }} />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                WITHDRAWALS
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 800, color: 'error.main' }}>
              -{currency} {fmt(summary.totalWithdrawals)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Total cash taken out
            </Typography>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%', bgcolor: 'action.hover' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
              <AccountBalanceWalletOutlinedIcon color="primary" sx={{ fontSize: 18 }} />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                NET INVESTED
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 800, color: 'primary.main' }}>
              {currency} {fmt(summary.netContributions)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Deposits minus withdrawals
            </Typography>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
              <SavingsOutlinedIcon color="primary" sx={{ fontSize: 18 }} />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                AVG MONTHLY INFLOW
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 800 }}>
              {currency} {fmt(summary.avgMonthlyContribution)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Across {summary.activeContributionMonths} active months
            </Typography>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
              <TrendingUpOutlinedIcon color="success" sx={{ fontSize: 18 }} />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                WEALTH ORIGIN
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 800 }}>
              {fmtPct(capitalPct)} / {fmtPct(growthPct)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Capital vs Organic Growth
            </Typography>
          </Paper>
        </Grid>
      </Grid>

      {/* Wealth Origin Progress Split */}
      <Card variant="outlined" sx={{ mb: 3, borderRadius: 2 }}>
        <CardContent>
          <Stack spacing={1.5}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                Where Your Wealth Came From
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Total Portfolio Value: <strong>{currency} {fmt(summary.currentPortfolioValue)}</strong>
              </Typography>
            </Stack>
            <Box sx={{ display: 'flex', height: 16, borderRadius: 8, overflow: 'hidden', bgcolor: 'divider' }}>
              <Box
                sx={{
                  width: `${Math.min(capitalPct, 100)}%`,
                  bgcolor: 'primary.main',
                  transition: 'width 0.8s ease',
                }}
              />
              <Box
                sx={{
                  width: `${Math.min(growthPct, 100)}%`,
                  bgcolor: 'success.main',
                  transition: 'width 0.8s ease',
                }}
              />
            </Box>
            <Stack direction="row" spacing={3} sx={{ flexWrap: 'wrap' }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: 'primary.main' }} />
                <Typography variant="body2">
                  <strong>Capital Injected:</strong> {currency} {fmt(summary.cumulativeContributions)} ({fmtPct(capitalPct)})
                </Typography>
              </Stack>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: 'success.main' }} />
                <Typography variant="body2">
                  <strong>Organic Market Gains & Income:</strong> {currency} {fmt(Math.max(Number(summary.currentPortfolioValue) - Number(summary.cumulativeContributions), 0))} ({fmtPct(growthPct)})
                </Typography>
              </Stack>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {/* Interactive SVG Inflows & Outflows Bar Chart */}
      <Card variant="outlined" sx={{ mb: 3, borderRadius: 2 }}>
        <CardContent>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                Periodic Capital Inflow vs Outflow
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Deposits (green) vs Withdrawals (red) per period
              </Typography>
            </Box>
            <Stack direction="row" spacing={2}>
              <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 10, height: 10, borderRadius: 1, bgcolor: 'success.main' }} />
                <Typography variant="caption">Deposits</Typography>
              </Stack>
              <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 10, height: 10, borderRadius: 1, bgcolor: 'error.main' }} />
                <Typography variant="caption">Withdrawals</Typography>
              </Stack>
              <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 10, height: 10, borderRadius: 1, bgcolor: 'primary.main' }} />
                <Typography variant="caption">Net Inflow</Typography>
              </Stack>
            </Stack>
          </Stack>

          {periods.length === 0 ? (
            <Box sx={{ p: 4, textAlign: 'center' }}>
              <Typography variant="body2" color="text.secondary">
                No cash flow transactions recorded in this period.
              </Typography>
            </Box>
          ) : (
            <Box sx={{ width: '100%', overflowX: 'auto', py: 1 }}>
              <svg
                viewBox={`0 0 ${chartWidth} ${chartHeight}`}
                style={{ width: '100%', height: 'auto', minWidth: 500 }}
              >
                {/* Horizontal guide lines */}
                {[0, 0.5, 1].map((ratio) => {
                  const y = paddingY + plotHeight * (1 - ratio);
                  const val = maxFlow * ratio;
                  return (
                    <g key={ratio}>
                      <line
                        x1={paddingX}
                        y1={y}
                        x2={chartWidth - paddingX}
                        y2={y}
                        stroke="#e0e0e0"
                        strokeDasharray="3 3"
                        strokeWidth="0.8"
                      />
                      <text
                        x={paddingX - 6}
                        y={y + 3}
                        fontSize="9"
                        fill="#888"
                        textAnchor="end"
                      >
                        {fmt(val, 0)}
                      </text>
                    </g>
                  );
                })}

                {/* Bars per period */}
                {periods.map((pt, i) => {
                  const step = plotWidth / periods.length;
                  const xCenter = paddingX + step * i + step / 2;
                  const depH = (Number(pt.deposits || 0) / maxFlow) * plotHeight;
                  const withH = (Number(pt.withdrawals || 0) / maxFlow) * plotHeight;
                  const net = Number(pt.netContributions || 0);

                  const isHovered = hoveredIndex === i;

                  return (
                    <g
                      key={pt.periodLabel}
                      onMouseEnter={() => setHoveredIndex(i)}
                      onMouseLeave={() => setHoveredIndex(null)}
                      style={{ cursor: 'pointer' }}
                    >
                      {/* Hover background column */}
                      {isHovered && (
                        <rect
                          x={xCenter - step / 2}
                          y={paddingY}
                          width={step}
                          height={plotHeight}
                          fill="rgba(0, 0, 0, 0.04)"
                          rx="4"
                        />
                      )}

                      {/* Deposit bar */}
                      <rect
                        x={xCenter - barWidth - 1}
                        y={paddingY + plotHeight - depH}
                        width={barWidth}
                        height={Math.max(depH, 1)}
                        fill="#2e7d32"
                        rx="2"
                        opacity={isHovered ? 1 : 0.85}
                      />

                      {/* Withdrawal bar */}
                      <rect
                        x={xCenter + 1}
                        y={paddingY + plotHeight - withH}
                        width={barWidth}
                        height={Math.max(withH, 1)}
                        fill="#d32f2f"
                        rx="2"
                        opacity={isHovered ? 1 : 0.85}
                      />

                      {/* Period label */}
                      <text
                        x={xCenter}
                        y={chartHeight - 6}
                        fontSize="10"
                        fill={isHovered ? '#1976d2' : '#666'}
                        fontWeight={isHovered ? 'bold' : 'normal'}
                        textAnchor="middle"
                      >
                        {pt.periodLabel}
                      </text>

                      {/* Hover tooltip bubble */}
                      {isHovered && (
                        <g>
                          <rect
                            x={Math.max(Math.min(xCenter - 65, chartWidth - 140), 10)}
                            y={10}
                            width="130"
                            height="45"
                            fill="#333"
                            rx="4"
                            opacity="0.9"
                          />
                          <text
                            x={Math.max(Math.min(xCenter, chartWidth - 75), 75)}
                            y={24}
                            fontSize="10"
                            fill="#fff"
                            fontWeight="bold"
                            textAnchor="middle"
                          >
                            {pt.periodLabel}: Net {currency} {fmt(net)}
                          </text>
                          <text
                            x={Math.max(Math.min(xCenter, chartWidth - 75), 75)}
                            y={36}
                            fontSize="9"
                            fill="#81c784"
                            textAnchor="middle"
                          >
                            +{currency} {fmt(pt.deposits)} / -{currency} {fmt(pt.withdrawals)}
                          </text>
                          <text
                            x={Math.max(Math.min(xCenter, chartWidth - 75), 75)}
                            y={48}
                            fontSize="8"
                            fill="#bbb"
                            textAnchor="middle"
                          >
                            Cumul: {currency} {fmt(pt.cumulativeNetContributions)}
                          </text>
                        </g>
                      )}
                    </g>
                  );
                })}
              </svg>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Periodic Breakdown Table */}
      <Card variant="outlined" sx={{ mb: 3, borderRadius: 2 }}>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
            Periodic Cash Flow Breakdown
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Period</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Deposits</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Withdrawals</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Net Contributions</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Internal Income</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Cumulative Invested</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {periods.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                      No activity in this period.
                    </TableCell>
                  </TableRow>
                ) : (
                  periods.map((p) => {
                    const net = Number(p.netContributions || 0);
                    return (
                      <TableRow key={p.periodLabel} hover>
                        <TableCell sx={{ fontWeight: 600 }}>{p.periodLabel}</TableCell>
                        <TableCell align="right" sx={{ color: 'success.main', fontWeight: 600 }}>
                          +{currency} {fmt(p.deposits)}
                        </TableCell>
                        <TableCell align="right" sx={{ color: 'error.main', fontWeight: 600 }}>
                          -{currency} {fmt(p.withdrawals)}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700, color: net >= 0 ? 'primary.main' : 'error.main' }}>
                          {net >= 0 ? '+' : ''}{currency} {fmt(net)}
                        </TableCell>
                        <TableCell align="right" sx={{ color: 'text.secondary' }}>
                          {currency} {fmt(p.internalIncome)}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>
                          {currency} {fmt(p.cumulativeNetContributions)}
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* Account Cash & Contribution Distribution */}
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
            Account Cash & Inflow Distribution
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Account Name</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Broker</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Native Currency</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Current Cash Balance</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Cash in {currency}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Period Deposits</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Period Withdrawals</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Net Contributions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {accountBreakdown.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                      No active accounts found.
                    </TableCell>
                  </TableRow>
                ) : (
                  accountBreakdown.map((acc) => (
                    <TableRow key={acc.accountId} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{acc.accountName}</TableCell>
                      <TableCell>{acc.brokerName}</TableCell>
                      <TableCell>
                        <Chip label={acc.accountCurrency} size="small" variant="outlined" />
                      </TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>
                        {acc.accountCurrency} {fmt(acc.currentCashBalance)}
                      </TableCell>
                      <TableCell align="right">
                        {currency} {fmt(acc.currentCashBalanceInBase)}
                      </TableCell>
                      <TableCell align="right" sx={{ color: 'success.main' }}>
                        +{currency} {fmt(acc.totalDeposits)}
                      </TableCell>
                      <TableCell align="right" sx={{ color: 'error.main' }}>
                        -{currency} {fmt(acc.totalWithdrawals)}
                      </TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>
                        {currency} {fmt(acc.netContributions)}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>
    </Box>
  );
}

import React, { useMemo, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
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
} from '@mui/material';

import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import PaidOutlinedIcon from '@mui/icons-material/PaidOutlined';
import PieChartOutlineOutlinedIcon from '@mui/icons-material/PieChartOutlineOutlined';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';

import { usePortfolio } from '../portfolios/usePortfolios';
import { usePerformance } from './usePerformance';
import { usePortfolioHistory } from '../analytics/useAnalytics';
import { ErrorAlert } from '../../components';

const PERIODS = ['1M', '3M', '6M', 'YTD', '1Y', 'ALL'] as const;
type Period = (typeof PERIODS)[number];

const fmt = (v: number | undefined | null, dp = 2) =>
  Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });

const fmtPct = (v: number | undefined | null) => `${(Number(v ?? 0) * 100).toFixed(2)}%`;

export function PerformanceDetailPage() {
  const { id: portfolioId } = useParams<{ id: string }>();
  const [selectedPeriod, setSelectedPeriod] = useState<Period>('1Y');
  const [hoveredPointIndex, setHoveredPointIndex] = useState<number | null>(null);

  const {
    data: portfolio,
    isLoading: isPortfolioLoading,
    error: portfolioError,
  } = usePortfolio(portfolioId || '');

  const {
    data: perfData,
    isLoading: isPerfLoading,
    error: perfError,
    refetch: refetchPerf,
  } = usePerformance(portfolioId);

  const {
    data: historyData,
    isLoading: isHistoryLoading,
    error: historyError,
    refetch: refetchHistory,
  } = usePortfolioHistory(
    portfolioId,
    selectedPeriod !== 'ALL' ? { period: selectedPeriod } : { period: 'ALL' },
  );

  const currency = portfolio?.baseCurrency || perfData?.currency || 'GBP';

  // ─── Period-scoped figures ───────────────────────────────────────────────────
  const periodReturnPct = useMemo(() => {
    if (selectedPeriod === 'ALL') {
      return (perfData?.returnMethod === 'TWR' ? perfData?.twrReturn : perfData?.mwrReturn) ?? 0;
    }
    return (historyData?.summary?.portfolioReturnPercentage ?? 0) / 100;
  }, [selectedPeriod, perfData, historyData]);

  const periodGainLoss = useMemo(() => {
    if (selectedPeriod === 'ALL') {
      return (perfData?.totalRealizedGainLoss ?? 0) + (perfData?.totalNetIncome ?? 0);
    }
    return historyData?.summary?.totalGainLoss ?? 0;
  }, [selectedPeriod, perfData, historyData]);

  const annualizedReturn = useMemo(() => {
    return perfData?.returnMethod === 'TWR' ? perfData?.twrAnnualized : null;
  }, [perfData]);

  const maxDrawdown = historyData?.summary?.maxDrawdownPercentage ?? 0;
  const netCashFlows = historyData?.summary?.netCashFlows ?? (perfData?.totalNetDeposits ?? 0);

  // ─── SVG Chart Data points ───────────────────────────────────────────────────
  const dataPoints = historyData?.dataPoints || [];

  const chartMetrics = useMemo(() => {
    if (dataPoints.length === 0) return null;

    const width = 800;
    const height = 280;
    const padding = { top: 20, right: 30, bottom: 40, left: 50 };
    const chartWidth = width - padding.left - padding.right;
    const chartHeight = height - padding.top - padding.bottom;

    const returns = dataPoints.map((d) => d.portfolioReturnPercentage);
    let min = Math.min(...returns, 0);
    let max = Math.max(...returns, 0);

    if (min === max) {
      min = -2;
      max = 2;
    } else {
      const span = max - min;
      min -= span * 0.1;
      max += span * 0.1;
    }

    const points = dataPoints.map((d, i) => {
      const x = padding.left + (i / Math.max(1, dataPoints.length - 1)) * chartWidth;
      const y = padding.top + chartHeight - ((d.portfolioReturnPercentage - min) / (max - min)) * chartHeight;
      return { x, y, ...d };
    });

    const linePath = points
      .map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`)
      .join(' ');

    const zeroY = padding.top + chartHeight - ((0 - min) / (max - min)) * chartHeight;

    const areaPath = `${linePath} L ${points[points.length - 1].x.toFixed(1)} ${(padding.top + chartHeight).toFixed(1)} L ${points[0].x.toFixed(1)} ${(padding.top + chartHeight).toFixed(1)} Z`;

    return {
      width,
      height,
      padding,
      points,
      linePath,
      areaPath,
      zeroY,
      minReturn: min,
      maxReturn: max,
    };
  }, [dataPoints]);

  // ─── Return Attribution ─────────────────────────────────────────────────────
  const capitalGains = (perfData?.totalRealizedGainLoss ?? 0);
  const dividendYieldIncome = (perfData?.totalDividendIncome ?? 0) + (perfData?.totalInterestIncome ?? 0);
  const costFriction = (perfData?.totalFees ?? 0) + (perfData?.totalTaxes ?? 0);
  const totalNetReturn = capitalGains + dividendYieldIncome - costFriction;

  const capitalGainsShare = totalNetReturn !== 0 ? Math.max(0, (capitalGains / Math.abs(totalNetReturn)) * 100) : 0;
  const incomeShare = totalNetReturn !== 0 ? Math.max(0, (dividendYieldIncome / Math.abs(totalNetReturn)) * 100) : 0;

  const isPositiveReturn = periodReturnPct >= 0;

  if (isPortfolioLoading || isPerfLoading) {
    return (
      <Box sx={{ p: 4 }}>
        <Skeleton variant="text" width={280} height={32} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={180} sx={{ borderRadius: 3, mb: 3 }} />
        <Skeleton variant="rectangular" height={360} sx={{ borderRadius: 3 }} />
      </Box>
    );
  }

  if (!portfolio || !perfData) {
    return (
      <Box sx={{ p: 4 }}>
        <ErrorAlert
          error={portfolioError || perfError || new Error('Failed to load performance analytics')}
          onClose={() => {
            refetchPerf();
          }}
        />
        <Button component={RouterLink} to={`/portfolios/${portfolioId}`} startIcon={<ArrowBackIcon />} sx={{ mt: 2 }}>
          Back to Portfolio
        </Button>
      </Box>
    );
  }

  const activePoint = hoveredPointIndex != null && chartMetrics ? chartMetrics.points[hoveredPointIndex] : null;

  return (
    <Box sx={{ p: { xs: 2, md: 4 }, maxWidth: 1440, mx: 'auto' }}>
      {/* ─── Breadcrumbs & Navigation ────────────────────────────────────────── */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2, mb: 3 }}
      >
        <Breadcrumbs aria-label="breadcrumb">
          <Link component={RouterLink} to="/portfolios" underline="hover" color="inherit">
            Portfolios
          </Link>
          <Link component={RouterLink} to={`/portfolios/${portfolioId}`} underline="hover" color="inherit">
            {portfolio.name}
          </Link>
          <Typography color="text.primary" sx={{ fontWeight: 600 }}>
            Performance Analytics
          </Typography>
        </Breadcrumbs>

        <Button
          component={RouterLink}
          to={`/portfolios/${portfolioId}`}
          startIcon={<ArrowBackIcon />}
          size="small"
          variant="outlined"
        >
          Back to Portfolio
        </Button>
      </Stack>

      <ErrorAlert error={historyError} onClose={() => refetchHistory()} />

      {/* ─── Performance Hero ───────────────────────────────────────────────── */}
      <Paper
        variant="outlined"
        sx={{
          p: { xs: 2.5, md: 3.5 },
          mb: 3,
          borderRadius: 3,
          background: (t) =>
            t.palette.mode === 'dark'
              ? 'linear-gradient(135deg, rgba(99,102,241,0.08) 0%, rgba(139,92,246,0.03) 100%)'
              : 'linear-gradient(135deg, rgba(99,102,241,0.04) 0%, rgba(139,92,246,0.02) 100%)',
        }}
      >
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2, mb: 3 }}
        >
          <Box>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.75, flexWrap: 'wrap', gap: 0.5 }}>
              <Typography variant="h4" sx={{ fontWeight: 800 }}>
                {portfolio.name} Performance
              </Typography>
              <Chip
                label={`${perfData.returnMethod} Methodology`}
                size="small"
                color="primary"
                variant="outlined"
                sx={{ fontWeight: 600 }}
              />
              <Chip label={currency} size="small" variant="outlined" />
              <Chip
                label={`Valuation: ${perfData.valuationBasis.replace('_', ' ')}`}
                size="small"
                variant="outlined"
              />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              Time-Weighted and Money-Weighted returns with fee drag and multi-period attribution.
            </Typography>
          </Box>

          {/* Period Selector Chips */}
          <Stack direction="row" spacing={0.75} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
            {PERIODS.map((period) => {
              const isSelected = selectedPeriod === period;
              return (
                <Chip
                  key={period}
                  label={period}
                  clickable
                  variant={isSelected ? 'filled' : 'outlined'}
                  color={isSelected ? 'primary' : 'default'}
                  onClick={() => setSelectedPeriod(period)}
                  sx={{ fontWeight: 700, fontSize: '0.8rem', height: 32 }}
                />
              );
            })}
          </Stack>
        </Stack>

        <Divider sx={{ mb: 3 }} />

        {/* Headline Return & Key Metrics Grid */}
        <Grid container spacing={2.5}>
          {/* Main Return Card */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Paper
              variant="outlined"
              sx={{
                p: 2.5,
                borderRadius: 2.5,
                bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(0,0,0,0.2)' : 'rgba(255,255,255,0.7)'),
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
              }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 700 }}>
                {selectedPeriod === 'ALL' ? 'Total All-Time Return' : `${selectedPeriod} Period Return`}
              </Typography>
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', my: 1 }}>
                <Box
                  sx={{
                    p: 1,
                    borderRadius: 2,
                    bgcolor: isPositiveReturn ? 'success.main' : 'error.main',
                    color: 'white',
                    display: 'flex',
                  }}
                >
                  {isPositiveReturn ? <TrendingUpIcon fontSize="medium" /> : <TrendingDownIcon fontSize="medium" />}
                </Box>
                <Typography
                  variant="h3"
                  sx={{ fontWeight: 800, color: isPositiveReturn ? 'success.main' : 'error.main' }}
                >
                  {fmtPct(periodReturnPct)}
                </Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Gain/Loss: <strong>{periodGainLoss >= 0 ? '+' : ''}{currency} {fmt(periodGainLoss)}</strong>
              </Typography>
            </Paper>
          </Grid>

          {/* Sub KPI: Annualized */}
          <Grid size={{ xs: 6, sm: 3, md: 2 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Annualized Return
              </Typography>
              <Typography
                variant="h5"
                sx={{
                  fontWeight: 700,
                  mt: 0.5,
                  color: annualizedReturn != null && annualizedReturn >= 0 ? 'success.main' : 'text.primary',
                }}
              >
                {annualizedReturn != null ? fmtPct(annualizedReturn) : '—'}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                TWR Compound Annual
              </Typography>
            </Paper>
          </Grid>

          {/* Sub KPI: Max Drawdown */}
          <Grid size={{ xs: 6, sm: 3, md: 2 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Max Drawdown
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5, color: maxDrawdown > 0 ? 'error.main' : 'text.primary' }}>
                {maxDrawdown > 0 ? `-${fmt(maxDrawdown)}%` : '0.00%'}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Peak-to-trough decline
              </Typography>
            </Paper>
          </Grid>

          {/* Sub KPI: Net Inflows */}
          <Grid size={{ xs: 6, sm: 3, md: 2 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Net Capital Inflow
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5 }}>
                {currency} {fmt(netCashFlows)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Deposits - Withdrawals
              </Typography>
            </Paper>
          </Grid>

          {/* Sub KPI: Total Cost Basis */}
          <Grid size={{ xs: 6, sm: 3, md: 2 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Total Cost Basis
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5 }}>
                {currency} {fmt(perfData.totalCostBasis)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Active capital deployed
              </Typography>
            </Paper>
          </Grid>
        </Grid>
      </Paper>

      {/* ─── Cumulative Return Timeline Chart ──────────────────────────────── */}
      <Paper variant="outlined" sx={{ p: 3, mb: 3, borderRadius: 3 }}>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              Cumulative Portfolio Return Trajectory ({selectedPeriod})
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Time-weighted continuous compounding trajectory unaffected by cash inflows and outflows.
            </Typography>
          </Box>
          {activePoint && (
            <Paper variant="outlined" sx={{ px: 2, py: 0.75, borderRadius: 2, bgcolor: 'background.default' }}>
              <Typography variant="caption" color="text.secondary" sx={{ mr: 1.5 }}>
                {new Date(activePoint.timestamp).toLocaleDateString()}
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  fontWeight: 700,
                  color: activePoint.portfolioReturnPercentage >= 0 ? 'success.main' : 'error.main',
                }}
              >
                Return: {activePoint.portfolioReturnPercentage >= 0 ? '+' : ''}
                {activePoint.portfolioReturnPercentage.toFixed(2)}%
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ ml: 1.5 }}>
                Value: {currency} {fmt(activePoint.marketValue)}
              </Typography>
            </Paper>
          )}
        </Stack>

        {isHistoryLoading ? (
          <Box sx={{ py: 8, display: 'flex', justifyContent: 'center' }}>
            <Skeleton variant="rectangular" width="100%" height={240} sx={{ borderRadius: 2 }} />
          </Box>
        ) : chartMetrics ? (
          <Box sx={{ width: '100%', overflowX: 'auto' }}>
            <svg
              viewBox={`0 0 ${chartMetrics.width} ${chartMetrics.height}`}
              style={{ width: '100%', height: 'auto', minHeight: 240 }}
            >
              <defs>
                <linearGradient id="perfGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#6366f1" stopOpacity="0.35" />
                  <stop offset="100%" stopColor="#6366f1" stopOpacity="0.0" />
                </linearGradient>
              </defs>

              {/* Zero reference line */}
              {chartMetrics.zeroY >= chartMetrics.padding.top &&
                chartMetrics.zeroY <= chartMetrics.height - chartMetrics.padding.bottom && (
                  <line
                    x1={chartMetrics.padding.left}
                    y1={chartMetrics.zeroY}
                    x2={chartMetrics.width - chartMetrics.padding.right}
                    y2={chartMetrics.zeroY}
                    stroke="#94a3b8"
                    strokeDasharray="4 4"
                    strokeWidth="1"
                  />
                )}

              {/* Gradient Area Fill */}
              <path d={chartMetrics.areaPath} fill="url(#perfGrad)" />

              {/* Line Stroke */}
              <path
                d={chartMetrics.linePath}
                fill="none"
                stroke="#6366f1"
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />

              {/* Interactive Points */}
              {chartMetrics.points.map((p, idx) => (
                <circle
                  key={idx}
                  cx={p.x}
                  cy={p.y}
                  r={hoveredPointIndex === idx ? 5 : 3}
                  fill={hoveredPointIndex === idx ? '#4f46e5' : '#6366f1'}
                  stroke="#ffffff"
                  strokeWidth={hoveredPointIndex === idx ? 2 : 1}
                  style={{ cursor: 'pointer', transition: 'r 0.2s' }}
                  onMouseEnter={() => setHoveredPointIndex(idx)}
                  onMouseLeave={() => setHoveredPointIndex(null)}
                />
              ))}

              {/* X Axis Date labels */}
              {chartMetrics.points.length > 0 && (
                <>
                  <text
                    x={chartMetrics.points[0].x}
                    y={chartMetrics.height - 12}
                    fill="#94a3b8"
                    fontSize="11"
                    textAnchor="start"
                  >
                    {new Date(chartMetrics.points[0].timestamp).toLocaleDateString(undefined, {
                      month: 'short',
                      day: 'numeric',
                    })}
                  </text>
                  <text
                    x={chartMetrics.points[chartMetrics.points.length - 1].x}
                    y={chartMetrics.height - 12}
                    fill="#94a3b8"
                    fontSize="11"
                    textAnchor="end"
                  >
                    {new Date(
                      chartMetrics.points[chartMetrics.points.length - 1].timestamp,
                    ).toLocaleDateString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      year: '2-digit',
                    })}
                  </text>
                </>
              )}
            </svg>
          </Box>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>
            No historical performance points available for this period.
          </Typography>
        )}
      </Paper>

      {/* ─── Return Attribution & Income vs Drag Breakdown ──────────────────── */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        {/* Attribution Waterfall */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: 3, borderRadius: 3, height: '100%' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
              <InsightsOutlinedIcon color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Return Attribution Breakdown
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5 }}>
              Decomposition of portfolio return into price growth, income yields, and friction drag.
            </Typography>

            {/* Visual multi-segment bar */}
            <Box sx={{ mb: 2.5 }}>
              <Stack direction="row" sx={{ height: 16, borderRadius: 2, overflow: 'hidden', mb: 1 }}>
                <Box
                  sx={{
                    width: `${capitalGainsShare}%`,
                    bgcolor: 'primary.main',
                    transition: 'width 0.5s ease',
                  }}
                />
                <Box
                  sx={{
                    width: `${incomeShare}%`,
                    bgcolor: 'secondary.main',
                    transition: 'width 0.5s ease',
                  }}
                />
              </Stack>
              <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between' }}>
                <Typography variant="caption" sx={{ color: 'primary.main', fontWeight: 600 }}>
                  ● Capital Growth: {capitalGainsShare.toFixed(1)}%
                </Typography>
                <Typography variant="caption" sx={{ color: 'secondary.main', fontWeight: 600 }}>
                  ● Income Yield: {incomeShare.toFixed(1)}%
                </Typography>
              </Stack>
            </Box>

            <Divider sx={{ my: 2 }} />

            <Stack spacing={2}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    Realized Capital Growth
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Gains locked from position disposals
                  </Typography>
                </Box>
                <Typography
                  variant="body1"
                  sx={{
                    fontWeight: 700,
                    color: capitalGains >= 0 ? 'success.main' : 'error.main',
                  }}
                >
                  {capitalGains >= 0 ? '+' : ''}{currency} {fmt(capitalGains)}
                </Typography>
              </Stack>

              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    Dividend & Interest Income
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Cash payouts earned from holdings
                  </Typography>
                </Box>
                <Typography variant="body1" sx={{ fontWeight: 700, color: 'secondary.main' }}>
                  +{currency} {fmt(dividendYieldIncome)}
                </Typography>
              </Stack>

              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    Fee & Tax Friction
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Broker commissions, platform fees & withholding taxes
                  </Typography>
                </Box>
                <Typography variant="body1" sx={{ fontWeight: 700, color: 'error.main' }}>
                  -{currency} {fmt(costFriction)}
                </Typography>
              </Stack>
            </Stack>
          </Paper>
        </Grid>

        {/* Income & Cost Breakdown */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: 3, borderRadius: 3, height: '100%' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
              <PaidOutlinedIcon color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Income & Cost Drag Analysis
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5 }}>
              Net yield retained after accounting for expenses and custodian deductions.
            </Typography>

            <Grid container spacing={2}>
              <Grid size={{ xs: 6 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                    DIVIDEND INCOME
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'success.main', mt: 0.5 }}>
                    +{currency} {fmt(perfData.totalDividendIncome)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Equity distributions
                  </Typography>
                </Paper>
              </Grid>

              <Grid size={{ xs: 6 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                    INTEREST INCOME
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'success.main', mt: 0.5 }}>
                    +{currency} {fmt(perfData.totalInterestIncome)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Cash & fixed income
                  </Typography>
                </Paper>
              </Grid>

              <Grid size={{ xs: 6 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                    PLATFORM FEES
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'error.main', mt: 0.5 }}>
                    -{currency} {fmt(perfData.totalFees)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Trading & custody costs
                  </Typography>
                </Paper>
              </Grid>

              <Grid size={{ xs: 6 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                    WITHHOLDING TAXES
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'error.main', mt: 0.5 }}>
                    -{currency} {fmt(perfData.totalTaxes)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Foreign tax withheld
                  </Typography>
                </Paper>
              </Grid>
            </Grid>

            <Box sx={{ mt: 2.5, p: 2, borderRadius: 2, bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)') }}>
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                  Net Income Retained:
                </Typography>
                <Typography variant="subtitle1" sx={{ fontWeight: 800, color: 'success.main' }}>
                  {currency} {fmt(perfData.totalNetIncome)}
                </Typography>
              </Stack>
            </Box>
          </Paper>
        </Grid>
      </Grid>

      {/* ─── Account Performance Attribution Table ─────────────────────────── */}
      <Paper variant="outlined" sx={{ p: 3, mb: 3, borderRadius: 3 }}>
        <Typography variant="h6" sx={{ fontWeight: 700, mb: 0.5 }}>
          Account Performance Attribution
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
          Individual return contribution, cost basis, realized gains, and net distributions per custodian account.
        </Typography>

        {perfData.byAccount.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3 }}>
            No individual accounts recorded for this portfolio.
          </Typography>
        ) : (
          <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Account</TableCell>
                  <TableCell align="right">Cost Basis</TableCell>
                  <TableCell align="right">Realized Gain/Loss</TableCell>
                  <TableCell align="right">Dividends</TableCell>
                  <TableCell align="right">Interest</TableCell>
                  <TableCell align="right">Fees & Taxes</TableCell>
                  <TableCell align="right">Currency</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {perfData.byAccount.map((acc) => {
                  const feesAndTaxes = (acc.fees ?? 0) + (acc.taxes ?? 0);
                  const isRealizedPositive = acc.realizedGainLoss >= 0;
                  return (
                    <TableRow key={acc.accountId} hover>
                      <TableCell sx={{ fontWeight: 600 }}>{acc.accountName}</TableCell>
                      <TableCell align="right">{fmt(acc.costBasis)}</TableCell>
                      <TableCell
                        align="right"
                        sx={{
                          fontWeight: 600,
                          color: isRealizedPositive ? 'success.main' : 'error.main',
                        }}
                      >
                        {isRealizedPositive ? '+' : ''}{fmt(acc.realizedGainLoss)}
                      </TableCell>
                      <TableCell align="right" sx={{ color: 'secondary.main', fontWeight: 600 }}>
                        +{fmt(acc.dividendIncome)}
                      </TableCell>
                      <TableCell align="right">+{fmt(acc.interestIncome)}</TableCell>
                      <TableCell align="right" sx={{ color: feesAndTaxes > 0 ? 'error.main' : 'text.secondary' }}>
                        {feesAndTaxes > 0 ? `-${fmt(feesAndTaxes)}` : '—'}
                      </TableCell>
                      <TableCell align="right">
                        <Chip label={acc.currency} size="small" variant="outlined" />
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {/* ─── Methodology Guide & XIRR Status ───────────────────────────────── */}
      <Paper variant="outlined" sx={{ p: 3, borderRadius: 3 }}>
        <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
          Return Methodology & Calculation Standards
        </Typography>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
              Time-Weighted Return (TWR)
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Measures portfolio compound rate of growth over multiple sub-periods, isolating investment performance from the timing and magnitude of cash deposits and withdrawals. Recommended standard for manager evaluation.
            </Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
              Money-Weighted Return (MWR)
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Factors in the exact timing and amount of capital added or withdrawn. Reflects the personal dollar return experienced by the investor.
            </Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
              Internal Rate of Return (XIRR)
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Computes the annualized discount rate that equates the net present value of all cash flows to current portfolio value. Will be enabled in the market data sync phase.
            </Typography>
          </Grid>
        </Grid>
      </Paper>
    </Box>
  );
}

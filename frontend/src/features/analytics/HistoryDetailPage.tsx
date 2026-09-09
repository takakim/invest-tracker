import React, { useMemo, useState, useEffect } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import {
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  FormControl,
  Grid,
  InputLabel,
  Link,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';

import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import QueryStatsOutlinedIcon from '@mui/icons-material/QueryStatsOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

import { usePortfolio } from '../portfolios/usePortfolios';
import { usePortfolioHistory } from './useAnalytics';
import { useBenchmarkList, useBenchmarkComparison } from '../benchmark/useBenchmark';
import { ErrorAlert } from '../../components';
import type { BenchmarkPeriod, HistoricalValuationPoint } from '../../types';

const PERIODS = ['1M', '3M', '6M', 'YTD', '1Y', '3Y', 'ALL'] as const;
type TimePeriod = (typeof PERIODS)[number];
type ChartMode = 'VALUE' | 'RETURN' | 'DRAWDOWN';

const fmt = (v: number | undefined | null, dp = 2) =>
  Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });

const fmtPct = (v: number | undefined | null) => `${(Number(v ?? 0)).toFixed(2)}%`;

export function HistoryDetailPage() {
  const { id: portfolioId } = useParams<{ id: string }>();
  const [period, setPeriod] = useState<TimePeriod>('1Y');
  const [chartMode, setChartMode] = useState<ChartMode>('VALUE');
  const [selectedBenchmarkId, setSelectedBenchmarkId] = useState<string>('');
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [filterDate, setFilterDate] = useState('');

  const {
    data: portfolio,
    isLoading: isPortfolioLoading,
    error: portfolioError,
  } = usePortfolio(portfolioId || '');

  const { data: benchmarkList = [] } = useBenchmarkList();

  // Auto-select first benchmark ETF or index if available
  useEffect(() => {
    if (benchmarkList.length > 0 && !selectedBenchmarkId) {
      const etf = benchmarkList.find((b) => b.assetClass === 'ETF');
      setSelectedBenchmarkId(etf ? etf.id : benchmarkList[0].id);
    }
  }, [benchmarkList, selectedBenchmarkId]);

  const {
    data: historyData,
    isLoading: isHistoryLoading,
    error: historyError,
    refetch: refetchHistory,
  } = usePortfolioHistory(portfolioId, {
    period,
    benchmarkId: selectedBenchmarkId || undefined,
  });

  // Benchmark comparison for excess return & metrics
  const benchmarkPeriod = (['1M', '3M', '6M', '1Y', 'YTD', 'ALL'].includes(period)
    ? (period as BenchmarkPeriod)
    : '1Y');

  const {
    data: comparison,
    isLoading: isComparisonLoading,
  } = useBenchmarkComparison(portfolioId, selectedBenchmarkId, benchmarkPeriod);

  const currency = portfolio?.baseCurrency || historyData?.baseCurrency || 'GBP';
  const dataPoints = useMemo(() => historyData?.dataPoints || [], [historyData]);
  const summary = historyData?.summary;

  // Compute drawdown points for each historical data point:
  // drawdown % = ((marketValue - runningPeak) / runningPeak) * 100
  const pointsWithDrawdown = useMemo(() => {
    let peak = 0;
    return dataPoints.map((pt) => {
      if (pt.marketValue > peak) {
        peak = pt.marketValue;
      }
      const drawdown = peak > 0 ? ((pt.marketValue - peak) / peak) * 100 : 0;
      return {
        ...pt,
        drawdownPercentage: Math.min(0, drawdown),
      };
    });
  }, [dataPoints]);

  // Active hover point
  const activePoint = hoverIndex !== null && pointsWithDrawdown[hoverIndex] ? pointsWithDrawdown[hoverIndex] : null;

  // Chart coordinate calculations (Pure SVG)
  const svgWidth = 850;
  const svgHeight = 340;
  const padding = { top: 25, right: 30, bottom: 45, left: 65 };
  const chartWidth = svgWidth - padding.left - padding.right;
  const chartHeight = svgHeight - padding.top - padding.bottom;

  const chartPaths = useMemo(() => {
    if (pointsWithDrawdown.length === 0) {
      return { minVal: 0, maxVal: 100, pathPrimary: '', pathSecondary: '', areaPrimary: '', zeroY: padding.top + chartHeight };
    }

    const n = pointsWithDrawdown.length;

    if (chartMode === 'VALUE') {
      let min = Math.min(...pointsWithDrawdown.map((d) => Math.min(d.marketValue, d.investedCapital)));
      let max = Math.max(...pointsWithDrawdown.map((d) => Math.max(d.marketValue, d.investedCapital)));
      if (min === max) {
        min = Math.max(0, min * 0.9);
        max = max * 1.1 || 1000;
      } else {
        const span = max - min;
        min = Math.max(0, min - span * 0.05);
        max = max + span * 0.08;
      }

      const pointsMarket = pointsWithDrawdown.map((d, i) => {
        const x = padding.left + (i / Math.max(1, n - 1)) * chartWidth;
        const y = padding.top + chartHeight - ((d.marketValue - min) / (max - min)) * chartHeight;
        return { x, y };
      });

      const pointsInvested = pointsWithDrawdown.map((d, i) => {
        const x = padding.left + (i / Math.max(1, n - 1)) * chartWidth;
        const y = padding.top + chartHeight - ((d.investedCapital - min) / (max - min)) * chartHeight;
        return { x, y };
      });

      const lineM = pointsMarket.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
      const lineI = pointsInvested.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
      const areaM = `${lineM} L ${pointsMarket[n - 1].x.toFixed(1)} ${(padding.top + chartHeight).toFixed(1)} L ${pointsMarket[0].x.toFixed(1)} ${(padding.top + chartHeight).toFixed(1)} Z`;

      return { minVal: min, maxVal: max, pathPrimary: lineM, pathSecondary: lineI, areaPrimary: areaM, zeroY: padding.top + chartHeight };
    }

    if (chartMode === 'RETURN') {
      const allReturns = pointsWithDrawdown.map((d) => d.portfolioReturnPercentage);
      pointsWithDrawdown.forEach((d) => {
        if (d.benchmarkReturnPercentage != null) allReturns.push(d.benchmarkReturnPercentage);
      });

      let min = Math.min(...allReturns, 0);
      let max = Math.max(...allReturns, 0);
      if (min === max) {
        min = -5;
        max = 5;
      } else {
        const span = max - min;
        min = min - span * 0.08;
        max = max + span * 0.08;
      }

      const pointsPortfolio = pointsWithDrawdown.map((d, i) => {
        const x = padding.left + (i / Math.max(1, n - 1)) * chartWidth;
        const y = padding.top + chartHeight - ((d.portfolioReturnPercentage - min) / (max - min)) * chartHeight;
        return { x, y };
      });

      const lineP = pointsPortfolio.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');

      let lineB = '';
      if (pointsWithDrawdown.some((d) => d.benchmarkReturnPercentage != null)) {
        const pointsBenchmark = pointsWithDrawdown
          .map((d, i) => {
            if (d.benchmarkReturnPercentage == null) return null;
            const x = padding.left + (i / Math.max(1, n - 1)) * chartWidth;
            const y = padding.top + chartHeight - ((d.benchmarkReturnPercentage - min) / (max - min)) * chartHeight;
            return { x, y };
          })
          .filter(Boolean) as { x: number; y: number }[];

        if (pointsBenchmark.length > 0) {
          lineB = pointsBenchmark.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
        }
      }

      const zeroY = padding.top + chartHeight - ((0 - min) / (max - min)) * chartHeight;
      const areaP = `${lineP} L ${pointsPortfolio[n - 1].x.toFixed(1)} ${zeroY.toFixed(1)} L ${pointsPortfolio[0].x.toFixed(1)} ${zeroY.toFixed(1)} Z`;

      return { minVal: min, maxVal: max, pathPrimary: lineP, pathSecondary: lineB, areaPrimary: areaP, zeroY };
    }

    // DRAWDOWN MODE
    const drawdowns = pointsWithDrawdown.map((d) => d.drawdownPercentage);
    const worstDrawdown = Math.min(...drawdowns, -5);
    const min = worstDrawdown * 1.1; // e.g. -15% -> -16.5%
    const max = 0.5; // just above 0%

    const pointsDrawdown = pointsWithDrawdown.map((d, i) => {
      const x = padding.left + (i / Math.max(1, n - 1)) * chartWidth;
      const y = padding.top + chartHeight - ((d.drawdownPercentage - min) / (max - min)) * chartHeight;
      return { x, y };
    });

    const zeroY = padding.top + chartHeight - ((0 - min) / (max - min)) * chartHeight;
    const lineD = pointsDrawdown.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
    const areaD = `${lineD} L ${pointsDrawdown[n - 1].x.toFixed(1)} ${zeroY.toFixed(1)} L ${pointsDrawdown[0].x.toFixed(1)} ${zeroY.toFixed(1)} Z`;

    return { minVal: min, maxVal: max, pathPrimary: lineD, pathSecondary: '', areaPrimary: areaD, zeroY };
  }, [pointsWithDrawdown, chartMode, chartWidth, chartHeight]);

  // Export full history points to CSV
  const handleExportCsv = () => {
    if (pointsWithDrawdown.length === 0) return;
    const headers = [
      'Date',
      'Market Value',
      'Invested Capital',
      'Cost Basis',
      'Cash Value',
      'Unrealized Gain/Loss',
      'Portfolio Return %',
      'Benchmark Return %',
      'Drawdown %',
    ];
    const rows = pointsWithDrawdown.map((pt) => [
      `"${pt.timestamp.split('T')[0]}"`,
      pt.marketValue.toFixed(2),
      pt.investedCapital.toFixed(2),
      pt.costBasis.toFixed(2),
      pt.cashValue.toFixed(2),
      pt.unrealizedGainLoss.toFixed(2),
      pt.portfolioReturnPercentage.toFixed(2),
      pt.benchmarkReturnPercentage != null ? pt.benchmarkReturnPercentage.toFixed(2) : '',
      pt.drawdownPercentage.toFixed(2),
    ]);

    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `portfolio-history-${portfolioId}-${period}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // Filtered tabular points
  const filteredTablePoints = useMemo(() => {
    if (!filterDate) return pointsWithDrawdown;
    return pointsWithDrawdown.filter((p) => p.timestamp.toLowerCase().includes(filterDate.toLowerCase()));
  }, [pointsWithDrawdown, filterDate]);

  const pagedPoints = useMemo(() => {
    const start = page * rowsPerPage;
    return filteredTablePoints.slice(start, start + rowsPerPage);
  }, [filteredTablePoints, page, rowsPerPage]);

  const selectedBenchmark = benchmarkList.find((b) => b.id === selectedBenchmarkId);

  const isReturnPositive = (summary?.portfolioReturnPercentage ?? 0) >= 0;
  const isExcessPositive = (summary?.excessReturnPercentage ?? 0) >= 0;

  if (isPortfolioLoading || (isHistoryLoading && !historyData)) {
    return (
      <Stack spacing={3}>
        <Skeleton variant="rectangular" height={50} sx={{ borderRadius: 1 }} />
        <Skeleton variant="rectangular" height={140} sx={{ borderRadius: 2 }} />
        <Skeleton variant="rectangular" height={400} sx={{ borderRadius: 2 }} />
      </Stack>
    );
  }

  if (portfolioError || historyError) {
    return (
      <Stack spacing={2}>
        <Button
          component={RouterLink}
          to={`/portfolios/${portfolioId}`}
          startIcon={<ArrowBackIcon />}
          sx={{ alignSelf: 'flex-start' }}
        >
          Back to Portfolio
        </Button>
        <ErrorAlert
          error={portfolioError || historyError}
          title="Failed to load portfolio history or benchmark analytics"
        />
      </Stack>
    );
  }

  return (
    <Stack spacing={3} data-testid="history-detail-page">
      {/* Breadcrumbs & Navigation */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}
      >
        <Breadcrumbs aria-label="breadcrumb">
          <Link component={RouterLink} to="/portfolios" underline="hover" color="inherit">
            Portfolios
          </Link>
          <Link component={RouterLink} to={`/portfolios/${portfolioId}`} underline="hover" color="inherit">
            {portfolio?.name || 'Portfolio'}
          </Link>
          <Typography color="text.primary" sx={{ fontWeight: 600 }}>
            History & Benchmark
          </Typography>
        </Breadcrumbs>

        <Button
          component={RouterLink}
          to={`/portfolios/${portfolioId}`}
          variant="outlined"
          size="small"
          startIcon={<ArrowBackIcon />}
        >
          Back to Portfolio
        </Button>
      </Stack>

      {/* Header & Controls Bar */}
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2 }}
            >
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 700 }}>
                  Historical Valuation & Benchmark Comparison
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Interactive multi-mode wealth trajectory, benchmark excess return (Alpha), and peak-to-trough drawdown depth
                </Typography>
              </Box>

              {/* Action Buttons */}
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                {/* Benchmark Selector */}
                {benchmarkList.length > 0 && (
                  <FormControl size="small" sx={{ minWidth: 220 }}>
                    <InputLabel id="history-benchmark-select-label">Benchmark Index / ETF</InputLabel>
                    <Select
                      labelId="history-benchmark-select-label"
                      id="history-benchmark-select"
                      value={selectedBenchmarkId}
                      label="Benchmark Index / ETF"
                      onChange={(e) => setSelectedBenchmarkId(e.target.value)}
                    >
                      {benchmarkList.map((b) => (
                        <MenuItem key={b.id} value={b.id}>
                          {b.name} {b.ticker ? `(${b.ticker})` : ''}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                )}

                {/* CSV Export */}
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<FileDownloadOutlinedIcon />}
                  onClick={handleExportCsv}
                  disabled={pointsWithDrawdown.length === 0}
                >
                  Export CSV
                </Button>
              </Stack>
            </Stack>

            <Divider />

            {/* Time Period Selector Chips */}
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1.5 }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                TIMEFRAME HORIZON:
              </Typography>
              <ToggleButtonGroup
                value={period}
                exclusive
                size="small"
                onChange={(_, val) => val && setPeriod(val)}
                aria-label="historical period"
              >
                {PERIODS.map((p) => (
                  <ToggleButton key={p} value={p} sx={{ px: 1.75, py: 0.5, fontWeight: 600, fontSize: '0.8rem' }}>
                    {p}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {/* Hero Performance KPI Strip */}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <AccountBalanceWalletOutlinedIcon color="primary" fontSize="small" />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                Ending Portfolio Value
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              {currency} {fmt(summary?.endingValue)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Starting: {currency} {fmt(summary?.startingValue)}
            </Typography>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <LayersOutlinedIcon color="primary" fontSize="small" />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                Net Capital Inflows
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              {currency} {fmt(summary?.netCashFlows)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Capital Gain: {currency} {fmt(summary?.totalGainLoss)}
            </Typography>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <ShowChartOutlinedIcon color={isReturnPositive ? 'success' : 'error'} fontSize="small" />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                Portfolio Return ({period})
              </Typography>
            </Stack>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline' }}>
              <Typography
                variant="h5"
                sx={{
                  fontWeight: 700,
                  color: isReturnPositive ? 'success.main' : 'error.main',
                }}
              >
                {isReturnPositive ? '+' : ''}
                {fmtPct(summary?.portfolioReturnPercentage)}
              </Typography>
            </Stack>
            <Typography variant="caption" color="text.secondary">
              Benchmark: {summary?.benchmarkReturnPercentage != null ? `${summary.benchmarkReturnPercentage >= 0 ? '+' : ''}${fmtPct(summary.benchmarkReturnPercentage)}` : 'N/A'}
            </Typography>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <CompareArrowsIcon color={isExcessPositive ? 'success' : 'error'} fontSize="small" />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                Alpha / Excess Return
              </Typography>
            </Stack>
            {summary?.excessReturnPercentage != null ? (
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 0.5 }}>
                <Typography
                  variant="h5"
                  sx={{
                    fontWeight: 700,
                    color: isExcessPositive ? 'success.main' : 'error.main',
                  }}
                >
                  {isExcessPositive ? '+' : ''}
                  {fmtPct(summary.excessReturnPercentage)}
                </Typography>
                <Chip
                  size="small"
                  label={isExcessPositive ? 'Outperforming' : 'Underperforming'}
                  color={isExcessPositive ? 'success' : 'error'}
                  sx={{ fontWeight: 700, fontSize: '0.75rem' }}
                />
              </Stack>
            ) : (
              <Typography variant="h6" color="text.secondary" sx={{ mt: 0.5 }}>
                Select Benchmark
              </Typography>
            )}
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
              Max Drawdown: <strong style={{ color: '#d32f2f' }}>-{fmtPct(summary?.maxDrawdownPercentage)}</strong>
            </Typography>
          </Card>
        </Grid>
      </Grid>

      {/* Multi-Mode Interactive SVG Chart Card */}
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Stack spacing={2.5}>
            {/* Chart Sub-header & Mode Switcher */}
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1.5 }}
            >
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Visual Trajectory & Performance Analysis
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {chartMode === 'VALUE'
                    ? 'Comparing total portfolio market value against cumulative invested capital basis'
                    : chartMode === 'RETURN'
                    ? `Comparing cumulative portfolio percentage return vs ${selectedBenchmark?.name || 'benchmark'} return`
                    : 'Peak-to-trough underwater drawdown depth percentage across the selected timeframe'}
                </Typography>
              </Box>

              {/* Mode Toggle */}
              <ToggleButtonGroup
                value={chartMode}
                exclusive
                size="small"
                onChange={(_, val) => val && setChartMode(val)}
                aria-label="chart display mode"
              >
                <ToggleButton value="VALUE" sx={{ px: 1.5, fontWeight: 600 }}>
                  Wealth & Capital
                </ToggleButton>
                <ToggleButton value="RETURN" sx={{ px: 1.5, fontWeight: 600 }}>
                  Return vs Benchmark (%)
                </ToggleButton>
                <ToggleButton value="DRAWDOWN" sx={{ px: 1.5, fontWeight: 600 }}>
                  Drawdown Depth (%)
                </ToggleButton>
              </ToggleButtonGroup>
            </Stack>

            {/* Active Point Hover Indicator */}
            {activePoint && (
              <Paper
                variant="outlined"
                sx={{
                  p: 1.5,
                  bgcolor: 'background.default',
                  borderRadius: 1.5,
                  borderLeft: '4px solid',
                  borderColor: 'primary.main',
                }}
              >
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 1, sm: 3 }} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    Date: {new Date(activePoint.timestamp).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })}
                  </Typography>
                  <Typography variant="body2">
                    Market Value: <strong>{currency} {fmt(activePoint.marketValue)}</strong>
                  </Typography>
                  <Typography variant="body2">
                    Invested Capital: <strong>{currency} {fmt(activePoint.investedCapital)}</strong>
                  </Typography>
                  <Typography variant="body2" sx={{ color: activePoint.portfolioReturnPercentage >= 0 ? 'success.main' : 'error.main' }}>
                    Portfolio Return: <strong>{activePoint.portfolioReturnPercentage >= 0 ? '+' : ''}{fmtPct(activePoint.portfolioReturnPercentage)}</strong>
                  </Typography>
                  {activePoint.benchmarkReturnPercentage != null && (
                    <Typography variant="body2" sx={{ color: 'secondary.main' }}>
                      Benchmark: <strong>{activePoint.benchmarkReturnPercentage >= 0 ? '+' : ''}{fmtPct(activePoint.benchmarkReturnPercentage)}</strong>
                    </Typography>
                  )}
                  <Typography variant="body2" sx={{ color: 'error.main' }}>
                    Drawdown: <strong>{fmtPct(activePoint.drawdownPercentage)}</strong>
                  </Typography>
                </Stack>
              </Paper>
            )}

            {/* SVG Vector Chart Area */}
            {pointsWithDrawdown.length === 0 ? (
              <Box sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">No historical valuation points recorded for this timeframe.</Typography>
              </Box>
            ) : (
              <Box
                sx={{
                  width: '100%',
                  overflowX: 'auto',
                  position: 'relative',
                  bgcolor: 'background.paper',
                  borderRadius: 1,
                  pt: 1,
                }}
              >
                <svg
                  viewBox={`0 0 ${svgWidth} ${svgHeight}`}
                  style={{ width: '100%', height: 'auto', display: 'block', userSelect: 'none' }}
                >
                  <defs>
                    <linearGradient id="wealthGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#2563eb" stopOpacity="0.30" />
                      <stop offset="100%" stopColor="#2563eb" stopOpacity="0.02" />
                    </linearGradient>
                    <linearGradient id="returnGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#10b981" stopOpacity="0.25" />
                      <stop offset="100%" stopColor="#10b981" stopOpacity="0.02" />
                    </linearGradient>
                    <linearGradient id="drawdownGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#ef4444" stopOpacity="0.05" />
                      <stop offset="100%" stopColor="#ef4444" stopOpacity="0.35" />
                    </linearGradient>
                  </defs>

                  {/* Horizontal Gridlines & Y-Axis labels */}
                  {[0, 0.25, 0.5, 0.75, 1].map((pct) => {
                    const y = padding.top + chartHeight * pct;
                    const val = chartPaths.maxVal - pct * (chartPaths.maxVal - chartPaths.minVal);
                    let label = `${fmt(val, 0)}`;
                    if (chartMode === 'RETURN' || chartMode === 'DRAWDOWN') {
                      label = `${val >= 0 ? '+' : ''}${val.toFixed(1)}%`;
                    }
                    return (
                      <g key={pct}>
                        <line
                          x1={padding.left}
                          y1={y}
                          x2={padding.left + chartWidth}
                          y2={y}
                          stroke="#374151"
                          strokeDasharray="4 4"
                          strokeOpacity={0.25}
                        />
                        <text
                          x={padding.left - 8}
                          y={y + 4}
                          textAnchor="end"
                          fontSize="10"
                          fill="#9ca3af"
                          fontFamily="sans-serif"
                        >
                          {label}
                        </text>
                      </g>
                    );
                  })}

                  {/* Zero guideline for return & drawdown modes */}
                  {(chartMode === 'RETURN' || chartMode === 'DRAWDOWN') && (
                    <line
                      x1={padding.left}
                      y1={chartPaths.zeroY}
                      x2={padding.left + chartWidth}
                      y2={chartPaths.zeroY}
                      stroke="#9ca3af"
                      strokeWidth="1.5"
                      strokeDasharray="2 2"
                    />
                  )}

                  {/* Area Fill */}
                  <path
                    d={chartPaths.areaPrimary}
                    fill={
                      chartMode === 'VALUE'
                        ? 'url(#wealthGrad)'
                        : chartMode === 'RETURN'
                        ? 'url(#returnGrad)'
                        : 'url(#drawdownGrad)'
                    }
                  />

                  {/* Secondary Line (Invested Capital in VALUE mode; Benchmark in RETURN mode) */}
                  {chartPaths.pathSecondary && (
                    <path
                      d={chartPaths.pathSecondary}
                      fill="none"
                      stroke={chartMode === 'VALUE' ? '#f59e0b' : '#a855f7'}
                      strokeWidth="2"
                      strokeDasharray={chartMode === 'VALUE' ? '4 4' : undefined}
                    />
                  )}

                  {/* Primary Line (Market Value in VALUE mode; Portfolio Return in RETURN mode; Drawdown in DRAWDOWN mode) */}
                  <path
                    d={chartPaths.pathPrimary}
                    fill="none"
                    stroke={
                      chartMode === 'VALUE'
                        ? '#2563eb'
                        : chartMode === 'RETURN'
                        ? '#10b981'
                        : '#ef4444'
                    }
                    strokeWidth="2.5"
                  />

                  {/* Date labels on X-axis */}
                  {pointsWithDrawdown.map((d, i) => {
                    // Show at most ~5-6 date labels evenly distributed
                    const step = Math.max(1, Math.floor(pointsWithDrawdown.length / 5));
                    if (i % step !== 0 && i !== pointsWithDrawdown.length - 1) return null;
                    const x = padding.left + (i / Math.max(1, pointsWithDrawdown.length - 1)) * chartWidth;
                    const dateStr = d.timestamp.split('T')[0];
                    return (
                      <text
                        key={i}
                        x={x}
                        y={padding.top + chartHeight + 18}
                        textAnchor="middle"
                        fontSize="10"
                        fill="#9ca3af"
                        fontFamily="sans-serif"
                      >
                        {dateStr}
                      </text>
                    );
                  })}

                  {/* Vertical Crosshair Line when hovering */}
                  {hoverIndex !== null && (
                    <line
                      x1={padding.left + (hoverIndex / Math.max(1, pointsWithDrawdown.length - 1)) * chartWidth}
                      y1={padding.top}
                      x2={padding.left + (hoverIndex / Math.max(1, pointsWithDrawdown.length - 1)) * chartWidth}
                      y2={padding.top + chartHeight}
                      stroke="#6b7280"
                      strokeWidth="1.5"
                      strokeDasharray="3 3"
                    />
                  )}

                  {/* Interactive Invisible hover columns */}
                  {pointsWithDrawdown.map((_, i) => {
                    const colWidth = chartWidth / pointsWithDrawdown.length;
                    const x = padding.left + i * colWidth;
                    return (
                      <rect
                        key={i}
                        x={x}
                        y={padding.top}
                        width={colWidth}
                        height={chartHeight}
                        fill="transparent"
                        style={{ cursor: 'crosshair' }}
                        onMouseEnter={() => setHoverIndex(i)}
                      />
                    );
                  })}
                </svg>
              </Box>
            )}

            {/* Chart Legend */}
            <Stack direction="row" spacing={3} sx={{ justifyContent: 'center', alignItems: 'center', flexWrap: 'wrap' }}>
              {chartMode === 'VALUE' ? (
                <>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Box sx={{ width: 14, height: 4, bgcolor: '#2563eb', borderRadius: 1 }} />
                    <Typography variant="caption" sx={{ fontWeight: 600 }}>
                      Portfolio Market Value
                    </Typography>
                  </Stack>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Box sx={{ width: 14, height: 4, bgcolor: '#f59e0b', borderRadius: 1 }} />
                    <Typography variant="caption" sx={{ fontWeight: 600 }}>
                      Invested Capital Basis (Dashed)
                    </Typography>
                  </Stack>
                </>
              ) : chartMode === 'RETURN' ? (
                <>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Box sx={{ width: 14, height: 4, bgcolor: '#10b981', borderRadius: 1 }} />
                    <Typography variant="caption" sx={{ fontWeight: 600 }}>
                      Portfolio Cumulative Return (%)
                    </Typography>
                  </Stack>
                  {chartPaths.pathSecondary && (
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <Box sx={{ width: 14, height: 4, bgcolor: '#a855f7', borderRadius: 1 }} />
                      <Typography variant="caption" sx={{ fontWeight: 600 }}>
                        {selectedBenchmark?.name || 'Benchmark'} Return (%)
                      </Typography>
                    </Stack>
                  )}
                </>
              ) : (
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  <Box sx={{ width: 14, height: 4, bgcolor: '#ef4444', borderRadius: 1 }} />
                  <Typography variant="caption" sx={{ fontWeight: 600 }}>
                    Underwater Drawdown from Historical Peak (%)
                  </Typography>
                </Stack>
              )}
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {/* Benchmark Comparison & Alpha Details Section */}
      {selectedBenchmark && (
        <Card variant="outlined" sx={{ borderRadius: 2 }}>
          <CardContent>
            <Stack spacing={2.5}>
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                <CompareArrowsIcon color="primary" />
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Benchmark Comparison: {selectedBenchmark.name} ({selectedBenchmark.ticker || 'N/A'})
                </Typography>
              </Stack>

              {isComparisonLoading ? (
                <Skeleton variant="rectangular" height={100} sx={{ borderRadius: 1 }} />
              ) : comparison ? (
                <Stack spacing={2}>
                  {/* Status Banner */}
                  <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1.5 }}
                  >
                    <Chip
                      icon={comparison.outperforming ? <TrendingUpIcon /> : <TrendingDownIcon />}
                      label={`${comparison.outperforming ? 'Outperforming' : 'Underperforming'} Benchmark by ${comparison.excessReturn >= 0 ? '+' : ''}${(comparison.excessReturn * 100).toFixed(2)}% (Alpha)`}
                      color={comparison.outperforming ? 'success' : 'error'}
                      variant="filled"
                      sx={{ fontWeight: 700, fontSize: '0.85rem' }}
                    />
                    <Typography variant="caption" color="text.secondary">
                      Calculation Horizon: <strong>{period}</strong> | Currency: <strong>{currency}</strong>
                    </Typography>
                  </Stack>

                  {/* Comparative Metrics Grid */}
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Card variant="outlined" sx={{ p: 2, bgcolor: 'background.default', borderRadius: 2 }}>
                        <Typography variant="caption" color="text.secondary">
                          Portfolio Cumulative Return ({period})
                        </Typography>
                        <Typography variant="h5" sx={{ fontWeight: 700, color: 'primary.main', mt: 0.5 }}>
                          {comparison.portfolioReturn >= 0 ? '+' : ''}
                          {(comparison.portfolioReturn * 100).toFixed(2)}%
                        </Typography>
                        {comparison.annualizedPortfolioReturn != null && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                            Annualized: <strong>{comparison.annualizedPortfolioReturn >= 0 ? '+' : ''}{(comparison.annualizedPortfolioReturn * 100).toFixed(2)}%</strong>
                          </Typography>
                        )}
                      </Card>
                    </Grid>

                    <Grid size={{ xs: 12, sm: 6 }}>
                      <Card variant="outlined" sx={{ p: 2, bgcolor: 'background.default', borderRadius: 2 }}>
                        <Typography variant="caption" color="text.secondary">
                          {comparison.benchmarkName} ({comparison.benchmarkTicker})
                        </Typography>
                        <Typography variant="h5" sx={{ fontWeight: 700, color: 'text.primary', mt: 0.5 }}>
                          {comparison.benchmarkReturn >= 0 ? '+' : ''}
                          {(comparison.benchmarkReturn * 100).toFixed(2)}%
                        </Typography>
                        {comparison.annualizedBenchmarkReturn != null && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                            Annualized: <strong>{comparison.annualizedBenchmarkReturn >= 0 ? '+' : ''}{(comparison.annualizedBenchmarkReturn * 100).toFixed(2)}%</strong>
                          </Typography>
                        )}
                      </Card>
                    </Grid>
                  </Grid>

                  {comparison.warnings && comparison.warnings.length > 0 && (
                    <Box sx={{ p: 1.5, bgcolor: 'warning.light', color: 'warning.contrastText', borderRadius: 1, fontSize: '0.85rem' }}>
                      {comparison.warnings.map((w, idx) => (
                        <div key={idx}>{w}</div>
                      ))}
                    </Box>
                  )}
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Unable to calculate comparative analytics. Ensure price quotes and transactions exist for this period.
                </Typography>
              )}
            </Stack>
          </CardContent>
        </Card>
      )}

      {/* Historical Valuation Ledger Table */}
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Stack spacing={2}>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1.5 }}
            >
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Historical Valuation Ledger
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Tabular snapshot of recorded market values, cost bases, cash flows, and drawdown depth
                </Typography>
              </Box>

              <TextField
                size="small"
                placeholder="Filter by Date (YYYY-MM-DD)..."
                value={filterDate}
                onChange={(e) => {
                  setFilterDate(e.target.value);
                  setPage(0);
                }}
                sx={{ width: { xs: '100%', sm: 260 } }}
              />
            </Stack>

            <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1.5 }}>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: 'action.hover' }}>
                    <TableCell sx={{ fontWeight: 700 }}>Date</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Market Value</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Invested Capital</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Cash Value</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Unrealized Gain/Loss</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Portfolio Return</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Benchmark Return</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Drawdown</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {pagedPoints.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                        No records match the current filter.
                      </TableCell>
                    </TableRow>
                  ) : (
                    pagedPoints.map((pt, idx) => {
                      const isPtPositive = pt.portfolioReturnPercentage >= 0;
                      const isGainPositive = pt.unrealizedGainLoss >= 0;
                      return (
                        <TableRow key={idx} hover>
                          <TableCell sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                            {pt.timestamp.split('T')[0]}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 600 }}>
                            {currency} {fmt(pt.marketValue)}
                          </TableCell>
                          <TableCell align="right" color="text.secondary">
                            {currency} {fmt(pt.investedCapital)}
                          </TableCell>
                          <TableCell align="right" color="text.secondary">
                            {currency} {fmt(pt.cashValue)}
                          </TableCell>
                          <TableCell
                            align="right"
                            sx={{
                              fontWeight: 600,
                              color: isGainPositive ? 'success.main' : 'error.main',
                            }}
                          >
                            {isGainPositive ? '+' : ''}
                            {currency} {fmt(pt.unrealizedGainLoss)}
                          </TableCell>
                          <TableCell
                            align="right"
                            sx={{
                              fontWeight: 600,
                              color: isPtPositive ? 'success.main' : 'error.main',
                            }}
                          >
                            {isPtPositive ? '+' : ''}
                            {fmtPct(pt.portfolioReturnPercentage)}
                          </TableCell>
                          <TableCell align="right">
                            {pt.benchmarkReturnPercentage != null ? (
                              <span
                                style={{
                                  fontWeight: 600,
                                  color: pt.benchmarkReturnPercentage >= 0 ? '#10b981' : '#ef4444',
                                }}
                              >
                                {pt.benchmarkReturnPercentage >= 0 ? '+' : ''}
                                {fmtPct(pt.benchmarkReturnPercentage)}
                              </span>
                            ) : (
                              <Typography variant="caption" color="text.secondary">—</Typography>
                            )}
                          </TableCell>
                          <TableCell
                            align="right"
                            sx={{
                              fontWeight: 600,
                              color: pt.drawdownPercentage < 0 ? 'error.main' : 'text.secondary',
                            }}
                          >
                            {fmtPct(pt.drawdownPercentage)}
                          </TableCell>
                        </TableRow>
                      );
                    })
                  )}
                </TableBody>
              </Table>
            </TableContainer>

            <TablePagination
              component="div"
              count={filteredTablePoints.length}
              page={page}
              onPageChange={(_, newPage) => setPage(newPage)}
              rowsPerPage={rowsPerPage}
              onRowsPerPageChange={(e) => {
                setRowsPerPage(parseInt(e.target.value, 10));
                setPage(0);
              }}
              rowsPerPageOptions={[5, 10, 25, 50]}
            />
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}

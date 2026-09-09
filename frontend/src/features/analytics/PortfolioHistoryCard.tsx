import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import TrendingDownOutlinedIcon from '@mui/icons-material/TrendingDownOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import QueryStatsOutlinedIcon from '@mui/icons-material/QueryStatsOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { usePortfolioHistory } from './useAnalytics';
import { useBenchmarkList } from '../benchmark/useBenchmark';
import type { HistoricalValuationPoint } from '../../types';

interface PortfolioHistoryCardProps {
  portfolioId: string;
  currency: string;
}

type ChartMode = 'VALUE' | 'RETURN';
type TimePeriod = '1M' | '3M' | '6M' | 'YTD' | '1Y' | '3Y' | 'ALL';

export function PortfolioHistoryCard({ portfolioId, currency }: PortfolioHistoryCardProps) {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<TimePeriod>('1Y');
  const [mode, setMode] = useState<ChartMode>('VALUE');
  const [benchmarkId, setBenchmarkId] = useState<string>('');
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  // Fetch benchmark instruments list (only predefined benchmarks)
  const { data: benchmarkOptions = [] } = useBenchmarkList();

  const { data: history, isLoading, error } = usePortfolioHistory(portfolioId, {
    period,
    benchmarkId: benchmarkId || undefined,
  });

  const dataPoints = history?.dataPoints || [];
  const summary = history?.summary;

  // Chart coordinate calculations
  const svgWidth = 800;
  const svgHeight = 320;
  const padding = { top: 20, right: 30, bottom: 40, left: 65 };
  const chartWidth = svgWidth - padding.left - padding.right;
  const chartHeight = svgHeight - padding.top - padding.bottom;

  const { minVal, maxVal, pathValue, pathInvested, pathBenchmark, areaValue } = useMemo(() => {
    if (dataPoints.length === 0) {
      return { minVal: 0, maxVal: 100, pathValue: '', pathInvested: '', pathBenchmark: '', areaValue: '' };
    }

    if (mode === 'VALUE') {
      let min = Math.min(...dataPoints.map((d) => Math.min(d.marketValue, d.investedCapital)));
      let max = Math.max(...dataPoints.map((d) => Math.max(d.marketValue, d.investedCapital)));
      if (min === max) {
        min = Math.max(0, min * 0.9);
        max = max * 1.1 || 1000;
      } else {
        const span = max - min;
        min = Math.max(0, min - span * 0.05);
        max = max + span * 0.08;
      }

      const pointsValue = dataPoints.map((d, i) => {
        const x = padding.left + (i / Math.max(1, dataPoints.length - 1)) * chartWidth;
        const y = padding.top + chartHeight - ((d.marketValue - min) / (max - min)) * chartHeight;
        return { x, y };
      });

      const pointsInvested = dataPoints.map((d, i) => {
        const x = padding.left + (i / Math.max(1, dataPoints.length - 1)) * chartWidth;
        const y = padding.top + chartHeight - ((d.investedCapital - min) / (max - min)) * chartHeight;
        return { x, y };
      });

      const lineV = pointsValue.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
      const lineI = pointsInvested.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');

      const areaV = `${lineV} L ${pointsValue[pointsValue.length - 1].x.toFixed(1)} ${(padding.top + chartHeight).toFixed(1)} L ${pointsValue[0].x.toFixed(1)} ${(padding.top + chartHeight).toFixed(1)} Z`;

      return { minVal: min, maxVal: max, pathValue: lineV, pathInvested: lineI, pathBenchmark: '', areaValue: areaV };
    } else {
      // RETURN (%) Mode
      let allReturns = dataPoints.map((d) => d.portfolioReturnPercentage);
      dataPoints.forEach((d) => {
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

      const pointsValue = dataPoints.map((d, i) => {
        const x = padding.left + (i / Math.max(1, dataPoints.length - 1)) * chartWidth;
        const y = padding.top + chartHeight - ((d.portfolioReturnPercentage - min) / (max - min)) * chartHeight;
        return { x, y };
      });

      const lineV = pointsValue.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');

      let lineB = '';
      if (dataPoints.some((d) => d.benchmarkReturnPercentage != null)) {
        const pointsB = dataPoints
          .map((d, i) => {
            if (d.benchmarkReturnPercentage == null) return null;
            const x = padding.left + (i / Math.max(1, dataPoints.length - 1)) * chartWidth;
            const y = padding.top + chartHeight - ((d.benchmarkReturnPercentage - min) / (max - min)) * chartHeight;
            return { x, y };
          })
          .filter(Boolean) as { x: number; y: number }[];

        if (pointsB.length > 0) {
          lineB = pointsB.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
        }
      }

      const areaV = `${lineV} L ${pointsValue[pointsValue.length - 1].x.toFixed(1)} ${(padding.top + chartHeight).toFixed(1)} L ${pointsValue[0].x.toFixed(1)} ${(padding.top + chartHeight).toFixed(1)} Z`;

      return { minVal: min, maxVal: max, pathValue: lineV, pathInvested: '', pathBenchmark: lineB, areaValue: areaV };
    }
  }, [dataPoints, mode, chartWidth, chartHeight]);

  const activePoint: HistoricalValuationPoint | null =
    hoverIndex !== null && dataPoints[hoverIndex] ? dataPoints[hoverIndex] : null;

  if (isLoading) {
    return (
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Skeleton variant="text" width="30%" height={32} />
          <Skeleton variant="rectangular" height={260} sx={{ my: 2, borderRadius: 1 }} />
        </CardContent>
      </Card>
    );
  }

  const isReturnPositive = (summary?.portfolioReturnPercentage ?? 0) >= 0;
  const isExcessPositive = (summary?.excessReturnPercentage ?? 0) >= 0;

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }} data-testid="portfolio-history-card">
      <CardContent>
        <Stack spacing={3}>
          {/* Header & Controls */}
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2 }}
          >
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <ShowChartOutlinedIcon color="primary" sx={{ fontSize: 30 }} />
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Historical Valuation & Performance Charting
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Track wealth growth, net invested capital, historical drawdowns, and benchmark returns.
                </Typography>
              </Box>
            </Stack>

            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
              {/* Mode Toggle */}
              <Tabs
                value={mode === 'VALUE' ? 0 : 1}
                onChange={(_, val) => setMode(val === 0 ? 'VALUE' : 'RETURN')}
                sx={{
                  minHeight: 36,
                  bgcolor: 'action.hover',
                  borderRadius: 2,
                  p: 0.5,
                  '& .MuiTab-root': { minHeight: 32, py: 0.5, px: 2, fontSize: '0.825rem', borderRadius: 1.5, fontWeight: 600 },
                }}
              >
                <Tab label="Wealth Growth ($)" />
                <Tab label="Relative Return (%)" />
              </Tabs>

              {/* Benchmark Selector */}
              {benchmarkOptions.length > 0 && (
                <FormControl size="small" sx={{ minWidth: 160 }}>
                  <InputLabel id="benchmark-select-label">Benchmark</InputLabel>
                  <Select
                    labelId="benchmark-select-label"
                    value={benchmarkId}
                    label="Benchmark"
                    onChange={(e) => setBenchmarkId(e.target.value)}
                    sx={{ fontSize: '0.85rem' }}
                  >
                    <MenuItem value="">
                      <em>None (No Benchmark)</em>
                    </MenuItem>
                    {benchmarkOptions.map((inst) => (
                      <MenuItem key={inst.id} value={inst.id}>
                        {inst.ticker || inst.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              )}

              {/* Full History Page CTA */}
              <Button
                variant="outlined"
                size="small"
                endIcon={<ArrowForwardIcon fontSize="small" />}
                onClick={() => navigate(`/portfolios/${portfolioId}/history`)}
              >
                Full History & Benchmark
              </Button>
            </Stack>
          </Stack>

          {/* Time Horizon Selector Chips */}
          <Stack direction="row" spacing={1} sx={{ overflowX: 'auto', pb: 0.5, alignItems: 'center' }}>
            <Typography variant="caption" sx={{ fontWeight: 600, color: 'text.secondary', mr: 0.5 }}>
              HORIZON:
            </Typography>
            {(['1M', '3M', '6M', 'YTD', '1Y', '3Y', 'ALL'] as TimePeriod[]).map((p) => (
              <Chip
                key={p}
                label={p}
                clickable
                color={period === p ? 'primary' : 'default'}
                variant={period === p ? 'filled' : 'outlined'}
                size="small"
                onClick={() => setPeriod(p)}
                sx={{ fontWeight: 600, px: 1 }}
              />
            ))}
          </Stack>

          {/* Key Summary Stat Cards */}
          {summary && (
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    Ending Valuation
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                    {currency} {summary.endingValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </Typography>
                  <Typography variant="caption" color={summary.totalGainLoss >= 0 ? 'success.main' : 'error.main'} sx={{ fontWeight: 600 }}>
                    {summary.totalGainLoss >= 0 ? '+' : ''}{currency} {summary.totalGainLoss.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} gain
                  </Typography>
                </Paper>
              </Grid>

              <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    Portfolio Period Return
                  </Typography>
                  <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', mt: 0.5 }}>
                    {isReturnPositive ? (
                      <TrendingUpOutlinedIcon color="success" sx={{ fontSize: 22 }} />
                    ) : (
                      <TrendingDownOutlinedIcon color="error" sx={{ fontSize: 22 }} />
                    )}
                    <Typography variant="h6" sx={{ fontWeight: 700, color: isReturnPositive ? 'success.main' : 'error.main' }}>
                      {isReturnPositive ? '+' : ''}{summary.portfolioReturnPercentage.toFixed(2)}%
                    </Typography>
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    Period: {period} ({history?.interval.toLowerCase()})
                  </Typography>
                </Paper>
              </Grid>

              {summary.benchmarkReturnPercentage != null && (
                <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                      Benchmark Return ({history?.benchmarkTicker || 'Index'})
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                      {(summary.benchmarkReturnPercentage ?? 0) >= 0 ? '+' : ''}
                      {summary.benchmarkReturnPercentage?.toFixed(2)}%
                    </Typography>
                    <Typography
                      variant="caption"
                      color={isExcessPositive ? 'success.main' : 'error.main'}
                      sx={{ fontWeight: 600 }}
                    >
                      Alpha: {isExcessPositive ? '+' : ''}{summary.excessReturnPercentage?.toFixed(2)}%
                    </Typography>
                  </Paper>
                </Grid>
              )}

              <Grid size={{ xs: 12, sm: 6, md: summary.benchmarkReturnPercentage != null ? 3 : 6 }}>
                <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    Maximum Drawdown
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5, color: summary.maxDrawdownPercentage > 10 ? 'warning.main' : 'text.primary' }}>
                    -{summary.maxDrawdownPercentage.toFixed(2)}%
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Peak-to-trough risk metric
                  </Typography>
                </Paper>
              </Grid>
            </Grid>
          )}

          {/* Interactive SVG Chart Area */}
          {dataPoints.length === 0 ? (
            <Box sx={{ py: 6, textAlign: 'center', bgcolor: 'action.hover', borderRadius: 2 }}>
              <Typography variant="body2" color="text.secondary">
                No historical transaction data available for the selected horizon.
              </Typography>
            </Box>
          ) : (
            <Box sx={{ position: 'relative', width: '100%', overflowX: 'auto' }}>
              <svg
                viewBox={`0 0 ${svgWidth} ${svgHeight}`}
                style={{ width: '100%', height: 'auto', minHeight: 280, display: 'block' }}
                onMouseLeave={() => setHoverIndex(null)}
              >
                <defs>
                  <linearGradient id="chartAreaGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#1976d2" stopOpacity="0.35" />
                    <stop offset="100%" stopColor="#1976d2" stopOpacity="0.02" />
                  </linearGradient>
                </defs>

                {/* Horizontal Gridlines & Y-Axis Labels */}
                {[0, 0.25, 0.5, 0.75, 1].map((ratio, idx) => {
                  const y = padding.top + chartHeight * ratio;
                  const tickVal = maxVal - (maxVal - minVal) * ratio;
                  return (
                    <g key={idx}>
                      <line
                        x1={padding.left}
                        y1={y}
                        x2={svgWidth - padding.right}
                        y2={y}
                        stroke="rgba(150, 150, 150, 0.2)"
                        strokeDasharray="4 4"
                      />
                      <text
                        x={padding.left - 8}
                        y={y + 4}
                        textAnchor="end"
                        fontSize="11"
                        fill="gray"
                        fontFamily="sans-serif"
                      >
                        {mode === 'VALUE'
                          ? `${currency} ${tickVal >= 1000 ? (tickVal / 1000).toFixed(1) + 'k' : tickVal.toFixed(0)}`
                          : `${tickVal >= 0 ? '+' : ''}${tickVal.toFixed(1)}%`}
                      </text>
                    </g>
                  );
                })}

                {/* X-Axis Date Labels */}
                {dataPoints
                  .filter((_, i) => i === 0 || i === Math.floor(dataPoints.length / 2) || i === dataPoints.length - 1)
                  .map((d, idx) => {
                    const origIndex = idx === 0 ? 0 : idx === 1 ? Math.floor(dataPoints.length / 2) : dataPoints.length - 1;
                    const x = padding.left + (origIndex / Math.max(1, dataPoints.length - 1)) * chartWidth;
                    const dateStr = new Date(d.timestamp).toLocaleDateString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      year: period === '3Y' || period === 'ALL' ? '2-digit' : undefined,
                    });
                    return (
                      <text
                        key={idx}
                        x={x}
                        y={svgHeight - 12}
                        textAnchor={idx === 0 ? 'start' : idx === 2 ? 'end' : 'middle'}
                        fontSize="11"
                        fill="gray"
                        fontFamily="sans-serif"
                      >
                        {dateStr}
                      </text>
                    );
                  })}

                {/* Area under portfolio curve */}
                {areaValue && <path d={areaValue} fill="url(#chartAreaGradient)" />}

                {/* Invested Capital Line (in Value Mode) */}
                {mode === 'VALUE' && pathInvested && (
                  <path
                    d={pathInvested}
                    fill="none"
                    stroke="#78909c"
                    strokeWidth="2"
                    strokeDasharray="5 5"
                  />
                )}

                {/* Benchmark Line (in Return Mode) */}
                {mode === 'RETURN' && pathBenchmark && (
                  <path
                    d={pathBenchmark}
                    fill="none"
                    stroke="#ff9800"
                    strokeWidth="2"
                    strokeDasharray="4 4"
                  />
                )}

                {/* Portfolio Value/Return Main Curve */}
                {pathValue && (
                  <path
                    d={pathValue}
                    fill="none"
                    stroke="#1976d2"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                )}

                {/* Interactive Hover Columns */}
                {dataPoints.map((d, i) => {
                  const x = padding.left + (i / Math.max(1, dataPoints.length - 1)) * chartWidth;
                  const colWidth = chartWidth / dataPoints.length;
                  return (
                    <rect
                      key={i}
                      x={x - colWidth / 2}
                      y={padding.top}
                      width={colWidth}
                      height={chartHeight}
                      fill="transparent"
                      style={{ cursor: 'crosshair' }}
                      onMouseEnter={() => setHoverIndex(i)}
                    />
                  );
                })}

                {/* Crosshair & Active Point Marker */}
                {hoverIndex !== null && dataPoints[hoverIndex] && (
                  <g>
                    {(() => {
                      const d = dataPoints[hoverIndex];
                      const x = padding.left + (hoverIndex / Math.max(1, dataPoints.length - 1)) * chartWidth;
                      const yVal =
                        mode === 'VALUE'
                          ? padding.top + chartHeight - ((d.marketValue - minVal) / (maxVal - minVal)) * chartHeight
                          : padding.top + chartHeight - ((d.portfolioReturnPercentage - minVal) / (maxVal - minVal)) * chartHeight;
                      return (
                        <>
                          <line
                            x1={x}
                            y1={padding.top}
                            x2={x}
                            y2={padding.top + chartHeight}
                            stroke="#1976d2"
                            strokeWidth="1.5"
                            strokeDasharray="2 2"
                          />
                          <circle cx={x} cy={yVal} r="5" fill="#1976d2" stroke="#fff" strokeWidth="2" />
                        </>
                      );
                    })()}
                  </g>
                )}
              </svg>

              {/* Hover Floating Stat Tooltip */}
              {activePoint && (
                <Paper
                  elevation={4}
                  sx={{
                    position: 'absolute',
                    top: 10,
                    right: 20,
                    p: 1.5,
                    borderRadius: 2,
                    bgcolor: 'background.paper',
                    border: '1px solid',
                    borderColor: 'divider',
                    minWidth: 200,
                  }}
                >
                  <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary', display: 'block', mb: 0.5 }}>
                    {new Date(activePoint.timestamp).toLocaleDateString(undefined, {
                      weekday: 'short',
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </Typography>
                  <Stack spacing={0.5}>
                    <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                      <Typography variant="caption" color="text.secondary">
                        Market Value:
                      </Typography>
                      <Typography variant="caption" sx={{ fontWeight: 700 }}>
                        {currency} {activePoint.marketValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </Typography>
                    </Stack>
                    <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                      <Typography variant="caption" color="text.secondary">
                        Invested Capital:
                      </Typography>
                      <Typography variant="caption" sx={{ fontWeight: 600 }}>
                        {currency} {activePoint.investedCapital.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </Typography>
                    </Stack>
                    <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                      <Typography variant="caption" color="text.secondary">
                        Portfolio Return:
                      </Typography>
                      <Typography
                        variant="caption"
                        color={activePoint.portfolioReturnPercentage >= 0 ? 'success.main' : 'error.main'}
                        sx={{ fontWeight: 700 }}
                      >
                        {activePoint.portfolioReturnPercentage >= 0 ? '+' : ''}
                        {activePoint.portfolioReturnPercentage.toFixed(2)}%
                      </Typography>
                    </Stack>
                    {activePoint.benchmarkReturnPercentage != null && (
                      <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                        <Typography variant="caption" color="warning.main">
                          Benchmark Return:
                        </Typography>
                        <Typography variant="caption" color="warning.main" sx={{ fontWeight: 700 }}>
                          {(activePoint.benchmarkReturnPercentage ?? 0) >= 0 ? '+' : ''}
                          {activePoint.benchmarkReturnPercentage?.toFixed(2)}%
                        </Typography>
                      </Stack>
                    )}
                  </Stack>
                </Paper>
              )}
            </Box>
          )}

          {/* Chart Legend */}
          <Stack direction="row" spacing={3} sx={{ justifyContent: 'center', alignItems: 'center', pt: 1 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Box sx={{ width: 16, height: 3, bgcolor: '#1976d2', borderRadius: 1 }} />
              <Typography variant="caption" sx={{ fontWeight: 600 }}>
                {mode === 'VALUE' ? 'Portfolio Value' : 'Portfolio Return (%)'}
              </Typography>
            </Stack>

            {mode === 'VALUE' && (
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 16, height: 2, borderBottom: '2px dashed #78909c' }} />
                <Typography variant="caption" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                  Net Invested Capital
                </Typography>
              </Stack>
            )}

            {mode === 'RETURN' && summary?.benchmarkReturnPercentage != null && (
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Box sx={{ width: 16, height: 2, borderBottom: '2px dashed #ff9800' }} />
                <Typography variant="caption" sx={{ fontWeight: 600, color: '#ff9800' }}>
                  Benchmark ({history?.benchmarkTicker || 'Index'})
                </Typography>
              </Stack>
            )}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

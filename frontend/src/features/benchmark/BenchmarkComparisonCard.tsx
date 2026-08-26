import React, { useState, useEffect } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Stack,
  Box,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  ToggleButtonGroup,
  ToggleButton,
  Chip,
  Alert,
  Skeleton,
  Grid,
  Divider,
} from '@mui/material';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import { useBenchmarkList, useBenchmarkComparison } from './useBenchmark';
import type { BenchmarkPeriod } from '../../types';

interface BenchmarkComparisonCardProps {
  portfolioId: string;
  currency: string;
}

export function BenchmarkComparisonCard({ portfolioId, currency }: BenchmarkComparisonCardProps) {
  const { data: benchmarks = [], isLoading: isBenchmarksLoading } = useBenchmarkList();
  const [selectedBenchmarkId, setSelectedBenchmarkId] = useState<string>('');
  const [period, setPeriod] = useState<BenchmarkPeriod>('1Y');

  // Auto-select first benchmark when available
  useEffect(() => {
    if (benchmarks.length > 0 && !selectedBenchmarkId) {
      // Prioritize ETF or first available
      const etf = benchmarks.find((b) => b.assetClass === 'ETF');
      setSelectedBenchmarkId(etf ? etf.id : benchmarks[0].id);
    }
  }, [benchmarks, selectedBenchmarkId]);

  const {
    data: comparison,
    isLoading: isComparisonLoading,
    error,
  } = useBenchmarkComparison(portfolioId, selectedBenchmarkId, period);

  if (isBenchmarksLoading) {
    return (
      <Card variant="outlined">
        <CardContent>
          <Skeleton variant="text" width="30%" height={32} />
          <Skeleton variant="rectangular" height={100} sx={{ my: 1, borderRadius: 1 }} />
        </CardContent>
      </Card>
    );
  }

  if (benchmarks.length === 0) {
    return null;
  }

  const pReturnPct = comparison ? (Number(comparison.portfolioReturn) * 100).toFixed(2) : '0.00';
  const bmReturnPct = comparison ? (Number(comparison.benchmarkReturn) * 100).toFixed(2) : '0.00';
  const excessPct = comparison ? (Number(comparison.excessReturn) * 100).toFixed(2) : '0.00';
  const isPositiveExcess = comparison ? Number(comparison.excessReturn) >= 0 : true;

  const pAnnPct = comparison?.annualizedPortfolioReturn != null
    ? (Number(comparison.annualizedPortfolioReturn) * 100).toFixed(2)
    : null;
  const bmAnnPct = comparison?.annualizedBenchmarkReturn != null
    ? (Number(comparison.annualizedBenchmarkReturn) * 100).toFixed(2)
    : null;

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }}>
      <CardContent>
        <Stack spacing={2.5}>
          {/* Header & Selectors */}
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2 }}
          >
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <CompareArrowsIcon color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 600, fontSize: '1.1rem' }}>
                Benchmark Comparison & Alpha
              </Typography>
            </Stack>

            <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <FormControl size="small" sx={{ minWidth: 200 }}>
                <InputLabel id="benchmark-select-label">Benchmark Index / ETF</InputLabel>
                <Select
                  labelId="benchmark-select-label"
                  id="benchmark-select"
                  value={selectedBenchmarkId}
                  label="Benchmark Index / ETF"
                  onChange={(e) => setSelectedBenchmarkId(e.target.value)}
                >
                  {benchmarks.map((b) => (
                    <MenuItem key={b.id} value={b.id}>
                      {b.name} {b.ticker ? `(${b.ticker})` : ''}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <ToggleButtonGroup
                value={period}
                exclusive
                size="small"
                onChange={(_, val) => val && setPeriod(val)}
                aria-label="benchmark period"
              >
                <ToggleButton value="1M">1M</ToggleButton>
                <ToggleButton value="3M">3M</ToggleButton>
                <ToggleButton value="6M">6M</ToggleButton>
                <ToggleButton value="1Y">1Y</ToggleButton>
                <ToggleButton value="YTD">YTD</ToggleButton>
                <ToggleButton value="ALL">ALL</ToggleButton>
              </ToggleButtonGroup>
            </Stack>
          </Stack>

          {isComparisonLoading ? (
            <Skeleton variant="rectangular" height={100} sx={{ borderRadius: 1 }} />
          ) : error || !comparison ? (
            <Alert severity="info">Select a benchmark to view comparative return analytics.</Alert>
          ) : (
            <>
              {/* Status Banner */}
              <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <Chip
                  icon={isPositiveExcess ? <TrendingUpIcon /> : <TrendingDownIcon />}
                  label={`${comparison.outperforming ? 'Outperforming' : 'Underperforming'} Benchmark by ${isPositiveExcess ? '+' : ''}${excessPct}% (Alpha)`}
                  color={comparison.outperforming ? 'success' : 'error'}
                  variant="filled"
                  sx={{ fontWeight: 700, fontSize: '0.85rem' }}
                />
                <Typography variant="caption" color="text.secondary">
                  Base Currency: <strong>{currency}</strong>
                </Typography>
              </Stack>

              {/* Side by Side Comparison Grid */}
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Card variant="outlined" sx={{ p: 2, bgcolor: 'background.default', borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary">
                      Portfolio Cumulative Return ({period})
                    </Typography>
                    <Typography variant="h5" sx={{ fontWeight: 700, color: 'primary.main', mt: 0.5 }}>
                      {`${Number(pReturnPct) >= 0 ? '+' : ''}${pReturnPct}%`}
                    </Typography>
                    {pAnnPct && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                        Annualized: <strong>{`${Number(pAnnPct) >= 0 ? '+' : ''}${pAnnPct}%`}</strong>
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
                      {`${Number(bmReturnPct) >= 0 ? '+' : ''}${bmReturnPct}%`}
                    </Typography>
                    {bmAnnPct && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                        Annualized: <strong>{`${Number(bmAnnPct) >= 0 ? '+' : ''}${bmAnnPct}%`}</strong>
                      </Typography>
                    )}
                  </Card>
                </Grid>
              </Grid>

              {comparison.warnings.length > 0 && (
                <Alert severity="warning" sx={{ fontSize: '0.85rem' }}>
                  {comparison.warnings.map((w, idx) => (
                    <div key={idx}>{w}</div>
                  ))}
                </Alert>
              )}
            </>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

import React, { useMemo, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  LinearProgress,
  Link,
  Paper,
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
  Typography,
} from '@mui/material';

import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PieChartOutlinedIcon from '@mui/icons-material/PieChartOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import PublicOutlinedIcon from '@mui/icons-material/PublicOutlined';
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';

import { usePortfolio } from '../portfolios/usePortfolios';
import { usePortfolioAnalytics } from './useAnalytics';
import { useTargetAllocation } from '../rebalancing/useRebalancing';
import { DonutChart, DONUT_PALETTE, DonutSliceItem } from './DonutChart';
import { ErrorAlert } from '../../components';
import type { AllocationItem, HoldingExposure } from '../../types';

type Dimension = 'ASSET_CLASS' | 'CURRENCY' | 'ACCOUNT' | 'HOLDINGS';

const fmt = (v: number | undefined | null, dp = 2) =>
  Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });

const fmtPct = (v: number | undefined | null) => `${(Number(v ?? 0) * 100).toFixed(1)}%`;

export function AllocationDetailPage() {
  const { id: portfolioId } = useParams<{ id: string }>();
  const [dimension, setDimension] = useState<Dimension>('ASSET_CLASS');
  const [hoveredSliceId, setHoveredSliceId] = useState<string | null>(null);
  const [searchFilter, setSearchFilter] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  const {
    data: portfolio,
    isLoading: isPortfolioLoading,
    error: portfolioError,
  } = usePortfolio(portfolioId || '');

  const {
    data: analytics,
    isLoading: isAnalyticsLoading,
    error: analyticsError,
    refetch: refetchAnalytics,
  } = usePortfolioAnalytics(portfolioId);

  const { data: targetPlan } = useTargetAllocation(portfolioId || '');

  const currency = portfolio?.baseCurrency || analytics?.baseCurrency || 'GBP';

  // Target weights lookup map
  const targetMap = useMemo(() => {
    const map = new Map<string, number>();
    if (!targetPlan || !targetPlan.items) return map;
    targetPlan.items.forEach((item) => {
      const key = targetPlan.allocationType === 'INSTRUMENT'
        ? (item.instrumentTicker || item.categoryKey)
        : item.categoryKey;
      map.set(key.toUpperCase(), item.targetPercentage);
    });
    return map;
  }, [targetPlan]);

  // Dimension items calculation
  const rawItems: {
    id: string;
    label: string;
    secondaryLabel?: string;
    marketValue: number;
    weightPercentage: number;
    costBasis?: number;
    unrealizedGainLoss?: number;
    targetWeight?: number | null;
  }[] = useMemo(() => {
    if (!analytics) return [];

    if (dimension === 'ASSET_CLASS') {
      return analytics.byAssetClass.map((it) => {
        const target = targetMap.get(it.category.toUpperCase()) ?? null;
        return {
          id: it.category,
          label: it.category,
          marketValue: Number(it.marketValue),
          weightPercentage: Number(it.percentage),
          costBasis: Number(it.costBasis),
          unrealizedGainLoss: Number(it.unrealizedGainLoss),
          targetWeight: target,
        };
      });
    }

    if (dimension === 'CURRENCY') {
      return analytics.byCurrency.map((it) => ({
        id: it.category,
        label: it.category,
        marketValue: Number(it.marketValue),
        weightPercentage: Number(it.percentage),
        costBasis: Number(it.costBasis),
        unrealizedGainLoss: Number(it.unrealizedGainLoss),
        targetWeight: null,
      }));
    }

    if (dimension === 'ACCOUNT') {
      return analytics.byAccount.map((it) => ({
        id: it.category,
        label: it.category,
        marketValue: Number(it.marketValue),
        weightPercentage: Number(it.percentage),
        costBasis: Number(it.costBasis),
        unrealizedGainLoss: Number(it.unrealizedGainLoss),
        targetWeight: null,
      }));
    }

    // HOLDINGS
    return analytics.topHoldings.map((h) => {
      const target = targetMap.get(h.ticker.toUpperCase()) ?? null;
      return {
        id: h.instrumentId,
        label: h.ticker,
        secondaryLabel: h.instrumentName,
        marketValue: Number(h.marketValue),
        weightPercentage: Number(h.weightPercentage),
        costBasis: Number(h.costBasis),
        unrealizedGainLoss: Number(h.unrealizedGainLoss),
        targetWeight: target,
      };
    });
  }, [analytics, dimension, targetMap]);

  // Donut slices
  const donutSlices: DonutSliceItem[] = useMemo(() => {
    return rawItems.map((item, idx) => ({
      id: item.id,
      label: item.label,
      value: item.marketValue,
      percentage: item.weightPercentage,
      color: DONUT_PALETTE[idx % DONUT_PALETTE.length],
    }));
  }, [rawItems]);

  // Hero KPI figures
  const totalValue = analytics?.totalCurrentValue ?? 0;
  const assetClassCount = analytics?.byAssetClass?.length ?? 0;
  const top1Holding = analytics?.topHoldings?.[0];
  const top1Weight = top1Holding ? (Number(top1Holding.weightPercentage) * 100).toFixed(1) : '0.0';

  const top3Concentration = useMemo(() => {
    if (!analytics?.topHoldings || analytics.topHoldings.length === 0) return 0;
    const top3 = analytics.topHoldings.slice(0, 3);
    const sum = top3.reduce((acc, h) => acc + (Number(h.weightPercentage) || 0), 0);
    return Number((sum * 100).toFixed(1));
  }, [analytics]);

  const cashWeight = useMemo(() => {
    if (!analytics || totalValue === 0) return 0;
    return Number(((analytics.totalCashValue / totalValue) * 100).toFixed(1));
  }, [analytics, totalValue]);

  // Concentration risk level
  const concentrationRisk = useMemo(() => {
    if (top3Concentration > 65 || Number(top1Weight) > 30) return { level: 'HIGH', color: 'error' as const };
    if (top3Concentration > 45 || Number(top1Weight) > 20) return { level: 'MODERATE', color: 'warning' as const };
    return { level: 'LOW', color: 'success' as const };
  }, [top3Concentration, top1Weight]);

  // Filtered rows for the table
  const filteredRows = useMemo(() => {
    if (!searchFilter.trim()) return rawItems;
    const q = searchFilter.toLowerCase();
    return rawItems.filter(
      (r) =>
        r.label.toLowerCase().includes(q) ||
        (r.secondaryLabel && r.secondaryLabel.toLowerCase().includes(q))
    );
  }, [rawItems, searchFilter]);

  const pagedRows = useMemo(() => {
    const start = page * rowsPerPage;
    return filteredRows.slice(start, start + rowsPerPage);
  }, [filteredRows, page, rowsPerPage]);

  // Export CSV
  const handleExportCsv = () => {
    if (rawItems.length === 0) return;
    const headers = [
      'Category / Item',
      'Secondary Label',
      'Market Value',
      'Portfolio Weight %',
      'Target Weight %',
      'Drift %',
      'Cost Basis',
      'Unrealized P&L',
    ];
    const rows = rawItems.map((r) => {
      const currentPct = (r.weightPercentage * 100).toFixed(2);
      const targetPct = r.targetWeight != null ? (r.targetWeight * 100).toFixed(2) : '';
      const drift = r.targetWeight != null ? ((r.weightPercentage - r.targetWeight) * 100).toFixed(2) : '';
      return [
        `"${r.label}"`,
        `"${r.secondaryLabel || ''}"`,
        r.marketValue.toFixed(2),
        currentPct,
        targetPct,
        drift,
        r.costBasis != null ? r.costBasis.toFixed(2) : '',
        r.unrealizedGainLoss != null ? r.unrealizedGainLoss.toFixed(2) : '',
      ];
    });

    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `portfolio-allocation-${portfolioId}-${dimension.toLowerCase()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  if (isPortfolioLoading || (isAnalyticsLoading && !analytics)) {
    return (
      <Stack spacing={3}>
        <Skeleton variant="rectangular" height={50} sx={{ borderRadius: 1 }} />
        <Skeleton variant="rectangular" height={130} sx={{ borderRadius: 2 }} />
        <Skeleton variant="rectangular" height={360} sx={{ borderRadius: 2 }} />
      </Stack>
    );
  }

  if (portfolioError || analyticsError) {
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
          error={portfolioError || analyticsError}
          title="Failed to load asset allocation analytics"
        />
      </Stack>
    );
  }

  return (
    <Stack spacing={3} data-testid="allocation-detail-page">
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
            Asset Allocation
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

      {/* Hero Header & Dimension Selector Bar */}
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2 }}
            >
              <Box>
                <Typography variant="h5" sx={{ fontWeight: 700 }}>
                  Asset Allocation & Exposure Analytics
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Multi-dimensional distribution analysis across asset classes, native currencies, account custody, and holdings concentration
                </Typography>
              </Box>

              <Button
                variant="outlined"
                size="small"
                startIcon={<FileDownloadOutlinedIcon />}
                onClick={handleExportCsv}
                disabled={rawItems.length === 0}
              >
                Export CSV
              </Button>
            </Stack>

            <Divider />

            {/* Dimension Toggle Chips */}
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1.5 }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                BREAKDOWN DIMENSION:
              </Typography>
              <ToggleButtonGroup
                value={dimension}
                exclusive
                size="small"
                onChange={(_, val) => {
                  if (val) {
                    setDimension(val);
                    setPage(0);
                  }
                }}
                aria-label="allocation dimension"
              >
                <ToggleButton value="ASSET_CLASS" sx={{ px: 2, fontWeight: 600, fontSize: '0.8rem' }}>
                  Asset Class
                </ToggleButton>
                <ToggleButton value="CURRENCY" sx={{ px: 2, fontWeight: 600, fontSize: '0.8rem' }}>
                  Currency Exposure
                </ToggleButton>
                <ToggleButton value="ACCOUNT" sx={{ px: 2, fontWeight: 600, fontSize: '0.8rem' }}>
                  Account Distribution
                </ToggleButton>
                <ToggleButton value="HOLDINGS" sx={{ px: 2, fontWeight: 600, fontSize: '0.8rem' }}>
                  Holdings Concentration
                </ToggleButton>
              </ToggleButtonGroup>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {/* Hero KPI Summary Strip */}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <AccountBalanceWalletOutlinedIcon color="primary" fontSize="small" />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                Total Portfolio Value
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              {currency} {fmt(totalValue)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Cash: {currency} {fmt(analytics?.totalCashValue)} ({cashWeight}%)
            </Typography>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <PieChartOutlinedIcon color="primary" fontSize="small" />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                Asset Classes Count
              </Typography>
            </Stack>
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              {assetClassCount} Classes
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Total Holdings: {analytics?.topHoldings?.length ?? 0} positions
            </Typography>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <SecurityOutlinedIcon color={concentrationRisk.color} fontSize="small" />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                Top 3 Concentration
              </Typography>
            </Stack>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 0.5 }}>
              <Typography variant="h5" sx={{ fontWeight: 700 }}>
                {top3Concentration}%
              </Typography>
              <Chip
                size="small"
                label={`${concentrationRisk.level} RISK`}
                color={concentrationRisk.color}
                sx={{ fontWeight: 700, fontSize: '0.75rem' }}
              />
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
              Top 1: {top1Holding?.ticker || 'N/A'} ({top1Weight}%)
            </Typography>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <PublicOutlinedIcon color="primary" fontSize="small" />
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                Target Allocation Plan
              </Typography>
            </Stack>
            {targetPlan ? (
              <>
                <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }} noWrap>
                  {targetPlan.name}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Drift Tolerance: ±{targetPlan.driftTolerancePercentage}%
                </Typography>
              </>
            ) : (
              <>
                <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5, fontWeight: 600 }}>
                  None Configured
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Set target weights in Rebalancing
                </Typography>
              </>
            )}
          </Card>
        </Grid>
      </Grid>

      {/* Concentration / Diversification Warning Alerts */}
      {concentrationRisk.level === 'HIGH' && (
        <Alert severity="warning" icon={<WarningAmberOutlinedIcon />} sx={{ borderRadius: 2 }}>
          <strong>High Portfolio Concentration Detected:</strong> The top 3 holdings account for{' '}
          <strong>{top3Concentration}%</strong> of total portfolio equity. Consider rebalancing across uncorrelated asset classes to mitigate single-asset drawdowns.
        </Alert>
      )}

      {/* Main Interactive Visual Analytics Card */}
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Interactive Donut Chart & Allocation Weights
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Hover over slices or categories to inspect granular capital allocation and drift status
              </Typography>
            </Box>

            <Grid container spacing={4} sx={{ alignItems: 'center' }}>
              {/* Left Column: Large SVG Donut Chart */}
              <Grid
                size={{ xs: 12, md: 5 }}
                sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', py: 2 }}
              >
                <DonutChart
                  items={donutSlices}
                  size={260}
                  centerTitle={`${fmt(totalValue, 0)} ${currency}`}
                  centerSubtitle="Portfolio Value"
                  currency={currency}
                  hoveredId={hoveredSliceId}
                  onHover={(slice) => setHoveredSliceId(slice ? slice.id : null)}
                />
              </Grid>

              {/* Right Column: Allocation Distribution Bars with Target Comparison */}
              <Grid size={{ xs: 12, md: 7 }}>
                <Stack spacing={2.2}>
                  {rawItems.slice(0, 8).map((item, idx) => {
                    const currentPct = item.weightPercentage * 100;
                    const sliceColor = DONUT_PALETTE[idx % DONUT_PALETTE.length];
                    const isHovered = hoveredSliceId === item.id;
                    const targetPct = item.targetWeight != null ? item.targetWeight * 100 : null;
                    const drift = targetPct != null ? currentPct - targetPct : null;

                    return (
                      <Box
                        key={item.id}
                        onMouseEnter={() => setHoveredSliceId(item.id)}
                        onMouseLeave={() => setHoveredSliceId(null)}
                        sx={{
                          p: 1,
                          borderRadius: 1.5,
                          bgcolor: isHovered ? 'action.hover' : 'transparent',
                          transition: 'background-color 0.15s ease',
                          cursor: 'pointer',
                        }}
                      >
                        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
                          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                            <Box
                              sx={{
                                width: 12,
                                height: 12,
                                borderRadius: '50%',
                                bgcolor: sliceColor,
                                flexShrink: 0,
                              }}
                            />
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {item.label}
                            </Typography>
                            {item.secondaryLabel && (
                              <Typography variant="caption" color="text.secondary" noWrap sx={{ maxWidth: 180 }}>
                                ({item.secondaryLabel})
                              </Typography>
                            )}
                          </Stack>

                          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {fmtPct(item.weightPercentage)}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              ({currency} {fmt(item.marketValue)})
                            </Typography>
                            {drift != null && (
                              <Chip
                                size="small"
                                label={`${drift >= 0 ? '+' : ''}${drift.toFixed(1)}%`}
                                color={Math.abs(drift) > (targetPlan?.driftTolerancePercentage ?? 5) ? 'warning' : 'default'}
                                sx={{ height: 20, fontSize: '0.7rem', fontWeight: 600 }}
                              />
                            )}
                          </Stack>
                        </Stack>

                        <LinearProgress
                          variant="determinate"
                          value={Math.min(100, currentPct)}
                          sx={{
                            height: 8,
                            borderRadius: 4,
                            bgcolor: 'action.selected',
                            '& .MuiLinearProgress-bar': {
                              bgcolor: sliceColor,
                              borderRadius: 4,
                            },
                          }}
                        />
                      </Box>
                    );
                  })}
                </Stack>
              </Grid>
            </Grid>
          </Stack>
        </CardContent>
      </Card>

      {/* Detailed Allocation & Exposure Ledger Table */}
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Stack spacing={2}>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1.5 }}
            >
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Granular Allocation & Exposure Ledger
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Complete breakdown of capital allocation, cost bases, target weights, and active deviations
                </Typography>
              </Box>

              <TextField
                size="small"
                placeholder="Filter categories or holdings..."
                value={searchFilter}
                onChange={(e) => {
                  setSearchFilter(e.target.value);
                  setPage(0);
                }}
                sx={{ width: { xs: '100%', sm: 260 } }}
              />
            </Stack>

            <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1.5 }}>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: 'action.hover' }}>
                    <TableCell sx={{ fontWeight: 700 }}>Category / Asset</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Market Value</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Portfolio Weight</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Target Weight</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Active Drift</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Cost Basis</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Unrealized Gain/Loss</TableCell>
                    <TableCell align="center" sx={{ fontWeight: 700 }}>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {pagedRows.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                        No records match the current filter.
                      </TableCell>
                    </TableRow>
                  ) : (
                    pagedRows.map((r, idx) => {
                      const currentPct = r.weightPercentage * 100;
                      const targetPct = r.targetWeight != null ? r.targetWeight * 100 : null;
                      const drift = targetPct != null ? currentPct - targetPct : null;
                      const isUnrealizedPositive = (r.unrealizedGainLoss ?? 0) >= 0;

                      return (
                        <TableRow key={idx} hover>
                          <TableCell sx={{ fontWeight: 600 }}>
                            {r.label}
                            {r.secondaryLabel && (
                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                                {r.secondaryLabel}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 600 }}>
                            {currency} {fmt(r.marketValue)}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>
                            {fmtPct(r.weightPercentage)}
                          </TableCell>
                          <TableCell align="right" color="text.secondary">
                            {targetPct != null ? `${targetPct.toFixed(1)}%` : '—'}
                          </TableCell>
                          <TableCell
                            align="right"
                            sx={{
                              fontWeight: 600,
                              color: drift == null
                                ? 'text.secondary'
                                : Math.abs(drift) > (targetPlan?.driftTolerancePercentage ?? 5)
                                ? 'warning.main'
                                : 'text.primary',
                            }}
                          >
                            {drift != null ? `${drift >= 0 ? '+' : ''}${drift.toFixed(1)}%` : '—'}
                          </TableCell>
                          <TableCell align="right" color="text.secondary">
                            {r.costBasis != null ? `${currency} ${fmt(r.costBasis)}` : '—'}
                          </TableCell>
                          <TableCell
                            align="right"
                            sx={{
                              fontWeight: 600,
                              color: isUnrealizedPositive ? 'success.main' : 'error.main',
                            }}
                          >
                            {r.unrealizedGainLoss != null ? (
                              `${isUnrealizedPositive ? '+' : ''}${currency} ${fmt(r.unrealizedGainLoss)}`
                            ) : (
                              '—'
                            )}
                          </TableCell>
                          <TableCell align="center">
                            {drift == null ? (
                              <Chip size="small" label="Unmanaged" variant="outlined" sx={{ height: 20, fontSize: '0.7rem' }} />
                            ) : Math.abs(drift) <= (targetPlan?.driftTolerancePercentage ?? 5) ? (
                              <Chip
                                size="small"
                                icon={<CheckCircleOutlineOutlinedIcon sx={{ fontSize: '0.8rem !important' }} />}
                                label="Optimal"
                                color="success"
                                variant="outlined"
                                sx={{ height: 20, fontSize: '0.7rem' }}
                              />
                            ) : drift > 0 ? (
                              <Chip
                                size="small"
                                label="Overweight"
                                color="warning"
                                variant="filled"
                                sx={{ height: 20, fontSize: '0.7rem', fontWeight: 600 }}
                              />
                            ) : (
                              <Chip
                                size="small"
                                label="Underweight"
                                color="info"
                                variant="filled"
                                sx={{ height: 20, fontSize: '0.7rem', fontWeight: 600 }}
                              />
                            )}
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
              count={filteredRows.length}
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

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
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';

import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined';
import BalanceOutlinedIcon from '@mui/icons-material/BalanceOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';

import { usePortfolio } from '../portfolios/usePortfolios';
import { useTargetAllocation, useRebalancingAnalysis, useDeleteTargetAllocation } from './useRebalancing';
import { TargetAllocationModal } from './TargetAllocationModal';
import { ConfirmDialog, ErrorAlert } from '../../components';
import type { RebalanceAction, RebalanceOrderItem } from '../../types';

const fmt = (v: number | undefined | null, dp = 2) =>
  Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });

const fmtPct = (v: number | undefined | null) => `${Number(v ?? 0).toFixed(1)}%`;

export function RebalancingDetailPage() {
  const { id: portfolioId } = useParams<{ id: string }>();
  const [modalOpen, setModalOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [rebalanceMode, setRebalanceMode] = useState<'FULL' | 'CASH_INJECTION'>('FULL');
  const [cashInjectionInput, setCashInjectionInput] = useState<string>('');
  const [copiedSnackbar, setCopiedSnackbar] = useState(false);

  const {
    data: portfolio,
    isLoading: isPortfolioLoading,
    error: portfolioError,
  } = usePortfolio(portfolioId || '');

  const {
    data: targetPlan,
    isLoading: isPlanLoading,
    error: planError,
  } = useTargetAllocation(portfolioId || '');

  const cashInjectionAmount =
    rebalanceMode === 'CASH_INJECTION' ? parseFloat(cashInjectionInput) || 0 : undefined;

  const {
    data: analysis,
    isLoading: isAnalysisLoading,
    error: analysisError,
    refetch: refetchAnalysis,
  } = useRebalancingAnalysis(portfolioId || '', cashInjectionAmount);

  const deleteMutation = useDeleteTargetAllocation(portfolioId || '');

  const currency = portfolio?.baseCurrency || analysis?.baseCurrency || 'GBP';

  // Orders breakdown
  const orders = useMemo(() => {
    if (!analysis) return [];
    return analysis.items.filter((i) => i.action !== 'HOLD');
  }, [analysis]);

  const buyOrders = useMemo(() => orders.filter((i) => i.action === 'BUY'), [orders]);
  const sellOrders = useMemo(() => orders.filter((i) => i.action === 'SELL'), [orders]);

  const totalTurnover = useMemo(() => {
    return orders.reduce((sum, ord) => sum + Number(ord.orderAmount), 0);
  }, [orders]);

  const maxDrift = useMemo(() => {
    if (!analysis || analysis.items.length === 0) return 0;
    const drifts = analysis.items.map((i) => Math.abs(i.driftPercentage));
    return Math.max(...drifts);
  }, [analysis]);

  const handleQuickCash = (amount: number) => {
    const current = parseFloat(cashInjectionInput) || 0;
    setCashInjectionInput((current + amount).toString());
  };

  const handleCopyPlan = () => {
    if (!analysis) return;
    const lines = [
      `Strategic Rebalancing Plan: ${analysis.portfolioName}`,
      `Date: ${new Date(analysis.asOf).toLocaleDateString()}`,
      `Mode: ${rebalanceMode === 'CASH_INJECTION' ? 'Cash Injection (Buy-Only)' : 'Full Rebalance (Buy & Sell)'}`,
      `Portfolio Value: ${fmt(analysis.totalPortfolioValue)} ${currency}`,
    ];

    if (analysis.cashInjectionAmount > 0) {
      lines.push(`Cash Injected: ${fmt(analysis.cashInjectionAmount)} ${currency}`);
      lines.push(`Post-Rebalance Total: ${fmt(analysis.totalPostRebalanceValue)} ${currency}`);
    }

    lines.push('\nActionable Trade Orders:');
    for (const item of analysis.items) {
      if (item.action === 'HOLD') continue;
      const qtyStr = item.estimatedQuantity ? ` (~${item.estimatedQuantity} shares)` : '';
      lines.push(
        `- ${item.action} ${item.categoryLabel}: ${fmt(item.orderAmount)} ${currency}${qtyStr} (Drift: ${item.driftPercentage >= 0 ? '+' : ''}${item.driftPercentage.toFixed(1)}%)`
      );
    }

    navigator.clipboard.writeText(lines.join('\n'));
    setCopiedSnackbar(true);
  };

  const handleExportOrdersCsv = () => {
    if (!analysis || orders.length === 0) return;
    const headers = [
      'Action',
      'Category / Asset',
      'Order Amount',
      'Currency',
      'Est Price',
      'Est Quantity',
      'Current Weight %',
      'Target Weight %',
      'Active Drift %',
      'Projected Weight %',
    ];

    const rows = orders.map((o) => [
      `"${o.action}"`,
      `"${o.categoryLabel}"`,
      o.orderAmount.toFixed(2),
      `"${o.currency}"`,
      o.estimatedPrice != null ? o.estimatedPrice.toFixed(2) : '',
      o.estimatedQuantity != null ? o.estimatedQuantity.toString() : '',
      o.currentWeightPercentage.toFixed(2),
      o.targetWeightPercentage.toFixed(2),
      o.driftPercentage.toFixed(2),
      o.projectedPostWeightPercentage.toFixed(2),
    ]);

    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `rebalance-orders-${portfolioId}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleDeletePlan = async () => {
    try {
      await deleteMutation.mutateAsync();
      setDeleteConfirmOpen(false);
    } catch {
      // Handled by react-query
    }
  };

  if (isPortfolioLoading || (isPlanLoading && !targetPlan)) {
    return (
      <Stack spacing={3}>
        <Skeleton variant="rectangular" height={50} sx={{ borderRadius: 1 }} />
        <Skeleton variant="rectangular" height={130} sx={{ borderRadius: 2 }} />
        <Skeleton variant="rectangular" height={380} sx={{ borderRadius: 2 }} />
      </Stack>
    );
  }

  if (portfolioError || planError) {
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
          error={portfolioError || planError}
          title="Failed to load target allocation and rebalancing data"
        />
      </Stack>
    );
  }

  return (
    <Stack spacing={3} data-testid="rebalancing-detail-page">
      {/* Breadcrumbs & Top Navigation */}
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
            Target Allocation & Rebalancing
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

      {/* Hero Header Card */}
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2 }}
          >
            <Box>
              <Typography variant="h5" sx={{ fontWeight: 700 }}>
                Strategic Target Allocation & Rebalancing Engine
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Monitor portfolio allocation drift against strategic models and execute cash-neutral or cash-injected rebalancing orders
              </Typography>
            </Box>

            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Button
                variant={targetPlan ? 'outlined' : 'contained'}
                size="small"
                startIcon={targetPlan ? <EditOutlinedIcon /> : <TuneOutlinedIcon />}
                onClick={() => setModalOpen(true)}
              >
                {targetPlan ? 'Edit Target Plan' : 'Configure Target Plan'}
              </Button>

              {targetPlan && (
                <Button
                  variant="outlined"
                  color="error"
                  size="small"
                  startIcon={<DeleteOutlinedIcon />}
                  onClick={() => setDeleteConfirmOpen(true)}
                >
                  Delete Plan
                </Button>
              )}
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {/* Unconfigured State Callout */}
      {!targetPlan ? (
        <Card
          variant="outlined"
          sx={{
            borderRadius: 2,
            borderStyle: 'dashed',
            borderColor: 'divider',
            p: 4,
            textAlign: 'center',
            bgcolor: 'action.hover',
          }}
        >
          <TuneOutlinedIcon color="primary" sx={{ fontSize: 56, mb: 1 }} />
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            No Target Allocation Configured
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 520, mx: 'auto', mt: 1, mb: 2.5 }}>
            Establish strategic asset class weights (e.g. 60% Equity / 30% Fixed Income / 10% Cash) or specific holding targets to enable automated drift detection and rebalancing orders.
          </Typography>
          <Button
            variant="contained"
            startIcon={<TuneOutlinedIcon />}
            onClick={() => setModalOpen(true)}
          >
            Configure Strategy Targets Now
          </Button>
        </Card>
      ) : (
        <>
          {/* Hero KPI Summary Strip */}
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
                  <TuneOutlinedIcon color="primary" fontSize="small" />
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                    Active Target Strategy
                  </Typography>
                </Stack>
                <Typography variant="h6" sx={{ fontWeight: 700 }} noWrap>
                  {targetPlan.name}
                </Typography>
                <Chip
                  size="small"
                  label={targetPlan.allocationType === 'ASSET_CLASS' ? 'Asset Class Model' : 'Holdings Model'}
                  variant="outlined"
                  sx={{ mt: 0.5, height: 20, fontSize: '0.7rem' }}
                />
              </Card>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
                  <WarningAmberOutlinedIcon
                    color={analysis?.hasDriftToleranceExceeded ? 'warning' : 'success'}
                    fontSize="small"
                  />
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                    Drift Tolerance & Status
                  </Typography>
                </Stack>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 0.5 }}>
                  <Typography variant="h5" sx={{ fontWeight: 700 }}>
                    ±{targetPlan.driftTolerancePercentage}%
                  </Typography>
                  <Chip
                    size="small"
                    icon={
                      analysis?.hasDriftToleranceExceeded ? (
                        <WarningAmberOutlinedIcon sx={{ fontSize: '0.8rem !important' }} />
                      ) : (
                        <CheckCircleOutlineOutlinedIcon sx={{ fontSize: '0.8rem !important' }} />
                      )
                    }
                    label={analysis?.hasDriftToleranceExceeded ? 'Drift Exceeded' : 'In Tolerance'}
                    color={analysis?.hasDriftToleranceExceeded ? 'warning' : 'success'}
                    sx={{ fontWeight: 700, fontSize: '0.75rem' }}
                  />
                </Stack>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                  Max Active Drift: <strong>{maxDrift.toFixed(1)}%</strong>
                </Typography>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
                  <BalanceOutlinedIcon color="primary" fontSize="small" />
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                    Actionable Orders Needed
                  </Typography>
                </Stack>
                <Typography variant="h5" sx={{ fontWeight: 700 }}>
                  {orders.length} {orders.length === 1 ? 'Order' : 'Orders'}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {buyOrders.length} Buy | {sellOrders.length} Sell
                </Typography>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <Card variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
                  <PaymentsOutlinedIcon color="primary" fontSize="small" />
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                    Rebalancing Turnover
                  </Typography>
                </Stack>
                <Typography variant="h5" sx={{ fontWeight: 700 }}>
                  {currency} {fmt(totalTurnover)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Post-Rebalance Drift: <strong>0.0%</strong>
                </Typography>
              </Card>
            </Grid>
          </Grid>

          {/* Drift Status Banner */}
          {analysis?.hasDriftToleranceExceeded ? (
            <Alert severity="warning" icon={<WarningAmberOutlinedIcon />} sx={{ borderRadius: 2 }}>
              <strong>Rebalancing Recommended:</strong> Portfolio allocation has breached the configured drift tolerance threshold of{' '}
              <strong>±{targetPlan.driftTolerancePercentage}%</strong>. Review suggested orders below to realign weights.
            </Alert>
          ) : (
            <Alert severity="success" icon={<CheckCircleOutlineOutlinedIcon />} sx={{ borderRadius: 2 }}>
              <strong>Portfolio Balanced:</strong> All monitored asset allocations are within the strategic drift tolerance window.
            </Alert>
          )}

          {/* Strategic Target vs Current Weight Breakdown Card */}
          <Card variant="outlined" sx={{ borderRadius: 2 }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Target Allocation Model vs Current Exposure
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Comparative breakdown of current market values, strategic target percentages, and active drift
                  </Typography>
                </Box>

                <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1.5 }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: 'action.hover' }}>
                        <TableCell sx={{ fontWeight: 700 }}>Category / Asset</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>Current Value</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>Current Weight</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>Target Weight</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>Active Drift</TableCell>
                        <TableCell align="center" sx={{ fontWeight: 700 }}>Drift Status</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>Target Value</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>Post-Weight</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {analysis?.items.map((item) => {
                        const isOver = item.driftStatus === 'OVERWEIGHT';
                        const isUnder = item.driftStatus === 'UNDERWEIGHT';

                        return (
                          <TableRow key={item.categoryKey} hover>
                            <TableCell sx={{ fontWeight: 600 }}>{item.categoryLabel}</TableCell>
                            <TableCell align="right">
                              {currency} {fmt(item.currentMarketValue)}
                            </TableCell>
                            <TableCell align="right" sx={{ fontWeight: 700 }}>
                              {fmtPct(item.currentWeightPercentage)}
                            </TableCell>
                            <TableCell align="right" sx={{ fontWeight: 600 }}>
                              {fmtPct(item.targetWeightPercentage)}
                            </TableCell>
                            <TableCell
                              align="right"
                              sx={{
                                fontWeight: 700,
                                color: item.isDriftExceeded
                                  ? 'warning.main'
                                  : 'text.primary',
                              }}
                            >
                              {item.driftPercentage >= 0 ? '+' : ''}
                              {fmtPct(item.driftPercentage)}
                            </TableCell>
                            <TableCell align="center">
                              {item.driftStatus === 'IN_TOLERANCE' ? (
                                <Chip
                                  size="small"
                                  label="In Tolerance"
                                  color="success"
                                  variant="outlined"
                                  sx={{ height: 20, fontSize: '0.7rem' }}
                                />
                              ) : isOver ? (
                                <Chip
                                  size="small"
                                  label="Overweight"
                                  color="warning"
                                  sx={{ height: 20, fontSize: '0.7rem', fontWeight: 600 }}
                                />
                              ) : isUnder ? (
                                <Chip
                                  size="small"
                                  label="Underweight"
                                  color="info"
                                  sx={{ height: 20, fontSize: '0.7rem', fontWeight: 600 }}
                                />
                              ) : (
                                <Chip size="small" label={item.driftStatus} variant="outlined" sx={{ height: 20, fontSize: '0.7rem' }} />
                              )}
                            </TableCell>
                            <TableCell align="right">
                              {currency} {fmt(item.targetValue)}
                            </TableCell>
                            <TableCell align="right" sx={{ fontWeight: 600, color: 'success.main' }}>
                              {fmtPct(item.projectedPostWeightPercentage)}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Stack>
            </CardContent>
          </Card>

          {/* Interactive Cash Injection & Deposit Simulator Card */}
          <Card variant="outlined" sx={{ borderRadius: 2 }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2 }}
                >
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      Rebalancing Execution Mode & Cash Simulator
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Simulate deploying new cash into underweight assets without triggering taxable sales
                    </Typography>
                  </Box>

                  <ToggleButtonGroup
                    value={rebalanceMode}
                    exclusive
                    size="small"
                    onChange={(_, val) => val && setRebalanceMode(val)}
                    aria-label="rebalance mode"
                  >
                    <ToggleButton value="FULL" sx={{ px: 2, fontWeight: 600 }}>
                      Full Rebalance (Buy & Sell)
                    </ToggleButton>
                    <ToggleButton value="CASH_INJECTION" sx={{ px: 2, fontWeight: 600 }}>
                      Cash Injection (Buy-Only)
                    </ToggleButton>
                  </ToggleButtonGroup>
                </Stack>

                {rebalanceMode === 'CASH_INJECTION' && (
                  <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default', borderRadius: 2 }}>
                    <Stack spacing={2}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                        Deposit Amount to Deploy ({currency}):
                      </Typography>

                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: 'center' }}>
                        <TextField
                          size="small"
                          type="number"
                          placeholder="e.g. 1000"
                          value={cashInjectionInput}
                          onChange={(e) => setCashInjectionInput(e.target.value)}
                          sx={{ width: { xs: '100%', sm: 220 } }}
                        />

                        {/* Quick cash pills */}
                        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
                          {[250, 500, 1000, 2500, 5000].map((amt) => (
                            <Chip
                              key={amt}
                              label={`+${currency} ${amt.toLocaleString()}`}
                              size="small"
                              onClick={() => handleQuickCash(amt)}
                              sx={{ cursor: 'pointer', fontWeight: 600 }}
                            />
                          ))}
                          {cashInjectionInput && (
                            <Button size="small" color="secondary" onClick={() => setCashInjectionInput('')}>
                              Clear
                            </Button>
                          )}
                        </Stack>
                      </Stack>

                      {analysis?.cashInjectionAmount && analysis.cashInjectionAmount > 0 && (
                        <Typography variant="caption" color="text.secondary">
                          Current Value: <strong>{currency} {fmt(analysis.totalPortfolioValue)}</strong> + Deposit:{' '}
                          <strong>{currency} {fmt(analysis.cashInjectionAmount)}</strong> = Post-Deposit:{' '}
                          <strong>{currency} {fmt(analysis.totalPostRebalanceValue)}</strong>
                        </Typography>
                      )}
                    </Stack>
                  </Paper>
                )}
              </Stack>
            </CardContent>
          </Card>

          {/* Actionable Trade Orders Ledger */}
          <Card variant="outlined" sx={{ borderRadius: 2 }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1.5 }}
                >
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      Actionable Rebalancing Trade Orders ({orders.length})
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Suggested orders to align actual allocations with target weights
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1.5}>
                    <Button
                      variant="outlined"
                      size="small"
                      startIcon={<ContentCopyOutlinedIcon />}
                      onClick={handleCopyPlan}
                      disabled={orders.length === 0}
                    >
                      Copy Plan
                    </Button>
                    <Button
                      variant="outlined"
                      size="small"
                      startIcon={<FileDownloadOutlinedIcon />}
                      onClick={handleExportOrdersCsv}
                      disabled={orders.length === 0}
                    >
                      Export CSV
                    </Button>
                  </Stack>
                </Stack>

                {orders.length === 0 ? (
                  <Box sx={{ py: 4, textAlign: 'center' }}>
                    <CheckCircleOutlineOutlinedIcon color="success" sx={{ fontSize: 44, mb: 1 }} />
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      No Trade Orders Required
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Portfolio allocations are aligned with target percentages.
                    </Typography>
                  </Box>
                ) : (
                  <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 1.5 }}>
                    <Table size="small">
                      <TableHead>
                        <TableRow sx={{ bgcolor: 'action.hover' }}>
                          <TableCell sx={{ fontWeight: 700 }}>Action</TableCell>
                          <TableCell sx={{ fontWeight: 700 }}>Category / Asset</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>Order Amount</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>Est Price</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>Est Qty</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>Current Weight</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>Target Weight</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>Active Drift</TableCell>
                          <TableCell align="right" sx={{ fontWeight: 700 }}>Projected Weight</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {orders.map((ord) => {
                          const isBuy = ord.action === 'BUY';
                          return (
                            <TableRow key={ord.categoryKey} hover>
                              <TableCell>
                                <Chip
                                  size="small"
                                  label={ord.action}
                                  color={isBuy ? 'success' : 'error'}
                                  variant="filled"
                                  sx={{ fontWeight: 800, fontSize: '0.75rem', minWidth: 55 }}
                                />
                              </TableCell>
                              <TableCell sx={{ fontWeight: 600 }}>{ord.categoryLabel}</TableCell>
                              <TableCell align="right" sx={{ fontWeight: 700 }}>
                                {currency} {fmt(ord.orderAmount)}
                              </TableCell>
                              <TableCell align="right" color="text.secondary">
                                {ord.estimatedPrice != null ? `${currency} ${fmt(ord.estimatedPrice)}` : '—'}
                              </TableCell>
                              <TableCell align="right" color="text.secondary">
                                {ord.estimatedQuantity != null ? ord.estimatedQuantity.toLocaleString() : '—'}
                              </TableCell>
                              <TableCell align="right">{fmtPct(ord.currentWeightPercentage)}</TableCell>
                              <TableCell align="right" sx={{ fontWeight: 600 }}>
                                {fmtPct(ord.targetWeightPercentage)}
                              </TableCell>
                              <TableCell
                                align="right"
                                sx={{
                                  fontWeight: 600,
                                  color: ord.isDriftExceeded ? 'warning.main' : 'text.secondary',
                                }}
                              >
                                {ord.driftPercentage >= 0 ? '+' : ''}
                                {fmtPct(ord.driftPercentage)}
                              </TableCell>
                              <TableCell align="right" sx={{ fontWeight: 600, color: 'success.main' }}>
                                {fmtPct(ord.projectedPostWeightPercentage)}
                              </TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </Stack>
            </CardContent>
          </Card>
        </>
      )}

      {/* Target Allocation Modal */}
      {modalOpen && (
        <TargetAllocationModal
          open={modalOpen}
          onClose={() => setModalOpen(false)}
          portfolioId={portfolioId || ''}
          existingPlan={targetPlan}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        open={deleteConfirmOpen}
        title="Delete Target Allocation Plan"
        message={`Are you sure you want to delete the "${targetPlan?.name}" plan? This will remove all strategic weights and disable drift monitoring.`}
        confirmLabel="Delete Plan"
        confirmColor="error"
        onConfirm={handleDeletePlan}
        onCancel={() => setDeleteConfirmOpen(false)}
      />

      {/* Copied Plan Snackbar */}
      <Snackbar
        open={copiedSnackbar}
        autoHideDuration={3000}
        onClose={() => setCopiedSnackbar(false)}
        message="Rebalancing plan copied to clipboard!"
      />
    </Stack>
  );
}

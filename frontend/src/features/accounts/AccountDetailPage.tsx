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
  IconButton,
  Link,
  Paper,
  Skeleton,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';

import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import PaidOutlinedIcon from '@mui/icons-material/PaidOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';

import { usePortfolio } from '../portfolios/usePortfolios';
import { useAccount } from './useAccounts';
import { useAccountPositionsPerformance, useRecalculatePositions } from '../positions/usePositions';
import { useTransactionsList } from '../transactions/useTransactions';
import { useImportBatchesList, useDeleteImportBatch } from '../imports/useCsvImport';
import { PositionTable } from '../positions/PositionTable';
import { TransactionTable } from '../transactions/TransactionTable';
import { CsvImportModal } from '../imports/CsvImportModal';
import { ConfirmDialog, EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { ImportBatch, Transaction } from '../../types';

const fmt = (v: number | undefined | null, dp = 2) =>
  Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });

export function computeAccountCash(transactions: Transaction[]): number {
  let cash = 0;
  for (const tx of transactions) {
    if (tx.status === 'CORRECTED') continue;
    const net = Number(tx.netAmount) || 0;
    const gross = Number(tx.grossAmount) || net;
    switch (tx.type) {
      case 'DEPOSIT':
      case 'DIVIDEND':
      case 'INTEREST':
      case 'SELL':
        cash += net;
        break;
      case 'WITHDRAWAL':
      case 'FEE':
        cash -= gross;
        break;
      case 'BUY':
        cash -= (net || gross);
        break;
      default:
        break;
    }
  }
  return Math.max(0, cash);
}

export function AccountDetailPage() {
  const { id: portfolioId, accountId } = useParams<{ id: string; accountId: string }>();
  const [activeTab, setActiveTab] = useState(0);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [batchToDelete, setBatchToDelete] = useState<ImportBatch | null>(null);

  const {
    data: portfolio,
    isLoading: isPortfolioLoading,
    error: portfolioError,
  } = usePortfolio(portfolioId || '');

  const {
    data: account,
    isLoading: isAccountLoading,
    error: accountError,
    refetch: refetchAccount,
  } = useAccount(portfolioId || '', accountId || '');

  const {
    data: positions = [],
    isLoading: isPositionsLoading,
    error: positionsError,
    refetch: refetchPositions,
  } = useAccountPositionsPerformance(portfolioId || '', accountId || '', true);

  const {
    data: transactions = [],
    isLoading: isTransactionsLoading,
    error: transactionsError,
    refetch: refetchTransactions,
  } = useTransactionsList(portfolioId || '', accountId || '');

  const {
    data: importBatches = [],
    isLoading: isBatchesLoading,
    error: batchesError,
    refetch: refetchBatches,
  } = useImportBatchesList(portfolioId || '', accountId || '');

  const deleteBatchMutation = useDeleteImportBatch(portfolioId || '', accountId || '');
  const recalculateMutation = useRecalculatePositions(portfolioId || '');

  // ─── Financial calculations ──────────────────────────────────────────────────
  const cashBalance = useMemo(() => computeAccountCash(transactions), [transactions]);

  const openPositions = useMemo(() => positions.filter((p) => p.currentQuantity > 0), [positions]);
  const openHoldingsCount = openPositions.length;

  const holdingsMarketValue = useMemo(() => {
    return openPositions.reduce((acc, p) => acc + (p.currentMarketValue || 0), 0);
  }, [openPositions]);

  const totalCostBasis = useMemo(() => {
    return openPositions.reduce((acc, p) => acc + (p.currentCostBasis || 0), 0);
  }, [openPositions]);

  const totalUnrealizedPnl = useMemo(() => {
    return openPositions.reduce((acc, p) => acc + (p.unrealizedGainLoss || 0), 0);
  }, [openPositions]);

  const unrealizedPct = totalCostBasis > 0 ? (totalUnrealizedPnl / totalCostBasis) * 100 : 0;

  const totalRealizedPnl = useMemo(() => {
    return positions.reduce((acc, p) => acc + (p.realizedGainLoss || 0), 0);
  }, [positions]);

  const totalAccountValue = cashBalance + holdingsMarketValue;

  const currency = account?.accountCurrency || portfolio?.baseCurrency || 'GBP';

  // Cash flow metrics
  const cashFlows = useMemo(() => {
    return transactions.filter(
      (tx) =>
        tx.type === 'DEPOSIT' ||
        tx.type === 'WITHDRAWAL' ||
        tx.type === 'DIVIDEND' ||
        tx.type === 'INTEREST' ||
        tx.type === 'FEE',
    );
  }, [transactions]);

  const totalDeposits = useMemo(() => {
    return transactions
      .filter((tx) => tx.type === 'DEPOSIT' && tx.status !== 'CORRECTED')
      .reduce((acc, tx) => acc + (Number(tx.netAmount) || 0), 0);
  }, [transactions]);

  const totalWithdrawals = useMemo(() => {
    return transactions
      .filter((tx) => tx.type === 'WITHDRAWAL' && tx.status !== 'CORRECTED')
      .reduce((acc, tx) => acc + (Number(tx.grossAmount) || Number(tx.netAmount) || 0), 0);
  }, [transactions]);

  const totalDividends = useMemo(() => {
    return transactions
      .filter((tx) => (tx.type === 'DIVIDEND' || tx.type === 'INTEREST') && tx.status !== 'CORRECTED')
      .reduce((acc, tx) => acc + (Number(tx.netAmount) || 0), 0);
  }, [transactions]);

  const totalFees = useMemo(() => {
    return transactions
      .filter((tx) => tx.status !== 'CORRECTED')
      .reduce((acc, tx) => acc + (Number(tx.feeAmount) || (tx.type === 'FEE' ? Number(tx.grossAmount) : 0)), 0);
  }, [transactions]);

  const netCapitalFlow = totalDeposits - totalWithdrawals;

  const handleDeleteBatch = async () => {
    if (!batchToDelete) return;
    await deleteBatchMutation.mutateAsync(batchToDelete.id, {
      onSuccess: () => setBatchToDelete(null),
    });
  };

  const isReadOnly = portfolio?.status !== 'ACTIVE' || account?.status !== 'ACTIVE';

  if (isPortfolioLoading || isAccountLoading) {
    return (
      <Box sx={{ p: 3 }}>
        <Skeleton variant="text" width={240} height={32} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={160} sx={{ borderRadius: 3, mb: 3 }} />
        <Skeleton variant="rectangular" height={400} sx={{ borderRadius: 3 }} />
      </Box>
    );
  }

  if (!account || !portfolio) {
    return (
      <Box sx={{ p: 3 }}>
        <ErrorAlert
          error={accountError || portfolioError || new Error('Account or Portfolio not found')}
          onClose={() => refetchAccount()}
        />
        <Button
          component={RouterLink}
          to={`/portfolios/${portfolioId}`}
          startIcon={<ArrowBackIcon />}
          sx={{ mt: 2 }}
        >
          Back to Portfolio
        </Button>
      </Box>
    );
  }

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
            {account.name}
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

      {/* ─── Account Summary Hero ──────────────────────────────────────────── */}
      <Paper
        variant="outlined"
        sx={{
          p: { xs: 2.5, md: 3.5 },
          mb: 3,
          borderRadius: 3,
          background: (t) =>
            t.palette.mode === 'dark'
              ? 'linear-gradient(135deg, rgba(255,255,255,0.03) 0%, rgba(255,255,255,0.01) 100%)'
              : 'linear-gradient(135deg, rgba(0,0,0,0.01) 0%, rgba(0,0,0,0.03) 100%)',
        }}
      >
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2, mb: 3 }}
        >
          <Box>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.75, flexWrap: 'wrap', gap: 0.5 }}>
              <Typography variant="h4" sx={{ fontWeight: 700 }}>
                {account.name}
              </Typography>
              <Chip label={account.brokerName} size="small" variant="outlined" sx={{ fontWeight: 600 }} />
              <Chip label={currency} size="small" color="primary" variant="outlined" />
              <Chip
                label={account.status}
                size="small"
                color={account.status === 'ACTIVE' ? 'success' : 'default'}
              />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              Account ID: {account.id} · Custodian: {account.brokerName}
            </Typography>
          </Box>

          {!isReadOnly && (
            <Stack direction="row" spacing={1.5} sx={{ flexWrap: 'wrap', gap: 1 }}>
              <Button
                variant="outlined"
                size="small"
                startIcon={<RefreshIcon />}
                onClick={() => recalculateMutation.mutate()}
                disabled={recalculateMutation.isPending}
              >
                Recalculate
              </Button>
              <Button
                variant="outlined"
                size="small"
                startIcon={<UploadFileIcon />}
                onClick={() => setImportModalOpen(true)}
              >
                Import CSV
              </Button>
            </Stack>
          )}
        </Stack>

        <Divider sx={{ mb: 3 }} />

        {/* Hero KPI Grid */}
        <Grid container spacing={2}>
          <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Total Account Value
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5 }}>
                {currency} {fmt(totalAccountValue)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Cash + Holdings
              </Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Available Cash
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5, color: 'primary.main' }}>
                {currency} {fmt(cashBalance)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Ready to deploy
              </Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Holdings Value
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5 }}>
                {currency} {fmt(holdingsMarketValue)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {openHoldingsCount} active {openHoldingsCount === 1 ? 'position' : 'positions'}
              </Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 6, sm: 6, md: 2.4 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Unrealized P&L
              </Typography>
              <Typography
                variant="h5"
                sx={{
                  fontWeight: 700,
                  mt: 0.5,
                  color: totalUnrealizedPnl >= 0 ? 'success.main' : 'error.main',
                }}
              >
                {totalUnrealizedPnl >= 0 ? '+' : ''}
                {currency} {fmt(totalUnrealizedPnl)}
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  fontWeight: 600,
                  color: totalUnrealizedPnl >= 0 ? 'success.main' : 'error.main',
                }}
              >
                {unrealizedPct >= 0 ? '+' : ''}
                {fmt(unrealizedPct)}% on cost
              </Typography>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 2.4 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                Total Dividends Earned
              </Typography>
              <Typography variant="h5" sx={{ fontWeight: 700, mt: 0.5, color: 'secondary.main' }}>
                {currency} {fmt(totalDividends)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Realized G/L: {totalRealizedPnl >= 0 ? '+' : ''}{currency} {fmt(totalRealizedPnl)}
              </Typography>
            </Paper>
          </Grid>
        </Grid>
      </Paper>

      {/* ─── Navigation Tabs ────────────────────────────────────────────────── */}
      <Paper variant="outlined" sx={{ borderRadius: 3, mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, val) => setActiveTab(val)}
          sx={{
            borderBottom: 1,
            borderColor: 'divider',
            px: 2,
            '& .MuiTab-root': { py: 2, fontSize: '0.95rem', fontWeight: 600 },
          }}
        >
          <Tab
            label={`Holdings (${openHoldingsCount})`}
            icon={<ShowChartOutlinedIcon fontSize="small" />}
            iconPosition="start"
          />
          <Tab
            label={`Transactions (${transactions.length})`}
            icon={<ReceiptLongOutlinedIcon fontSize="small" />}
            iconPosition="start"
          />
          <Tab
            label={`Import History (${importBatches.length})`}
            icon={<HistoryOutlinedIcon fontSize="small" />}
            iconPosition="start"
          />
          <Tab
            label="Cash Flow Timeline"
            icon={<PaidOutlinedIcon fontSize="small" />}
            iconPosition="start"
          />
        </Tabs>

        <Box sx={{ p: { xs: 2, md: 3 } }}>
          {/* Tab 0: Position Holdings */}
          {activeTab === 0 && (
            <PositionTable
              portfolioId={portfolioId!}
              accountId={accountId!}
              defaultCurrency={currency}
              isReadOnly={isReadOnly}
            />
          )}

          {/* Tab 1: Transaction Ledger */}
          {activeTab === 1 && (
            <TransactionTable
              portfolioId={portfolioId!}
              accountId={accountId!}
              defaultCurrency={currency}
              isReadOnly={isReadOnly}
            />
          )}

          {/* Tab 2: Import History */}
          {activeTab === 2 && (
            <Box>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2, mb: 2 }}
              >
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    Broker Import Batches
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Review statement imports, audit row counts, and roll back batches if needed.
                  </Typography>
                </Box>
                {!isReadOnly && (
                  <Button
                    variant="contained"
                    size="small"
                    startIcon={<UploadFileIcon />}
                    onClick={() => setImportModalOpen(true)}
                  >
                    Import New CSV
                  </Button>
                )}
              </Stack>

              <ErrorAlert error={batchesError} onClose={() => refetchBatches()} />

              {isBatchesLoading ? (
                <LoadingState variant="table" count={2} />
              ) : importBatches.length === 0 ? (
                <EmptyState
                  title="No Import Batches Found"
                  description="Import CSV trade confirmations or activity feeds from Freetrade, Trading 212, InvestEngine, or standard broker exports."
                  actionLabel={!isReadOnly ? 'Import CSV' : undefined}
                  onAction={() => setImportModalOpen(true)}
                  icon={<UploadFileIcon sx={{ fontSize: 48, opacity: 0.7 }} />}
                />
              ) : (
                <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Import Date</TableCell>
                        <TableCell>Broker</TableCell>
                        <TableCell>File Name</TableCell>
                        <TableCell align="right">Imported Rows</TableCell>
                        <TableCell align="right">Skipped Rows</TableCell>
                        <TableCell align="right">Total Rows</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Action</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {importBatches.map((batch) => (
                        <TableRow key={batch.id} hover>
                          <TableCell>
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                              {new Date(batch.createdAt).toLocaleDateString()}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {new Date(batch.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Chip label={batch.brokerType} size="small" variant="outlined" />
                          </TableCell>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                            {batch.fileName}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 600, color: 'success.main' }}>
                            {batch.importedRows}
                          </TableCell>
                          <TableCell align="right" color="text.secondary">
                            {batch.skippedRows}
                          </TableCell>
                          <TableCell align="right" sx={{ fontWeight: 600 }}>
                            {batch.totalRows}
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={batch.status}
                              size="small"
                              color={
                                batch.status === 'COMPLETED'
                                  ? 'success'
                                  : batch.status === 'FAILED'
                                    ? 'error'
                                    : 'warning'
                              }
                            />
                          </TableCell>
                          <TableCell align="right">
                            {!isReadOnly && (
                              <Tooltip title="Rollback batch and associated transactions">
                                <IconButton
                                  size="small"
                                  color="error"
                                  onClick={() => setBatchToDelete(batch)}
                                  aria-label={`rollback batch ${batch.fileName}`}
                                >
                                  <DeleteOutlinedIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          )}

          {/* Tab 3: Cash Flow Timeline */}
          {activeTab === 3 && (
            <Box>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2, mb: 3 }}
              >
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>
                    Cash Flow & Capital Movement
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Chronological audit of deposits, withdrawals, dividends, interest, and platform fees.
                  </Typography>
                </Box>
              </Stack>

              {/* Cash Flow Summary Cards */}
              <Grid container spacing={2} sx={{ mb: 3 }}>
                <Grid size={{ xs: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                      Total Deposits
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: 'success.main', mt: 0.5 }}>
                      +{currency} {fmt(totalDeposits)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Capital injected
                    </Typography>
                  </Paper>
                </Grid>

                <Grid size={{ xs: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                      Total Withdrawals
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: 'error.main', mt: 0.5 }}>
                      -{currency} {fmt(totalWithdrawals)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Capital returned
                    </Typography>
                  </Paper>
                </Grid>

                <Grid size={{ xs: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                      Net Capital Invested
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                      {netCapitalFlow >= 0 ? '+' : ''}{currency} {fmt(netCapitalFlow)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Deposits minus withdrawals
                    </Typography>
                  </Paper>
                </Grid>

                <Grid size={{ xs: 6, md: 3 }}>
                  <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                      Dividends & Income
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, color: 'secondary.main', mt: 0.5 }}>
                      +{currency} {fmt(totalDividends)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Fees paid: {currency} {fmt(totalFees)}
                    </Typography>
                  </Paper>
                </Grid>
              </Grid>

              {cashFlows.length === 0 ? (
                <EmptyState
                  title="No Cash Movements Recorded"
                  description="Deposit funds, record withdrawals, or receive dividends to populate your cash timeline."
                  icon={<PaidOutlinedIcon sx={{ fontSize: 48, opacity: 0.7 }} />}
                />
              ) : (
                <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Date</TableCell>
                        <TableCell>Movement Type</TableCell>
                        <TableCell>Instrument / Note</TableCell>
                        <TableCell align="right">Amount</TableCell>
                        <TableCell>Status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {cashFlows.map((tx) => {
                        const isInflow = tx.type === 'DEPOSIT' || tx.type === 'DIVIDEND' || tx.type === 'INTEREST';
                        const amount = isInflow ? Number(tx.netAmount) : (Number(tx.grossAmount) || Number(tx.netAmount));
                        return (
                          <TableRow key={tx.id} hover sx={{ opacity: tx.status === 'CORRECTED' ? 0.5 : 1 }}>
                            <TableCell>
                              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                {new Date(tx.tradeDate).toLocaleDateString()}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {new Date(tx.tradeDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                              </Typography>
                            </TableCell>
                            <TableCell>
                              <Chip
                                label={tx.type}
                                size="small"
                                color={
                                  tx.type === 'DEPOSIT'
                                    ? 'success'
                                    : tx.type === 'DIVIDEND' || tx.type === 'INTEREST'
                                      ? 'secondary'
                                      : 'error'
                                }
                              />
                            </TableCell>
                            <TableCell>
                              {tx.instrumentName ? (
                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                  {tx.instrumentName} {tx.instrumentTicker ? `(${tx.instrumentTicker})` : ''}
                                </Typography>
                              ) : (
                                <Typography variant="body2" color="text.secondary">
                                  {tx.notes || 'Direct Cash Flow'}
                                </Typography>
                              )}
                            </TableCell>
                            <TableCell
                              align="right"
                              sx={{
                                fontWeight: 700,
                                color: isInflow ? 'success.main' : 'error.main',
                              }}
                            >
                              {isInflow ? '+' : '-'}
                              {tx.currency} {fmt(amount)}
                            </TableCell>
                            <TableCell>
                              <Chip
                                label={tx.status}
                                size="small"
                                variant={tx.status === 'CORRECTED' ? 'outlined' : 'filled'}
                              />
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          )}
        </Box>
      </Paper>

      {/* ─── Modals ─────────────────────────────────────────────────────────── */}
      <CsvImportModal
        open={importModalOpen}
        portfolioId={portfolioId!}
        accountId={accountId!}
        onClose={() => setImportModalOpen(false)}
      />

      <ConfirmDialog
        open={Boolean(batchToDelete)}
        title="Rollback Import Batch"
        message={`Are you sure you want to rollback import batch "${batchToDelete?.fileName}"? All transactions imported in this batch will be reverted.`}
        confirmLabel="Rollback Batch"
        confirmColor="error"
        isPending={deleteBatchMutation.isPending}
        onConfirm={handleDeleteBatch}
        onCancel={() => setBatchToDelete(null)}
      />
    </Box>
  );
}

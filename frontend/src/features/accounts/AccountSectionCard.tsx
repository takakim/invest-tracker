import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  Paper,
  Stack,
  Tab,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';

import { useAccountPositionsPerformance } from '../positions/usePositions';
import { useTransactionsList } from '../transactions/useTransactions';
import { PositionTable } from '../positions/PositionTable';
import { TransactionTable } from '../transactions/TransactionTable';
import { computeAccountCash } from './AccountDetailPage';
import type { Account, Portfolio } from '../../types';

interface AccountSectionCardProps {
  portfolio: Portfolio;
  account: Account;
  onEdit: (account: Account) => void;
  onArchive: (account: Account) => void;
}

const fmt = (v: number | undefined | null, dp = 2) =>
  Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });

export function AccountSectionCard({
  portfolio,
  account,
  onEdit,
  onArchive,
}: AccountSectionCardProps) {
  const navigate = useNavigate();
  const [tabIndex, setTabIndex] = useState(0);

  const { data: positions = [] } = useAccountPositionsPerformance(portfolio.id, account.id, false);
  const { data: transactions = [] } = useTransactionsList(portfolio.id, account.id);

  const cashBalance = useMemo(() => computeAccountCash(transactions), [transactions]);

  const openPositions = useMemo(() => positions.filter((p) => p.currentQuantity > 0), [positions]);
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
  const totalAccountValue = cashBalance + holdingsMarketValue;
  const currency = account.accountCurrency || portfolio.baseCurrency;

  const isReadOnly = portfolio.status !== 'ACTIVE' || account.status !== 'ACTIVE';

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, borderRadius: 3 }}>
      {/* Account Header */}
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2, mb: 2 }}
      >
        <Box>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5, flexWrap: 'wrap', gap: 0.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              {account.name}
            </Typography>
            <Chip label={account.brokerName} size="small" variant="outlined" />
            <Chip label={currency} size="small" color="primary" variant="outlined" />
            <Chip
              label={account.status}
              size="small"
              color={account.status === 'ACTIVE' ? 'success' : 'default'}
            />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            ID: {account.id}
          </Typography>
        </Box>

        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Button
            variant="outlined"
            size="small"
            endIcon={<ArrowForwardIcon fontSize="small" />}
            onClick={() => navigate(`/portfolios/${portfolio.id}/accounts/${account.id}`)}
          >
            Account Details
          </Button>

          {!isReadOnly && (
            <>
              <Tooltip title="Edit account">
                <IconButton
                  size="small"
                  onClick={() => onEdit(account)}
                  aria-label={`edit ${account.name}`}
                >
                  <EditOutlinedIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title="Archive account">
                <IconButton
                  size="small"
                  color="error"
                  onClick={() => onArchive(account)}
                  aria-label={`archive ${account.name}`}
                >
                  <ArchiveOutlinedIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </>
          )}
        </Stack>
      </Stack>

      {/* Inline 3-KPI summary strip */}
      <Paper
        variant="elevation"
        elevation={0}
        sx={{
          p: 1.5,
          mb: 2,
          borderRadius: 2,
          bgcolor: (t) => (t.palette.mode === 'dark' ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)'),
          border: '1px dashed',
          borderColor: 'divider',
        }}
      >
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={{ xs: 1.5, sm: 3 }}
          sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}
        >
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 600 }}>
              AVAILABLE CASH
            </Typography>
            <Typography variant="body1" sx={{ fontWeight: 700, color: 'primary.main' }}>
              {currency} {fmt(cashBalance)}
            </Typography>
          </Box>

          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 600 }}>
              OPEN POSITIONS
            </Typography>
            <Typography variant="body1" sx={{ fontWeight: 700 }}>
              {openPositions.length} {openPositions.length === 1 ? 'Holding' : 'Holdings'}
            </Typography>
          </Box>

          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 600 }}>
              UNREALIZED P&L
            </Typography>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'baseline' }}>
              <Typography
                variant="body1"
                sx={{
                  fontWeight: 700,
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
                ({unrealizedPct >= 0 ? '+' : ''}
                {fmt(unrealizedPct)}%)
              </Typography>
            </Stack>
          </Box>

          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 600 }}>
              TOTAL ACCOUNT VALUE
            </Typography>
            <Typography variant="body1" sx={{ fontWeight: 700 }}>
              {currency} {fmt(totalAccountValue)}
            </Typography>
          </Box>
        </Stack>
      </Paper>

      <Divider sx={{ mb: 2 }} />

      <Tabs
        value={tabIndex}
        onChange={(_, val) => setTabIndex(val)}
        sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}
      >
        <Tab
          label={`Position Holdings (${openPositions.length})`}
          icon={<ShowChartOutlinedIcon fontSize="small" />}
          iconPosition="start"
        />
        <Tab
          label={`Transaction Ledger (${transactions.length})`}
          icon={<ReceiptLongOutlinedIcon fontSize="small" />}
          iconPosition="start"
        />
      </Tabs>

      {tabIndex === 0 ? (
        <PositionTable
          portfolioId={portfolio.id}
          accountId={account.id}
          defaultCurrency={currency}
          isReadOnly={isReadOnly}
        />
      ) : (
        <TransactionTable
          portfolioId={portfolio.id}
          accountId={account.id}
          defaultCurrency={currency}
          isReadOnly={isReadOnly}
        />
      )}
    </Paper>
  );
}

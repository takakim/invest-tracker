import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { useCashFlowAnalytics } from './useAnalytics';

interface CashFlowSummaryCardProps {
  portfolioId: string;
  currency: string;
}

export function CashFlowSummaryCard({ portfolioId, currency }: CashFlowSummaryCardProps) {
  const navigate = useNavigate();
  const { data: analytics, isLoading, error } = useCashFlowAnalytics(portfolioId, { period: 'ALL', groupBy: 'MONTH' });

  if (isLoading) {
    return (
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Skeleton variant="text" width="40%" height={28} />
          <Skeleton variant="rectangular" height={80} sx={{ mt: 2, borderRadius: 1 }} />
        </CardContent>
      </Card>
    );
  }

  if (error || !analytics) return null;

  const fmt = (v: number | string | undefined | null) => {
    const n = Number(v ?? 0);
    return n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };
  const fmtPct = (v: number | string | undefined | null) => `${Number(v ?? 0).toFixed(1)}%`;

  const { summary } = analytics;
  const capitalPct = Number(summary.capitalContributionsPercentage || 0);
  const growthPct = Number(summary.marketGrowthPercentage || 0);

  return (
    <Card
      variant="outlined"
      sx={{ borderRadius: 2 }}
      data-testid="cash-flow-summary-card"
    >
      <CardContent>
        <Stack spacing={2.5}>
          {/* Header row */}
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}
          >
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <AccountBalanceWalletOutlinedIcon color="primary" sx={{ fontSize: 24 }} />
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  Cash Flow & Savings
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  External capital deposits, withdrawals & wealth origin · {currency}
                </Typography>
              </Box>
            </Stack>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
              <Tooltip title="Average net capital contributed per active month">
                <Chip
                  icon={<SavingsOutlinedIcon />}
                  label={`Avg Inflow ${currency} ${fmt(summary.avgMonthlyContribution)}/mo`}
                  color="primary"
                  variant="outlined"
                  size="small"
                  sx={{ fontWeight: 600 }}
                />
              </Tooltip>
              <Tooltip title="Capital contributions vs market growth ratio">
                <Chip
                  icon={<TrendingUpOutlinedIcon />}
                  label={`Wealth Split ${fmtPct(capitalPct)} / ${fmtPct(growthPct)}`}
                  color="success"
                  variant="outlined"
                  size="small"
                  sx={{ fontWeight: 600 }}
                />
              </Tooltip>
            </Stack>
          </Stack>

          {/* 4 KPI pills */}
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={1.5}
            sx={{ width: '100%' }}
          >
            <Box
              sx={{
                flex: 1,
                p: 1.5,
                bgcolor: 'background.default',
                borderRadius: 1.5,
                border: 1,
                borderColor: 'divider',
              }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                Total Deposits
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, color: 'success.main', mt: 0.5 }}>
                +{currency} {fmt(summary.totalDeposits)}
              </Typography>
            </Box>

            <Box
              sx={{
                flex: 1,
                p: 1.5,
                bgcolor: 'background.default',
                borderRadius: 1.5,
                border: 1,
                borderColor: 'divider',
              }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                Total Withdrawals
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, color: 'error.main', mt: 0.5 }}>
                -{currency} {fmt(summary.totalWithdrawals)}
              </Typography>
            </Box>

            <Box
              sx={{
                flex: 1,
                p: 1.5,
                bgcolor: 'background.default',
                borderRadius: 1.5,
                border: 1,
                borderColor: 'divider',
              }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                Net Invested Capital
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.main', mt: 0.5 }}>
                {currency} {fmt(summary.netContributions)}
              </Typography>
            </Box>

            <Box
              sx={{
                flex: 1,
                p: 1.5,
                bgcolor: 'background.default',
                borderRadius: 1.5,
                border: 1,
                borderColor: 'divider',
              }}
            >
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                Active Months
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                {summary.activeContributionMonths} mo
              </Typography>
            </Box>
          </Stack>

          {/* Wealth Origin Progress Bar */}
          <Box sx={{ p: 1.5, bgcolor: 'background.default', borderRadius: 1.5, border: 1, borderColor: 'divider' }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', mb: 0.75 }}>
              <Typography variant="caption" sx={{ fontWeight: 600 }}>
                Wealth Origin: Contributions ({fmtPct(capitalPct)}) vs Growth ({fmtPct(growthPct)})
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Portfolio Value: {currency} {fmt(summary.currentPortfolioValue)}
              </Typography>
            </Stack>
            <Box
              sx={{
                display: 'flex',
                height: 10,
                borderRadius: 5,
                overflow: 'hidden',
                bgcolor: 'divider',
              }}
            >
              <Box
                sx={{
                  width: `${Math.min(capitalPct, 100)}%`,
                  bgcolor: 'primary.main',
                  transition: 'width 0.6s ease',
                }}
              />
              <Box
                sx={{
                  width: `${Math.min(growthPct, 100)}%`,
                  bgcolor: 'success.main',
                  transition: 'width 0.6s ease',
                }}
              />
            </Box>
          </Box>

          {/* Footer view detail button */}
          <Stack direction="row" sx={{ justifyContent: 'flex-end' }}>
            <Button
              size="small"
              variant="text"
              endIcon={<ArrowForwardIcon />}
              onClick={() => navigate(`/portfolios/${portfolioId}/cash-flows`)}
              sx={{ textTransform: 'none', fontWeight: 600 }}
            >
              View Full Cash Flow Analytics & Ledger
            </Button>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

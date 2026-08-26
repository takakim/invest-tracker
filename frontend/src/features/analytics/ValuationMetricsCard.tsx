import React from 'react';
import {
  Card,
  CardContent,
  Typography,
  Stack,
  Chip,
  Box,
  Alert,
  Skeleton,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import { usePortfolioAnalytics } from './useAnalytics';

interface ValuationMetricsCardProps {
  portfolioId: string;
  currency: string;
}

export function ValuationMetricsCard({ portfolioId, currency }: ValuationMetricsCardProps) {
  const { data: analytics, isLoading, error } = usePortfolioAnalytics(portfolioId);

  if (isLoading) {
    return (
      <Card variant="outlined">
        <CardContent>
          <Skeleton variant="text" width="40%" height={32} />
          <Skeleton variant="rectangular" height={80} sx={{ my: 1, borderRadius: 1 }} />
        </CardContent>
      </Card>
    );
  }

  if (error || !analytics) {
    return null;
  }

  const isPositive = analytics.totalUnrealizedGainLoss >= 0;
  const formattedValue = Number(analytics.totalCurrentValue).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  const formattedCost = Number(analytics.totalCostBasis).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  const formattedUnrealized = Number(analytics.totalUnrealizedGainLoss).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  const formattedReturnPct = Number(analytics.totalUnrealizedReturnPercentage).toFixed(2);
  const formattedCash = Number(analytics.totalCashValue).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }}>
      <CardContent>
        <Stack spacing={2}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <AccountBalanceWalletIcon color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 600, fontSize: '1.1rem' }}>
                Portfolio Valuation & Unrealized P&L
              </Typography>
            </Stack>
            <Chip
              icon={isPositive ? <TrendingUpIcon /> : <TrendingDownIcon />}
              label={`${isPositive ? '+' : ''}${formattedReturnPct}% (${isPositive ? '+' : ''}${formattedUnrealized} ${currency})`}
              color={isPositive ? 'success' : 'error'}
              variant="outlined"
              sx={{ fontWeight: 600 }}
            />
          </Stack>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} sx={{ alignItems: { sm: 'center' } }}>
            <Box>
              <Typography variant="body2" color="text.secondary">
                Total Market Value ({currency})
              </Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: 'primary.main', mt: 0.5 }}>
                {formattedValue} <Typography component="span" variant="h6" color="text.secondary">{currency}</Typography>
              </Typography>
            </Box>

            <Box sx={{ borderLeft: { sm: '1px solid #e0e0e0' }, pl: { sm: 3 } }}>
              <Typography variant="body2" color="text.secondary">
                Cost Basis: <strong>{formattedCost} {currency}</strong>
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Available Cash: <strong>{formattedCash} {currency}</strong>
              </Typography>
            </Box>
          </Stack>

          {analytics.warnings.length > 0 && (
            <Alert severity="warning" sx={{ mt: 1, fontSize: '0.85rem' }}>
              {analytics.warnings.map((w, idx) => (
                <div key={idx}>{w}</div>
              ))}
            </Alert>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

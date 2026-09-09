import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { usePerformance } from './usePerformance';
import { usePortfolioHistory } from '../analytics/useAnalytics';
import type { PerformanceResult } from '../../types';

interface Props {
  portfolioId: string;
  currency?: string;
}

const PERIODS = ['1M', '3M', '6M', 'YTD', '1Y', 'ALL'] as const;
type Period = (typeof PERIODS)[number];

function formatPct(value: number | null | undefined): string {
  if (value == null) return '—';
  return `${(value * 100).toFixed(2)}%`;
}

function formatMoney(value: number | undefined | null, currency = 'USD'): string {
  if (value == null) return '—';
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(value);
}

interface MetricProps {
  label: string;
  value: string;
  positive?: boolean | null;
  tooltip?: string;
}

function Metric({ label, value, positive, tooltip }: MetricProps) {
  const color =
    positive == null
      ? 'text.primary'
      : positive
        ? 'success.main'
        : 'error.main';

  return (
    <Stack spacing={0.5}>
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }} noWrap>
          {label}
        </Typography>
        {tooltip && (
          <Tooltip title={tooltip} arrow placement="top">
            <InfoOutlinedIcon sx={{ fontSize: 13, color: 'text.disabled' }} />
          </Tooltip>
        )}
      </Stack>
      <Typography variant="body1" sx={{ fontWeight: 700 }} color={color} noWrap>
        {value}
      </Typography>
    </Stack>
  );
}

interface ReturnBadgeProps {
  result: PerformanceResult;
  selectedPeriod: Period;
  periodReturnPct: number | null;
  periodGainLoss?: number | null;
}

function ReturnBadge({ result, selectedPeriod, periodReturnPct, periodGainLoss }: ReturnBadgeProps) {
  const allTimeReturn =
    result.returnMethod === 'TWR' ? result.twrReturn : result.mwrReturn;
  const displayValue = selectedPeriod === 'ALL' ? allTimeReturn : periodReturnPct;
  const annualized = result.returnMethod === 'TWR' ? result.twrAnnualized : null;
  const isPositive = displayValue != null && displayValue >= 0;

  return (
    <Stack sx={{ alignItems: { xs: 'flex-start', sm: 'flex-end' } }} spacing={1}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Box
          sx={{
            p: 1.5,
            borderRadius: 2,
            bgcolor: isPositive ? 'success.main' : 'error.main',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {isPositive ? (
            <TrendingUpIcon sx={{ color: 'white', fontSize: 28 }} />
          ) : (
            <TrendingDownIcon sx={{ color: 'white', fontSize: 28 }} />
          )}
        </Box>
        <Stack sx={{ alignItems: { sm: 'flex-end' } }}>
          <Typography variant="h4" sx={{ fontWeight: 800 }} color={isPositive ? 'success.main' : 'error.main'}>
            {formatPct(displayValue)}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {selectedPeriod === 'ALL'
              ? `${annualized != null ? `${formatPct(annualized)} annualized · ` : ''}All Time`
              : `${selectedPeriod} Period Return`}
            {periodGainLoss != null && ` (${periodGainLoss >= 0 ? '+' : ''}${formatMoney(periodGainLoss, result.currency)})`}
          </Typography>
        </Stack>
      </Stack>
      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
        <Chip
          label={result.returnMethod}
          size="small"
          variant="outlined"
          color="primary"
          sx={{ fontWeight: 600, fontSize: 11, height: 20 }}
        />
        <Chip
          label={selectedPeriod}
          size="small"
          color="primary"
          sx={{ fontWeight: 600, fontSize: 11, height: 20 }}
        />
      </Stack>
    </Stack>
  );
}

/**
 * PerformanceSummaryCard — displays TWR/MWR return with period switching,
 * income and cost breakdowns, and a CTA to the full Performance Detail page.
 */
export default function PerformanceSummaryCard({ portfolioId, currency }: Props) {
  const navigate = useNavigate();
  const [selectedPeriod, setSelectedPeriod] = useState<Period>('ALL');

  const { data, isLoading, isError, error } = usePerformance(portfolioId);
  const { data: historyData } = usePortfolioHistory(
    portfolioId,
    selectedPeriod !== 'ALL' ? { period: selectedPeriod } : undefined,
  );

  if (isLoading) {
    return (
      <Card variant="outlined" sx={{ borderRadius: 3 }}>
        <CardContent sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={28} />
        </CardContent>
      </Card>
    );
  }

  if (isError) {
    const err = error as { status?: number };
    if (err?.status === 501) {
      return (
        <Card variant="outlined" sx={{ borderRadius: 3 }}>
          <CardContent>
            <Typography variant="body2" color="text.secondary">
              XIRR performance calculation is not yet available. Change the portfolio return method to TWR or MWR to view analytics.
            </Typography>
          </CardContent>
        </Card>
      );
    }
    return null;
  }

  if (!data) return null;

  const curr = currency ?? data.currency;

  const periodReturnPct =
    selectedPeriod === 'ALL'
      ? data.returnMethod === 'TWR'
        ? data.twrReturn
        : data.mwrReturn
      : historyData?.summary?.portfolioReturnPercentage != null
        ? historyData.summary.portfolioReturnPercentage / 100
        : null;

  const periodGainLoss =
    selectedPeriod !== 'ALL' ? historyData?.summary?.totalGainLoss : null;

  return (
    <Card
      variant="outlined"
      sx={{
        borderRadius: 3,
        background: 'linear-gradient(135deg, rgba(99,102,241,0.04) 0%, rgba(139,92,246,0.04) 100%)',
      }}
    >
      <CardContent sx={{ p: 3 }}>
        <Stack spacing={3}>
          {/* Header & Controls */}
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2 }}
          >
            <Box>
              <Typography variant="overline" color="text.secondary" sx={{ fontWeight: 700, letterSpacing: 1.2 }}>
                Portfolio Performance
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Valuation basis: {data.valuationBasis.replace('_', ' ')}
              </Typography>
              {/* Period Selector Chips */}
              <Stack direction="row" spacing={0.75} sx={{ mt: 1.5, flexWrap: 'wrap', gap: 0.5 }}>
                {PERIODS.map((period) => {
                  const isSelected = selectedPeriod === period;
                  return (
                    <Chip
                      key={period}
                      label={period}
                      size="small"
                      clickable
                      variant={isSelected ? 'filled' : 'outlined'}
                      color={isSelected ? 'primary' : 'default'}
                      onClick={() => setSelectedPeriod(period)}
                      sx={{ fontWeight: 600, fontSize: '0.75rem', height: 26 }}
                    />
                  );
                })}
              </Stack>
            </Box>

            <Stack direction="column" sx={{ alignItems: { xs: 'flex-start', md: 'flex-end' }, gap: 1.5 }}>
              <ReturnBadge
                result={data}
                selectedPeriod={selectedPeriod}
                periodReturnPct={periodReturnPct}
                periodGainLoss={periodGainLoss}
              />
              <Button
                variant="outlined"
                size="small"
                endIcon={<ArrowForwardIcon fontSize="small" />}
                onClick={() => navigate(`/portfolios/${portfolioId}/performance`)}
              >
                View Performance Detail
              </Button>
            </Stack>
          </Stack>

          <Divider />

          {/* Key metrics grid */}
          <Grid container spacing={3}>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <Metric
                label="Net Deposits"
                value={formatMoney(data.totalNetDeposits ?? 0, curr)}
                tooltip="Total cumulative external cash deposits minus withdrawals"
              />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <Metric
                label="Cost Basis"
                value={formatMoney(data.totalCostBasis, curr)}
                tooltip="Sum of open lot acquisition costs"
              />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <Metric
                label="Realized Gain/Loss"
                value={formatMoney(data.totalRealizedGainLoss, curr)}
                positive={data.totalRealizedGainLoss > 0 ? true : data.totalRealizedGainLoss < 0 ? false : null}
              />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <Metric
                label="Net Income"
                value={formatMoney(data.totalNetIncome, curr)}
                positive={data.totalNetIncome > 0 ? true : data.totalNetIncome < 0 ? false : null}
              />
            </Grid>
            <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
              <Metric
                label="Dividends"
                value={formatMoney(data.totalDividendIncome, curr)}
                positive={data.totalDividendIncome > 0 ? true : null}
              />
            </Grid>
          </Grid>

          <Divider />

          {/* Income / cost breakdown */}
          <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap' }}>
            <Metric label="Interest" value={formatMoney(data.totalInterestIncome, curr)} />
            <Metric label="Fees" value={formatMoney(data.totalFees, curr)} positive={data.totalFees === 0 ? null : false} />
            <Metric label="Taxes" value={formatMoney(data.totalTaxes, curr)} positive={data.totalTaxes === 0 ? null : false} />
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

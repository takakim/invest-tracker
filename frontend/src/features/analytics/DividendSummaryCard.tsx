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
import PaidOutlinedIcon from '@mui/icons-material/PaidOutlined';
import PercentOutlinedIcon from '@mui/icons-material/PercentOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { useDividendAnalytics } from './useAnalytics';

interface DividendSummaryCardProps {
  portfolioId: string;
  currency: string;
}

export function DividendSummaryCard({ portfolioId, currency }: DividendSummaryCardProps) {
  const navigate = useNavigate();
  const { data: analytics, isLoading, error } = useDividendAnalytics(portfolioId);

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
  const fmtPct = (v: number | string | undefined | null) => `${Number(v ?? 0).toFixed(2)}%`;

  const calendar = analytics.projectedMonthlyCalendar ?? analytics.projectedCalendar ?? [];
  const maxBar = Math.max(...calendar.map((m) => Number(m.projectedAmount || 0)), 1);

  return (
    <Card
      variant="outlined"
      sx={{ borderRadius: 2 }}
      data-testid="dividend-summary-card"
    >
      <CardContent>
        <Stack spacing={2.5}>
          {/* Header row */}
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}
          >
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <PaidOutlinedIcon color="primary" sx={{ fontSize: 24 }} />
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                  Dividend Income
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Ledger-derived yield · {currency}
                </Typography>
              </Box>
            </Stack>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
              <Tooltip title="Annualised yield based on current market price and TTM DPS">
                <Chip
                  icon={<PercentOutlinedIcon />}
                  label={`Yield ${fmtPct(analytics.portfolioDividendYieldPercentage)}`}
                  color="primary"
                  variant="outlined"
                  size="small"
                  sx={{ fontWeight: 600 }}
                />
              </Tooltip>
              <Tooltip title="Yield on original cost basis">
                <Chip
                  icon={<TrendingUpOutlinedIcon />}
                  label={`YOC ${fmtPct(analytics.portfolioYieldOnCostPercentage)}`}
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
            direction="row"
            sx={{ flexWrap: 'wrap', gap: 2 }}
          >
            <Box>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                Projected Annual
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, color: 'success.main', lineHeight: 1.2 }}>
                {fmt(analytics.projectedAnnualDividendIncome)}&nbsp;
                <Typography component="span" variant="caption" color="text.secondary">{currency}</Typography>
              </Typography>
            </Box>
            <Box sx={{ borderLeft: '1px solid', borderColor: 'divider', pl: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                YTD
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                {fmt(analytics.totalDividendsYtd)}&nbsp;
                <Typography component="span" variant="caption" color="text.secondary">{currency}</Typography>
              </Typography>
            </Box>
            <Box sx={{ borderLeft: '1px solid', borderColor: 'divider', pl: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                TTM
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                {fmt(analytics.totalDividendsTtm)}&nbsp;
                <Typography component="span" variant="caption" color="text.secondary">{currency}</Typography>
              </Typography>
            </Box>
            <Box sx={{ borderLeft: '1px solid', borderColor: 'divider', pl: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                All-Time
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                {fmt(analytics.totalDividendsAllTime)}&nbsp;
                <Typography component="span" variant="caption" color="text.secondary">{currency}</Typography>
              </Typography>
            </Box>
          </Stack>

          {/* 12-month spark bars */}
          {calendar.length > 0 && (
            <Box>
              <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
                12-month forward calendar
              </Typography>
              <Stack direction="row" spacing={0.5} sx={{ alignItems: 'flex-end', height: 40 }}>
                {calendar.map((m) => {
                  const amt = Number(m.projectedAmount || 0);
                  const pct = (amt / maxBar) * 100;
                  return (
                    <Tooltip
                      key={m.month}
                      title={`${m.monthName}: ${fmt(amt)} ${currency}`}
                      arrow
                    >
                      <Box
                        sx={{
                          flex: 1,
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          gap: 0.25,
                          cursor: 'default',
                        }}
                      >
                        <Box
                          sx={{
                            width: '100%',
                            height: `${Math.max(pct, amt > 0 ? 6 : 2)}%`,
                            minHeight: amt > 0 ? 4 : 2,
                            maxHeight: 32,
                            borderRadius: 0.5,
                            bgcolor: amt > 0 ? 'success.main' : 'action.hover',
                            opacity: amt > 0 ? 0.85 : 0.4,
                            transition: 'opacity 0.2s',
                            '&:hover': { opacity: 1 },
                          }}
                        />
                        <Typography sx={{ fontSize: '0.55rem', color: 'text.secondary', lineHeight: 1 }}>
                          {m.monthName.slice(0, 1)}
                        </Typography>
                      </Box>
                    </Tooltip>
                  );
                })}
              </Stack>
            </Box>
          )}

          {/* CTA */}
          <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button
              size="small"
              variant="text"
              endIcon={<ArrowForwardIcon fontSize="small" />}
              onClick={() => navigate(`/portfolios/${portfolioId}/dividends`)}
              id="btn-view-dividend-details"
            >
              View Dividend Details & Projections
            </Button>
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

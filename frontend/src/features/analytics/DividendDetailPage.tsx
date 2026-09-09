import React, { useMemo, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import {
  Box,
  Breadcrumbs,
  Button,
  Chip,
  Divider,
  Grid,
  Link,
  Paper,
  Skeleton,
  Slider,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';

import PaidOutlinedIcon from '@mui/icons-material/PaidOutlined';
import PercentOutlinedIcon from '@mui/icons-material/PercentOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined';
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined';
import AutoGraphOutlinedIcon from '@mui/icons-material/AutoGraphOutlined';
import LightbulbOutlinedIcon from '@mui/icons-material/LightbulbOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import StarOutlinedIcon from '@mui/icons-material/StarOutlined';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

import { usePortfolio } from '../portfolios/usePortfolios';
import { useDividendAnalytics } from './useAnalytics';
import type { HoldingDividendMetric, YearlyDividendHistory } from '../../types';

// ─── helpers ──────────────────────────────────────────────────────────────────
const fmt = (v: number | undefined | null, dp = 2) =>
  Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });
const fmtPct = (v: number | undefined | null) => `${Number(v ?? 0).toFixed(2)}%`;

// ─── DRIP projection engine ───────────────────────────────────────────────────
interface ProjectionYear {
  year: number;
  dividendIncome: number;
  cumulativeDRIP: number;
  cumulativeCash: number;
  portfolioValueDRIP: number;
}

function computeProjection(
  currentPortfolioValue: number,
  projectedAnnualIncome: number,
  dgrPct: number,        // dividend growth rate %
  reinvestPct: number,   // reinvestment percentage 0-100
  years = 10,
): ProjectionYear[] {
  const dgr = dgrPct / 100;
  const rp = reinvestPct / 100;

  // Derive the base dividend yield from current portfolio state.
  // When reinvestment % > 0 the reinvested dividends grow the portfolio value,
  // which then generates additional yield — compounding income over time.
  // DGR is applied to the yield rate each year (dividend growth outpaces price).
  const baseYield = currentPortfolioValue > 0 ? projectedAnnualIncome / currentPortfolioValue : 0;

  let portfolioValueDRIP = currentPortfolioValue > 0 ? currentPortfolioValue : 1;
  let currentYield = baseYield;
  let cumulativeDRIP = 0;
  let cumulativeCash = 0;

  // Fallback: if no portfolio value can be estimated, grow income by DGR alone
  // (reinvestment has no compounding effect in this case)
  const useFallback = currentPortfolioValue <= 0;
  let fallbackIncome = projectedAnnualIncome;

  const rows: ProjectionYear[] = [];
  for (let y = 1; y <= years; y++) {
    let annualIncome: number;

    if (useFallback) {
      // Simple DGR-only model — reinvest % has no compounding effect
      if (y > 1) fallbackIncome = fallbackIncome * (1 + dgr);
      annualIncome = fallbackIncome;
    } else {
      // Full DRIP model: income = yield_rate × portfolio_value
      // DGR grows the yield rate; reinvestment grows the portfolio value.
      if (y > 1) currentYield = currentYield * (1 + dgr);
      annualIncome = portfolioValueDRIP * currentYield;
    }

    const reinvested = annualIncome * rp;
    const cashTaken = annualIncome * (1 - rp);
    portfolioValueDRIP = portfolioValueDRIP + reinvested;
    cumulativeDRIP += annualIncome;
    cumulativeCash += cashTaken;

    rows.push({
      year: y,
      dividendIncome: annualIncome,
      cumulativeDRIP,
      cumulativeCash,
      portfolioValueDRIP,
    });
  }
  return rows;
}

// ─── tiny SVG chart ───────────────────────────────────────────────────────────
function ProjectionChart({
  rows,
  currency,
}: {
  rows: ProjectionYear[];
  currency: string;
}) {
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  const W = 680, H = 220;
  const pad = { top: 16, right: 20, bottom: 36, left: 60 };
  const cw = W - pad.left - pad.right;
  const ch = H - pad.top - pad.bottom;

  const incomeValues = rows.map((r) => r.dividendIncome);
  const maxIncome = Math.max(...incomeValues, 1);

  const points = rows.map((r, i) => ({
    x: pad.left + (i / Math.max(1, rows.length - 1)) * cw,
    y: pad.top + ch - (r.dividendIncome / maxIncome) * ch,
    income: r.dividendIncome,
    year: r.year,
  }));

  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
  const areaPath = `${linePath} L ${points[points.length - 1].x.toFixed(1)} ${(pad.top + ch).toFixed(1)} L ${points[0].x.toFixed(1)} ${(pad.top + ch).toFixed(1)} Z`;

  const yTicks = 4;
  const areaId = 'drip-area-gradient';

  return (
    <Box sx={{ overflowX: 'auto' }}>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', maxWidth: W, display: 'block' }}>
        <defs>
          <linearGradient id={areaId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#4caf50" stopOpacity="0.25" />
            <stop offset="100%" stopColor="#4caf50" stopOpacity="0.02" />
          </linearGradient>
        </defs>
        {/* grid */}
        {Array.from({ length: yTicks + 1 }, (_, i) => {
          const y = pad.top + (i / yTicks) * ch;
          const val = maxIncome * (1 - i / yTicks);
          return (
            <g key={i}>
              <line x1={pad.left} y1={y} x2={pad.left + cw} y2={y} stroke="#e0e0e0" strokeDasharray="3 3" />
              <text x={pad.left - 6} y={y + 4} textAnchor="end" fontSize={9} fill="#9e9e9e">
                {fmt(val, 0)}
              </text>
            </g>
          );
        })}
        {/* X axis labels */}
        {points.map((p) => (
          <text key={p.year} x={p.x} y={pad.top + ch + 18} textAnchor="middle" fontSize={10} fill="#757575">
            Yr {p.year}
          </text>
        ))}
        {/* area */}
        <path d={areaPath} fill={`url(#${areaId})`} />
        {/* line */}
        <path d={linePath} fill="none" stroke="#4caf50" strokeWidth={2.5} strokeLinejoin="round" />
        {/* dots + hover */}
        {points.map((p, i) => (
          <circle
            key={p.year}
            cx={p.x}
            cy={p.y}
            r={hoverIdx === i ? 6 : 4}
            fill={hoverIdx === i ? '#2e7d32' : '#4caf50'}
            stroke="white"
            strokeWidth={1.5}
            style={{ cursor: 'pointer', transition: 'r 0.15s' }}
            onMouseEnter={() => setHoverIdx(i)}
            onMouseLeave={() => setHoverIdx(null)}
          />
        ))}
        {/* tooltip bubble */}
        {hoverIdx !== null && (() => {
          const p = points[hoverIdx];
          const tx = Math.min(p.x + 10, W - 130);
          return (
            <g>
              <rect x={tx} y={p.y - 32} width={120} height={30} rx={4} fill="#263238" opacity={0.92} />
              <text x={tx + 8} y={p.y - 20} fontSize={10} fill="#e8f5e9">
                Year {p.year}
              </text>
              <text x={tx + 8} y={p.y - 8} fontSize={10} fill="#a5d6a7">
                {fmt(p.income)} {currency}
              </text>
            </g>
          );
        })()}
      </svg>
    </Box>
  );
}

// ─── yearly bar chart ─────────────────────────────────────────────────────────
function YearlyBarChart({
  rows,
  currency,
}: {
  rows: YearlyDividendHistory[];
  currency: string;
}) {
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  const W = 680, H = 200;
  const pad = { top: 16, right: 20, bottom: 36, left: 60 };
  const cw = W - pad.left - pad.right;
  const ch = H - pad.top - pad.bottom;

  const maxVal = Math.max(...rows.map((r) => r.netAmount), 1);
  const barW = rows.length > 0 ? (cw / rows.length) * 0.6 : 20;
  const gap = rows.length > 0 ? cw / rows.length : 40;

  const yTicks = 4;

  if (rows.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
        No historical dividend data yet.
      </Typography>
    );
  }

  return (
    <Box sx={{ overflowX: 'auto' }}>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', maxWidth: W, display: 'block' }}>
        {/* grid */}
        {Array.from({ length: yTicks + 1 }, (_, i) => {
          const y = pad.top + (i / yTicks) * ch;
          const val = maxVal * (1 - i / yTicks);
          return (
            <g key={i}>
              <line x1={pad.left} y1={y} x2={pad.left + cw} y2={y} stroke="#e0e0e0" strokeDasharray="3 3" />
              <text x={pad.left - 6} y={y + 4} textAnchor="end" fontSize={9} fill="#9e9e9e">
                {fmt(val, 0)}
              </text>
            </g>
          );
        })}
        {rows.map((row, i) => {
          const barH = (row.netAmount / maxVal) * ch;
          const x = pad.left + i * gap + (gap - barW) / 2;
          const y = pad.top + ch - barH;
          const isHov = hoverIdx === i;
          return (
            <g
              key={row.year}
              onMouseEnter={() => setHoverIdx(i)}
              onMouseLeave={() => setHoverIdx(null)}
              style={{ cursor: 'default' }}
            >
              <rect
                x={x}
                y={y}
                width={barW}
                height={barH}
                rx={3}
                fill={isHov ? '#2e7d32' : '#4caf50'}
                opacity={isHov ? 1 : 0.8}
              />
              <text x={x + barW / 2} y={pad.top + ch + 18} textAnchor="middle" fontSize={10} fill="#757575">
                {row.year}
              </text>
              {isHov && (
                <g>
                  <rect x={x - 10} y={y - 36} width={barW + 20} height={28} rx={4} fill="#263238" opacity={0.92} />
                  <text x={x + barW / 2} y={y - 22} textAnchor="middle" fontSize={9} fill="#e8f5e9">
                    {row.year}
                  </text>
                  <text x={x + barW / 2} y={y - 10} textAnchor="middle" fontSize={9} fill="#a5d6a7">
                    {fmt(row.netAmount)}
                  </text>
                </g>
              )}
            </g>
          );
        })}
      </svg>
    </Box>
  );
}

// ─── strategy insight cards ───────────────────────────────────────────────────
function InsightCard({
  icon,
  title,
  body,
  severity = 'info',
}: {
  icon: React.ReactNode;
  title: string;
  body: React.ReactNode;
  severity?: 'info' | 'success' | 'warning';
}) {
  const bgMap = {
    info: (dark: boolean) => (dark ? 'rgba(25,118,210,0.08)' : '#e3f2fd'),
    success: (dark: boolean) => (dark ? 'rgba(76,175,80,0.08)' : '#f1f8f3'),
    warning: (dark: boolean) => (dark ? 'rgba(237,108,2,0.08)' : '#fff3e0'),
  };
  const borderMap = {
    info: 'rgba(25,118,210,0.3)',
    success: 'rgba(76,175,80,0.3)',
    warning: 'rgba(237,108,2,0.3)',
  };
  const colorMap = { info: 'primary.main', success: 'success.main', warning: 'warning.main' };

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        borderRadius: 2,
        bgcolor: (theme) => bgMap[severity](theme.palette.mode === 'dark'),
        border: `1px solid ${borderMap[severity]}`,
      }}
    >
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
        <Box sx={{ color: colorMap[severity], mt: 0.25 }}>{icon}</Box>
        <Box>
          <Typography variant="body2" sx={{ fontWeight: 700, mb: 0.5 }}>
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.5 }}>
            {body}
          </Typography>
        </Box>
      </Stack>
    </Paper>
  );
}

function buildInsights(
  holdings: HoldingDividendMetric[],
  projectedCalendar: { month: number; monthName: string; projectedAmount: number }[],
  currency: string,
) {
  const insights: React.ReactNode[] = [];

  // Top yielder
  const sorted = [...holdings].sort((a, b) => b.currentYieldPercentage - a.currentYieldPercentage);
  if (sorted.length > 0) {
    const top = sorted[0];
    insights.push(
      <InsightCard
        key="top-yield"
        icon={<StarOutlinedIcon />}
        title="Top Yielding Holding"
        severity="success"
        body={`${top.ticker ?? top.instrumentName} has the highest current yield at ${fmtPct(top.currentYieldPercentage)}. Consider adding to this position if fundamentals remain sound.`}
      />,
    );
  }

  // YOC leader (compounding working)
  const yocLeaders = [...holdings]
    .filter((h) => h.yieldOnCostPercentage > h.currentYieldPercentage)
    .sort((a, b) => b.yieldOnCostPercentage - a.yieldOnCostPercentage);
  if (yocLeaders.length > 0) {
    const top = yocLeaders[0];
    insights.push(
      <InsightCard
        key="yoc"
        icon={<CheckCircleOutlinedIcon />}
        title="Compounding Working for You"
        severity="success"
        body={`${top.ticker ?? top.instrumentName} yields ${fmtPct(top.yieldOnCostPercentage)} on your cost basis vs ${fmtPct(top.currentYieldPercentage)} current yield — your patience is being rewarded.`}
      />,
    );
  }

  // Income concentration risk
  const totalProjected = holdings.reduce((s, h) => s + h.projectedAnnualIncome, 0);
  if (holdings.length >= 2 && totalProjected > 0) {
    const top3 = [...holdings]
      .sort((a, b) => b.projectedAnnualIncome - a.projectedAnnualIncome)
      .slice(0, 3);
    const top3Sum = top3.reduce((s, h) => s + h.projectedAnnualIncome, 0);
    const top3Pct = (top3Sum / totalProjected) * 100;
    if (top3Pct > 60) {
      insights.push(
        <InsightCard
          key="concentration"
          icon={<WarningAmberOutlinedIcon />}
          title="Income Concentration Risk"
          severity="warning"
          body={`Your top 3 holdings (${top3.map((h) => h.ticker ?? h.instrumentName).join(', ')}) represent ${top3Pct.toFixed(0)}% of projected income. Consider diversifying dividend sources to reduce single-stock risk.`}
        />,
      );
    }
  }

  // Calendar gap analysis
  const gapMonths = projectedCalendar
    .filter((m) => Number(m.projectedAmount || 0) === 0)
    .map((m) => m.monthName);
  if (gapMonths.length > 0 && holdings.length > 0) {
    insights.push(
      <InsightCard
        key="gaps"
        icon={<CalendarMonthOutlinedIcon />}
        title="Income Calendar Gaps"
        severity="info"
        body={`You have no projected income in: ${gapMonths.join(', ')}. Adding holdings that pay in these months would create a smoother monthly income stream.`}
      />,
    );
  }

  // No income at all
  if (holdings.length === 0) {
    insights.push(
      <InsightCard
        key="none"
        icon={<LightbulbOutlinedIcon />}
        title="No Dividend Holdings Yet"
        severity="info"
        body={`Record dividend transactions or import broker CSV files to start tracking and projecting your dividend income here.`}
      />,
    );
  }

  return insights;
}

// ─── page ─────────────────────────────────────────────────────────────────────
export function DividendDetailPage() {
  const { id: portfolioId = '' } = useParams();
  const { data: portfolio } = usePortfolio(portfolioId);
  const { data: analytics, isLoading } = useDividendAnalytics(portfolioId);

  const currency = portfolio?.baseCurrency ?? '';

  // DRIP projection state
  const [dgr, setDgr] = useState(3);           // dividend growth rate %
  const [reinvest, setReinvest] = useState(100); // reinvestment %
  const [monthlyTarget, setMonthlyTarget] = useState('');
  const [historyTab, setHistoryTab] = useState<'yearly' | 'monthly'>('yearly');
  const [sortField, setSortField] = useState<keyof HoldingDividendMetric>('projectedAnnualIncome');
  const [sortAsc, setSortAsc] = useState(false);
  const [showAllHoldings, setShowAllHoldings] = useState(false);

  const projectedAnnual = Number(analytics?.projectedAnnualDividendIncome ?? 0);
  // Estimate current portfolio value from analytics or fallback to 0
  const currentPortfolioValue = useMemo(() => {
    if (!analytics?.holdings) return 0;
    // sum of (projectedAnnualIncome / (currentYieldPct/100)) for holdings with yield
    const total = analytics.holdings.reduce((sum, h) => {
      const yld = Number(h.currentYieldPercentage ?? 0) / 100;
      if (yld > 0 && h.projectedAnnualIncome > 0) {
        return sum + Number(h.projectedAnnualIncome) / yld;
      }
      return sum;
    }, 0);
    return total;
  }, [analytics]);

  const projectionRows = useMemo(
    () =>
      projectedAnnual > 0
        ? computeProjection(currentPortfolioValue, projectedAnnual, dgr, reinvest)
        : [],
    [currentPortfolioValue, projectedAnnual, dgr, reinvest],
  );

  // When dividends will cover the monthly target
  const monthlyTargetNum = parseFloat(monthlyTarget);
  const coverageYear = useMemo(() => {
    if (!monthlyTargetNum || monthlyTargetNum <= 0) return null;
    const annual = monthlyTargetNum * 12;
    const found = projectionRows.find((r) => r.dividendIncome >= annual);
    return found ? found.year : null;
  }, [projectionRows, monthlyTargetNum]);

  const holdings = analytics?.holdings ?? [];
  // Filter to only dividend-paying holdings unless 'show all' is toggled on
  const filteredHoldings = useMemo(
    () => showAllHoldings ? holdings : holdings.filter((h) => Number(h.totalReceivedAllTime ?? 0) > 0),
    [holdings, showAllHoldings],
  );
  const sortedHoldings = useMemo(() => {
    return [...filteredHoldings].sort((a, b) => {
      const av = Number(a[sortField] ?? 0);
      const bv = Number(b[sortField] ?? 0);
      return sortAsc ? av - bv : bv - av;
    });
  }, [filteredHoldings, sortField, sortAsc]);

  const calendar = analytics?.projectedMonthlyCalendar ?? analytics?.projectedCalendar ?? [];
  const insights = useMemo(
    () => buildInsights(holdings, calendar, currency),
    [holdings, calendar, currency],
  );

  const handleSort = (field: keyof HoldingDividendMetric) => {
    if (sortField === field) setSortAsc((v) => !v);
    else { setSortField(field); setSortAsc(false); }
  };

  // ── loading ──
  if (isLoading || !portfolio) {
    return (
      <Box>
        <Skeleton variant="text" width="30%" height={32} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={200} sx={{ borderRadius: 2 }} />
      </Box>
    );
  }

  return (
    <Box>
      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 3 }} aria-label="breadcrumb">
        <Link component={RouterLink} underline="hover" color="inherit" to="/portfolios">
          Portfolios
        </Link>
        <Link component={RouterLink} underline="hover" color="inherit" to={`/portfolios/${portfolioId}`}>
          {portfolio.name}
        </Link>
        <Typography color="text.primary" sx={{ fontWeight: 600 }}>
          Dividend Analytics
        </Typography>
      </Breadcrumbs>

      {/* Back + header */}
      <Stack direction="row" sx={{ alignItems: 'center', mb: 3, gap: 2 }}>
        <Button
          component={RouterLink}
          to={`/portfolios/${portfolioId}`}
          startIcon={<ArrowBackIcon />}
          size="small"
          variant="outlined"
          id="btn-back-to-portfolio"
        >
          Back
        </Button>
        <Box>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <PaidOutlinedIcon color="primary" sx={{ fontSize: 28 }} />
            <Typography variant="h5" sx={{ fontWeight: 700 }}>
              Dividend Analytics
            </Typography>
            <Chip label={currency} size="small" color="primary" variant="outlined" />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            {portfolio.name} · Ledger-derived income, projections & strategy insights
          </Typography>
        </Box>
      </Stack>

      {/* ── KPI header strip ── */}
      <Grid container spacing={2} sx={{ mb: 4 }}>
        {[
          { label: 'Projected Annual', value: fmt(projectedAnnual), icon: <SavingsOutlinedIcon />, color: 'success.main' },
          { label: 'Current Yield', value: fmtPct(analytics?.portfolioDividendYieldPercentage), icon: <PercentOutlinedIcon />, color: 'primary.main' },
          { label: 'Yield on Cost', value: fmtPct(analytics?.portfolioYieldOnCostPercentage), icon: <TrendingUpOutlinedIcon />, color: 'success.main' },
          { label: 'All-Time Received', value: fmt(analytics?.totalDividendsAllTime), icon: <AccountBalanceWalletOutlinedIcon />, color: 'text.primary' },
          { label: 'YTD', value: fmt(analytics?.totalDividendsYtd), icon: <CalendarMonthOutlinedIcon />, color: 'info.main' },
          { label: 'TTM', value: fmt(analytics?.totalDividendsTtm), icon: <CalendarMonthOutlinedIcon />, color: 'info.main' },
        ].map(({ label, value, icon, color }) => (
          <Grid key={label} size={{ xs: 6, sm: 4, md: 2 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, height: '100%' }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
                <Box sx={{ color, fontSize: 18, display: 'flex' }}>{icon}</Box>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  {label}
                </Typography>
              </Stack>
              <Typography variant="h6" sx={{ fontWeight: 700, color }}>
                {value}
              </Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      {/* ── Section 1: Yearly & Monthly history ── */}
      <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mb: 3 }}>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 2, gap: 1 }}>
          <AutoGraphOutlinedIcon color="primary" />
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            Historical Dividend Income
          </Typography>
        </Stack>
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
          <Tabs value={historyTab} onChange={(_, v) => setHistoryTab(v)} textColor="primary" indicatorColor="primary">
            <Tab label="Yearly" value="yearly" sx={{ textTransform: 'none', fontWeight: 600 }} />
            <Tab label="Monthly" value="monthly" sx={{ textTransform: 'none', fontWeight: 600 }} />
          </Tabs>
        </Box>

        {historyTab === 'yearly' && (
          <>
            <YearlyBarChart rows={analytics?.yearlyHistory ?? []} currency={currency} />
            <Divider sx={{ my: 2 }} />
            <TableContainer>
              <Table size="small" aria-label="yearly dividend history">
                <TableHead sx={{ bgcolor: 'action.hover' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Year</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Net ({currency})</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Gross ({currency})</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Tax ({currency})</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(analytics?.yearlyHistory ?? []).map((row) => (
                    <TableRow key={row.year} hover>
                      <TableCell sx={{ fontWeight: 700 }}>{row.year}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700, color: 'success.main', fontFamily: 'monospace' }}>{fmt(row.netAmount)}</TableCell>
                      <TableCell align="right" sx={{ fontFamily: 'monospace' }}>{fmt(row.grossAmount)}</TableCell>
                      <TableCell align="right" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>{fmt(row.taxAmount)}</TableCell>
                    </TableRow>
                  ))}
                  {(analytics?.yearlyHistory ?? []).length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                        No yearly dividend data yet.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}

        {historyTab === 'monthly' && (
          <TableContainer>
            <Table size="small" aria-label="monthly dividend history">
              <TableHead sx={{ bgcolor: 'action.hover' }}>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Period</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Net ({currency})</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Gross ({currency})</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Tax ({currency})</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(analytics?.monthlyHistory ?? []).map((row) => (
                  <TableRow key={row.yearMonth} hover>
                    <TableCell sx={{ fontWeight: 600 }}>{row.yearMonth}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700, color: 'success.main', fontFamily: 'monospace' }}>{fmt(row.netAmount)}</TableCell>
                    <TableCell align="right" sx={{ fontFamily: 'monospace' }}>{fmt(row.grossAmount)}</TableCell>
                    <TableCell align="right" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>{fmt(row.taxAmount)}</TableCell>
                  </TableRow>
                ))}
                {(analytics?.monthlyHistory ?? []).length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                      No monthly dividend data yet.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {/* ── Section 2: 12-Month Forward Calendar ── */}
      <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mb: 3 }}>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 2, gap: 1 }}>
          <CalendarMonthOutlinedIcon color="primary" />
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            12-Month Forward Income Calendar
          </Typography>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Estimated monthly cashflows based on TTM dividend distribution schedules.
        </Typography>
        {calendar.length > 0 ? (
          <Grid container spacing={1.5}>
            {calendar.map((m) => {
              const amt = Number(m.projectedAmount || 0);
              const maxAmt = Math.max(...calendar.map((x) => Number(x.projectedAmount || 0)), 1);
              const barPct = (amt / maxAmt) * 100;
              return (
                <Grid key={m.month} size={{ xs: 6, sm: 4, md: 2 }}>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: 1.5,
                      borderRadius: 2,
                      textAlign: 'center',
                      bgcolor: amt > 0
                        ? (theme) => theme.palette.mode === 'dark' ? 'rgba(76,175,80,0.07)' : '#f4faf5'
                        : 'background.default',
                      border: amt > 0 ? '1px solid rgba(76,175,80,0.4)' : undefined,
                    }}
                  >
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                      {m.monthName}
                    </Typography>
                    <Typography
                      variant="body1"
                      sx={{ fontWeight: 700, my: 0.5, color: amt > 0 ? 'success.main' : 'text.disabled' }}
                    >
                      {fmt(amt)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">{currency}</Typography>
                    <Box sx={{ mt: 1, height: 4, borderRadius: 2, bgcolor: 'action.hover', overflow: 'hidden' }}>
                      <Box sx={{ height: '100%', width: `${barPct}%`, bgcolor: 'success.main', borderRadius: 2 }} />
                    </Box>
                  </Paper>
                </Grid>
              );
            })}
          </Grid>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
            No projected calendar data available.
          </Typography>
        )}
      </Paper>

      {/* ── Section 3: Holdings Breakdown ── */}
      <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mb: 3 }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', mb: 2, gap: 1 }}
        >
          <Stack direction="row" sx={{ alignItems: 'center', gap: 1 }}>
            <AccountBalanceWalletOutlinedIcon color="primary" />
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              Holdings Income Breakdown
            </Typography>
            <Chip
              label={`${sortedHoldings.length}${!showAllHoldings && holdings.length > sortedHoldings.length ? ` of ${holdings.length}` : ''} holdings`}
              size="small"
              variant="outlined"
            />
          </Stack>
          <Stack direction="row" sx={{ alignItems: 'center', gap: 0.5 }}>
            <Typography variant="caption" color={showAllHoldings ? 'text.primary' : 'text.secondary'} sx={{ fontWeight: showAllHoldings ? 600 : 400 }}>
              {showAllHoldings ? 'Showing all holdings' : 'Dividend-paying only'}
            </Typography>
            <Tooltip title={showAllHoldings ? 'Show only holdings that have paid dividends' : 'Show all portfolio holdings including non-dividend ones'}>
              <Switch
                size="small"
                checked={showAllHoldings}
                onChange={(e) => setShowAllHoldings(e.target.checked)}
                slotProps={{ input: { 'aria-label': 'toggle all holdings' } }}
                id="switch-show-all-holdings"
              />
            </Tooltip>
          </Stack>
        </Stack>
        <TableContainer>
          <Table size="small" aria-label="holdings dividend breakdown">
            <TableHead sx={{ bgcolor: 'action.hover' }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 700 }}>Instrument</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Asset Class</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, cursor: 'pointer' }} onClick={() => handleSort('currentShares')}>
                  Shares {sortField === 'currentShares' ? (sortAsc ? '↑' : '↓') : ''}
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, cursor: 'pointer' }} onClick={() => handleSort('trailingTwelveMonthsDps')}>
                  TTM DPS {sortField === 'trailingTwelveMonthsDps' ? (sortAsc ? '↑' : '↓') : ''}
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, cursor: 'pointer' }} onClick={() => handleSort('projectedAnnualIncome')}>
                  Projected ({currency}) {sortField === 'projectedAnnualIncome' ? (sortAsc ? '↑' : '↓') : ''}
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, cursor: 'pointer' }} onClick={() => handleSort('currentYieldPercentage')}>
                  Yield % {sortField === 'currentYieldPercentage' ? (sortAsc ? '↑' : '↓') : ''}
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, cursor: 'pointer' }} onClick={() => handleSort('yieldOnCostPercentage')}>
                  YOC % {sortField === 'yieldOnCostPercentage' ? (sortAsc ? '↑' : '↓') : ''}
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, cursor: 'pointer' }} onClick={() => handleSort('totalReceivedAllTime')}>
                  All-Time ({currency}) {sortField === 'totalReceivedAllTime' ? (sortAsc ? '↑' : '↓') : ''}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedHoldings.map((h) => (
                <TableRow key={h.instrumentId} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{h.ticker ?? h.instrumentName}</Typography>
                    <Typography variant="caption" color="text.secondary">{h.instrumentName}</Typography>
                  </TableCell>
                  <TableCell><Chip label={h.assetClass} size="small" variant="outlined" /></TableCell>
                  <TableCell align="right">{Number(h.currentShares).toLocaleString(undefined, { maximumFractionDigits: 4 })}</TableCell>
                  <TableCell align="right" sx={{ fontFamily: 'monospace' }}>{fmt(h.trailingTwelveMonthsDps, 4)}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700, color: h.projectedAnnualIncome > 0 ? 'success.main' : 'text.primary', fontFamily: 'monospace' }}>
                    {fmt(h.projectedAnnualIncome)}
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600, color: 'primary.main' }}>{fmtPct(h.currentYieldPercentage)}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600, color: 'success.main' }}>{fmtPct(h.yieldOnCostPercentage)}</TableCell>
                  <TableCell align="right" sx={{ fontFamily: 'monospace' }}>{fmt(h.totalReceivedAllTime)}</TableCell>
                </TableRow>
              ))}
              {sortedHoldings.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                    {showAllHoldings
                      ? 'No holdings recorded yet.'
                      : 'No dividend-paying holdings yet. Toggle the switch above to see all holdings.'}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* ── Section 4: 10-Year DRIP Projection Engine ── */}
      <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mb: 3 }}>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 1, gap: 1 }}>
          <AutoGraphOutlinedIcon color="primary" />
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            10-Year Dividend Reinvestment Projection (DRIP)
          </Typography>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Adjust the sliders to model how reinvesting dividends can compound your income stream. Calculations are based on your current projected annual income of <strong>{fmt(projectedAnnual)} {currency}</strong>.
        </Typography>

        {projectedAnnual > 0 ? (
          <>
            {/* Controls */}
            <Grid container spacing={3} sx={{ mb: 3 }}>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>
                  Dividend Growth Rate (DGR): {dgr}%
                </Typography>
                <Slider
                  value={dgr}
                  onChange={(_, v) => setDgr(v as number)}
                  min={0}
                  max={15}
                  step={0.5}
                  marks={[{ value: 0, label: '0%' }, { value: 5, label: '5%' }, { value: 10, label: '10%' }, { value: 15, label: '15%' }]}
                  color="success"
                  size="small"
                  aria-label="Dividend growth rate slider"
                  id="slider-dgr"
                />
                <Typography variant="caption" color="text.secondary">
                  Historical dividend growth rate assumption
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>
                  Reinvestment %: {reinvest}%
                </Typography>
                <Slider
                  value={reinvest}
                  onChange={(_, v) => setReinvest(v as number)}
                  min={0}
                  max={100}
                  step={5}
                  marks={[{ value: 0, label: '0%' }, { value: 50, label: '50%' }, { value: 100, label: '100%' }]}
                  color="primary"
                  size="small"
                  aria-label="Reinvestment percentage slider"
                  id="slider-reinvest"
                />
                <Typography variant="caption" color="text.secondary">
                  {reinvest === 100 ? 'Full DRIP: all dividends reinvested' : reinvest === 0 ? 'Cash mode: all dividends taken as cash' : `Mixed: ${reinvest}% reinvested, ${100 - reinvest}% as cash`}
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>
                  Passive Income Target
                </Typography>
                <TextField
                  size="small"
                  label={`Monthly target (${currency})`}
                  type="number"
                  value={monthlyTarget}
                  onChange={(e) => setMonthlyTarget(e.target.value)}
                  slotProps={{ htmlInput: { min: 0 } }}
                  fullWidth
                  id="input-monthly-target"
                />
                {coverageYear !== null && (
                  <Typography variant="caption" sx={{ color: 'success.main', fontWeight: 600, mt: 0.5, display: 'block' }}>
                    ✓ Dividends cover {currency}{monthlyTarget}/month by Year {coverageYear}
                  </Typography>
                )}
                {monthlyTargetNum > 0 && coverageYear === null && (
                  <Typography variant="caption" sx={{ color: 'warning.main', fontWeight: 600, mt: 0.5, display: 'block' }}>
                    ⚠ Target not reached within 10 years at current rates
                  </Typography>
                )}
              </Grid>
            </Grid>

            {/* Chart */}
            <ProjectionChart rows={projectionRows} currency={currency} />

            <Divider sx={{ my: 2 }} />

            {/* Milestone table */}
            <TableContainer>
              <Table size="small" aria-label="10-year DRIP projection table">
                <TableHead sx={{ bgcolor: 'action.hover' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Year</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Annual Dividend ({currency})</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Cumulative Dividends ({currency})</TableCell>
                    {reinvest < 100 && <TableCell align="right" sx={{ fontWeight: 700 }}>Cumulative Cash Kept ({currency})</TableCell>}
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Est. Portfolio Value ({currency})</TableCell>
                    {monthlyTargetNum > 0 && <TableCell align="center" sx={{ fontWeight: 700 }}>Target Met?</TableCell>}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {projectionRows.map((r) => {
                    const targetAnnual = monthlyTargetNum * 12;
                    const metTarget = monthlyTargetNum > 0 && r.dividendIncome >= targetAnnual;
                    return (
                      <TableRow key={r.year} hover sx={metTarget ? { bgcolor: 'rgba(76,175,80,0.06)' } : {}}>
                        <TableCell sx={{ fontWeight: 700 }}>Year {r.year}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700, color: 'success.main', fontFamily: 'monospace' }}>
                          {fmt(r.dividendIncome)}
                        </TableCell>
                        <TableCell align="right" sx={{ fontFamily: 'monospace' }}>{fmt(r.cumulativeDRIP)}</TableCell>
                        {reinvest < 100 && (
                          <TableCell align="right" sx={{ fontFamily: 'monospace' }}>{fmt(r.cumulativeCash)}</TableCell>
                        )}
                        <TableCell align="right" sx={{ fontFamily: 'monospace' }}>{fmt(r.portfolioValueDRIP)}</TableCell>
                        {monthlyTargetNum > 0 && (
                          <TableCell align="center">
                            {metTarget ? (
                              <Chip label="✓ Met" size="small" color="success" />
                            ) : (
                              <Chip label="—" size="small" variant="outlined" />
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
            Record dividend transactions to enable the 10-year projection engine.
          </Typography>
        )}
      </Paper>

      {/* ── Section 5: Strategy Insights ── */}
      <Paper variant="outlined" sx={{ p: 3, borderRadius: 2, mb: 3 }}>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 2, gap: 1 }}>
          <LightbulbOutlinedIcon color="primary" />
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            Strategy Insights
          </Typography>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Observations based on your portfolio&apos;s dividend history and current holdings.
        </Typography>
        <Stack spacing={1.5}>
          {insights.length > 0 ? insights : (
            <InsightCard
              icon={<LightbulbOutlinedIcon />}
              title="No Insights Available"
              severity="info"
              body="Add dividend-bearing holdings and record transactions to see personalised strategy insights."
            />
          )}
        </Stack>
      </Paper>
    </Box>
  );
}

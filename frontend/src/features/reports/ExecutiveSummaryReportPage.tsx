import React, { useState } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  useTheme,
  alpha,
  Alert,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DownloadIcon from '@mui/icons-material/Download';
import PrintIcon from '@mui/icons-material/Print';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import AssessmentIcon from '@mui/icons-material/Assessment';

import { usePortfolio } from '../portfolios/usePortfolios';
import { usePortfolioAnalytics, useCashFlowAnalytics } from '../analytics/useAnalytics';
import { usePerformance } from '../performance/usePerformance';
import { useAccountsList } from '../accounts/useAccounts';
import { exportApi } from '../../api/export';
import { LoadingState, ErrorAlert } from '../../components';
import type { Account, AccountCashFlowSummary } from '../../types';

export function ExecutiveSummaryReportPage() {
  const { id: paramId, portfolioId: paramPortfolioId } = useParams<{ id?: string; portfolioId?: string }>();
  const portfolioId = paramPortfolioId || paramId || '';
  const theme = useTheme();

  const [downloadingPdf, setDownloadingPdf] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const { data: portfolio, isLoading: portfolioLoading, error: portfolioError } = usePortfolio(portfolioId);
  const { data: analytics, isLoading: analyticsLoading, error: analyticsError } = usePortfolioAnalytics(portfolioId);
  const { data: performance, isLoading: performanceLoading, error: performanceError } = usePerformance(portfolioId);
  const { data: cashFlow, isLoading: cashFlowLoading } = useCashFlowAnalytics(portfolioId, { period: 'ALL', groupBy: 'MONTH' });
  const { data: accounts, isLoading: accountsLoading } = useAccountsList(portfolioId);

  const isLoading = portfolioLoading || analyticsLoading || performanceLoading || accountsLoading;
  const hasError = portfolioError || analyticsError || performanceError;

  const handleDownloadPdf = async () => {
    setDownloadingPdf(true);
    setDownloadError(null);
    try {
      await exportApi.downloadExecutiveSummaryPdf(portfolioId);
    } catch (err: unknown) {
      const e = err as { message?: string };
      setDownloadError(e.message || 'Failed to download Executive Summary PDF');
    } finally {
      setDownloadingPdf(false);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  if (isLoading) {
    return (
      <Box sx={{ p: 4 }}>
        <LoadingState message="Compiling Executive Portfolio Audit Report..." />
      </Box>
    );
  }

  if (hasError) {
    return (
      <Box sx={{ p: 4 }}>
        <ErrorAlert error={portfolioError || analyticsError || performanceError} />
      </Box>
    );
  }

  const baseCurrency = portfolio?.baseCurrency || analytics?.baseCurrency || 'GBP';
  const formatMoney = (amount?: number | null, curr?: string | null) => {
    const validCurr = (curr && curr.trim().length === 3) ? curr.trim().toUpperCase() : baseCurrency;
    if (amount === undefined || amount === null) return `${validCurr} 0.00`;
    try {
      return new Intl.NumberFormat('en-GB', {
        style: 'currency',
        currency: validCurr,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(amount);
    } catch {
      return `${validCurr} ${Number(amount).toFixed(2)}`;
    }
  };

  const formatPercent = (pct?: number | null) => {
    if (pct === undefined || pct === null) return '0.00%';
    const sign = pct >= 0 ? '+' : '';
    return `${sign}${(pct * 100).toFixed(2)}%`;
  };

  const netInvested = performance?.totalNetDeposits ?? 0;
  const totalValue = analytics?.totalCurrentValue ?? 0;
  const totalGain = (analytics?.totalUnrealizedGainLoss ?? 0) + (performance?.totalRealizedGainLoss ?? 0);
  const twrPct = performance?.twrReturn ? (performance.twrReturn >= 0 ? `+${(performance.twrReturn * 100).toFixed(2)}%` : `${(performance.twrReturn * 100).toFixed(2)}%`) : '0.00%';
  const annTwrPct = performance?.twrAnnualized ? (performance.twrAnnualized >= 0 ? `+${(performance.twrAnnualized * 100).toFixed(2)}%` : `${(performance.twrAnnualized * 100).toFixed(2)}%`) : '0.00%';
  const cfs = cashFlow?.summary;

  return (
    <Box
      sx={{
        p: { xs: 2, sm: 3, md: 4 },
        maxWidth: 1200,
        margin: '0 auto',
        '@media print': {
          p: 1,
          maxWidth: '100%',
          backgroundColor: '#ffffff',
          color: '#000000',
        },
      }}
    >
      {/* Top Breadcrumb Navigation & Action Header (Hidden in Print) */}
      <Box sx={{ mb: 3, '@media print': { display: 'none' } }}>
        <Breadcrumbs sx={{ mb: 2 }}>
          <Link component={RouterLink} to="/portfolios" color="inherit" underline="hover">
            Portfolios
          </Link>
          <Link component={RouterLink} to={`/portfolios/${portfolioId}`} color="inherit" underline="hover">
            {portfolio?.name || 'Portfolio'}
          </Link>
          <Typography color="text.primary" sx={{ fontWeight: 600 }}>
            Executive Summary Report
          </Typography>
        </Breadcrumbs>

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' } }}>
          <Button
            component={RouterLink}
            to={`/portfolios/${portfolioId}`}
            startIcon={<ArrowBackIcon />}
            variant="outlined"
            size="small"
            sx={{ borderRadius: 2 }}
          >
            Back to Portfolio
          </Button>

          <Stack direction="row" spacing={1.5}>
            <Button
              variant="outlined"
              color="inherit"
              startIcon={<PrintIcon />}
              onClick={handlePrint}
              sx={{ borderRadius: 2 }}
            >
              Print / Save PDF
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={<DownloadIcon />}
              onClick={handleDownloadPdf}
              loading={downloadingPdf}
              sx={{ borderRadius: 2 }}
            >
              Download Official PDF
            </Button>
          </Stack>
        </Stack>

        {downloadError && (
          <Alert severity="error" sx={{ mt: 2, borderRadius: 2 }}>
            {downloadError}
          </Alert>
        )}
      </Box>

      {/* Printable Report Document Card */}
      <Paper
        elevation={0}
        sx={{
          p: { xs: 3, sm: 4 },
          borderRadius: 3,
          border: `1px solid ${theme.palette.divider}`,
          bgcolor: 'background.paper',
          '@media print': {
            p: 0,
            border: 'none',
            boxShadow: 'none',
          },
        }}
      >
        {/* Navy Header Banner Bar */}
        <Box sx={{ height: 4, bgcolor: '#0f172a', borderRadius: 1, mb: 2 }} />

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'flex-end' }, mb: 3 }}>
          <Box>
            <Typography variant="overline" sx={{ color: 'text.secondary', fontWeight: 700, letterSpacing: 1.5 }}>
              INVEST-TRACKER • EXECUTIVE SUMMARY REPORT
            </Typography>
            <Typography variant="h4" sx={{ fontWeight: 800, color: 'text.primary', mt: 0.5 }}>
              {portfolio?.name || 'Investment Portfolio'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Base Currency: <strong>{baseCurrency}</strong> • Cost Basis: <strong>{portfolio?.costBasisMethod || 'FIFO'}</strong> • Return Method: <strong>{portfolio?.returnMethod || 'TWR'}</strong>
            </Typography>
          </Box>
          <Box sx={{ textAlign: { xs: 'left', sm: 'right' } }}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              Valuation As-Of:
            </Typography>
            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              {analytics?.asOf ? new Date(analytics.asOf).toUTCString() : new Date().toUTCString()}
            </Typography>
          </Box>
        </Stack>

        <Divider sx={{ mb: 3 }} />

        {/* Section 1: KPI Metrics Overview */}
        <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
          Portfolio Performance & Wealth Metrics
        </Typography>

        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined" sx={{ bgcolor: alpha(theme.palette.primary.main, 0.03) }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  TOTAL PORTFOLIO VALUE
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 700, my: 0.5 }}>
                  {formatMoney(totalValue)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Cash Balance: {formatMoney(analytics?.totalCashValue ?? 0)}
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined" sx={{ bgcolor: alpha(theme.palette.info.main, 0.03) }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  NET CAPITAL INVESTED
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 700, my: 0.5 }}>
                  {formatMoney(netInvested)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Cumulative Net Contributions
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined" sx={{ bgcolor: alpha(theme.palette.success.main, 0.03) }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  TOTAL CUMULATIVE RETURN
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 700, my: 0.5, color: totalGain >= 0 ? 'success.main' : 'error.main' }}>
                  {totalGain >= 0 ? '+' : ''}{formatMoney(totalGain)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {twrPct} TWR ({annTwrPct} Annualized)
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined">
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  UNREALIZED GAIN / LOSS
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, my: 0.5, color: (analytics?.totalUnrealizedGainLoss ?? 0) >= 0 ? 'success.main' : 'error.main' }}>
                  {(analytics?.totalUnrealizedGainLoss ?? 0) >= 0 ? '+' : ''}{formatMoney(analytics?.totalUnrealizedGainLoss ?? 0)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {formatPercent(analytics?.totalUnrealizedReturnPercentage ?? 0)} on open positions
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined">
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  REALIZED CAPITAL GAIN
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, my: 0.5, color: (performance?.totalRealizedGainLoss ?? 0) >= 0 ? 'success.main' : 'error.main' }}>
                  {(performance?.totalRealizedGainLoss ?? 0) >= 0 ? '+' : ''}{formatMoney(performance?.totalRealizedGainLoss ?? 0)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Closed tax-lot disposals
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined">
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                  DIVIDENDS COLLECTED
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, my: 0.5 }}>
                  {formatMoney(performance?.totalDividendIncome ?? 0)}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Gross distributions received
                </Typography>
              </CardContent>
            </Card>
          </Grid>

          {cfs && (
            <>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Card variant="outlined">
                  <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      CAPITAL VS. MARKET SPLIT
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, my: 0.5 }}>
                      {cfs.capitalContributionsPercentage.toFixed(1)}% / {cfs.marketGrowthPercentage.toFixed(1)}%
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Invested Capital vs. Growth
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>

              <Grid size={{ xs: 12, sm: 4 }}>
                <Card variant="outlined">
                  <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      AVG. MONTHLY CONTRIBUTION
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, my: 0.5 }}>
                      {formatMoney(cfs.avgMonthlyContribution)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {cfs.activeContributionMonths} active contribution months
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>

              <Grid size={{ xs: 12, sm: 4 }}>
                <Card variant="outlined">
                  <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
                      NET INTERNAL INCOME
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 700, my: 0.5 }}>
                      {formatMoney(cfs.totalDividends + cfs.totalInterest - cfs.totalFees)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Dividends + Interest - Fees
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </>
          )}
        </Grid>

        <Divider sx={{ my: 3 }} />

        {/* Section 2: Asset Allocation & Currency Distribution */}
        <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
          Asset Allocation & Currency Distribution
        </Typography>

        <Grid container spacing={3} sx={{ mb: 3 }}>
          <Grid size={{ xs: 12, md: 6 }}>
            <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
              <Table size="small">
                <TableHead sx={{ bgcolor: alpha(theme.palette.primary.main, 0.05) }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Asset Class</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Market Value ({baseCurrency})</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Share</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {analytics?.byAssetClass?.map((item) => (
                    <TableRow key={item.category}>
                      <TableCell sx={{ fontWeight: 500 }}>{item.category}</TableCell>
                      <TableCell align="right">{formatMoney(item.marketValue, baseCurrency)}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>{(item.percentage * 100).toFixed(1)}%</TableCell>
                    </TableRow>
                  ))}
                  {(!analytics?.byAssetClass || analytics.byAssetClass.length === 0) && (
                    <TableRow>
                      <TableCell colSpan={3} align="center" sx={{ color: 'text.secondary', py: 2 }}>
                        No asset class data available
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
              <Table size="small">
                <TableHead sx={{ bgcolor: alpha(theme.palette.primary.main, 0.05) }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Currency Exposure</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Market Value ({baseCurrency})</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Share</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {analytics?.byCurrency?.map((item) => (
                    <TableRow key={item.category}>
                      <TableCell sx={{ fontWeight: 500 }}>{item.category}</TableCell>
                      <TableCell align="right">{formatMoney(item.marketValue, baseCurrency)}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>{(item.percentage * 100).toFixed(1)}%</TableCell>
                    </TableRow>
                  ))}
                  {(!analytics?.byCurrency || analytics.byCurrency.length === 0) && (
                    <TableRow>
                      <TableCell colSpan={3} align="center" sx={{ color: 'text.secondary', py: 2 }}>
                        No currency data available
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Grid>
        </Grid>

        <Divider sx={{ my: 3 }} />

        {/* Section 3: Active Holdings Matrix */}
        <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
          Active Holdings Matrix ({analytics?.topHoldings?.length ?? 0} Positions)
        </Typography>

        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2, mb: 3 }}>
          <Table size="small">
            <TableHead sx={{ bgcolor: alpha(theme.palette.primary.main, 0.05) }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 700 }}>Instrument</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Ticker</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Class</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>Quantity</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>Price</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>Market Value</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>Gain / Loss</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>Weight</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {analytics?.topHoldings?.map((h) => {
                const gain = h.unrealizedGainLoss ?? 0;
                return (
                  <TableRow key={h.instrumentId}>
                    <TableCell sx={{ fontWeight: 600 }}>{h.instrumentName}</TableCell>
                    <TableCell>{h.ticker || '—'}</TableCell>
                    <TableCell>
                      <Chip size="small" label={h.assetClass} variant="outlined" sx={{ fontSize: '0.7rem' }} />
                    </TableCell>
                    <TableCell align="right">{Number(h.quantity).toFixed(2)}</TableCell>
                    <TableCell align="right">{formatMoney(h.currentPrice)}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>{formatMoney(h.marketValue)}</TableCell>
                    <TableCell align="right" sx={{ color: gain >= 0 ? 'success.main' : 'error.main', fontWeight: 600 }}>
                      {gain >= 0 ? '+' : ''}{formatMoney(gain)}
                    </TableCell>
                    <TableCell align="right">{(h.weightPercentage * 100).toFixed(2)}%</TableCell>
                  </TableRow>
                );
              })}
              {(!analytics?.topHoldings || analytics.topHoldings.length === 0) && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ color: 'text.secondary', py: 3 }}>
                    No active positions held in this portfolio.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

        <Divider sx={{ my: 3 }} />

        {/* Section 4: Custodian & Broker Cash Accounts */}
        <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
          Custodian & Broker Cash Accounts
        </Typography>

        <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2, mb: 3 }}>
          <Table size="small">
            <TableHead sx={{ bgcolor: alpha(theme.palette.primary.main, 0.05) }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 700 }}>Account Name</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Broker / Custodian</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Currency</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Tax Treatment</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>Cash Balance</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {cashFlow?.accountBreakdown && cashFlow.accountBreakdown.length > 0 ? (
                cashFlow.accountBreakdown.map((acc: AccountCashFlowSummary) => {
                  const match = accounts?.find((a: Account) => a.id === acc.accountId);
                  return (
                    <TableRow key={acc.accountId}>
                      <TableCell sx={{ fontWeight: 600 }}>{acc.accountName}</TableCell>
                      <TableCell>{acc.brokerName}</TableCell>
                      <TableCell>{acc.accountCurrency}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={match?.taxTreatment || 'TAXABLE'}
                          variant="outlined"
                          sx={{ fontSize: '0.7rem' }}
                        />
                      </TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>
                        {formatMoney(acc.currentCashBalance, acc.accountCurrency)}
                      </TableCell>
                    </TableRow>
                  );
                })
              ) : accounts && accounts.length > 0 ? (
                accounts.map((acc: Account) => (
                  <TableRow key={acc.id}>
                    <TableCell sx={{ fontWeight: 600 }}>{acc.name}</TableCell>
                    <TableCell>{acc.brokerName}</TableCell>
                    <TableCell>{acc.accountCurrency}</TableCell>
                    <TableCell>
                      <Chip size="small" label={acc.taxTreatment} variant="outlined" sx={{ fontSize: '0.7rem' }} />
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>
                      {formatMoney(0, acc.accountCurrency)}
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ color: 'text.secondary', py: 3 }}>
                    No accounts registered in this portfolio.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {/* Document Footer */}
        <Box sx={{ mt: 4, pt: 2, borderTop: `1px solid ${theme.palette.divider}`, textAlign: 'center' }}>
          <Typography variant="caption" color="text.secondary">
            Generated by Invest-Tracker • Confidential Financial Record • Not Financial Advice
          </Typography>
        </Box>
      </Paper>
    </Box>
  );
}

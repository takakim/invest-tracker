import React, { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import {
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  Grid,
  InputLabel,
  LinearProgress,
  Link,
  MenuItem,
  Paper,
  Select,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
  useTheme,
  alpha,
  Alert,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SettingsIcon from '@mui/icons-material/Settings';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';

import { useTaxReport, useAvailableTaxYears, useUpdateTaxSettings } from './useTaxAllowances';
import { usePortfolio } from '../portfolios/usePortfolios';
import { LoadingState, ErrorAlert, EmptyState } from '../../components';
import type { TaxRegime, TaxSettingsRequest } from '../../types';

export function TaxAllowanceDetailPage() {
  const { portfolioId: paramPortfolioId, id: paramId } = useParams<{ portfolioId?: string; id?: string }>();
  const portfolioId = paramPortfolioId || paramId || '';
  const theme = useTheme();

  const [selectedRegime, setSelectedRegime] = useState<TaxRegime>('UK_HMRC');
  const [selectedYear, setSelectedYear] = useState<string>('');
  const [activeTab, setActiveTab] = useState<number>(0);

  // Settings Modal State
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [cgtAllowanceInput, setCgtAllowanceInput] = useState<string>('');
  const [divAllowanceInput, setDivAllowanceInput] = useState<string>('');
  const [lossCarryforwardInput, setLossCarryforwardInput] = useState<string>('');
  const [notesInput, setNotesInput] = useState<string>('');

  const { data: portfolio } = usePortfolio(portfolioId);
  const { data: availableYears, isLoading: yearsLoading } = useAvailableTaxYears(portfolioId);

  // Determine active tax year (default to current if not set by user)
  const currentDefaultYear =
    selectedRegime === 'UK_HMRC'
      ? availableYears?.currentUkTaxYear || '2024/25'
      : availableYears?.currentCalendarYear || String(new Date().getFullYear());

  const effectiveTaxYear = selectedYear || currentDefaultYear;

  const {
    data: report,
    isLoading: reportLoading,
    error: reportError,
    refetch,
  } = useTaxReport(portfolioId, effectiveTaxYear, selectedRegime);

  const updateSettingsMutation = useUpdateTaxSettings(portfolioId);

  const formatCurrency = (val?: number | null, currency = report?.baseCurrency || 'GBP') => {
    if (val === undefined || val === null) return '—';
    return new Intl.NumberFormat('en-GB', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(val);
  };

  const openSettingsDialog = () => {
    if (report) {
      setCgtAllowanceInput(String(report.capitalGains.annualExemptAmount ?? ''));
      setDivAllowanceInput(String(report.dividendIncome.annualDividendAllowance ?? ''));
      setLossCarryforwardInput(String(report.capitalGains.lossCarryforwardApplied ?? '0'));
      setNotesInput('');
    }
    setSettingsOpen(true);
  };

  const handleSaveSettings = async () => {
    const payload: TaxSettingsRequest = {
      taxYear: effectiveTaxYear,
      taxRegime: selectedRegime,
      cgtAllowance: cgtAllowanceInput ? parseFloat(cgtAllowanceInput) : null,
      dividendAllowance: divAllowanceInput ? parseFloat(divAllowanceInput) : null,
      lossCarryforward: lossCarryforwardInput ? parseFloat(lossCarryforwardInput) : 0,
      notes: notesInput.trim() || null,
    };
    await updateSettingsMutation.mutateAsync(payload);
    setSettingsOpen(false);
    refetch();
  };

  const getTaxTreatmentChip = (treatment?: string) => {
    switch (treatment) {
      case 'TAX_EXEMPT':
        return (
          <Chip
            size="small"
            label="ISA / Exempt"
            sx={{
              bgcolor: alpha(theme.palette.success.main, 0.12),
              color: theme.palette.success.main,
              fontWeight: 600,
            }}
          />
        );
      case 'TAX_DEFERRED':
        return (
          <Chip
            size="small"
            label="SIPP / Deferred"
            sx={{
              bgcolor: alpha(theme.palette.info.main, 0.12),
              color: theme.palette.info.main,
              fontWeight: 600,
            }}
          />
        );
      case 'TAXABLE':
      default:
        return (
          <Chip
            size="small"
            label="Taxable (GIA)"
            sx={{
              bgcolor: alpha(theme.palette.warning.main, 0.12),
              color: theme.palette.warning.main,
              fontWeight: 600,
            }}
          />
        );
    }
  };

  if (yearsLoading || reportLoading) {
    return (
      <Box sx={{ p: 4 }}>
        <LoadingState message="Calculating capital gains and dividend allowances..." />
      </Box>
    );
  }

  if (reportError) {
    return (
      <Box sx={{ p: 4 }}>
        <ErrorAlert error={reportError} />
      </Box>
    );
  }

  const cgt = report?.capitalGains;
  const div = report?.dividendIncome;
  const sheltered = report?.shelteredSummary;

  const cgtUsedPercent = cgt && cgt.annualExemptAmount > 0
    ? Math.min(100, (cgt.allowanceUsed / cgt.annualExemptAmount) * 100)
    : 0;

  const divUsedPercent = div && div.annualDividendAllowance > 0
    ? Math.min(100, (div.allowanceUsed / div.annualDividendAllowance) * 100)
    : 0;

  const yearOptions =
    selectedRegime === 'UK_HMRC'
      ? availableYears?.availableUkTaxYears || ['2024/25', '2023/24']
      : availableYears?.availableCalendarYears || [String(new Date().getFullYear())];

  return (
    <Box sx={{ p: { xs: 2, sm: 3, md: 4 }, maxWidth: 1400, margin: '0 auto' }}>
      {/* Breadcrumb Navigation */}
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/portfolios" color="inherit" underline="hover">
          Portfolios
        </Link>
        <Link
          component={RouterLink}
          to={`/portfolios/${portfolioId}`}
          color="inherit"
          underline="hover"
        >
          {portfolio?.name || 'Portfolio'}
        </Link>
        <Typography color="text.primary" sx={{ fontWeight: 600 }}>
          Tax Allowance Tracker
        </Typography>
      </Breadcrumbs>

      {/* Hero Header & Control Bar */}
      <Paper
        elevation={0}
        sx={{
          p: 3,
          mb: 4,
          borderRadius: 3,
          background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.08)} 0%, ${alpha(
            theme.palette.background.paper,
            0.95
          )} 100%)`,
          border: `1px solid ${alpha(theme.palette.primary.main, 0.2)}`,
        }}
      >
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={2}
          sx={{ justifyContent: 'space-between', alignItems: { xs: 'flex-start', md: 'center' } }}
        >
          <Box>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <Button
                component={RouterLink}
                to={`/portfolios/${portfolioId}`}
                startIcon={<ArrowBackIcon />}
                size="small"
                variant="outlined"
                sx={{ borderRadius: 2 }}
              >
                Back to Portfolio
              </Button>
              <Typography variant="h5" sx={{ fontWeight: 700 }}>
                Capital Gains & Dividend Allowance Tracker
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Tax year period: {report?.periodStart ? new Date(report.periodStart).toLocaleDateString('en-GB') : '—'} to{' '}
              {report?.periodEnd ? new Date(report.periodEnd).toLocaleDateString('en-GB') : '—'} • Base Currency: {report?.baseCurrency}
            </Typography>
          </Box>

          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            {/* Regime Selector */}
            <FormControl size="small" sx={{ minWidth: 150 }}>
              <InputLabel id="tax-regime-label">Regime</InputLabel>
              <Select
                labelId="tax-regime-label"
                value={selectedRegime}
                label="Regime"
                onChange={(e) => {
                  const newRegime = e.target.value as TaxRegime;
                  setSelectedRegime(newRegime);
                  setSelectedYear(''); // reset to default for regime
                }}
              >
                <MenuItem value="UK_HMRC">UK HMRC</MenuItem>
                <MenuItem value="CALENDAR_YEAR">Calendar Year</MenuItem>
              </Select>
            </FormControl>

            {/* Tax Year Selector */}
            <FormControl size="small" sx={{ minWidth: 140 }}>
              <InputLabel id="tax-year-label">Tax Year</InputLabel>
              <Select
                labelId="tax-year-label"
                value={effectiveTaxYear}
                label="Tax Year"
                onChange={(e) => setSelectedYear(e.target.value)}
              >
                {yearOptions.map((year) => (
                  <MenuItem key={year} value={year}>
                    {year}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {/* Settings Button */}
            <Button
              variant="outlined"
              color="primary"
              startIcon={<SettingsIcon />}
              onClick={openSettingsDialog}
              sx={{ borderRadius: 2 }}
            >
              Tax Settings
            </Button>
          </Stack>
        </Stack>
      </Paper>

      {/* Warnings Banner if any */}
      {report?.warnings && report.warnings.length > 0 && (
        <Alert severity="warning" sx={{ mb: 3, borderRadius: 2 }}>
          {report.warnings.map((w, idx) => (
            <div key={idx}>{w}</div>
          ))}
        </Alert>
      )}

      {/* Sheltered Savings Hero Banner */}
      {sheltered && (sheltered.shelteredRealizedGains > 0 || sheltered.shelteredGrossDividends > 0) && (
        <Card
          elevation={0}
          sx={{
            mb: 4,
            borderRadius: 3,
            border: `1px solid ${alpha(theme.palette.success.main, 0.3)}`,
            background: `linear-gradient(135deg, ${alpha(theme.palette.success.main, 0.1)} 0%, ${alpha(
              theme.palette.background.paper,
              0.8
            )} 100%)`,
          }}
        >
          <CardContent sx={{ p: 3 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5} sx={{ alignItems: 'center' }}>
              <Box
                sx={{
                  p: 1.5,
                  borderRadius: '50%',
                  bgcolor: alpha(theme.palette.success.main, 0.2),
                  color: theme.palette.success.main,
                  display: 'flex',
                }}
              >
                <ShieldOutlinedIcon sx={{ fontSize: 36 }} />
              </Box>
              <Box sx={{ flex: 1 }}>
                <Typography variant="h6" color="success.main" sx={{ fontWeight: 700 }}>
                  Tax-Sheltered Wealth Growth
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Assets held within your ISA and SIPP accounts completely bypass UK Capital Gains and Dividend taxes.
                </Typography>
                <Stack direction="row" spacing={3} sx={{ mt: 1.5, flexWrap: 'wrap' }}>
                  <Typography variant="body2">
                    <strong>Realized Gains Shielded:</strong> {formatCurrency(sheltered.shelteredRealizedGains)}
                  </Typography>
                  <Typography variant="body2">
                    <strong>Dividends Shielded:</strong> {formatCurrency(sheltered.shelteredGrossDividends)}
                  </Typography>
                  <Typography variant="body2" sx={{ color: theme.palette.success.dark, fontWeight: 700 }}>
                    <strong>Estimated Tax Saved:</strong> {formatCurrency(sheltered.totalEstimatedTaxSaved)}
                  </Typography>
                </Stack>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      )}

      {/* Main KPI Allowance Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {/* 1. Capital Gains Tax (CGT) Allowance Card */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Card
            elevation={0}
            sx={{
              height: '100%',
              borderRadius: 3,
              border: `1px solid ${theme.palette.divider}`,
              bgcolor: 'background.paper',
            }}
          >
            <CardContent sx={{ p: 3 }}>
              <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                <Box>
                  <Typography variant="subtitle2" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                    Capital Gains Tax Allowance ({report?.taxYear})
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 700, mt: 0.5 }}>
                    {formatCurrency(cgt?.allowanceRemaining)}{' '}
                    <Typography component="span" variant="body2" color="text.secondary">
                      remaining
                    </Typography>
                  </Typography>
                </Box>
                <Chip
                  size="small"
                  label={cgt?.taxableCapitalGain && cgt.taxableCapitalGain > 0 ? 'Exceeded' : 'Within Limit'}
                  color={cgt?.taxableCapitalGain && cgt.taxableCapitalGain > 0 ? 'error' : 'success'}
                  variant="outlined"
                  sx={{ fontWeight: 600 }}
                />
              </Stack>

              {/* Progress Gauge */}
              <Box sx={{ my: 2 }}>
                <Stack direction="row" sx={{ justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    Used: {formatCurrency(cgt?.allowanceUsed)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Annual Limit: {formatCurrency(cgt?.annualExemptAmount)}
                  </Typography>
                </Stack>
                <LinearProgress
                  variant="determinate"
                  value={cgtUsedPercent}
                  color={cgtUsedPercent >= 100 ? 'error' : cgtUsedPercent > 75 ? 'warning' : 'primary'}
                  sx={{ height: 10, borderRadius: 5 }}
                />
              </Box>

              <Divider sx={{ my: 2 }} />

              {/* Breakdown metrics */}
              <Grid container spacing={2}>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Total Disposal Proceeds
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {formatCurrency(cgt?.totalDisposalProceeds)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Total Disposal Cost Basis
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {formatCurrency(cgt?.totalDisposalCostBasis)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Net Realized Capital Gain
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{
                      fontWeight: 600,
                      color: cgt?.netRealizedGainLoss && cgt.netRealizedGainLoss >= 0 ? 'success.main' : 'error.main',
                    }}
                  >
                    {formatCurrency(cgt?.netRealizedGainLoss)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Loss Carryforward Applied
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {formatCurrency(cgt?.lossCarryforwardApplied)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Taxable Capital Gain
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{
                      fontWeight: 700,
                      color: cgt?.taxableCapitalGain && cgt.taxableCapitalGain > 0 ? 'error.main' : 'text.primary',
                    }}
                  >
                    {formatCurrency(cgt?.taxableCapitalGain)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Estimated CGT Liability
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 700, color: 'error.main' }}>
                    {formatCurrency(cgt?.estimatedTaxBasicRate)}{' '}
                    <Typography component="span" variant="caption" color="text.secondary">
                      (Basic)
                    </Typography>{' '}
                    /{' '}
                    {formatCurrency(cgt?.estimatedTaxHigherRate)}{' '}
                    <Typography component="span" variant="caption" color="text.secondary">
                      (Higher)
                    </Typography>
                  </Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>

        {/* 2. Dividend Allowance Card */}
        <Grid size={{ xs: 12, md: 6 }}>
          <Card
            elevation={0}
            sx={{
              height: '100%',
              borderRadius: 3,
              border: `1px solid ${theme.palette.divider}`,
              bgcolor: 'background.paper',
            }}
          >
            <CardContent sx={{ p: 3 }}>
              <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                <Box>
                  <Typography variant="subtitle2" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                    Dividend Allowance ({report?.taxYear})
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 700, mt: 0.5 }}>
                    {formatCurrency(div?.allowanceRemaining)}{' '}
                    <Typography component="span" variant="body2" color="text.secondary">
                      remaining
                    </Typography>
                  </Typography>
                </Box>
                <Chip
                  size="small"
                  label={div?.taxableDividendIncome && div.taxableDividendIncome > 0 ? 'Exceeded' : 'Within Limit'}
                  color={div?.taxableDividendIncome && div.taxableDividendIncome > 0 ? 'error' : 'success'}
                  variant="outlined"
                  sx={{ fontWeight: 600 }}
                />
              </Stack>

              {/* Progress Gauge */}
              <Box sx={{ my: 2 }}>
                <Stack direction="row" sx={{ justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    Used: {formatCurrency(div?.allowanceUsed)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Annual Limit: {formatCurrency(div?.annualDividendAllowance)}
                  </Typography>
                </Stack>
                <LinearProgress
                  variant="determinate"
                  value={divUsedPercent}
                  color={divUsedPercent >= 100 ? 'error' : divUsedPercent > 75 ? 'warning' : 'primary'}
                  sx={{ height: 10, borderRadius: 5 }}
                />
              </Box>

              <Divider sx={{ my: 2 }} />

              {/* Breakdown metrics */}
              <Grid container spacing={2}>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Total Gross Dividends (Taxable)
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {formatCurrency(div?.totalGrossDividends)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Withholding Tax Paid
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {formatCurrency(div?.totalWithholdingTax)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Net Dividends Received
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {formatCurrency(div?.netDividendsReceived)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Taxable Dividend Income
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{
                      fontWeight: 700,
                      color: div?.taxableDividendIncome && div.taxableDividendIncome > 0 ? 'error.main' : 'text.primary',
                    }}
                  >
                    {formatCurrency(div?.taxableDividendIncome)}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="caption" color="text.secondary">
                    Estimated Dividend Tax by Tax Bracket
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600, color: 'error.main' }}>
                    Basic (8.75%): {formatCurrency(div?.estimatedTaxBasicRate)} • Higher (33.75%):{' '}
                    {formatCurrency(div?.estimatedTaxHigherRate)} • Additional (39.35%):{' '}
                    {formatCurrency(div?.estimatedTaxAdditionalRate)}
                  </Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Tax-Loss Harvesting Candidates Card */}
      {report?.lossHarvestOpportunities && report.lossHarvestOpportunities.length > 0 && (
        <Card
          elevation={0}
          sx={{
            mb: 4,
            borderRadius: 3,
            border: `1px solid ${theme.palette.divider}`,
            bgcolor: 'background.paper',
          }}
        >
          <CardContent sx={{ p: 3 }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 2 }}>
              <TrendingDownIcon color="error" />
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Tax-Loss Harvesting Opportunities
              </Typography>
              <Chip
                size="small"
                label={`${report?.lossHarvestOpportunities?.length || 0} Candidates`}
                color="error"
                variant="outlined"
                sx={{ fontWeight: 600 }}
              />
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Active positions currently held at an unrealized loss in taxable accounts. Selling these can realize capital losses to offset taxable capital gains or establish carried-forward losses.
            </Typography>

            <TableContainer component={Paper} elevation={0} sx={{ border: `1px solid ${theme.palette.divider}`, borderRadius: 2 }}>
              <Table size="small">
                <TableHead sx={{ bgcolor: alpha(theme.palette.primary.main, 0.04) }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Account</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Instrument</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Quantity</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Current Price</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Market Value ({report?.baseCurrency})</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Cost Basis ({report?.baseCurrency})</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700, color: 'error.main' }}>Unrealized Loss</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(report?.lossHarvestOpportunities || []).map((opp, idx) => (
                    <TableRow key={idx} hover>
                      <TableCell>{opp.accountName}</TableCell>
                      <TableCell>
                        <strong>{opp.instrumentName}</strong> {opp.ticker && <Chip size="small" label={opp.ticker} sx={{ ml: 1 }} />}
                      </TableCell>
                      <TableCell align="right">{opp.quantity.toLocaleString('en-GB')}</TableCell>
                      <TableCell align="right">{formatCurrency(opp.currentPrice, opp.priceCurrency)}</TableCell>
                      <TableCell align="right">{formatCurrency(opp.currentMarketValueBase)}</TableCell>
                      <TableCell align="right">{formatCurrency(opp.totalCostBasisBase)}</TableCell>
                      <TableCell align="right" sx={{ color: 'error.main', fontWeight: 700 }}>
                        -{formatCurrency(opp.unrealizedLossBase)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>
      )}

      {/* Itemized Activity Ledger Tabs */}
      <Paper elevation={0} sx={{ borderRadius: 3, border: `1px solid ${theme.palette.divider}`, overflow: 'hidden' }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2, pt: 1 }}>
          <Tabs value={activeTab} onChange={(_, val) => setActiveTab(val)}>
            <Tab label={`Taxable Disposals (${report?.disposals?.length || 0})`} sx={{ fontWeight: 600 }} />
            <Tab label={`Taxable Dividends (${report?.dividends?.length || 0})`} sx={{ fontWeight: 600 }} />
          </Tabs>
        </Box>

        {/* Tab 0: Disposals */}
        {activeTab === 0 && (
          <Box sx={{ p: 2 }}>
            {(report?.disposals || []).length === 0 ? (
              <EmptyState title="No Taxable Disposals" description="No realized disposals in taxable accounts occurred during this tax year period." />
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead sx={{ bgcolor: alpha(theme.palette.primary.main, 0.04) }}>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }}>Disposal Date</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Account</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Instrument</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Quantity</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Proceeds ({report?.baseCurrency})</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Cost Basis ({report?.baseCurrency})</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Realized Gain / (Loss)</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(report?.disposals || []).map((d) => (
                      <TableRow key={d.disposalTransactionId} hover>
                        <TableCell>{new Date(d.disposalDate).toLocaleDateString('en-GB')}</TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                            <span>{d.accountName}</span>
                            {getTaxTreatmentChip(d.taxTreatment)}
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <strong>{d.instrumentName}</strong> {d.ticker && <Chip size="small" label={d.ticker} sx={{ ml: 1 }} />}
                        </TableCell>
                        <TableCell align="right">{d.quantity.toLocaleString('en-GB')}</TableCell>
                        <TableCell align="right">{formatCurrency(d.proceedsBase)}</TableCell>
                        <TableCell align="right">{formatCurrency(d.costBasisBase)}</TableCell>
                        <TableCell
                          align="right"
                          sx={{
                            fontWeight: 700,
                            color: d.realizedGainLossBase >= 0 ? 'success.main' : 'error.main',
                          }}
                        >
                          {d.realizedGainLossBase >= 0 ? '+' : ''}
                          {formatCurrency(d.realizedGainLossBase)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>
        )}

        {/* Tab 1: Dividends */}
        {activeTab === 1 && (
          <Box sx={{ p: 2 }}>
            {(report?.dividends || []).length === 0 ? (
              <EmptyState title="No Taxable Dividends" description="No dividend distributions in taxable accounts were received during this tax year period." />
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead sx={{ bgcolor: alpha(theme.palette.primary.main, 0.04) }}>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }}>Payment Date</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Account</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Instrument</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Gross Amount ({report?.baseCurrency})</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Withholding Tax</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Net Received ({report?.baseCurrency})</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(report?.dividends || []).map((divItem) => (
                      <TableRow key={divItem.transactionId} hover>
                        <TableCell>{new Date(divItem.paymentDate).toLocaleDateString('en-GB')}</TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                            <span>{divItem.accountName}</span>
                            {getTaxTreatmentChip(divItem.taxTreatment)}
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <strong>{divItem.instrumentName}</strong> {divItem.ticker && <Chip size="small" label={divItem.ticker} sx={{ ml: 1 }} />}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>
                          {formatCurrency(divItem.grossAmountBase)}
                        </TableCell>
                        <TableCell align="right" sx={{ color: 'text.secondary' }}>
                          {formatCurrency(divItem.withholdingTaxBase)}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700, color: 'success.main' }}>
                          {formatCurrency(divItem.netAmountBase)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>
        )}
      </Paper>

      {/* Tax Settings Dialog */}
      <Dialog open={settingsOpen} onClose={() => setSettingsOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>
          Tax Settings & Custom Allowances ({effectiveTaxYear})
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Override statutory annual exemptions or record prior-year capital losses carried forward to offset against current capital gains.
            </Typography>

            <TextField
              label="Annual CGT Exemption Allowance"
              type="number"
              fullWidth
              value={cgtAllowanceInput}
              onChange={(e) => setCgtAllowanceInput(e.target.value)}
              helperText={`Statutory UK HMRC allowance for ${effectiveTaxYear} is typically £3,000`}
            />

            <TextField
              label="Annual Dividend Allowance"
              type="number"
              fullWidth
              value={divAllowanceInput}
              onChange={(e) => setDivAllowanceInput(e.target.value)}
              helperText={`Statutory UK HMRC allowance for ${effectiveTaxYear} is typically £500`}
            />

            <TextField
              label="Loss Carryforward from Prior Years"
              type="number"
              fullWidth
              value={lossCarryforwardInput}
              onChange={(e) => setLossCarryforwardInput(e.target.value)}
              helperText="Allowable unexpired capital losses from previous tax years"
            />

            <TextField
              label="Notes / Tax Reference"
              multiline
              rows={2}
              fullWidth
              value={notesInput}
              onChange={(e) => setNotesInput(e.target.value)}
              placeholder="e.g. HMRC UTR, registered loss filing reference"
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setSettingsOpen(false)} color="inherit">
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleSaveSettings}
            disabled={updateSettingsMutation.isPending}
          >
            {updateSettingsMutation.isPending ? 'Saving...' : 'Save Settings'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

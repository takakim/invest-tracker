import React, { useState, useEffect } from 'react';
import { Link as RouterLink, useParams, useNavigate } from 'react-router-dom';
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
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';

import UnfoldMoreIcon from '@mui/icons-material/UnfoldMore';
import UnfoldLessIcon from '@mui/icons-material/UnfoldLess';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import PieChartOutlineOutlinedIcon from '@mui/icons-material/PieChartOutlineOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import QueryStatsOutlinedIcon from '@mui/icons-material/QueryStatsOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined';
import BalanceOutlinedIcon from '@mui/icons-material/BalanceOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';

import { usePortfolio, useUpdatePortfolio, useArchivePortfolio } from './usePortfolios';
import {
  useAccountsList,
  useCreateAccount,
  useUpdateAccount,
  useArchiveAccount,
} from '../accounts/useAccounts';
import { PortfolioFormModal } from './PortfolioFormModal';
import { AccountFormModal } from '../accounts/AccountFormModal';
import { AccountSectionCard } from '../accounts/AccountSectionCard';
import { PositionTable } from '../positions/PositionTable';
import { TransactionTable } from '../transactions/TransactionTable';
import PerformanceSummaryCard from '../performance/PerformanceSummaryCard';
import { ValuationMetricsCard } from '../analytics/ValuationMetricsCard';
import { AssetAllocationCard } from '../analytics/AssetAllocationCard';
import { DividendAnalyticsCard } from '../analytics/DividendAnalyticsCard';
import { DividendSummaryCard } from '../analytics/DividendSummaryCard';
import { PortfolioHistoryCard } from '../analytics/PortfolioHistoryCard';
import { ExportReportModal } from '../analytics/ExportReportModal';
import { usePortfolioAnalytics } from '../analytics/useAnalytics';
import { TargetAllocationCard, RebalancingCalculatorCard } from '../rebalancing';
import { StickyHeroBar } from './StickyHeroBar';
import { CollapsibleSection, ConfirmDialog, EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { Account, AccountCreateInput, PortfolioCreateInput } from '../../types';


export function PortfolioDetailPage() {
  const { id: portfolioId = '' } = useParams();
  const navigate = useNavigate();

  const {
    data: portfolio,
    isLoading: isPortfolioLoading,
    error: portfolioError,
    refetch: refetchPortfolio,
  } = usePortfolio(portfolioId);

  const {
    data: accounts = [],
    isLoading: isAccountsLoading,
    error: accountsError,
    refetch: refetchAccounts,
  } = useAccountsList(portfolioId);

  const updatePortfolioMutation = useUpdatePortfolio();
  const archivePortfolioMutation = useArchivePortfolio();

  const createAccountMutation = useCreateAccount(portfolioId);
  const updateAccountMutation = useUpdateAccount(portfolioId);
  const archiveAccountMutation = useArchiveAccount(portfolioId);

  // Modal states
  const [portfolioEditOpen, setPortfolioEditOpen] = useState(false);
  const [portfolioArchiveOpen, setPortfolioArchiveOpen] = useState(false);
  const [exportModalOpen, setExportModalOpen] = useState(false);

  // Collapsible section states
  const SECTION_KEYS = [
    'valuation',
    'allocation',
    'performance',
    'history',
    'dividends',
    'rebalancing',
    'accounts',
  ] as const;
  type SectionKey = (typeof SECTION_KEYS)[number];

  const [expandedSections, setExpandedSections] = useState<Record<SectionKey, boolean>>({
    valuation: true,
    allocation: true,
    performance: true,
    history: true,
    dividends: true,
    rebalancing: true,
    accounts: true,
  });

  const [stickyVisible, setStickyVisible] = useState(false);
  const [activeSection, setActiveSection] = useState<SectionKey>('valuation');

  const { data: analytics } = usePortfolioAnalytics(portfolioId);

  useEffect(() => {
    const handleScroll = () => {
      setStickyVisible(window.scrollY > 280);

      // Determine active section based on scroll position
      for (const key of SECTION_KEYS) {
        const el = document.getElementById(`section-${key}`);
        if (el) {
          const rect = el.getBoundingClientRect();
          if (rect.top <= 140 && rect.bottom > 140) {
            setActiveSection(key);
            break;
          }
        }
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleJumpToSection = (key: string) => {
    const secKey = key as SectionKey;
    setExpandedSections((prev) => ({ ...prev, [secKey]: true }));
    setActiveSection(secKey);

    setTimeout(() => {
      const el = document.getElementById(`section-${secKey}`);
      if (el) {
        const yOffset = -70;
        const y = el.getBoundingClientRect().top + window.pageYOffset + yOffset;
        window.scrollTo({ top: y, behavior: 'smooth' });
      }
    }, 50);
  };

  const toggleSection = (key: SectionKey, next?: boolean) => {
    setExpandedSections((prev) => ({
      ...prev,
      [key]: next !== undefined ? next : !prev[key],
    }));
  };

  const handleExpandAll = () => {
    const allOpen = SECTION_KEYS.reduce((acc, k) => ({ ...acc, [k]: true }), {} as Record<SectionKey, boolean>);
    setExpandedSections(allOpen);
  };

  const handleCollapseAll = () => {
    const allClosed = SECTION_KEYS.reduce((acc, k) => ({ ...acc, [k]: false }), {} as Record<SectionKey, boolean>);
    setExpandedSections(allClosed);
  };

  const [accountModalOpen, setAccountModalOpen] = useState(false);
  const [editingAccount, setEditingAccount] = useState<Account | null>(null);
  const [archiveAccountTarget, setArchiveAccountTarget] = useState<Account | null>(null);

  if (isPortfolioLoading) {
    return <LoadingState message="Loading portfolio details..." variant="skeleton" count={4} />;
  }

  if (portfolioError || !portfolio) {
    return (
      <Box>
        <Button component={RouterLink} to="/portfolios" startIcon={<ArrowBackIcon />} sx={{ mb: 2 }}>
          Back to Portfolios
        </Button>
        <ErrorAlert
          error={portfolioError || 'Portfolio not found'}
          title="Portfolio Error"
          onClose={() => refetchPortfolio()}
        />
      </Box>
    );
  }

  const handleUpdatePortfolioSubmit = async (formData: PortfolioCreateInput) => {
    await updatePortfolioMutation.mutateAsync(
      { id: portfolio.id, input: formData },
      {
        onSuccess: () => setPortfolioEditOpen(false),
      },
    );
  };

  const handleArchivePortfolioConfirm = async () => {
    await archivePortfolioMutation.mutateAsync(portfolio.id, {
      onSuccess: () => {
        setPortfolioArchiveOpen(false);
        navigate('/portfolios');
      },
    });
  };

  const handleOpenCreateAccount = () => {
    setEditingAccount(null);
    setAccountModalOpen(true);
  };

  const handleOpenEditAccount = (account: Account) => {
    setEditingAccount(account);
    setAccountModalOpen(true);
  };

  const handleAccountSubmit = async (formData: AccountCreateInput) => {
    if (editingAccount) {
      await updateAccountMutation.mutateAsync(
        { accountId: editingAccount.id, input: formData },
        {
          onSuccess: () => setAccountModalOpen(false),
        },
      );
    } else {
      await createAccountMutation.mutateAsync(formData, {
        onSuccess: () => setAccountModalOpen(false),
      });
    }
  };

  const handleArchiveAccountConfirm = async () => {
    if (!archiveAccountTarget) return;
    await archiveAccountMutation.mutateAsync(archiveAccountTarget.id, {
      onSuccess: () => setArchiveAccountTarget(null),
    });
  };

  return (
    <Box>
      {/* Sticky Hero Bar */}
      <StickyHeroBar
        portfolioName={portfolio.name}
        currency={portfolio.baseCurrency}
        totalMarketValue={analytics?.totalCurrentValue}
        unrealizedReturnPercentage={analytics?.totalUnrealizedReturnPercentage}
        unrealizedGainLoss={analytics?.totalUnrealizedGainLoss}
        visible={stickyVisible}
        activeSectionKey={activeSection}
        onJumpToSection={handleJumpToSection}
        onAddAccount={handleOpenCreateAccount}
        onExportReport={() => setExportModalOpen(true)}
      />

      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 3 }} aria-label="breadcrumb">
        <Link component={RouterLink} underline="hover" color="inherit" to="/portfolios">
          Portfolios
        </Link>
        <Typography color="text.primary" sx={{ fontWeight: 600 }}>
          {portfolio.name}
        </Typography>
      </Breadcrumbs>

      {/* Header Info */}
      <Paper sx={{ p: 3, mb: 4, borderRadius: 3 }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-start' }, gap: 2, mb: 3 }}
        >
          <Box>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <Typography variant="h4" sx={{ fontWeight: 700 }}>
                {portfolio.name}
              </Typography>
              <Chip
                label={portfolio.status}
                color={portfolio.status === 'ACTIVE' ? 'success' : 'default'}
                size="small"
                variant={portfolio.status === 'ACTIVE' ? 'filled' : 'outlined'}
              />
            </Stack>
            <Typography variant="caption" color="text.secondary">
              ID: {portfolio.id}
            </Typography>
          </Box>

          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              startIcon={<FileDownloadOutlinedIcon />}
              onClick={() => setExportModalOpen(true)}
              size="small"
            >
              Export Statements
            </Button>
            <Button
              variant="outlined"
              startIcon={<EditOutlinedIcon />}
              onClick={() => setPortfolioEditOpen(true)}
              size="small"
            >
              Edit Portfolio
            </Button>
            <Button
              variant="outlined"
              color="error"
              startIcon={<ArchiveOutlinedIcon />}
              onClick={() => setPortfolioArchiveOpen(true)}
              size="small"
            >
              Archive
            </Button>
          </Stack>
        </Stack>

        <Divider sx={{ my: 2 }} />

        {/* Overview Stats */}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined" sx={{ bgcolor: 'background.default' }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">
                  Base Currency
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                  {portfolio.baseCurrency}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined" sx={{ bgcolor: 'background.default' }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">
                  Cost Basis Strategy
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                  {portfolio.costBasisMethod}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined" sx={{ bgcolor: 'background.default' }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">
                  Return Strategy
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                  {portfolio.returnMethod}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined" sx={{ bgcolor: 'background.default' }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">
                  Active Accounts
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                  {accounts.length}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Paper>

      {/* Global Section Controls */}
      <Stack
        direction="row"
        sx={{ justifyContent: 'flex-end', alignItems: 'center', mb: 2, gap: 1 }}
      >
        <Button
          size="small"
          variant="text"
          startIcon={<UnfoldMoreIcon fontSize="small" />}
          onClick={handleExpandAll}
          sx={{ fontSize: '0.8rem', color: 'text.secondary' }}
        >
          Expand All Sections
        </Button>
        <Button
          size="small"
          variant="text"
          startIcon={<UnfoldLessIcon fontSize="small" />}
          onClick={handleCollapseAll}
          sx={{ fontSize: '0.8rem', color: 'text.secondary' }}
        >
          Collapse All Sections
        </Button>
      </Stack>

      {/* Valuation & Unrealized P&L */}
      <CollapsibleSection
        id="section-valuation"
        title="Valuation & Unrealized P&L"
        subtitle="Current live valuations, cost basis, and unrealized returns across portfolio holdings"
        icon={<AccountBalanceWalletOutlinedIcon />}
        expanded={expandedSections.valuation}
        onToggle={(expanded) => toggleSection('valuation', expanded)}
      >
        <ValuationMetricsCard portfolioId={portfolio.id} currency={portfolio.baseCurrency} />
      </CollapsibleSection>

      {/* Asset Allocation Breakdown */}
      <CollapsibleSection
        id="section-allocation"
        title="Asset Allocation Breakdown"
        subtitle="Diversification by asset class, geography, sector, and currency"
        icon={<PieChartOutlineOutlinedIcon />}
        expanded={expandedSections.allocation}
        onToggle={(expanded) => toggleSection('allocation', expanded)}
      >
        <AssetAllocationCard portfolioId={portfolio.id} currency={portfolio.baseCurrency} />
      </CollapsibleSection>

      {/* Portfolio Performance */}
      <CollapsibleSection
        id="section-performance"
        title="Portfolio Performance"
        subtitle="Time-Weighted Return (TWR) and Money-Weighted Return (MWR) with income breakdown"
        icon={<TrendingUpOutlinedIcon />}
        expanded={expandedSections.performance}
        onToggle={(expanded) => toggleSection('performance', expanded)}
      >
        <PerformanceSummaryCard portfolioId={portfolio.id} currency={portfolio.baseCurrency} />
      </CollapsibleSection>

      {/* Historical Valuation & Wealth Growth Charting */}
      <CollapsibleSection
        id="section-history"
        title="Historical Valuation & Wealth Growth"
        subtitle="Time series valuation, invested capital comparison, drawdowns, and benchmark tracking"
        icon={<QueryStatsOutlinedIcon />}
        expanded={expandedSections.history}
        onToggle={(expanded) => toggleSection('history', expanded)}
      >
        <PortfolioHistoryCard portfolioId={portfolio.id} currency={portfolio.baseCurrency} />
      </CollapsibleSection>

      {/* Dividend Analytics & Income Projection */}
      <CollapsibleSection
        id="section-dividends"
        title="Dividend Analytics & Income Projection"
        subtitle="Yield, monthly income projections, ex-dividend schedule, and dividend safety"
        icon={<PaymentsOutlinedIcon />}
        expanded={expandedSections.dividends}
        onToggle={(expanded) => toggleSection('dividends', expanded)}
      >
        <DividendSummaryCard portfolioId={portfolio.id} currency={portfolio.baseCurrency} />
      </CollapsibleSection>

      {/* Target Allocation Strategy & Rebalancing */}
      <CollapsibleSection
        id="section-rebalancing"
        title="Target Allocation Strategy & Rebalancing"
        subtitle="Strategic target models, drift tracking, and actionable rebalancing trade orders"
        icon={<TuneOutlinedIcon />}
        expanded={expandedSections.rebalancing}
        onToggle={(expanded) => toggleSection('rebalancing', expanded)}
      >
        <Stack spacing={2.5}>
          <TargetAllocationCard portfolioId={portfolio.id} currency={portfolio.baseCurrency} />
          <RebalancingCalculatorCard portfolioId={portfolio.id} currency={portfolio.baseCurrency} />
        </Stack>
      </CollapsibleSection>

      {/* Accounts Section */}
      <CollapsibleSection
        id="section-accounts"
        title="Accounts"
        headingVariant="h5"
        subtitle="Brokerage, custody, and cash accounts with tax-lot holdings and transaction ledgers"
        icon={<LayersOutlinedIcon />}
        badge={<Chip label={`${accounts.length} Accounts`} size="small" variant="outlined" color="primary" sx={{ height: 22, fontSize: '0.75rem' }} />}
        actions={
          <Button
            variant="contained"
            size="small"
            startIcon={<AddIcon />}
            onClick={handleOpenCreateAccount}
            disabled={portfolio.status !== 'ACTIVE'}
          >
            Add Account
          </Button>
        }
        expanded={expandedSections.accounts}
        onToggle={(expanded) => toggleSection('accounts', expanded)}
      >
        <ErrorAlert error={accountsError} onClose={() => refetchAccounts()} />

        {isAccountsLoading ? (
          <LoadingState variant="table" count={2} />
        ) : accounts.length === 0 ? (
          <EmptyState
            title="No Accounts in this Portfolio"
            description="Add brokerage or custodian accounts (e.g. Interactive Brokers, ISA, SIPP) to track cash and holdings."
            actionLabel={portfolio.status === 'ACTIVE' ? 'Add Account' : undefined}
            onAction={handleOpenCreateAccount}
            icon={<AccountBalanceOutlinedIcon sx={{ fontSize: 52, opacity: 0.7 }} />}
          />
        ) : (
          <Stack spacing={3} sx={{ mt: 1 }}>
            {accounts.map((account) => (
              <AccountSectionCard
                key={account.id}
                portfolio={portfolio}
                account={account}
                onEdit={handleOpenEditAccount}
                onArchive={setArchiveAccountTarget}
              />
            ))}
          </Stack>
        )}
      </CollapsibleSection>

      {/* Edit Portfolio Modal */}
      <PortfolioFormModal
        open={portfolioEditOpen}
        portfolio={portfolio}
        isPending={updatePortfolioMutation.isPending}
        error={updatePortfolioMutation.error}
        onClose={() => setPortfolioEditOpen(false)}
        onSubmit={handleUpdatePortfolioSubmit}
      />

      {/* Archive Portfolio Confirm */}
      <ConfirmDialog
        open={portfolioArchiveOpen}
        title="Archive Portfolio"
        message={`Are you sure you want to archive "${portfolio.name}"? This action cannot be undone.`}
        confirmLabel="Archive Portfolio"
        confirmColor="error"
        isPending={archivePortfolioMutation.isPending}
        onConfirm={handleArchivePortfolioConfirm}
        onCancel={() => setPortfolioArchiveOpen(false)}
      />

      {/* Create / Edit Account Modal */}
      <AccountFormModal
        open={accountModalOpen}
        account={editingAccount}
        defaultCurrency={portfolio.baseCurrency}
        isPending={createAccountMutation.isPending || updateAccountMutation.isPending}
        error={createAccountMutation.error || updateAccountMutation.error}
        onClose={() => setAccountModalOpen(false)}
        onSubmit={handleAccountSubmit}
      />

      {/* Archive Account Confirm */}
      <ConfirmDialog
        open={Boolean(archiveAccountTarget)}
        title="Archive Account"
        message={`Are you sure you want to archive "${archiveAccountTarget?.name}"? Financial history is preserved, but no new transactions can be recorded.`}
        confirmLabel="Archive Account"
        confirmColor="error"
        isPending={archiveAccountMutation.isPending}
        onConfirm={handleArchiveAccountConfirm}
        onCancel={() => setArchiveAccountTarget(null)}
      />

      {/* Export Statements Modal */}
      <ExportReportModal
        open={exportModalOpen}
        portfolioId={portfolio.id}
        portfolioName={portfolio.name}
        onClose={() => setExportModalOpen(false)}
      />
    </Box>
  );
}

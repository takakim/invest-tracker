import React, { useState } from 'react';
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
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';

import { usePortfolio, useUpdatePortfolio, useArchivePortfolio } from './usePortfolios';
import {
  useAccountsList,
  useCreateAccount,
  useUpdateAccount,
  useArchiveAccount,
} from '../accounts/useAccounts';
import { PortfolioFormModal } from './PortfolioFormModal';
import { AccountFormModal } from '../accounts/AccountFormModal';
import { ConfirmDialog, EmptyState, ErrorAlert, LoadingState } from '../../components';
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

      {/* Accounts Section */}
      <Box sx={{ mb: 4 }}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}
        >
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 600 }}>
              Accounts
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Brokerage, custody, and cash accounts belonging to this portfolio.
            </Typography>
          </Box>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={handleOpenCreateAccount}
            disabled={portfolio.status !== 'ACTIVE'}
          >
            Add Account
          </Button>
        </Stack>

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
          <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
            <Table aria-label="accounts table">
              <TableHead>
                <TableRow>
                  <TableCell>Account Name</TableCell>
                  <TableCell>Broker / Custodian</TableCell>
                  <TableCell>Currency</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {accounts.map((account) => (
                  <TableRow key={account.id} hover>
                    <TableCell sx={{ fontWeight: 600 }}>{account.name}</TableCell>
                    <TableCell>{account.brokerName}</TableCell>
                    <TableCell>
                      <Chip label={account.accountCurrency} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={account.status}
                        size="small"
                        color={account.status === 'ACTIVE' ? 'success' : 'default'}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                        <Tooltip title="Edit account">
                          <IconButton
                            size="small"
                            onClick={() => handleOpenEditAccount(account)}
                            aria-label={`edit ${account.name}`}
                          >
                            <EditOutlinedIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Archive account">
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() => setArchiveAccountTarget(account)}
                            aria-label={`archive ${account.name}`}
                          >
                            <ArchiveOutlinedIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Box>

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
    </Box>
  );
}

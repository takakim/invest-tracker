import React, { useState } from 'react';
import { Link as RouterLink, useParams, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  FormControl,
  Grid,
  InputLabel,
  Link,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Snackbar,
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
  useTheme,
  alpha,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import CloseIcon from '@mui/icons-material/Close';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import ContentCutIcon from '@mui/icons-material/ContentCut';
import MonetizationOnOutlinedIcon from '@mui/icons-material/MonetizationOnOutlined';
import SyncAltIcon from '@mui/icons-material/SyncAlt';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

import { usePortfolio } from '../portfolios/usePortfolios';
import { useAccountsList } from '../accounts/useAccounts';
import {
  useCorporateActions,
  useScanCorporateActions,
  useApplyCorporateAction,
  useDismissCorporateAction,
} from './useCorporateActions';
import type { CorporateAction, CorporateActionStatus, CorporateActionType } from '../../types';

export function CorporateActionsDetailPage() {
  const theme = useTheme();
  const navigate = useNavigate();
  const { id: portfolioId = '' } = useParams();

  const [currentTab, setCurrentTab] = useState<'PENDING' | 'APPLIED' | 'DISMISSED' | 'ALL'>('PENDING');
  const [typeFilter, setTypeFilter] = useState<'ALL' | CorporateActionType>('ALL');

  // Scanning feedback state
  const [scanSnackbar, setScanSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'info' | 'error' }>({
    open: false,
    message: '',
    severity: 'info',
  });

  // Action Dialog states
  const [selectedAction, setSelectedAction] = useState<CorporateAction | null>(null);
  const [applyDialogOpen, setApplyDialogOpen] = useState(false);
  const [dismissDialogOpen, setDismissDialogOpen] = useState(false);

  // Form states for Apply
  const [selectedAccountId, setSelectedAccountId] = useState<string>('');
  const [customQuantity, setCustomQuantity] = useState<string>('');
  const [customGrossAmount, setCustomGrossAmount] = useState<string>('');
  const [customTaxAmount, setCustomTaxAmount] = useState<string>('0.00');
  const [notes, setNotes] = useState<string>('');

  const { data: portfolio, isLoading: isPortfolioLoading } = usePortfolio(portfolioId);
  const { data: accounts = [], isLoading: isAccountsLoading } = useAccountsList(portfolioId);

  const statusFilter = currentTab === 'ALL' ? undefined : (currentTab as CorporateActionStatus);
  const { data: actions = [], isLoading: isActionsLoading } = useCorporateActions(portfolioId, statusFilter);

  const scanMutation = useScanCorporateActions(portfolioId);
  const applyMutation = useApplyCorporateAction(portfolioId);
  const dismissMutation = useDismissCorporateAction(portfolioId);

  const handleScan = async () => {
    try {
      const res = await scanMutation.mutateAsync();
      setScanSnackbar({
        open: true,
        message: `Scan complete: ${res.scannedInstrumentsCount} instruments scanned, ${res.discoveredActionsCount} actions discovered (${res.newPendingActionsCount} new pending).`,
        severity: 'success',
      });
    } catch (err: any) {
      setScanSnackbar({
        open: true,
        message: err.message || 'Failed to scan for corporate actions.',
        severity: 'error',
      });
    }
  };

  const handleOpenApply = (action: CorporateAction) => {
    setSelectedAction(action);
    const defaultAccId = action.suggestedAccountId || (accounts.length > 0 ? accounts[0].id : '');
    setSelectedAccountId(defaultAccId);
    setCustomQuantity(action.proposedImpactQuantity ? String(action.proposedImpactQuantity) : '');
    setCustomGrossAmount(action.proposedImpactAmount ? String(action.proposedImpactAmount) : '');
    setCustomTaxAmount('0.00');
    setNotes(action.description || '');
    setApplyDialogOpen(true);
  };

  const handleConfirmApply = async () => {
    if (!selectedAction || !selectedAccountId) return;

    try {
      await applyMutation.mutateAsync({
        actionId: selectedAction.id,
        input: {
          accountId: selectedAccountId,
          quantity: customQuantity ? parseFloat(customQuantity) : undefined,
          grossAmount: customGrossAmount ? parseFloat(customGrossAmount) : undefined,
          taxAmount: customTaxAmount ? parseFloat(customTaxAmount) : 0,
          notes: notes || undefined,
        },
      });
      setApplyDialogOpen(false);
      setSelectedAction(null);
      setScanSnackbar({
        open: true,
        message: `Successfully applied ${selectedAction.actionType} for ${selectedAction.ticker || selectedAction.instrumentName}!`,
        severity: 'success',
      });
    } catch (err: any) {
      setScanSnackbar({
        open: true,
        message: err.message || 'Failed to apply corporate action.',
        severity: 'error',
      });
    }
  };

  const handleOpenDismiss = (action: CorporateAction) => {
    setSelectedAction(action);
    setDismissDialogOpen(true);
  };

  const handleConfirmDismiss = async () => {
    if (!selectedAction) return;

    try {
      await dismissMutation.mutateAsync(selectedAction.id);
      setDismissDialogOpen(false);
      setSelectedAction(null);
      setScanSnackbar({
        open: true,
        message: `Corporate action dismissed.`,
        severity: 'info',
      });
    } catch (err: any) {
      setScanSnackbar({
        open: true,
        message: err.message || 'Failed to dismiss corporate action.',
        severity: 'error',
      });
    }
  };

  const filteredActions = actions.filter((act) => {
    if (typeFilter === 'ALL') return true;
    return act.actionType === typeFilter;
  });

  const pendingCount = actions.filter((a) => a.status === 'PENDING').length;
  const appliedCount = actions.filter((a) => a.status === 'APPLIED').length;
  const dismissedCount = actions.filter((a) => a.status === 'DISMISSED').length;

  if (isPortfolioLoading || isActionsLoading || isAccountsLoading) {
    return (
      <Box sx={{ p: 3, maxWidth: 1400, mx: 'auto' }}>
        <Skeleton variant="text" width={250} height={32} />
        <Skeleton variant="rectangular" height={140} sx={{ mt: 3, borderRadius: 3 }} />
        <Skeleton variant="rectangular" height={400} sx={{ mt: 3, borderRadius: 3 }} />
      </Box>
    );
  }

  const renderTypeChip = (type: CorporateActionType) => {
    switch (type) {
      case 'STOCK_SPLIT':
        return (
          <Chip
            size="small"
            icon={<ContentCutIcon fontSize="small" />}
            label="Stock Split"
            color="primary"
            variant="filled"
            sx={{ fontWeight: 700 }}
          />
        );
      case 'REVERSE_STOCK_SPLIT':
        return (
          <Chip
            size="small"
            icon={<SyncAltIcon fontSize="small" />}
            label="Reverse Split"
            color="secondary"
            variant="filled"
            sx={{ fontWeight: 700 }}
          />
        );
      case 'DIVIDEND':
        return (
          <Chip
            size="small"
            icon={<MonetizationOnOutlinedIcon fontSize="small" />}
            label="Dividend"
            color="success"
            variant="filled"
            sx={{ fontWeight: 700 }}
          />
        );
    }
  };

  const renderStatusChip = (status: CorporateActionStatus) => {
    switch (status) {
      case 'PENDING':
        return <Chip size="small" label="Pending" color="warning" variant="filled" sx={{ fontWeight: 700 }} />;
      case 'APPLIED':
        return <Chip size="small" icon={<CheckCircleOutlinedIcon />} label="Applied" color="success" variant="outlined" sx={{ fontWeight: 700 }} />;
      case 'DISMISSED':
        return <Chip size="small" label="Dismissed" color="default" variant="outlined" sx={{ fontWeight: 600 }} />;
    }
  };

  return (
    <Box sx={{ p: { xs: 2, sm: 3 }, maxWidth: 1400, mx: 'auto' }}>
      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 2 }} aria-label="breadcrumb">
        <Link component={RouterLink} underline="hover" color="inherit" to="/portfolios">
          Portfolios
        </Link>
        <Link component={RouterLink} underline="hover" color="inherit" to={`/portfolios/${portfolioId}`}>
          {portfolio?.name || 'Portfolio'}
        </Link>
        <Typography color="text.primary" sx={{ fontWeight: 600 }}>
          Corporate Actions
        </Typography>
      </Breadcrumbs>

      {/* Header Banner */}
      <Paper sx={{ p: 3, mb: 3, borderRadius: 3 }}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          sx={{
            justifyContent: 'space-between',
            alignItems: { md: 'center' },
            gap: 2,
          }}
        >
          <Box>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 0.5 }}>
              <AutoFixHighIcon sx={{ fontSize: 32, color: theme.palette.warning.main }} />
              <Typography variant="h5" sx={{ fontWeight: 800 }}>
                Corporate Actions & Splits Feed
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary">
              Automatically detect stock splits, reverse stock splits, and cash dividends from Yahoo Finance feeds,
              and apply net share adjustments or cash payouts directly to your ledger.
            </Typography>
          </Box>

          <Stack direction="row" spacing={1.5}>
            <Button
              variant="outlined"
              startIcon={<ArrowBackIcon />}
              onClick={() => navigate(`/portfolios/${portfolioId}`)}
            >
              Back to Portfolio
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={scanMutation.isPending ? <CircularProgress size={18} color="inherit" /> : <PlayArrowIcon />}
              onClick={handleScan}
              disabled={scanMutation.isPending}
              sx={{ fontWeight: 700, px: 2.5 }}
            >
              {scanMutation.isPending ? 'Scanning Market Feeds...' : 'Scan For Actions'}
            </Button>
          </Stack>
        </Stack>

        <Divider sx={{ my: 2 }} />

        {/* Quick Stats Grid */}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined" sx={{ bgcolor: alpha(theme.palette.warning.main, 0.04) }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">
                  Pending Actions
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 800, color: theme.palette.warning.main, mt: 0.5 }}>
                  {pendingCount}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined" sx={{ bgcolor: alpha(theme.palette.success.main, 0.04) }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">
                  Applied to Ledger
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 800, color: theme.palette.success.main, mt: 0.5 }}>
                  {appliedCount}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <Card variant="outlined" sx={{ bgcolor: 'background.default' }}>
              <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">
                  Dismissed
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 800, color: 'text.secondary', mt: 0.5 }}>
                  {dismissedCount}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Paper>

      {/* Tabs and Filters */}
      <Paper sx={{ p: 2, mb: 3, borderRadius: 3 }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          sx={{
            justifyContent: 'space-between',
            alignItems: { sm: 'center' },
            gap: 2,
            mb: 2,
          }}
        >
          <Tabs
            value={currentTab}
            onChange={(_, val) => setCurrentTab(val)}
            variant="scrollable"
            scrollButtons="auto"
          >
            <Tab label={`Pending (${pendingCount})`} value="PENDING" />
            <Tab label={`Applied (${appliedCount})`} value="APPLIED" />
            <Tab label={`Dismissed (${dismissedCount})`} value="DISMISSED" />
            <Tab label="All Actions" value="ALL" />
          </Tabs>

          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600 }}>
              Action Type:
            </Typography>
            <Select
              size="small"
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value as any)}
              sx={{ minWidth: 150, fontSize: '0.85rem' }}
            >
              <MenuItem value="ALL">All Types</MenuItem>
              <MenuItem value="STOCK_SPLIT">Stock Splits</MenuItem>
              <MenuItem value="REVERSE_STOCK_SPLIT">Reverse Splits</MenuItem>
              <MenuItem value="DIVIDEND">Dividends</MenuItem>
            </Select>
          </Stack>
        </Stack>

        {/* Corporate Actions Table */}
        {filteredActions.length === 0 ? (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <InfoOutlinedIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
            <Typography variant="h6" color="text.secondary" sx={{ fontWeight: 600 }}>
              No corporate actions found for this tab
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Click "Scan For Actions" above to query market data feeds for any splits or dividends.
            </Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Instrument</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Type</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Ex-Date</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Terms / Rate</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Held Shares</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Proposed Impact</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Target Account</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredActions.map((act) => {
                  const isSplit = act.actionType === 'STOCK_SPLIT' || act.actionType === 'REVERSE_STOCK_SPLIT';
                  return (
                    <TableRow key={act.id} hover>
                      <TableCell>
                        <Box>
                          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                              {act.ticker || '—'}
                              {act.resultingInstrumentTicker && ` → ${act.resultingInstrumentTicker}`}
                            </Typography>
                            {act.isin && (
                              <Typography variant="caption" color="text.secondary">
                                ({act.isin})
                              </Typography>
                            )}
                          </Stack>
                          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', maxWidth: 220 }}>
                            {act.instrumentName}
                            {act.resultingInstrumentName && ` → ${act.resultingInstrumentName}`}
                          </Typography>
                        </Box>
                      </TableCell>

                      <TableCell>{renderTypeChip(act.actionType)}</TableCell>

                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {new Date(act.exDate).toLocaleDateString()}
                        </Typography>
                        {act.paymentDate && (
                          <Typography variant="caption" color="text.secondary">
                            Pay: {new Date(act.paymentDate).toLocaleDateString()}
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell>
                        {isSplit ? (
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {act.ratioTo}:{act.ratioFrom}
                          </Typography>
                        ) : (
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {act.amountPerShare?.toFixed(4)} {act.currency}
                          </Typography>
                        )}
                        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', maxWidth: 160 }}>
                          {act.description}
                        </Typography>
                      </TableCell>

                      <TableCell align="right">
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {act.heldQuantityAtExDate != null ? Number(act.heldQuantityAtExDate).toFixed(4) : '0.0000'}
                        </Typography>
                      </TableCell>

                      <TableCell align="right">
                        {isSplit ? (
                          <Typography
                            variant="body2"
                            sx={{
                              fontWeight: 700,
                              color: act.actionType === 'STOCK_SPLIT' ? 'primary.main' : 'secondary.main',
                            }}
                          >
                            {act.actionType === 'STOCK_SPLIT' ? '+' : '-'}
                            {act.proposedImpactQuantity != null ? Number(act.proposedImpactQuantity).toFixed(4) : '0.0000'} shs
                            {act.resultingInstrumentTicker ? ` ${act.resultingInstrumentTicker}` : ''}
                          </Typography>
                        ) : (
                          <Typography variant="body2" sx={{ fontWeight: 700, color: 'success.main' }}>
                            +{act.proposedImpactAmount != null ? Number(act.proposedImpactAmount).toFixed(2) : '0.00'} {act.currency}
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell>
                        <Typography variant="body2">
                          {act.suggestedAccountName || 'None'}
                        </Typography>
                      </TableCell>

                      <TableCell>{renderStatusChip(act.status)}</TableCell>

                      <TableCell align="right">
                        {act.status === 'PENDING' ? (
                          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                            <Button
                              size="small"
                              variant="contained"
                              color="primary"
                              onClick={() => handleOpenApply(act)}
                              sx={{ fontWeight: 700 }}
                            >
                              Apply
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              color="inherit"
                              onClick={() => handleOpenDismiss(act)}
                            >
                              Dismiss
                            </Button>
                          </Stack>
                        ) : act.status === 'APPLIED' ? (
                          <Tooltip title={`Applied Transaction: ${act.appliedTransactionId || 'Ledger entry'}`}>
                            <Chip size="small" label="Settled" color="success" variant="outlined" />
                          </Tooltip>
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            Dismissed
                          </Typography>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {/* Apply Action Dialog */}
      <Dialog open={applyDialogOpen} onClose={() => setApplyDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>
          Apply Corporate Action: {selectedAction?.actionType.replace('_', ' ')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            This will record a {selectedAction?.actionType} transaction in the ledger, recalculating position shares and cost basis.
          </DialogContentText>

          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <Box sx={{ p: 2, bgcolor: alpha(theme.palette.info.main, 0.08), borderRadius: 2 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                {selectedAction?.ticker} — {selectedAction?.instrumentName}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Ex-Date: {selectedAction ? new Date(selectedAction.exDate).toLocaleDateString() : ''}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Held Shares at Ex-Date: {selectedAction?.heldQuantityAtExDate ? Number(selectedAction.heldQuantityAtExDate).toFixed(4) : '0.0000'}
              </Typography>
            </Box>

            {selectedAction?.resultingInstrumentTicker && (
              <Alert severity="info" sx={{ borderRadius: 2 }}>
                Spin-off allotment: applying this corporate action will create a holding of{' '}
                <strong>{selectedAction.resultingInstrumentTicker} ({selectedAction.resultingInstrumentName || 'Honeywell Aerospace'})</strong>{' '}
                in your selected account.
              </Alert>
            )}

            <FormControl fullWidth size="small">
              <InputLabel id="select-account-label">Target Account</InputLabel>
              <Select
                labelId="select-account-label"
                value={selectedAccountId}
                label="Target Account"
                onChange={(e) => setSelectedAccountId(e.target.value)}
              >
                {accounts.map((acc) => (
                  <MenuItem key={acc.id} value={acc.id}>
                    {acc.name} ({acc.brokerName} - {acc.accountCurrency})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {(selectedAction?.actionType === 'STOCK_SPLIT' || selectedAction?.actionType === 'REVERSE_STOCK_SPLIT') ? (
              <TextField
                label={selectedAction?.resultingInstrumentTicker ? `Allotment Quantity (${selectedAction.resultingInstrumentTicker} Shares)` : "Adjustment Quantity (Net Shares)"}
                type="number"
                size="small"
                value={customQuantity}
                onChange={(e) => setCustomQuantity(e.target.value)}
                helperText={selectedAction?.resultingInstrumentTicker ? `Calculated allotment of ${selectedAction.resultingInstrumentTicker} shares based on distribution ratio.` : "Pre-calculated net adjustment shares based on split ratio."}
                fullWidth
              />
            ) : (
              <Stack direction="row" spacing={2}>
                <TextField
                  label="Gross Dividend Amount"
                  type="number"
                  size="small"
                  value={customGrossAmount}
                  onChange={(e) => setCustomGrossAmount(e.target.value)}
                  fullWidth
                />
                <TextField
                  label="Withholding Tax"
                  type="number"
                  size="small"
                  value={customTaxAmount}
                  onChange={(e) => setCustomTaxAmount(e.target.value)}
                  fullWidth
                />
              </Stack>
            )}

            <TextField
              label="Transaction Notes"
              size="small"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              fullWidth
              multiline
              rows={2}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setApplyDialogOpen(false)} color="inherit">
            Cancel
          </Button>
          <Button
            onClick={handleConfirmApply}
            variant="contained"
            color="primary"
            disabled={applyMutation.isPending || !selectedAccountId}
            sx={{ fontWeight: 700 }}
          >
            {applyMutation.isPending ? 'Applying...' : 'Confirm & Ingest'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dismiss Action Dialog */}
      <Dialog open={dismissDialogOpen} onClose={() => setDismissDialogOpen(false)}>
        <DialogTitle sx={{ fontWeight: 700 }}>Dismiss Corporate Action</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to dismiss the {selectedAction?.actionType} for {selectedAction?.ticker || selectedAction?.instrumentName}?
            It will not create any ledger transactions.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDismissDialogOpen(false)} color="inherit">
            Cancel
          </Button>
          <Button
            onClick={handleConfirmDismiss}
            variant="contained"
            color="warning"
            disabled={dismissMutation.isPending}
            sx={{ fontWeight: 700 }}
          >
            {dismissMutation.isPending ? 'Dismissing...' : 'Dismiss Action'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Notification Snackbar */}
      <Snackbar
        open={scanSnackbar.open}
        autoHideDuration={6000}
        onClose={() => setScanSnackbar((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setScanSnackbar((prev) => ({ ...prev, open: false }))}
          severity={scanSnackbar.severity}
          sx={{ width: '100%', borderRadius: 2, fontWeight: 600 }}
        >
          {scanSnackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}

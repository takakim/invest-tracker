import React, { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Grid,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';

import {
  usePortfoliosList,
  useCreatePortfolio,
  useUpdatePortfolio,
  useArchivePortfolio,
} from './usePortfolios';
import { PortfolioFormModal } from './PortfolioFormModal';
import { ConfirmDialog, EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { Portfolio, PortfolioCreateInput } from '../../types';

export function PortfolioListPage() {
  const { data: portfolios = [], isLoading, error, refetch } = usePortfoliosList();
  const createMutation = useCreatePortfolio();
  const updateMutation = useUpdatePortfolio();
  const archiveMutation = useArchivePortfolio();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingPortfolio, setEditingPortfolio] = useState<Portfolio | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Portfolio | null>(null);

  const handleOpenCreate = () => {
    setEditingPortfolio(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (portfolio: Portfolio) => {
    setEditingPortfolio(portfolio);
    setModalOpen(true);
  };

  const handleFormSubmit = async (formData: PortfolioCreateInput) => {
    if (editingPortfolio) {
      await updateMutation.mutateAsync(
        { id: editingPortfolio.id, input: formData },
        {
          onSuccess: () => setModalOpen(false),
        },
      );
    } else {
      await createMutation.mutateAsync(formData, {
        onSuccess: () => setModalOpen(false),
      });
    }
  };

  const handleConfirmArchive = async () => {
    if (!archiveTarget) return;
    await archiveMutation.mutateAsync(archiveTarget.id, {
      onSuccess: () => setArchiveTarget(null),
    });
  };

  return (
    <Box>
      {/* Header */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 4, gap: 2 }}
      >
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Portfolios
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage your investment portfolios, reporting currencies, and calculation methods.
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleOpenCreate}
          sx={{ alignSelf: { xs: 'flex-start', sm: 'auto' } }}
        >
          New Portfolio
        </Button>
      </Stack>

      <ErrorAlert error={error} onClose={() => refetch()} />

      {isLoading ? (
        <LoadingState variant="skeleton" count={3} />
      ) : portfolios.length === 0 ? (
        <EmptyState
          title="No Portfolios Found"
          description="Create your first investment portfolio to begin organizing your brokerage and custodian accounts."
          actionLabel="Create Portfolio"
          onAction={handleOpenCreate}
          icon={<AccountBalanceWalletOutlinedIcon sx={{ fontSize: 56, opacity: 0.7 }} />}
        />
      ) : (
        <Grid container spacing={3}>
          {portfolios.map((portfolio) => (
            <Grid size={{ xs: 12, md: 6 }} key={portfolio.id}>
              <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                <CardContent sx={{ flexGrow: 1, p: 3 }}>
                  <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 600 }}>
                        {portfolio.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        ID: {portfolio.id.substring(0, 8)}...
                      </Typography>
                    </Box>
                    <Chip
                      label={portfolio.status}
                      size="small"
                      color={portfolio.status === 'ACTIVE' ? 'success' : 'default'}
                      variant={portfolio.status === 'ACTIVE' ? 'filled' : 'outlined'}
                    />
                  </Stack>

                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1, mt: 2 }}>
                    <Chip
                      label={`Currency: ${portfolio.baseCurrency}`}
                      size="small"
                      variant="outlined"
                      sx={{ fontWeight: 600 }}
                    />
                    <Chip
                      label={`Cost Basis: ${portfolio.costBasisMethod}`}
                      size="small"
                      variant="outlined"
                    />
                    <Chip
                      label={`Return: ${portfolio.returnMethod}`}
                      size="small"
                      variant="outlined"
                    />
                  </Stack>
                </CardContent>

                <CardActions sx={{ px: 3, pb: 2.5, pt: 0, justifyContent: 'space-between' }}>
                  <Stack direction="row" spacing={0.5}>
                    <Tooltip title="Edit settings">
                      <IconButton
                        size="small"
                        onClick={() => handleOpenEdit(portfolio)}
                        aria-label={`edit ${portfolio.name}`}
                      >
                        <EditOutlinedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Archive portfolio">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => setArchiveTarget(portfolio)}
                        aria-label={`archive ${portfolio.name}`}
                      >
                        <ArchiveOutlinedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>

                  <Button
                    component={RouterLink}
                    to={`/portfolios/${portfolio.id}`}
                    endIcon={<ArrowForwardIcon />}
                    size="small"
                  >
                    View Accounts
                  </Button>
                </CardActions>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {/* Create / Edit Modal */}
      <PortfolioFormModal
        open={modalOpen}
        portfolio={editingPortfolio}
        isPending={createMutation.isPending || updateMutation.isPending}
        error={createMutation.error || updateMutation.error}
        onClose={() => setModalOpen(false)}
        onSubmit={handleFormSubmit}
      />

      {/* Archive Confirmation Modal */}
      <ConfirmDialog
        open={Boolean(archiveTarget)}
        title="Archive Portfolio"
        message={`Are you sure you want to archive "${archiveTarget?.name}"? Financial history is preserved, but no new accounts can be added.`}
        confirmLabel="Archive"
        confirmColor="error"
        isPending={archiveMutation.isPending}
        onConfirm={handleConfirmArchive}
        onCancel={() => setArchiveTarget(null)}
      />
    </Box>
  );
}

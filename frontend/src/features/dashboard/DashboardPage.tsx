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
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';

import { usePortfoliosList, useCreatePortfolio } from '../portfolios/usePortfolios';
import { useInstrumentsList } from '../instruments/useInstruments';
import { PortfolioFormModal } from '../portfolios/PortfolioFormModal';
import { EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { PortfolioCreateInput } from '../../types';

export function DashboardPage() {
  const { data: portfolios = [], isLoading: isPortfoliosLoading, error: portfoliosError } = usePortfoliosList();
  const { data: instruments = [], isLoading: isInstrumentsLoading } = useInstrumentsList();
  const createPortfolioMutation = useCreatePortfolio();

  const [portfolioModalOpen, setPortfolioModalOpen] = useState(false);

  const handleCreatePortfolioSubmit = async (formData: PortfolioCreateInput) => {
    await createPortfolioMutation.mutateAsync(formData, {
      onSuccess: () => setPortfolioModalOpen(false),
    });
  };

  const activePortfolios = portfolios.filter((p) => p.status === 'ACTIVE');

  return (
    <Box>
      {/* Header */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 4, gap: 2 }}
      >
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Dashboard
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Overview of your investment portfolios, accounts, and tracked instruments.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setPortfolioModalOpen(true)}
          >
            New Portfolio
          </Button>
          <Button
            component={RouterLink}
            to="/instruments"
            variant="outlined"
            startIcon={<ShowChartOutlinedIcon />}
          >
            Instruments
          </Button>
          <Button
            component={RouterLink}
            to="/market"
            variant="outlined"
            startIcon={<AssessmentOutlinedIcon />}
          >
            Market & FX
          </Button>
        </Stack>
      </Stack>

      <ErrorAlert error={portfoliosError} />

      {/* Metrics Row */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <Card sx={{ p: 1 }}>
            <CardContent>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                <Box
                  sx={{
                    p: 1.5,
                    borderRadius: 2,
                    bgcolor: 'primary.main',
                    color: 'primary.contrastText',
                    display: 'flex',
                  }}
                >
                  <AccountBalanceWalletOutlinedIcon sx={{ fontSize: 28 }} />
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Active Portfolios
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 700 }}>
                    {isPortfoliosLoading ? '—' : activePortfolios.length}
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <Card sx={{ p: 1 }}>
            <CardContent>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                <Box
                  sx={{
                    p: 1.5,
                    borderRadius: 2,
                    bgcolor: 'secondary.main',
                    color: 'secondary.contrastText',
                    display: 'flex',
                  }}
                >
                  <ShowChartOutlinedIcon sx={{ fontSize: 28 }} />
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Tracked Instruments
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 700 }}>
                    {isInstrumentsLoading ? '—' : instruments.length}
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 4 }}>
          <Card sx={{ p: 1 }}>
            <CardContent>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                <Box
                  sx={{
                    p: 1.5,
                    borderRadius: 2,
                    bgcolor: 'success.main',
                    color: 'success.contrastText',
                    display: 'flex',
                  }}
                >
                  <AssessmentOutlinedIcon sx={{ fontSize: 28 }} />
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Platform Status
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                    Production Ready
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Portfolios Section */}
      <Box sx={{ mb: 4 }}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}
        >
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            Your Portfolios
          </Typography>
          <Button
            component={RouterLink}
            to="/portfolios"
            endIcon={<ArrowForwardIcon />}
            size="small"
          >
            View All Portfolios
          </Button>
        </Stack>

        {isPortfoliosLoading ? (
          <LoadingState variant="skeleton" count={2} />
        ) : activePortfolios.length === 0 ? (
          <EmptyState
            title="No Portfolios Configured"
            description="Create your first investment portfolio to configure your base currency and manage accounts."
            actionLabel="Create Portfolio"
            onAction={() => setPortfolioModalOpen(true)}
            icon={<AccountBalanceWalletOutlinedIcon sx={{ fontSize: 52, opacity: 0.7 }} />}
          />
        ) : (
          <Grid container spacing={3}>
            {activePortfolios.slice(0, 4).map((portfolio) => (
              <Grid size={{ xs: 12, md: 6 }} key={portfolio.id}>
                <Paper sx={{ p: 3, borderRadius: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
                  <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 600 }}>
                        {portfolio.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Base Currency: {portfolio.baseCurrency}
                      </Typography>
                    </Box>
                    <Chip label={portfolio.status} size="small" color="success" />
                  </Stack>

                  <Stack direction="row" spacing={1} sx={{ my: 'auto', py: 1 }}>
                    <Chip label={`Cost: ${portfolio.costBasisMethod}`} size="small" variant="outlined" />
                    <Chip label={`Return: ${portfolio.returnMethod}`} size="small" variant="outlined" />
                  </Stack>

                  <Box sx={{ pt: 2, mt: 'auto', display: 'flex', justifyContent: 'flex-end' }}>
                    <Button
                      component={RouterLink}
                      to={`/portfolios/${portfolio.id}`}
                      endIcon={<ArrowForwardIcon />}
                      size="small"
                      variant="outlined"
                    >
                      Open Portfolio
                    </Button>
                  </Box>
                </Paper>
              </Grid>
            ))}
          </Grid>
        )}
      </Box>

      {/* Portfolio Create Modal */}
      <PortfolioFormModal
        open={portfolioModalOpen}
        isPending={createPortfolioMutation.isPending}
        error={createPortfolioMutation.error}
        onClose={() => setPortfolioModalOpen(false)}
        onSubmit={handleCreatePortfolioSubmit}
      />
    </Box>
  );
}

import React, { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Paper,
  Stack,
  Typography,
  alpha,
  useTheme,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import PieChartOutlineOutlinedIcon from '@mui/icons-material/PieChartOutlineOutlined';
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined';

import { usePortfoliosList, useCreatePortfolio } from '../portfolios/usePortfolios';
import { PortfolioFormModal } from '../portfolios/PortfolioFormModal';
import { EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { PortfolioCreateInput } from '../../types';

// Benchmark and FX market pulse snapshot
const MARKET_SNAPSHOT = [
  { symbol: 'S&P 500', name: 'SPY', price: '596.20', change: '+0.84%', positive: true },
  { symbol: 'NASDAQ 100', name: 'QQQ', price: '508.40', change: '+1.15%', positive: true },
  { symbol: 'FTSE All-World', name: 'VWRP', price: '98.50', change: '+0.42%', positive: true },
  { symbol: 'Apple', name: 'AAPL', price: '232.80', change: '+1.60%', positive: true },
  { symbol: 'Nvidia', name: 'NVDA', price: '138.25', change: '+2.45%', positive: true },
  { symbol: 'GBP / USD', name: 'FX', price: '1.2840', change: '+0.12%', positive: true },
];

export function DashboardPage() {
  const theme = useTheme();
  const { data: portfolios = [], isLoading: isPortfoliosLoading, error: portfoliosError } = usePortfoliosList();
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
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 3.5, gap: 2 }}
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

      {/* Market Ticker Strip */}
      <Paper
        sx={{
          p: 1.5,
          mb: 3.5,
          borderRadius: 2.5,
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          overflowX: 'auto',
          bgcolor: theme.palette.mode === 'dark' ? alpha(theme.palette.background.paper, 0.6) : '#FFFFFF',
          border: `1px solid ${theme.palette.divider}`,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, pl: 1, minWidth: 'max-content' }}>
          <TrendingUpOutlinedIcon sx={{ fontSize: 18, color: 'primary.main' }} />
          <Typography variant="caption" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'text.secondary' }}>
            Market Pulse
          </Typography>
        </Box>
        <Box sx={{ width: '1px', height: 20, bgcolor: 'divider' }} />
        <Stack direction="row" spacing={2.5} sx={{ minWidth: 'max-content', py: 0.25 }}>
          {MARKET_SNAPSHOT.map((item) => (
            <Stack key={item.name} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="caption" sx={{ fontWeight: 600 }}>
                {item.name}
              </Typography>
              <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>
                {item.price}
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  fontWeight: 600,
                  fontSize: '0.7rem',
                  color: item.positive ? 'success.main' : 'error.main',
                  bgcolor: item.positive ? alpha(theme.palette.success.main, 0.1) : alpha(theme.palette.error.main, 0.1),
                  px: 0.75,
                  py: 0.1,
                  borderRadius: 1,
                }}
              >
                {item.change}
              </Typography>
            </Stack>
          ))}
        </Stack>
      </Paper>

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
                    Asset Master Data
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5 }}>
                    Active & Tracked
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
                <Paper
                  sx={{
                    p: 3,
                    borderRadius: 3,
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    transition: 'transform 0.2s ease, border-color 0.2s ease',
                    '&:hover': {
                      borderColor: 'primary.main',
                      transform: 'translateY(-2px)',
                    },
                  }}
                >
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

                  <Stack direction="row" spacing={1} sx={{ my: 1, flexWrap: 'wrap', gap: 1 }}>
                    <Chip label={`Cost: ${portfolio.costBasisMethod}`} size="small" variant="outlined" />
                    <Chip label={`Return: ${portfolio.returnMethod}`} size="small" variant="outlined" />
                  </Stack>

                  {/* Portfolio Quick Navigation Links */}
                  <Stack direction="row" spacing={1} sx={{ pt: 1.5, pb: 1, flexWrap: 'wrap', gap: 1 }}>
                    <Button
                      component={RouterLink}
                      to={`/portfolios/${portfolio.id}/performance`}
                      size="small"
                      variant="text"
                      startIcon={<TrendingUpOutlinedIcon sx={{ fontSize: 16 }} />}
                      sx={{ fontSize: '0.78rem', py: 0.5, px: 1 }}
                    >
                      Performance
                    </Button>
                    <Button
                      component={RouterLink}
                      to={`/portfolios/${portfolio.id}/allocation`}
                      size="small"
                      variant="text"
                      startIcon={<PieChartOutlineOutlinedIcon sx={{ fontSize: 16 }} />}
                      sx={{ fontSize: '0.78rem', py: 0.5, px: 1 }}
                    >
                      Allocation
                    </Button>
                    <Button
                      component={RouterLink}
                      to={`/portfolios/${portfolio.id}/rebalancing`}
                      size="small"
                      variant="text"
                      startIcon={<TuneOutlinedIcon sx={{ fontSize: 16 }} />}
                      sx={{ fontSize: '0.78rem', py: 0.5, px: 1 }}
                    >
                      Rebalance
                    </Button>
                  </Stack>

                  <Box sx={{ pt: 2, mt: 'auto', display: 'flex', justifyContent: 'flex-end' }}>
                    <Button
                      component={RouterLink}
                      to={`/portfolios/${portfolio.id}`}
                      endIcon={<ArrowForwardIcon />}
                      size="small"
                      variant="contained"
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

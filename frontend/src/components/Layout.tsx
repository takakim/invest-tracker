import React, { useState } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import {
  Alert,
  AppBar,
  Box,
  Button,
  Chip,
  Container,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  ListSubheader,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import CurrencyExchangeIcon from '@mui/icons-material/CurrencyExchange';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined';

import { ConfirmDialog } from './ConfirmDialog';
import { useResetDatabase } from '../features/system/useSystem';
import { useAppTheme } from '../theme';
import { usePortfoliosList } from '../features/portfolios/usePortfolios';

interface LayoutProps {
  children: React.ReactNode;
}

const NAV_ITEMS = [
  { label: 'Dashboard', path: '/', icon: <DashboardOutlinedIcon /> },
  { label: 'Portfolios', path: '/portfolios', icon: <AccountBalanceWalletOutlinedIcon /> },
  { label: 'Instruments', path: '/instruments', icon: <ShowChartOutlinedIcon /> },
  { label: 'Market & FX', path: '/market', icon: <CurrencyExchangeIcon /> },
];

const DRAWER_WIDTH = 250;

export function Layout({ children }: LayoutProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { mode, toggleTheme } = useAppTheme();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);

  const resetMutation = useResetDatabase();
  const { data: portfolios = [] } = usePortfoliosList();
  const activePortfolios = portfolios.filter((p) => p.status === 'ACTIVE');

  // Check active portfolio from URL if on /portfolios/:id
  const pathParts = location.pathname.split('/');
  const activePortfolioId = pathParts[1] === 'portfolios' && pathParts[2] && pathParts[2] !== 'new' ? pathParts[2] : '';

  const handleResetConfirm = () => {
    resetMutation.mutate('RESET', {
      onSuccess: () => {
        setResetDialogOpen(false);
        setSnackbarMessage('Database has been completely reset.');
        navigate('/');
      },
    });
  };

  const isCurrent = (path: string) => {
    if (path === '/') return location.pathname === '/';
    return location.pathname.startsWith(path);
  };

  const drawerContent = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Brand Header */}
      <Toolbar sx={{ px: 2.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 36,
              height: 36,
              borderRadius: 2,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'primary.main',
              color: 'primary.contrastText',
            }}
          >
            <TrendingUpIcon sx={{ fontSize: 22 }} />
          </Box>
          <Box>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
              Invest Tracker
            </Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '0.7rem' }}>
              Multi-Currency Ledger
            </Typography>
          </Box>
        </Box>
      </Toolbar>
      <Divider />

      {/* Main Navigation */}
      <List sx={{ px: 1.5, py: 1.5 }}>
        <ListSubheader
          sx={{
            bgcolor: 'transparent',
            color: 'text.secondary',
            fontSize: '0.72rem',
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '0.08em',
            px: 1.5,
            py: 0.5,
            lineHeight: '20px',
          }}
        >
          Overview
        </ListSubheader>
        {NAV_ITEMS.map((item) => {
          const active = isCurrent(item.path);
          return (
            <ListItemButton
              key={item.path}
              component={RouterLink}
              to={item.path}
              onClick={() => setMobileOpen(false)}
              selected={active}
              sx={{
                borderRadius: 2,
                mb: 0.5,
                py: 1,
                '&.Mui-selected': {
                  backgroundColor: 'primary.main',
                  color: 'primary.contrastText',
                  '&:hover': {
                    backgroundColor: 'primary.dark',
                  },
                  '& .MuiListItemIcon-root': {
                    color: 'primary.contrastText',
                  },
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 36, color: active ? 'inherit' : 'text.secondary' }}>
                {item.icon}
              </ListItemIcon>
              <ListItemText
                primary={item.label}
                slotProps={{
                  primary: {
                    sx: { fontSize: '0.88rem', fontWeight: active ? 600 : 500 },
                  },
                }}
              />
            </ListItemButton>
          );
        })}
      </List>

      {/* Quick Portfolios Section */}
      {activePortfolios.length > 0 && (
        <List sx={{ px: 1.5, py: 0.5 }}>
          <Divider sx={{ my: 1 }} />
          <ListSubheader
            sx={{
              bgcolor: 'transparent',
              color: 'text.secondary',
              fontSize: '0.72rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              px: 1.5,
              py: 0.5,
              lineHeight: '20px',
            }}
          >
            Active Portfolios
          </ListSubheader>
          {activePortfolios.slice(0, 3).map((p) => {
            const isSelected = activePortfolioId === p.id;
            return (
              <ListItemButton
                key={p.id}
                component={RouterLink}
                to={`/portfolios/${p.id}`}
                onClick={() => setMobileOpen(false)}
                selected={isSelected}
                sx={{
                  borderRadius: 2,
                  mb: 0.5,
                  py: 0.75,
                  '&.Mui-selected': {
                    bgcolor: (theme) => (theme.palette.mode === 'dark' ? 'rgba(99, 102, 241, 0.2)' : 'rgba(79, 70, 229, 0.08)'),
                    color: 'primary.main',
                    fontWeight: 600,
                  },
                }}
              >
                <ListItemText
                  primary={p.name}
                  secondary={`${p.baseCurrency} • ${p.costBasisMethod}`}
                  slotProps={{
                    primary: {
                      noWrap: true,
                      sx: { fontSize: '0.84rem', fontWeight: isSelected ? 600 : 500 },
                    },
                    secondary: {
                      sx: { fontSize: '0.72rem' },
                    },
                  }}
                />
              </ListItemButton>
            );
          })}
        </List>
      )}

      {/* Footer System Box */}
      <Box sx={{ mt: 'auto', p: 2, borderTop: 1, borderColor: 'divider' }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
          <Chip
            icon={<FiberManualRecordIcon sx={{ fontSize: '10px !important', color: 'success.main' }} />}
            label="API Connected"
            size="small"
            variant="outlined"
            sx={{ fontSize: '0.72rem', height: 24 }}
          />
          <Tooltip title={mode === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}>
            <IconButton size="small" onClick={toggleTheme} color="inherit">
              {mode === 'dark' ? <LightModeOutlinedIcon fontSize="small" /> : <DarkModeOutlinedIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </Stack>

        <Button
          fullWidth
          variant="outlined"
          color="error"
          size="small"
          startIcon={<RestartAltIcon />}
          onClick={() => setResetDialogOpen(true)}
          sx={{ textTransform: 'none', borderRadius: 2, fontSize: '0.8rem', py: 0.75 }}
        >
          Reset Database
        </Button>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', backgroundColor: 'background.default' }}>
      {/* Top Application Header Bar */}
      <AppBar
        position="fixed"
        sx={{
          zIndex: (theme) => theme.zIndex.drawer + 1,
          backgroundColor: 'background.paper',
          color: 'text.primary',
          borderBottom: 1,
          borderColor: 'divider',
          boxShadow: 'none',
        }}
      >
        <Toolbar sx={{ minHeight: { xs: 56, sm: 64 }, px: { xs: 2, sm: 3 } }}>
          {isMobile && (
            <IconButton
              color="inherit"
              edge="start"
              onClick={() => setMobileOpen(!mobileOpen)}
              sx={{ mr: 2 }}
              aria-label="open drawer"
            >
              <MenuIcon />
            </IconButton>
          )}

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mr: 2 }}>
            <TrendingUpIcon color="primary" sx={{ fontSize: 26 }} />
            <Typography variant="h6" sx={{ fontWeight: 700, display: { xs: 'none', sm: 'block' } }}>
              Invest Tracker
            </Typography>
          </Box>

          {/* Quick Portfolio Switcher */}
          {activePortfolios.length > 0 && (
            <Box sx={{ display: { xs: 'none', md: 'flex' }, alignItems: 'center', ml: 2 }}>
              <Select
                size="small"
                value={activePortfolioId || ''}
                displayEmpty
                onChange={(e) => {
                  const val = e.target.value;
                  if (val) navigate(`/portfolios/${val}`);
                }}
                sx={{
                  minWidth: 200,
                  fontSize: '0.85rem',
                  height: 36,
                  borderRadius: 2,
                  '& .MuiSelect-select': { py: 0.75 },
                }}
              >
                <MenuItem value="" disabled>
                  <em>Switch Portfolio...</em>
                </MenuItem>
                {activePortfolios.map((p) => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.name} ({p.baseCurrency})
                  </MenuItem>
                ))}
              </Select>
            </Box>
          )}

          <Box sx={{ flexGrow: 1 }} />

          {/* Header Action Tools */}
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <Chip
              icon={<AutoAwesomeOutlinedIcon sx={{ fontSize: '14px !important', color: 'primary.main' }} />}
              label="AI Insights Ready"
              size="small"
              variant="outlined"
              sx={{ display: { xs: 'none', sm: 'inline-flex' }, height: 26, fontSize: '0.75rem' }}
            />

            <Tooltip title={mode === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}>
              <IconButton onClick={toggleTheme} color="inherit" size="small" sx={{ p: 1 }}>
                {mode === 'dark' ? <LightModeOutlinedIcon fontSize="small" /> : <DarkModeOutlinedIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </Stack>
        </Toolbar>
      </AppBar>

      {/* Navigation Drawer */}
      <Box
        component="nav"
        sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}
        aria-label="navigation"
      >
        {isMobile ? (
          <Drawer
            variant="temporary"
            open={mobileOpen}
            onClose={() => setMobileOpen(false)}
            ModalProps={{ keepMounted: true }}
            sx={{
              '& .MuiDrawer-paper': { boxSizing: 'border-box', width: DRAWER_WIDTH },
            }}
          >
            {drawerContent}
          </Drawer>
        ) : (
          <Drawer
            variant="permanent"
            sx={{
              '& .MuiDrawer-paper': {
                boxSizing: 'border-box',
                width: DRAWER_WIDTH,
                borderRight: '1px solid',
                borderColor: 'divider',
                top: { xs: 56, sm: 64 },
                height: { xs: 'calc(100% - 56px)', sm: 'calc(100% - 64px)' },
              },
            }}
            open
          >
            {drawerContent}
          </Drawer>
        )}
      </Box>

      {/* Main Page Area */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 2, sm: 3, md: 4 },
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          mt: { xs: 8, sm: 9 },
        }}
      >
        <Container maxWidth="lg" sx={{ px: { xs: 0, sm: 2 } }}>
          {children}
        </Container>
      </Box>

      {/* Reset Database Confirmation Dialog */}
      <ConfirmDialog
        open={resetDialogOpen}
        title="Reset Entire Database?"
        message="This will permanently delete all portfolios, accounts, holdings, transactions, CSV import batches, and market observations. This action CANNOT be undone."
        confirmLabel="Reset Database"
        confirmColor="error"
        isPending={resetMutation.isPending}
        onConfirm={handleResetConfirm}
        onCancel={() => setResetDialogOpen(false)}
      />

      {/* Feedback Snackbar */}
      <Snackbar
        open={Boolean(snackbarMessage)}
        autoHideDuration={4000}
        onClose={() => setSnackbarMessage(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbarMessage(null)}
          severity="success"
          variant="filled"
          sx={{ width: '100%' }}
        >
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
}

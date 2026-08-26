import React, { useState } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import {
  Alert,
  AppBar,
  Box,
  Button,
  Container,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Snackbar,
  Toolbar,
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
import { ConfirmDialog } from './ConfirmDialog';
import { useResetDatabase } from '../features/system/useSystem';

interface LayoutProps {
  children: React.ReactNode;
}

const NAV_ITEMS = [
  { label: 'Dashboard', path: '/', icon: <DashboardOutlinedIcon /> },
  { label: 'Portfolios', path: '/portfolios', icon: <AccountBalanceWalletOutlinedIcon /> },
  { label: 'Instruments', path: '/instruments', icon: <ShowChartOutlinedIcon /> },
  { label: 'Market & FX', path: '/market', icon: <CurrencyExchangeIcon /> },
];

const DRAWER_WIDTH = 240;

export function Layout({ children }: LayoutProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState<string | null>(null);

  const resetMutation = useResetDatabase();

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
      <Toolbar sx={{ px: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <TrendingUpIcon color="primary" sx={{ fontSize: 28 }} />
        <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.main' }}>
          Invest Tracker
        </Typography>
      </Toolbar>
      <Divider />
      <List sx={{ px: 1.5, py: 2 }}>
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
              <ListItemIcon sx={{ minWidth: 38, color: active ? 'inherit' : 'text.secondary' }}>
                {item.icon}
              </ListItemIcon>
              <ListItemText
                primary={item.label}
                slotProps={{
                  primary: {
                    sx: { fontSize: '0.9rem', fontWeight: active ? 600 : 500 },
                  },
                }}
              />
            </ListItemButton>
          );
        })}
      </List>
      <Box sx={{ mt: 'auto', p: 2 }}>
        <Button
          fullWidth
          variant="outlined"
          color="error"
          size="small"
          startIcon={<RestartAltIcon />}
          onClick={() => setResetDialogOpen(true)}
          sx={{ mb: 1.5, textTransform: 'none', borderRadius: 2 }}
        >
          Reset Database
        </Button>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
          Invest Tracker v1.0.0
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', backgroundColor: 'background.default' }}>
      {/* App Bar for Mobile */}
      {isMobile && (
        <AppBar position="fixed" sx={{ zIndex: theme.zIndex.drawer + 1, backgroundColor: 'background.paper', color: 'text.primary', borderBottom: 1, borderColor: 'divider' }}>
          <Toolbar>
            <IconButton
              color="inherit"
              edge="start"
              onClick={() => setMobileOpen(!mobileOpen)}
              sx={{ mr: 2 }}
              aria-label="open drawer"
            >
              <MenuIcon />
            </IconButton>
            <TrendingUpIcon color="primary" sx={{ mr: 1 }} />
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              Invest Tracker
            </Typography>
          </Toolbar>
        </AppBar>
      )}

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
          mt: { xs: 8, md: 0 },
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

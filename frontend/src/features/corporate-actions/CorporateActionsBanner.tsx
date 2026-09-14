import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Chip,
  Stack,
  Typography,
  useTheme,
  alpha,
} from '@mui/material';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { useCorporateActions } from './useCorporateActions';

export interface CorporateActionsBannerProps {
  portfolioId: string;
}

export function CorporateActionsBanner({ portfolioId }: CorporateActionsBannerProps) {
  const theme = useTheme();
  const { data: pendingActions, isLoading } = useCorporateActions(portfolioId, 'PENDING');

  if (isLoading || !pendingActions || pendingActions.length === 0) {
    return null;
  }

  const count = pendingActions.length;
  const splitCount = pendingActions.filter(
    (a) => a.actionType === 'STOCK_SPLIT' || a.actionType === 'REVERSE_STOCK_SPLIT'
  ).length;
  const divCount = pendingActions.filter((a) => a.actionType === 'DIVIDEND').length;

  return (
    <Box sx={{ mb: 3 }} data-testid="corporate-actions-banner">
      <Alert
        severity="warning"
        icon={<AutoFixHighIcon sx={{ fontSize: 28, color: theme.palette.warning.main }} />}
        sx={{
          borderRadius: 3,
          border: `1px solid ${alpha(theme.palette.warning.main, 0.3)}`,
          bgcolor: alpha(theme.palette.warning.main, 0.08),
          p: 2,
          '& .MuiAlert-message': { width: '100%' },
        }}
      >
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          sx={{
            justifyContent: 'space-between',
            alignItems: { xs: 'flex-start', sm: 'center' },
            gap: 2,
          }}
        >
          <Box>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
              <AlertTitle sx={{ mb: 0, fontWeight: 700, fontSize: '1rem' }}>
                Pending Corporate Actions Detected ({count})
              </AlertTitle>
              {splitCount > 0 && (
                <Chip
                  label={`${splitCount} Split${splitCount > 1 ? 's' : ''}`}
                  size="small"
                  color="info"
                  sx={{ height: 22, fontWeight: 600, fontSize: '0.75rem' }}
                />
              )}
              {divCount > 0 && (
                <Chip
                  label={`${divCount} Dividend${divCount > 1 ? 's' : ''}`}
                  size="small"
                  color="success"
                  sx={{ height: 22, fontWeight: 600, fontSize: '0.75rem' }}
                />
              )}
            </Stack>
            <Typography variant="body2" color="text.secondary">
              Recent stock splits or dividend distributions were detected for instruments in this portfolio.
              Review and apply them to update your position quantities and cash balance.
            </Typography>
          </Box>

          <Button
            component={RouterLink}
            to={`/portfolios/${portfolioId}/corporate-actions`}
            variant="contained"
            color="warning"
            size="small"
            endIcon={<ArrowForwardIcon />}
            sx={{ fontWeight: 700, whiteSpace: 'nowrap', px: 2, py: 1, borderRadius: 2 }}
          >
            Review & Ingest
          </Button>
        </Stack>
      </Alert>
    </Box>
  );
}

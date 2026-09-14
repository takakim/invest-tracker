import React from 'react';
import {
  Box,
  Button,
  Chip,
  Container,
  Stack,
  Typography,
  useTheme,
  alpha,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import AddIcon from '@mui/icons-material/Add';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';

export interface StickyHeroBarProps {
  portfolioName: string;
  currency: string;
  totalMarketValue?: number;
  unrealizedReturnPercentage?: number;
  unrealizedGainLoss?: number;
  visible: boolean;
  activeSectionKey?: string;
  onJumpToSection: (sectionKey: string) => void;
  onAddAccount: () => void;
  onExportReport: () => void;
}

const SECTIONS = [
  { key: 'valuation', label: 'Valuation' },
  { key: 'allocation', label: 'Allocation' },
  { key: 'performance', label: 'Performance' },
  { key: 'history', label: 'History' },
  { key: 'cashFlows', label: 'Cash Flows' },
  { key: 'dividends', label: 'Dividends' },
  { key: 'rebalancing', label: 'Rebalancing' },
  { key: 'accounts', label: 'Accounts' },
] as const;

export function StickyHeroBar({
  portfolioName,
  currency,
  totalMarketValue,
  unrealizedReturnPercentage,
  unrealizedGainLoss,
  visible,
  activeSectionKey,
  onJumpToSection,
  onAddAccount,
  onExportReport,
}: StickyHeroBarProps) {
  const theme = useTheme();
  const isPositive = (unrealizedGainLoss ?? 0) >= 0;

  return (
    <Box
      data-testid="sticky-hero-bar"
      sx={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 1150,
        bgcolor: alpha(theme.palette.background.paper, 0.88),
        backdropFilter: 'blur(16px)',
        borderBottom: `1px solid ${alpha(theme.palette.divider, 0.4)}`,
        boxShadow: visible ? theme.shadows[4] : 'none',
        transform: visible ? 'translateY(0)' : 'translateY(-100%)',
        opacity: visible ? 1 : 0,
        pointerEvents: visible ? 'auto' : 'none',
        transition: 'transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.25s ease',
        py: 1,
      }}
    >
      <Container maxWidth="xl">
        <Stack
          direction="row"
          sx={{
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 2,
          }}
        >
          {/* Left: Portfolio Identity & Realtime Metric Badge */}
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', minWidth: 0, flexShrink: 0 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 800, whiteSpace: 'nowrap' }} noWrap>
              {portfolioName}
            </Typography>

            {totalMarketValue != null && (
              <Chip
                size="small"
                icon={isPositive ? <TrendingUpIcon /> : <TrendingDownIcon />}
                label={`${currency} ${totalMarketValue.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 })} (${isPositive ? '+' : ''}${(unrealizedReturnPercentage ?? 0).toFixed(1)}%)`}
                color={isPositive ? 'success' : 'error'}
                variant="outlined"
                sx={{
                  fontWeight: 700,
                  fontSize: '0.75rem',
                  height: 24,
                  bgcolor: alpha(isPositive ? theme.palette.success.main : theme.palette.error.main, 0.08),
                }}
              />
            )}
          </Stack>

          {/* Center: Section Anchor Jump Pills */}
          <Stack
            direction="row"
            spacing={0.5}
            sx={{
              overflowX: 'auto',
              scrollbarWidth: 'none',
              '&::-webkit-scrollbar': { display: 'none' },
              mx: 1,
            }}
          >
            {SECTIONS.map((sec) => {
              const isActive = activeSectionKey === sec.key;
              return (
                <Button
                  key={sec.key}
                  size="small"
                  variant={isActive ? 'contained' : 'text'}
                  onClick={() => onJumpToSection(sec.key)}
                  sx={{
                    px: 1.25,
                    py: 0.25,
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    borderRadius: 3,
                    minWidth: 'auto',
                    whiteSpace: 'nowrap',
                    color: isActive ? undefined : 'text.secondary',
                    bgcolor: isActive ? 'primary.main' : 'transparent',
                    '&:hover': {
                      bgcolor: isActive ? 'primary.dark' : 'action.hover',
                    },
                  }}
                >
                  {sec.label}
                </Button>
              );
            })}
          </Stack>

          {/* Right: Quick Actions */}
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexShrink: 0 }}>
            <Button
              size="small"
              variant="outlined"
              startIcon={<FileDownloadOutlinedIcon fontSize="small" />}
              onClick={onExportReport}
              sx={{ fontSize: '0.75rem', px: 1.25, py: 0.25, display: { xs: 'none', sm: 'inline-flex' } }}
            >
              Export
            </Button>
            <Button
              size="small"
              variant="contained"
              startIcon={<AddIcon fontSize="small" />}
              onClick={onAddAccount}
              sx={{ fontSize: '0.75rem', px: 1.5, py: 0.25 }}
            >
              Add Account
            </Button>
          </Stack>
        </Stack>
      </Container>
    </Box>
  );
}

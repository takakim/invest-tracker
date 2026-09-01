import React, { useState } from 'react';
import {
  Box,
  Collapse,
  IconButton,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';

export interface CollapsibleSectionProps {
  id?: string;
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  badge?: React.ReactNode;
  actions?: React.ReactNode;
  headingVariant?: 'h4' | 'h5' | 'h6';
  defaultExpanded?: boolean;
  expanded?: boolean;
  onToggle?: (expanded: boolean) => void;
  children: React.ReactNode;
  sx?: object;
}

export function CollapsibleSection({
  id,
  title,
  subtitle,
  icon,
  badge,
  actions,
  headingVariant = 'h6',
  defaultExpanded = true,
  expanded: controlledExpanded,
  onToggle,
  children,
  sx,
}: CollapsibleSectionProps) {
  const [internalExpanded, setInternalExpanded] = useState(defaultExpanded);
  const isControlled = controlledExpanded !== undefined;
  const isExpanded = isControlled ? controlledExpanded : internalExpanded;

  const handleToggle = () => {
    const nextState = !isExpanded;
    if (!isControlled) {
      setInternalExpanded(nextState);
    }
    onToggle?.(nextState);
  };

  return (
    <Paper
      id={id}
      variant="outlined"
      sx={{
        mb: 3,
        borderRadius: 3,
        overflow: 'hidden',
        transition: 'box-shadow 0.2s ease-in-out',
        '&:hover': {
          boxShadow: (theme) => `0 4px 20px ${theme.palette.mode === 'dark' ? 'rgba(0,0,0,0.4)' : 'rgba(0,0,0,0.06)'}`,
        },
        ...sx,
      }}
    >
      <Box
        onClick={handleToggle}
        sx={{
          p: 2.5,
          cursor: 'pointer',
          userSelect: 'none',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          bgcolor: (theme) =>
            theme.palette.mode === 'dark'
              ? isExpanded
                ? 'rgba(255,255,255,0.02)'
                : 'transparent'
              : isExpanded
                ? 'rgba(0,0,0,0.01)'
                : 'transparent',
          '&:hover': {
            bgcolor: (theme) =>
              theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.02)',
          },
        }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
          {icon && (
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                color: 'primary.main',
              }}
            >
              {icon}
            </Box>
          )}
          <Box>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant={headingVariant} sx={{ fontWeight: 700, fontSize: headingVariant === 'h5' ? '1.25rem' : '1.05rem', lineHeight: 1.3 }}>
                {title}
              </Typography>
              {badge}
            </Stack>
            {subtitle && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
                {subtitle}
              </Typography>
            )}
          </Box>
        </Stack>

        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: 'center' }}
          onClick={(e) => e.stopPropagation()}
        >
          {actions}
          <IconButton
            size="small"
            onClick={handleToggle}
            aria-label={isExpanded ? `collapse ${title}` : `expand ${title}`}
            sx={{
              color: 'text.secondary',
              transition: 'transform 0.2s ease',
            }}
          >
            {isExpanded ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </Stack>
      </Box>

      <Collapse in={isExpanded} timeout="auto" unmountOnExit={false}>
        <Box sx={{ p: 2.5, pt: 0 }}>
          {children}
        </Box>
      </Collapse>
    </Paper>
  );
}

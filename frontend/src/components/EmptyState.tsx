import React from 'react';
import { Box, Button, Paper, Typography } from '@mui/material';
import FolderOpenOutlinedIcon from '@mui/icons-material/FolderOpenOutlined';

interface EmptyStateProps {
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
  icon?: React.ReactNode;
}

export function EmptyState({
  title,
  description,
  actionLabel,
  onAction,
  icon,
}: EmptyStateProps) {
  return (
    <Paper
      elevation={0}
      sx={(theme) => ({
        py: 6,
        px: 3,
        textAlign: 'center',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.02)' : 'rgba(248, 250, 252, 0.6)',
        borderRadius: 3,
        border: `1.5px dashed ${theme.palette.divider}`,
        transition: 'border-color 0.2s ease, background-color 0.2s ease',
        '&:hover': {
          borderColor: theme.palette.text.secondary,
        },
      })}
    >
      <Box
        sx={(theme) => ({
          color: theme.palette.text.secondary,
          mb: 2,
          p: 1.5,
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.04)' : 'rgba(15, 23, 42, 0.04)',
        })}
      >
        {icon || <FolderOpenOutlinedIcon sx={{ fontSize: 40, opacity: 0.8 }} />}
      </Box>
      <Typography variant="h6" sx={{ mb: 0.75, fontWeight: 600 }}>
        {title}
      </Typography>
      {description && (
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ maxWidth: 480, mb: actionLabel && onAction ? 3 : 0, lineHeight: 1.6 }}
        >
          {description}
        </Typography>
      )}
      {actionLabel && onAction && (
        <Button variant="contained" color="primary" onClick={onAction} sx={{ px: 3 }}>
          {actionLabel}
        </Button>
      )}
    </Paper>
  );
}

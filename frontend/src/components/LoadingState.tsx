import React from 'react';
import { Box, CircularProgress, Skeleton, Stack, Typography } from '@mui/material';

interface LoadingStateProps {
  message?: string;
  variant?: 'spinner' | 'skeleton' | 'table';
  count?: number;
}

export function LoadingState({
  message = 'Loading...',
  variant = 'spinner',
  count = 3,
}: LoadingStateProps) {
  if (variant === 'skeleton') {
    return (
      <Stack spacing={2} sx={{ width: '100%', my: 2 }}>
        {Array.from({ length: count }).map((_, i) => (
          <Skeleton key={i} variant="rounded" height={80} animation="wave" />
        ))}
      </Stack>
    );
  }

  if (variant === 'table') {
    return (
      <Stack spacing={1} sx={{ width: '100%', my: 2 }}>
        <Skeleton variant="rectangular" height={40} />
        {Array.from({ length: count }).map((_, i) => (
          <Skeleton key={i} variant="rounded" height={52} animation="wave" />
        ))}
      </Stack>
    );
  }

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        py: 8,
      }}
    >
      <CircularProgress size={36} sx={{ mb: 2 }} />
      <Typography variant="body2" color="text.secondary">
        {message}
      </Typography>
    </Box>
  );
}

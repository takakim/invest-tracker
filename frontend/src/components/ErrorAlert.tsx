import React from 'react';
import { Alert, AlertTitle, Box, Typography } from '@mui/material';
import { ApiError } from '../api';
import type { ProblemDetail } from '../types';

interface ErrorAlertProps {
  error: unknown;
  title?: string;
  onClose?: () => void;
}

export function ErrorAlert({ error, title, onClose }: ErrorAlertProps) {
  if (!error) return null;

  let problemTitle = title || 'Error';
  let detailMessage = 'An unexpected error occurred';
  let fieldErrors: string[] | undefined;

  if (error instanceof ApiError) {
    const p: ProblemDetail = error.problem;
    problemTitle = title || p.title || 'Request Failed';
    detailMessage = p.detail || error.message;
    fieldErrors = p.errors;
  } else if (error instanceof Error) {
    detailMessage = error.message;
  } else if (typeof error === 'string') {
    detailMessage = error;
  }

  return (
    <Alert severity="error" onClose={onClose} sx={{ mb: 2 }}>
      <AlertTitle>{problemTitle}</AlertTitle>
      <Typography variant="body2">{detailMessage}</Typography>
      {fieldErrors && fieldErrors.length > 0 && (
        <Box component="ul" sx={{ pl: 2, mb: 0, mt: 1 }}>
          {fieldErrors.map((err, idx) => (
            <Typography component="li" variant="caption" key={idx}>
              {err}
            </Typography>
          ))}
        </Box>
      )}
    </Alert>
  );
}

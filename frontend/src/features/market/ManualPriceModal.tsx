import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Stack,
  Alert,
  Typography,
} from '@mui/material';
import { Instrument } from '../../types';
import { useRecordPriceOverride } from './useMarketData';

interface ManualPriceModalProps {
  open: boolean;
  instrument: Instrument | null;
  onClose: () => void;
}

export function ManualPriceModal({ open, instrument, onClose }: ManualPriceModalProps) {
  const [price, setPrice] = useState<string>('');
  const [reason, setReason] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const overrideMutation = useRecordPriceOverride(instrument?.id || '');

  if (!instrument) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    const numericPrice = parseFloat(price);
    if (isNaN(numericPrice) || numericPrice < 0) {
      setErrorMsg('Please enter a valid non-negative price.');
      return;
    }

    try {
      await overrideMutation.mutateAsync({
        price: numericPrice,
        currency: instrument.currency,
        reason: reason.trim() || undefined,
      });
      setPrice('');
      setReason('');
      onClose();
    } catch (err: unknown) {
      const e = err as { message?: string };
      setErrorMsg(e.message || 'Failed to record manual price override');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>
          Manual Price Override
          <Typography variant="body2" color="text.secondary">
            {instrument.name} ({instrument.ticker || instrument.currency})
          </Typography>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {errorMsg && <Alert severity="error">{errorMsg}</Alert>}
            <TextField
              label="Override Price"
              type="number"
              required
              slotProps={{ htmlInput: { min: 0, step: 'any' } }}
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              helperText={`Currency: ${instrument.currency}`}
              fullWidth
              autoFocus
            />
            <TextField
              label="Reason / Provenance"
              placeholder="e.g. End-of-month broker statement"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              fullWidth
              multiline
              rows={2}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} color="inherit">
            Cancel
          </Button>
          <Button type="submit" variant="contained" loading={overrideMutation.isPending}>
            Record Override
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

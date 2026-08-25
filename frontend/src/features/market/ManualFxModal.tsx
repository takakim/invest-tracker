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
import { useRecordFxOverride } from './useFxRates';

interface ManualFxModalProps {
  open: boolean;
  initialBase?: string;
  initialQuote?: string;
  onClose: () => void;
}

export function ManualFxModal({ open, initialBase = 'GBP', initialQuote = 'USD', onClose }: ManualFxModalProps) {
  const [baseCurrency, setBaseCurrency] = useState(initialBase);
  const [quoteCurrency, setQuoteCurrency] = useState(initialQuote);
  const [rate, setRate] = useState<string>('');
  const [reason, setReason] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const fxMutation = useRecordFxOverride();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    const base = baseCurrency.trim().toUpperCase();
    const quote = quoteCurrency.trim().toUpperCase();
    const numericRate = parseFloat(rate);

    if (base.length !== 3 || quote.length !== 3) {
      setErrorMsg('Currencies must be 3-letter ISO codes.');
      return;
    }
    if (base === quote) {
      setErrorMsg('Base and quote currencies cannot be the same.');
      return;
    }
    if (isNaN(numericRate) || numericRate <= 0) {
      setErrorMsg('FX rate must be strictly positive.');
      return;
    }

    try {
      await fxMutation.mutateAsync({
        baseCurrency: base,
        quoteCurrency: quote,
        rate: numericRate,
        reason: reason.trim() || undefined,
      });
      setRate('');
      setReason('');
      onClose();
    } catch (err: unknown) {
      const e = err as { message?: string };
      setErrorMsg(e.message || 'Failed to record manual FX override');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>
          Manual FX Rate Override
          <Typography variant="body2" color="text.secondary">
            Set custom exchange rate with provenance notes
          </Typography>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {errorMsg && <Alert severity="error">{errorMsg}</Alert>}
            <Stack direction="row" spacing={2}>
              <TextField
                label="Base Currency"
                value={baseCurrency}
                onChange={(e) => setBaseCurrency(e.target.value.toUpperCase())}
                slotProps={{ htmlInput: { maxLength: 3 } }}
                required
                fullWidth
              />
              <TextField
                label="Quote Currency"
                value={quoteCurrency}
                onChange={(e) => setQuoteCurrency(e.target.value.toUpperCase())}
                slotProps={{ htmlInput: { maxLength: 3 } }}
                required
                fullWidth
              />
            </Stack>
            <TextField
              label={`Rate (1 ${baseCurrency || 'BASE'} = X ${quoteCurrency || 'QUOTE'})`}
              type="number"
              required
              slotProps={{ htmlInput: { min: 0.00000001, step: 'any' } }}
              value={rate}
              onChange={(e) => setRate(e.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label="Reason / Provenance"
              placeholder="e.g. Bank wire exchange receipt"
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
          <Button type="submit" variant="contained" loading={fxMutation.isPending}>
            Record FX Override
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

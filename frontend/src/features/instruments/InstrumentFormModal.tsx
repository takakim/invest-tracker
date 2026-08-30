import React from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormHelperText,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { instrumentSchema, type InstrumentFormData } from '../../forms/schemas';
import { ErrorAlert } from '../../components';

import type { Instrument } from '../../types';

interface InstrumentFormModalProps {
  open: boolean;
  instrument?: Instrument | null;
  isPending: boolean;
  error?: unknown;
  onClose: () => void;
  onSubmit: (data: InstrumentFormData) => void;
}

export function InstrumentFormModal({
  open,
  instrument,
  isPending,
  error,
  onClose,
  onSubmit,
}: InstrumentFormModalProps) {
  const isEditing = Boolean(instrument);

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<InstrumentFormData>({
    resolver: zodResolver(instrumentSchema),
    defaultValues: {
      name: '',
      assetClass: 'STOCK',
      ticker: '',
      isin: '',
      exchange: '',
      currency: 'USD',
      manualPriceOnly: false,
    },
  });

  React.useEffect(() => {
    if (open) {
      if (instrument) {
        reset({
          name: instrument.name,
          assetClass: instrument.assetClass,
          ticker: instrument.ticker || '',
          isin: instrument.isin || '',
          exchange: instrument.exchange || '',
          currency: instrument.currency,
          manualPriceOnly: Boolean(instrument.manualPriceOnly),
        });
      } else {
        reset({
          name: '',
          assetClass: 'STOCK',
          ticker: '',
          isin: '',
          exchange: '',
          currency: 'USD',
          manualPriceOnly: false,
        });
      }
    }
  }, [open, instrument, reset]);

  const handleFormSubmit = (data: InstrumentFormData) => {
    onSubmit(data);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 600 }}>
        {isEditing ? 'Edit Instrument' : 'Register New Instrument'}
      </DialogTitle>
      <form onSubmit={handleSubmit(handleFormSubmit)} noValidate>
        <DialogContent dividers>
          <Stack spacing={3} sx={{ mt: 1 }}>
            <ErrorAlert error={error} />

            <TextField
              label="Instrument Name"
              required
              fullWidth
              autoFocus
              placeholder="e.g. Apple Inc, Vanguard S&P 500 UCITS ETF"
              error={Boolean(errors.name)}
              helperText={errors.name?.message}
              {...register('name')}
            />

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormControl fullWidth error={Boolean(errors.assetClass)}>
                  <InputLabel id="asset-class-label">Asset Class</InputLabel>
                  <Controller
                    name="assetClass"
                    control={control}
                    render={({ field }) => (
                      <Select
                        {...field}
                        labelId="asset-class-label"
                        label="Asset Class"
                      >
                        <MenuItem value="STOCK">Stock / Equity</MenuItem>
                        <MenuItem value="ETF">ETF</MenuItem>
                        <MenuItem value="MUTUAL_FUND">Mutual Fund</MenuItem>
                        <MenuItem value="BOND">Bond</MenuItem>
                        <MenuItem value="REIT">REIT</MenuItem>
                        <MenuItem value="CRYPTO">Crypto</MenuItem>
                        <MenuItem value="CASH">Cash</MenuItem>
                        <MenuItem value="OTHER">Other</MenuItem>
                      </Select>
                    )}
                  />
                  {errors.assetClass && (
                    <FormHelperText>{errors.assetClass.message}</FormHelperText>
                  )}
                </FormControl>
              </Grid>

              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Trading / Native Currency"
                  required
                  fullWidth
                  placeholder="e.g. USD, GBP, EUR"
                  error={Boolean(errors.currency)}
                  helperText={errors.currency?.message}
                  slotProps={{ htmlInput: { maxLength: 3, style: { textTransform: 'uppercase' } } }}
                  {...register('currency')}
                />
              </Grid>
            </Grid>

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Ticker Symbol"
                  fullWidth
                  placeholder="e.g. AAPL, VUAG.L"
                  error={Boolean(errors.ticker)}
                  helperText={errors.ticker?.message || 'Optional exchange ticker'}
                  slotProps={{ htmlInput: { maxLength: 32 } }}
                  {...register('ticker')}
                />
              </Grid>

              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="ISIN"
                  fullWidth
                  placeholder="e.g. US0378331005"
                  error={Boolean(errors.isin)}
                  helperText={errors.isin?.message || 'Optional 12-char identifier'}
                  slotProps={{ htmlInput: { maxLength: 12, style: { textTransform: 'uppercase' } } }}
                  {...register('isin')}
                />
              </Grid>
            </Grid>

            <TextField
              label="Exchange / Venue"
              fullWidth
              placeholder="e.g. NASDAQ, LSE, XETRA"
              error={Boolean(errors.exchange)}
              helperText={errors.exchange?.message || 'Optional trading exchange'}
              slotProps={{ htmlInput: { maxLength: 80 } }}
              {...register('exchange')}
            />

            <Controller
              name="manualPriceOnly"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={
                    <Switch
                      checked={Boolean(field.value)}
                      onChange={(e) => field.onChange(e.target.checked)}
                      color="primary"
                    />
                  }
                  label={
                    <Stack>
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>
                        Manual Price Only
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Disable automated external API price polling for this asset.
                      </Typography>
                    </Stack>
                  }
                />
              )}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={isPending} color="inherit">
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={isPending}>
            {isPending ? (isEditing ? 'Saving...' : 'Registering...') : (isEditing ? 'Save Changes' : 'Register Instrument')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

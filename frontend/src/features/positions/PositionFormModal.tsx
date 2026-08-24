import React, { useEffect } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { positionSchema, type PositionFormData } from '../../forms/schemas';
import { useInstrumentsList } from '../instruments/useInstruments';
import type { Position } from '../../types';
import { ErrorAlert } from '../../components';

interface PositionFormModalProps {
  open: boolean;
  position?: Position | null;
  defaultCurrency?: string;
  isPending: boolean;
  error?: unknown;
  onClose: () => void;
  onSubmit: (data: PositionFormData) => void;
}

export function PositionFormModal({
  open,
  position,
  defaultCurrency = 'GBP',
  isPending,
  error,
  onClose,
  onSubmit,
}: PositionFormModalProps) {
  const isEditing = Boolean(position);
  const { data: instruments = [] } = useInstrumentsList();

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<PositionFormData>({
    resolver: zodResolver(positionSchema),
    defaultValues: {
      instrumentId: '',
      quantity: 0,
      costBasisAmount: undefined,
      costBasisCurrency: defaultCurrency,
    },
  });

  useEffect(() => {
    if (position) {
      reset({
        instrumentId: position.instrumentId,
        quantity: position.quantity,
        costBasisAmount: position.costBasisAmount ?? undefined,
        costBasisCurrency: position.costBasisCurrency ?? defaultCurrency,
      });
    } else {
      reset({
        instrumentId: '',
        quantity: 0,
        costBasisAmount: undefined,
        costBasisCurrency: defaultCurrency,
      });
    }
  }, [position, defaultCurrency, reset, open]);

  const handleFormSubmit = (data: PositionFormData) => {
    onSubmit(data);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 600 }}>
        {isEditing ? 'Edit Position Holding' : 'Add Position Holding'}
      </DialogTitle>
      <form onSubmit={handleSubmit(handleFormSubmit)} noValidate>
        <DialogContent dividers>
          <Stack spacing={3} sx={{ mt: 1 }}>
            <ErrorAlert error={error} />

            {!isEditing ? (
              <FormControl fullWidth error={Boolean(errors.instrumentId)}>
                <InputLabel id="instrument-select-label">Select Instrument</InputLabel>
                <Controller
                  name="instrumentId"
                  control={control}
                  render={({ field }) => (
                    <Select
                      {...field}
                      labelId="instrument-select-label"
                      label="Select Instrument"
                    >
                      {instruments.map((inst) => (
                        <MenuItem key={inst.id} value={inst.id}>
                          {inst.name} {inst.ticker ? `(${inst.ticker})` : ''} [{inst.currency}]
                        </MenuItem>
                      ))}
                    </Select>
                  )}
                />
                {errors.instrumentId && (
                  <FormHelperText>{errors.instrumentId.message}</FormHelperText>
                )}
              </FormControl>
            ) : (
              <TextField
                label="Instrument"
                value={position?.instrumentName || ''}
                disabled
                fullWidth
              />
            )}

            <TextField
              label="Share / Token Quantity"
              required
              fullWidth
              type="number"
              placeholder="e.g. 10.50"
              slotProps={{ htmlInput: { step: 'any', min: 0 } }}
              error={Boolean(errors.quantity)}
              helperText={errors.quantity?.message || 'High-precision decimal quantity'}
              {...register('quantity')}
            />

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Total Cost Basis Amount"
                  fullWidth
                  type="number"
                  placeholder="e.g. 1500.00"
                  slotProps={{ htmlInput: { step: 'any', min: 0 } }}
                  error={Boolean(errors.costBasisAmount)}
                  helperText={errors.costBasisAmount?.message || 'Optional acquisition cost'}
                  {...register('costBasisAmount')}
                />
              </Grid>

              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Cost Basis Currency"
                  fullWidth
                  placeholder="e.g. GBP, USD"
                  error={Boolean(errors.costBasisCurrency)}
                  helperText={errors.costBasisCurrency?.message}
                  slotProps={{ htmlInput: { maxLength: 3, style: { textTransform: 'uppercase' } } }}
                  {...register('costBasisCurrency')}
                />
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={isPending} color="inherit">
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={isPending}>
            {isPending ? 'Saving...' : isEditing ? 'Save Changes' : 'Add Position'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

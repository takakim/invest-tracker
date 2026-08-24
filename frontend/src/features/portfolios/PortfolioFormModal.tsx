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
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { portfolioSchema, type PortfolioFormData } from '../../forms/schemas';
import type { Portfolio } from '../../types';
import { ErrorAlert } from '../../components';

interface PortfolioFormModalProps {
  open: boolean;
  portfolio?: Portfolio | null;
  isPending: boolean;
  error?: unknown;
  onClose: () => void;
  onSubmit: (data: PortfolioFormData) => void;
}

export function PortfolioFormModal({
  open,
  portfolio,
  isPending,
  error,
  onClose,
  onSubmit,
}: PortfolioFormModalProps) {
  const isEditing = Boolean(portfolio);

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<PortfolioFormData>({
    resolver: zodResolver(portfolioSchema),
    defaultValues: {
      name: '',
      baseCurrency: 'GBP',
      costBasisMethod: 'FIFO',
      returnMethod: 'XIRR',
    },
  });

  useEffect(() => {
    if (portfolio) {
      reset({
        name: portfolio.name,
        baseCurrency: portfolio.baseCurrency,
        costBasisMethod: portfolio.costBasisMethod,
        returnMethod: portfolio.returnMethod,
      });
    } else {
      reset({
        name: '',
        baseCurrency: 'GBP',
        costBasisMethod: 'FIFO',
        returnMethod: 'XIRR',
      });
    }
  }, [portfolio, reset, open]);

  const handleFormSubmit = (data: PortfolioFormData) => {
    onSubmit(data);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 600 }}>
        {isEditing ? 'Edit Portfolio' : 'Create New Portfolio'}
      </DialogTitle>
      <form onSubmit={handleSubmit(handleFormSubmit)} noValidate>
        <DialogContent dividers>
          <Stack spacing={3} sx={{ mt: 1 }}>
            <ErrorAlert error={error} />

            <TextField
              label="Portfolio Name"
              required
              fullWidth
              autoFocus
              error={Boolean(errors.name)}
              helperText={errors.name?.message}
              {...register('name')}
            />

            <TextField
              label="Base Currency"
              required
              fullWidth
              placeholder="e.g. GBP, USD, EUR"
              error={Boolean(errors.baseCurrency)}
              helperText={errors.baseCurrency?.message || '3-letter ISO-4217 currency code'}
              slotProps={{ htmlInput: { maxLength: 3, style: { textTransform: 'uppercase' } } }}
              {...register('baseCurrency')}
            />

            <FormControl fullWidth error={Boolean(errors.costBasisMethod)}>
              <InputLabel id="cost-basis-label">Cost Basis Method</InputLabel>
              <Controller
                name="costBasisMethod"
                control={control}
                render={({ field }) => (
                  <Select
                    {...field}
                    labelId="cost-basis-label"
                    label="Cost Basis Method"
                  >
                    <MenuItem value="FIFO">FIFO (First In, First Out)</MenuItem>
                    <MenuItem value="LIFO">LIFO (Last In, First Out)</MenuItem>
                    <MenuItem value="AVERAGE_COST">Average Cost</MenuItem>
                  </Select>
                )}
              />
              {errors.costBasisMethod && (
                <FormHelperText>{errors.costBasisMethod.message}</FormHelperText>
              )}
            </FormControl>

            <FormControl fullWidth error={Boolean(errors.returnMethod)}>
              <InputLabel id="return-method-label">Return Calculation Method</InputLabel>
              <Controller
                name="returnMethod"
                control={control}
                render={({ field }) => (
                  <Select
                    {...field}
                    labelId="return-method-label"
                    label="Return Calculation Method"
                  >
                    <MenuItem value="XIRR">XIRR (Money-Weighted Return with cash timing)</MenuItem>
                    <MenuItem value="TWR">TWR (Time-Weighted Return)</MenuItem>
                    <MenuItem value="MWR">MWR (Money-Weighted Return)</MenuItem>
                  </Select>
                )}
              />
              {errors.returnMethod && (
                <FormHelperText>{errors.returnMethod.message}</FormHelperText>
              )}
            </FormControl>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={isPending} color="inherit">
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={isPending}>
            {isPending ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Portfolio'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

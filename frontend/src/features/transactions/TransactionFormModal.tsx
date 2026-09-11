import React, { useEffect } from 'react';
import { useForm, Controller, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Box,
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
  Typography,
} from '@mui/material';
import { transactionSchema, type TransactionFormData } from '../../forms/schemas';
import { useInstrumentsList } from '../instruments/useInstruments';
import { ErrorAlert } from '../../components';
import type { Transaction, TransactionType } from '../../types';

interface TransactionFormModalProps {
  open: boolean;
  defaultCurrency?: string;
  initialData?: Transaction | null;
  isPending: boolean;
  error?: unknown;
  onClose: () => void;
  onSubmit: (data: TransactionFormData) => void;
}

const TRANSACTION_TYPES: { value: TransactionType; label: string }[] = [
  { value: 'BUY', label: 'BUY — Buy Shares / Tokens' },
  { value: 'SELL', label: 'SELL — Sell Shares / Tokens' },
  { value: 'DIVIDEND', label: 'DIVIDEND — Dividend Income' },
  { value: 'DEPOSIT', label: 'DEPOSIT — Cash Deposit' },
  { value: 'WITHDRAWAL', label: 'WITHDRAWAL — Cash Withdrawal' },
  { value: 'FEE', label: 'FEE — Account / Transaction Fee' },
  { value: 'INTEREST', label: 'INTEREST — Cash Interest Income' },
  { value: 'STOCK_SPLIT', label: 'STOCK_SPLIT — Corporate Stock Split' },
];

export function TransactionFormModal({
  open,
  defaultCurrency = 'GBP',
  initialData = null,
  isPending,
  error,
  onClose,
  onSubmit,
}: TransactionFormModalProps) {
  const isEdit = Boolean(initialData);
  const { data: instruments = [] } = useInstrumentsList({ enabled: open });

  const {
    register,
    handleSubmit,
    control,
    reset,
    setValue,
    formState: { errors },
  } = useForm<TransactionFormData>({
    resolver: zodResolver(transactionSchema),
    defaultValues: {
      type: 'BUY',
      tradeDate: new Date().toISOString().slice(0, 16),
      instrumentId: '',
      quantity: undefined,
      price: undefined,
      grossAmount: 0,
      feeAmount: undefined,
      taxAmount: undefined,
      currency: defaultCurrency,
      notes: '',
    },
  });

  const selectedType = useWatch({ control, name: 'type' });
  const watchedQuantity = useWatch({ control, name: 'quantity' });
  const watchedPrice = useWatch({ control, name: 'price' });
  const watchedGross = useWatch({ control, name: 'grossAmount' });
  const watchedFee = useWatch({ control, name: 'feeAmount' });
  const watchedTax = useWatch({ control, name: 'taxAmount' });

  const isTrade = selectedType === 'BUY' || selectedType === 'SELL';
  const isSplit = selectedType === 'STOCK_SPLIT' || selectedType === 'REVERSE_STOCK_SPLIT';
  const requiresInstrument = isTrade || isSplit || selectedType === 'DIVIDEND';

  // Auto-calculate gross amount on quantity * price change
  useEffect(() => {
    if (isTrade && watchedQuantity && watchedPrice) {
      const q = Number(watchedQuantity);
      const p = Number(watchedPrice);
      if (!isNaN(q) && !isNaN(p)) {
        setValue('grossAmount', Number((q * p).toFixed(4)));
      }
    } else if (isSplit) {
      setValue('grossAmount', 0);
      setValue('price', undefined);
      setValue('feeAmount', undefined);
      setValue('taxAmount', undefined);
    }
  }, [isTrade, isSplit, watchedQuantity, watchedPrice, setValue]);

  const watchedCurrency = useWatch({ control, name: 'currency' });

  useEffect(() => {
    if (open) {
      if (initialData) {
        reset({
          type: initialData.type,
          tradeDate: initialData.tradeDate
            ? new Date(initialData.tradeDate).toISOString().slice(0, 16)
            : new Date().toISOString().slice(0, 16),
          instrumentId: initialData.instrumentId || '',
          quantity: initialData.quantity != null ? initialData.quantity : undefined,
          price: initialData.price != null ? initialData.price : undefined,
          grossAmount: initialData.grossAmount != null ? initialData.grossAmount : 0,
          feeAmount: initialData.feeAmount != null ? initialData.feeAmount : undefined,
          taxAmount: initialData.taxAmount != null ? initialData.taxAmount : undefined,
          currency: initialData.currency || defaultCurrency,
          notes: initialData.notes || '',
        });
      } else {
        reset({
          type: 'BUY',
          tradeDate: new Date().toISOString().slice(0, 16),
          instrumentId: '',
          quantity: undefined,
          price: undefined,
          grossAmount: 0,
          feeAmount: undefined,
          taxAmount: undefined,
          currency: defaultCurrency,
          notes: '',
        });
      }
    }
  }, [open, initialData, defaultCurrency, reset]);

  // Compute live net amount preview
  const gross = Number(watchedGross || 0);
  const fee = Number(watchedFee || 0);
  const tax = Number(watchedTax || 0);
  const netAmountPreview =
    isSplit ? 0 : selectedType === 'BUY' ? gross + fee : selectedType === 'SELL' ? gross - fee - tax : selectedType === 'DIVIDEND' ? gross - tax : gross;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ fontWeight: 600 }}>
        {isEdit ? 'Edit Transaction' : 'Record Transaction'}
      </DialogTitle>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <DialogContent dividers>
          <Stack spacing={3} sx={{ mt: 1 }}>
            <ErrorAlert error={error} />

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <FormControl fullWidth error={Boolean(errors.type)}>
                  <InputLabel id="tx-type-label">Transaction Type</InputLabel>
                  <Controller
                    name="type"
                    control={control}
                    render={({ field }) => (
                      <Select labelId="tx-type-label" label="Transaction Type" {...field}>
                        {TRANSACTION_TYPES.map((t) => (
                          <MenuItem key={t.value} value={t.value}>
                            {t.label}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.type && <FormHelperText>{errors.type.message}</FormHelperText>}
                </FormControl>
              </Grid>

              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Trade Date / Time"
                  required
                  fullWidth
                  type="datetime-local"
                  slotProps={{ inputLabel: { shrink: true } }}
                  error={Boolean(errors.tradeDate)}
                  helperText={errors.tradeDate?.message}
                  {...register('tradeDate')}
                />
              </Grid>
            </Grid>

            {requiresInstrument && (
              <FormControl fullWidth error={Boolean(errors.instrumentId)}>
                <InputLabel id="tx-inst-label">Financial Instrument</InputLabel>
                <Controller
                  name="instrumentId"
                  control={control}
                  render={({ field }) => (
                    <Select labelId="tx-inst-label" label="Financial Instrument" {...field} value={field.value || ''}>
                      <MenuItem value="">
                        <em>Select an instrument</em>
                      </MenuItem>
                      {instruments.map((inst) => (
                        <MenuItem key={inst.id} value={inst.id}>
                          {inst.ticker ? `${inst.ticker} — ` : ''}
                          {inst.name} {inst.isin ? `(${inst.isin})` : ''}
                        </MenuItem>
                      ))}
                      {field.value && !instruments.some((inst) => inst.id === field.value) && (
                        <MenuItem key={field.value} value={field.value} sx={{ display: 'none' }}>
                          {initialData?.instrumentName || field.value}
                        </MenuItem>
                      )}
                    </Select>
                  )}
                />
                {errors.instrumentId && <FormHelperText>{errors.instrumentId.message}</FormHelperText>}
              </FormControl>
            )}

            {(isTrade || isSplit) && (
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: isTrade ? 6 : 12 }}>
                  <TextField
                    label={isSplit ? 'Split Share Quantity' : 'Share Quantity'}
                    required
                    fullWidth
                    type="number"
                    placeholder={isSplit ? 'e.g. 181.605204' : 'e.g. 10.50'}
                    slotProps={{ htmlInput: { step: 'any', min: 0 } }}
                    error={Boolean(errors.quantity)}
                    helperText={errors.quantity?.message || (isSplit ? 'Number of additional shares received (or deducted for reverse split)' : undefined)}
                    {...register('quantity')}
                  />
                </Grid>
                {isTrade && (
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField
                      label="Execution Price per Share"
                      required
                      fullWidth
                      type="number"
                      placeholder="e.g. 150.25"
                      slotProps={{ htmlInput: { step: 'any', min: 0 } }}
                      error={Boolean(errors.price)}
                      helperText={errors.price?.message}
                      {...register('price')}
                    />
                  </Grid>
                )}
              </Grid>
            )}

            {!isSplit && (
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <TextField
                    label="Gross Amount"
                    required
                    fullWidth
                    type="number"
                    placeholder="e.g. 1500.00"
                    slotProps={{ htmlInput: { step: 'any', min: 0 } }}
                    error={Boolean(errors.grossAmount)}
                    helperText={errors.grossAmount?.message}
                    {...register('grossAmount')}
                  />
                </Grid>

                <Grid size={{ xs: 12, sm: 4 }}>
                  <TextField
                    label="Broker Fee"
                    fullWidth
                    type="number"
                    placeholder="e.g. 5.00"
                    slotProps={{ htmlInput: { step: 'any', min: 0 } }}
                    error={Boolean(errors.feeAmount)}
                    helperText={errors.feeAmount?.message}
                    {...register('feeAmount')}
                  />
                </Grid>

                <Grid size={{ xs: 12, sm: 4 }}>
                  <TextField
                    label="Tax Amount"
                    fullWidth
                    type="number"
                    placeholder="e.g. 10.00"
                    slotProps={{ htmlInput: { step: 'any', min: 0 } }}
                    error={Boolean(errors.taxAmount)}
                    helperText={errors.taxAmount?.message}
                    {...register('taxAmount')}
                  />
                </Grid>
              </Grid>
            )}

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Currency Code"
                  required
                  fullWidth
                  slotProps={{ htmlInput: { maxLength: 3, style: { textTransform: 'uppercase' } } }}
                  error={Boolean(errors.currency)}
                  helperText={errors.currency?.message}
                  {...register('currency')}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Notes / Reference"
                  fullWidth
                  placeholder="e.g. Order #12345"
                  error={Boolean(errors.notes)}
                  helperText={errors.notes?.message}
                  {...register('notes')}
                />
              </Grid>
            </Grid>

            <Box sx={{ p: 2, borderRadius: 2, bgcolor: 'action.hover' }}>
              <Typography variant="caption" color="text.secondary">
                Net Cash Flow Preview
              </Typography>
              <Typography variant="h6" sx={{ fontWeight: 700, color: selectedType === 'BUY' || selectedType === 'WITHDRAWAL' || selectedType === 'FEE' ? 'error.main' : 'success.main' }}>
                {selectedType === 'BUY' || selectedType === 'WITHDRAWAL' || selectedType === 'FEE' ? '-' : '+'} {watchedCurrency || defaultCurrency} {netAmountPreview.toFixed(2)}
              </Typography>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={isPending} color="inherit">
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={isPending}>
            {isPending ? (isEdit ? 'Saving...' : 'Recording...') : (isEdit ? 'Save Changes' : 'Record Transaction')}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

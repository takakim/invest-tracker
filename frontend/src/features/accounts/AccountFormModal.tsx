import React, { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import { accountSchema, type AccountFormData } from '../../forms/schemas';
import type { Account } from '../../types';
import { ErrorAlert } from '../../components';

interface AccountFormModalProps {
  open: boolean;
  account?: Account | null;
  defaultCurrency?: string;
  isPending: boolean;
  error?: unknown;
  onClose: () => void;
  onSubmit: (data: AccountFormData) => void;
}

export function AccountFormModal({
  open,
  account,
  defaultCurrency = 'GBP',
  isPending,
  error,
  onClose,
  onSubmit,
}: AccountFormModalProps) {
  const isEditing = Boolean(account);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AccountFormData>({
    resolver: zodResolver(accountSchema),
    defaultValues: {
      name: '',
      brokerName: '',
      accountCurrency: defaultCurrency,
      taxTreatment: 'TAXABLE',
    },
  });

  useEffect(() => {
    if (account) {
      reset({
        name: account.name,
        brokerName: account.brokerName,
        accountCurrency: account.accountCurrency,
        taxTreatment: account.taxTreatment || 'TAXABLE',
      });
    } else {
      reset({
        name: '',
        brokerName: '',
        accountCurrency: defaultCurrency,
        taxTreatment: 'TAXABLE',
      });
    }
  }, [account, defaultCurrency, reset, open]);

  const handleFormSubmit = (data: AccountFormData) => {
    onSubmit(data);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 600 }}>
        {isEditing ? 'Edit Account' : 'Add New Account'}
      </DialogTitle>
      <form onSubmit={handleSubmit(handleFormSubmit)} noValidate>
        <DialogContent dividers>
          <Stack spacing={3} sx={{ mt: 1 }}>
            <ErrorAlert error={error} />

            <TextField
              label="Account Name"
              required
              fullWidth
              autoFocus
              placeholder="e.g. Stocks & Shares ISA, SIPP, Taxable"
              error={Boolean(errors.name)}
              helperText={errors.name?.message}
              {...register('name')}
            />

            <TextField
              label="Broker / Custodian Name"
              required
              fullWidth
              placeholder="e.g. Interactive Brokers, Trading 212, Vanguard"
              error={Boolean(errors.brokerName)}
              helperText={errors.brokerName?.message}
              {...register('brokerName')}
            />

            <TextField
              label="Account Currency"
              required
              fullWidth
              placeholder="e.g. GBP, USD, EUR"
              error={Boolean(errors.accountCurrency)}
              helperText={errors.accountCurrency?.message || '3-letter ISO-4217 currency code'}
              slotProps={{ htmlInput: { maxLength: 3, style: { textTransform: 'uppercase' } } }}
              {...register('accountCurrency')}
            />

            <TextField
              label="Tax Treatment"
              select
              fullWidth
              defaultValue="TAXABLE"
              helperText="Determines whether realized capital gains and dividends are subject to tax allowances"
              error={Boolean(errors.taxTreatment)}
              {...register('taxTreatment')}
            >
              <MenuItem value="TAXABLE">Taxable (General Investment Account / GIA)</MenuItem>
              <MenuItem value="TAX_EXEMPT">Tax-Exempt (Stocks & Shares ISA, Roth IRA)</MenuItem>
              <MenuItem value="TAX_DEFERRED">Tax-Deferred (SIPP, 401k, Pension)</MenuItem>
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={isPending} color="inherit">
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={isPending}>
            {isPending ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Account'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

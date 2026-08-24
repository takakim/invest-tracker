import React, { useState } from 'react';
import {
  Box,
  Button,
  Chip,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import EditNoteOutlinedIcon from '@mui/icons-material/EditNoteOutlined';

import { useTransactionsList, useCreateTransaction, useCorrectTransaction } from './useTransactions';
import { TransactionFormModal } from './TransactionFormModal';
import { CsvImportModal } from '../imports/CsvImportModal';
import { EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { Transaction, TransactionCreateInput, TransactionType } from '../../types';
import type { TransactionFormData } from '../../forms/schemas';

interface TransactionTableProps {
  portfolioId: string;
  accountId: string;
  defaultCurrency?: string;
  isReadOnly?: boolean;
}

export function TransactionTable({
  portfolioId,
  accountId,
  defaultCurrency = 'GBP',
  isReadOnly = false,
}: TransactionTableProps) {
  const [selectedTypeFilter, setSelectedTypeFilter] = useState<TransactionType | undefined>(undefined);
  const [formOpen, setFormOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);

  const {
    data: transactions = [],
    isLoading,
    error,
    refetch,
  } = useTransactionsList(portfolioId, accountId, selectedTypeFilter);

  const createMutation = useCreateTransaction(portfolioId, accountId);

  const handleFormSubmit = async (formData: TransactionFormData) => {
    const payload: TransactionCreateInput = {
      type: formData.type as TransactionType,
      tradeDate: new Date(formData.tradeDate).toISOString(),
      instrumentId: formData.instrumentId || undefined,
      quantity: formData.quantity ? Number(formData.quantity) : undefined,
      price: formData.price ? Number(formData.price) : undefined,
      grossAmount: Number(formData.grossAmount),
      feeAmount: formData.feeAmount ? Number(formData.feeAmount) : undefined,
      taxAmount: formData.taxAmount ? Number(formData.taxAmount) : undefined,
      currency: formData.currency || defaultCurrency,
      notes: formData.notes || undefined,
    };

    await createMutation.mutateAsync(payload, {
      onSuccess: () => setFormOpen(false),
    });
  };

  const getTypeChipColor = (type: TransactionType) => {
    switch (type) {
      case 'BUY':
        return 'primary';
      case 'SELL':
        return 'info';
      case 'DIVIDEND':
      case 'INTEREST':
        return 'secondary';
      case 'DEPOSIT':
        return 'success';
      case 'WITHDRAWAL':
      case 'FEE':
        return 'warning';
      default:
        return 'default';
    }
  };

  return (
    <Box sx={{ mt: 3 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2, mb: 2 }}
      >
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Transaction Ledger
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Immutable log of trade executions, cash movements, dividends, and fees.
          </Typography>
        </Box>
        {!isReadOnly && (
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              size="small"
              startIcon={<UploadFileIcon />}
              onClick={() => setImportModalOpen(true)}
            >
              Import CSV
            </Button>
            <Button
              variant="contained"
              size="small"
              startIcon={<AddIcon />}
              onClick={() => setFormOpen(true)}
            >
              Record Transaction
            </Button>
          </Stack>
        )}
      </Stack>

      <ErrorAlert error={error} onClose={() => refetch()} />

      {isLoading ? (
        <LoadingState variant="table" count={3} />
      ) : transactions.length === 0 ? (
        <EmptyState
          title="No Transactions Recorded"
          description="Record buys, sells, dividends, or deposits to populate your ledger."
          actionLabel={!isReadOnly ? 'Record Transaction' : undefined}
          onAction={() => setFormOpen(true)}
          icon={<ReceiptLongOutlinedIcon sx={{ fontSize: 48, opacity: 0.7 }} />}
        />
      ) : (
        <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
          <Table aria-label="transactions table" size="small">
            <TableHead>
              <TableRow>
                <TableCell>Date & Time</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Instrument</TableCell>
                <TableCell align="right">Qty</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell align="right">Fee / Tax</TableCell>
                <TableCell align="right">Net Amount</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {transactions.map((tx) => (
                <TableRow
                  key={tx.id}
                  hover
                  sx={{ opacity: tx.status === 'CORRECTED' ? 0.5 : 1 }}
                >
                  <TableCell variant="body">
                    {new Date(tx.tradeDate).toLocaleDateString()} {new Date(tx.tradeDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={tx.type}
                      size="small"
                      color={getTypeChipColor(tx.type) as any}
                    />
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>
                    {tx.instrumentName ? (
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        <span>{tx.instrumentName}</span>
                        {tx.instrumentTicker && (
                          <Typography variant="caption" color="text.secondary">
                            ({tx.instrumentTicker})
                          </Typography>
                        )}
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        Cash Flow
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {tx.quantity != null ? tx.quantity : '—'}
                  </TableCell>
                  <TableCell align="right">
                    {tx.price != null ? `${tx.currency} ${tx.price}` : '—'}
                  </TableCell>
                  <TableCell align="right">
                    {tx.feeAmount || tx.taxAmount ? (
                      `${tx.currency} ${((tx.feeAmount || 0) + (tx.taxAmount || 0)).toFixed(2)}`
                    ) : (
                      '—'
                    )}
                  </TableCell>
                  <TableCell
                    align="right"
                    sx={{
                      fontWeight: 700,
                      color:
                        tx.type === 'BUY' || tx.type === 'WITHDRAWAL' || tx.type === 'FEE'
                          ? 'error.main'
                          : 'success.main',
                    }}
                  >
                    {tx.currency} {tx.netAmount.toLocaleString()}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={tx.status}
                      size="small"
                      variant={tx.status === 'CORRECTED' ? 'outlined' : 'filled'}
                      color={tx.status === 'COMPLETED' ? 'success' : 'default'}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Record Transaction Modal */}
      <TransactionFormModal
        open={formOpen}
        defaultCurrency={defaultCurrency}
        isPending={createMutation.isPending}
        error={createMutation.error}
        onClose={() => setFormOpen(false)}
        onSubmit={handleFormSubmit}
      />

      {/* CSV Import Modal */}
      <CsvImportModal
        open={importModalOpen}
        portfolioId={portfolioId}
        accountId={accountId}
        onClose={() => setImportModalOpen(false)}
      />
    </Box>
  );
}

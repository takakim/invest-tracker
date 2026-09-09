import React, { useMemo, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  IconButton,
  InputAdornment,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import EditNoteOutlinedIcon from '@mui/icons-material/EditNoteOutlined';
import DownloadIcon from '@mui/icons-material/Download';
import SearchIcon from '@mui/icons-material/Search';

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

const FILTER_TYPES: (TransactionType | 'ALL')[] = [
  'ALL',
  'BUY',
  'SELL',
  'DIVIDEND',
  'INTEREST',
  'DEPOSIT',
  'WITHDRAWAL',
  'FEE',
];

export function TransactionTable({
  portfolioId,
  accountId,
  defaultCurrency = 'GBP',
  isReadOnly = false,
}: TransactionTableProps) {
  const [selectedTypeFilter, setSelectedTypeFilter] = useState<TransactionType | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);

  const {
    data: transactions = [],
    isLoading,
    error,
    refetch,
  } = useTransactionsList(portfolioId, accountId);

  const createMutation = useCreateTransaction(portfolioId, accountId);

  const filteredTransactions = useMemo(() => {
    return transactions.filter((tx) => {
      if (selectedTypeFilter !== 'ALL' && tx.type !== selectedTypeFilter) {
        return false;
      }
      if (startDate) {
        const txDate = tx.tradeDate.slice(0, 10);
        if (txDate < startDate) return false;
      }
      if (endDate) {
        const txDate = tx.tradeDate.slice(0, 10);
        if (txDate > endDate) return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const matchesInstrument = tx.instrumentName?.toLowerCase().includes(q);
        const matchesTicker = tx.instrumentTicker?.toLowerCase().includes(q);
        const matchesNotes = tx.notes?.toLowerCase().includes(q);
        const matchesType = tx.type.toLowerCase().includes(q);
        if (!matchesInstrument && !matchesTicker && !matchesNotes && !matchesType) {
          return false;
        }
      }
      return true;
    });
  }, [transactions, selectedTypeFilter, startDate, endDate, searchQuery]);

  const handleExportCsv = () => {
    if (filteredTransactions.length === 0) return;
    const headers = [
      'Date',
      'Type',
      'Instrument',
      'Ticker',
      'Quantity',
      'Price',
      'Gross Amount',
      'Fee Amount',
      'Tax Amount',
      'Net Amount',
      'Currency',
      'Status',
      'Notes',
    ];
    const rows = filteredTransactions.map((tx) => [
      `"${tx.tradeDate}"`,
      `"${tx.type}"`,
      `"${(tx.instrumentName || '').replace(/"/g, '""')}"`,
      `"${(tx.instrumentTicker || '').replace(/"/g, '""')}"`,
      tx.quantity != null ? tx.quantity : '',
      tx.price != null ? tx.price : '',
      tx.grossAmount != null ? tx.grossAmount : '',
      tx.feeAmount != null ? tx.feeAmount : '',
      tx.taxAmount != null ? tx.taxAmount : '',
      tx.netAmount != null ? tx.netAmount : '',
      `"${tx.currency}"`,
      `"${tx.status}"`,
      `"${(tx.notes || '').replace(/"/g, '""')}"`,
    ]);
    const csvContent =
      'data:text/csv;charset=utf-8,' +
      [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `transactions-account-${accountId}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleResetFilters = () => {
    setSelectedTypeFilter('ALL');
    setSearchQuery('');
    setStartDate('');
    setEndDate('');
  };

  const hasActiveFilters =
    selectedTypeFilter !== 'ALL' || Boolean(searchQuery.trim()) || Boolean(startDate) || Boolean(endDate);

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
        direction={{ xs: 'column', md: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, gap: 2, mb: 2 }}
      >
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Transaction Ledger
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Immutable log of trade executions, cash movements, dividends, and fees.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
          <Button
            variant="outlined"
            size="small"
            startIcon={<DownloadIcon />}
            onClick={handleExportCsv}
            disabled={filteredTransactions.length === 0}
          >
            Export CSV
          </Button>
          {!isReadOnly && (
            <>
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
            </>
          )}
        </Stack>
      </Stack>

      {/* Filter Controls Bar */}
      <Paper variant="outlined" sx={{ p: 2, mb: 2, borderRadius: 2 }}>
        <Stack spacing={1.5}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ alignItems: { md: 'center' } }}>
            <TextField
              placeholder="Search by instrument name, ticker, notes, or type..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              size="small"
              sx={{ flexGrow: 1 }}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon color="action" fontSize="small" />
                    </InputAdornment>
                  ),
                },
              }}
            />
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <TextField
                label="From Date"
                type="date"
                size="small"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
                sx={{ width: { xs: '50%', sm: 160 } }}
              />
              <TextField
                label="To Date"
                type="date"
                size="small"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
                sx={{ width: { xs: '50%', sm: 160 } }}
              />
              {hasActiveFilters && (
                <Button size="small" onClick={handleResetFilters} color="inherit">
                  Reset
                </Button>
              )}
            </Stack>
          </Stack>

          {/* Type Filter Chips */}
          <Stack direction="row" spacing={0.75} sx={{ flexWrap: 'wrap', gap: 0.5, alignItems: 'center' }}>
            <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5, fontWeight: 600 }}>
              Type:
            </Typography>
            {FILTER_TYPES.map((t) => {
              const isSelected = selectedTypeFilter === t;
              return (
                <Chip
                  key={t}
                  label={t}
                  size="small"
                  clickable
                  variant={isSelected ? 'filled' : 'outlined'}
                  color={isSelected ? (t === 'ALL' ? 'primary' : (getTypeChipColor(t as TransactionType) as any)) : 'default'}
                  onClick={() => setSelectedTypeFilter(t)}
                  sx={{ height: 24, fontSize: '0.75rem' }}
                />
              );
            })}
            <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto', pl: 1 }}>
              Showing {filteredTransactions.length} of {transactions.length}
            </Typography>
          </Stack>
        </Stack>
      </Paper>

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
      ) : filteredTransactions.length === 0 ? (
        <EmptyState
          title="No Matching Transactions"
          description="No transactions matched your search or date criteria."
          actionLabel="Reset Filters"
          onAction={handleResetFilters}
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
              {filteredTransactions.map((tx) => (
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
                        {tx.notes || 'Cash Flow'}
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
      {formOpen && (
        <TransactionFormModal
          open={formOpen}
          defaultCurrency={defaultCurrency}
          isPending={createMutation.isPending}
          error={createMutation.error}
          onClose={() => setFormOpen(false)}
          onSubmit={handleFormSubmit}
        />
      )}

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

import React, { useState, useMemo } from 'react';
import {
  Box,
  Button,
  Chip,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Paper,
  Select,
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
import SearchIcon from '@mui/icons-material/Search';
import PriceChangeOutlinedIcon from '@mui/icons-material/PriceChangeOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import SyncIcon from '@mui/icons-material/Sync';

import {
  useInstrumentsList,
  useCreateInstrument,
  useUpdateInstrument,
  useRefreshAllPrices,
  useRefreshInstrumentPrice,
} from './useInstruments';
import { InstrumentFormModal } from './InstrumentFormModal';
import { ManualPriceModal } from '../market/ManualPriceModal';
import { MarketRatesCard } from '../market/MarketRatesCard';
import { EmptyState, ErrorAlert, LoadingState, SortableTableHead } from '../../components';
import type { AssetClass, Instrument, InstrumentCreateInput } from '../../types';
import { Order, sortRows } from '../../utils/sorting';

const ASSET_CLASS_LABELS: Record<AssetClass, string> = {
  STOCK: 'Stock',
  ETF: 'ETF',
  MUTUAL_FUND: 'Mutual Fund',
  BOND: 'Bond',
  REIT: 'REIT',
  CRYPTO: 'Crypto',
  CASH: 'Cash',
  OTHER: 'Other',
};

export function InstrumentListPage() {
  const { data: instruments = [], isLoading, error, refetch } = useInstrumentsList();
  const createMutation = useCreateInstrument();
  const updateMutation = useUpdateInstrument();
  const refreshAllMutation = useRefreshAllPrices();
  const refreshPriceMutation = useRefreshInstrumentPrice();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingInstrument, setEditingInstrument] = useState<Instrument | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedAssetClass, setSelectedAssetClass] = useState<string>('ALL');
  const [priceModalOpen, setPriceModalOpen] = useState(false);
  const [selectedInstrument, setSelectedInstrument] = useState<Instrument | null>(null);
  const [order, setOrder] = useState<Order>('asc');
  const [orderBy, setOrderBy] = useState<string>('name');

  const handleRequestSort = (property: string) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  const filteredInstruments = useMemo(() => {
    return instruments.filter((inst) => {
      const matchesClass =
        selectedAssetClass === 'ALL' || inst.assetClass === selectedAssetClass;
      if (!matchesClass) return false;

      if (!searchQuery.trim()) return true;
      const q = searchQuery.toLowerCase().trim();
      return (
        inst.name.toLowerCase().includes(q) ||
        (inst.ticker && inst.ticker.toLowerCase().includes(q)) ||
        (inst.isin && inst.isin.toLowerCase().includes(q)) ||
        (inst.exchange && inst.exchange.toLowerCase().includes(q))
      );
    });
  }, [instruments, searchQuery, selectedAssetClass]);

  const sortedInstruments = useMemo(() => {
    return sortRows(filteredInstruments, order, orderBy);
  }, [filteredInstruments, order, orderBy]);

  const handleCreateOrEditSubmit = async (formData: InstrumentCreateInput) => {
    if (editingInstrument) {
      await updateMutation.mutateAsync(
        { id: editingInstrument.id, input: formData },
        {
          onSuccess: () => {
            setModalOpen(false);
            setEditingInstrument(null);
          },
        },
      );
    } else {
      await createMutation.mutateAsync(formData, {
        onSuccess: () => setModalOpen(false),
      });
    }
  };

  const handleOpenEditModal = (inst: Instrument) => {
    setEditingInstrument(inst);
    setModalOpen(true);
  };

  const handleOpenPriceModal = (inst: Instrument) => {
    setSelectedInstrument(inst);
    setPriceModalOpen(true);
  };

  return (
    <Box>
      {/* Header */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 3, gap: 2 }}
      >
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Instrument Directory
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Master catalogue of securities, funds, crypto, and cash instruments tracked across portfolios.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5} sx={{ alignSelf: { xs: 'flex-start', sm: 'auto' } }}>
          <Button
            variant="outlined"
            startIcon={refreshAllMutation.isPending ? <SyncIcon sx={{ animation: 'spin 1s linear infinite' }} /> : <RefreshIcon />}
            disabled={refreshAllMutation.isPending}
            onClick={() => refreshAllMutation.mutate()}
          >
            {refreshAllMutation.isPending ? 'Fetching Latest Prices...' : 'Fetch All Latest Prices'}
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setModalOpen(true)}
          >
            Register Instrument
          </Button>
        </Stack>
      </Stack>

      {/* Market FX Rates Card */}
      <Box sx={{ mb: 3 }}>
        <MarketRatesCard />
      </Box>

      <ErrorAlert error={error} onClose={() => refetch()} />

      {/* Filter and Search Bar */}
      <Paper sx={{ p: 2, mb: 3, borderRadius: 2 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: 'center' }}>
          <TextField
            placeholder="Search by name, ticker, ISIN, exchange..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            size="small"
            fullWidth
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
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="asset-class-filter-label">Asset Class</InputLabel>
            <Select
              labelId="asset-class-filter-label"
              value={selectedAssetClass}
              label="Asset Class"
              onChange={(e) => setSelectedAssetClass(e.target.value)}
            >
              <MenuItem value="ALL">All Asset Classes</MenuItem>
              {Object.entries(ASSET_CLASS_LABELS).map(([value, label]) => (
                <MenuItem key={value} value={value}>
                  {label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
      </Paper>

      {/* Instruments Table */}
      {isLoading ? (
        <LoadingState message="Loading instrument directory..." />
      ) : filteredInstruments.length === 0 ? (
        <EmptyState
          title="No Matching Instruments"
          description="No instruments matched your search and filter criteria. Try adjusting your query."
        />
      ) : (
        <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
          <Table aria-label="instruments table">
            <SortableTableHead<Instrument>
              headCells={[
                { id: 'name', label: 'Instrument Name', sortable: true },
                { id: 'assetClass', label: 'Asset Class', sortable: true },
                { id: 'ticker', label: 'Ticker / Symbol', sortable: true },
                { id: 'isin', label: 'ISIN', sortable: true },
                { id: 'latestPrice', label: 'Latest Price', align: 'right', sortable: true },
                { id: 'priceAsOf', label: 'Last Updated', align: 'right', sortable: true },
                { id: 'currency', label: 'Currency', sortable: true },
                { id: 'actions', label: 'Actions', align: 'right', sortable: false },
              ]}
              order={order}
              orderBy={orderBy}
              onRequestSort={handleRequestSort}
            />
            <TableBody>
              {sortedInstruments.map((instrument) => (
                <TableRow key={instrument.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <span>{instrument.name}</span>
                      {instrument.manualPriceOnly && (
                        <Chip
                          label="Manual Only"
                          size="small"
                          color="default"
                          variant="outlined"
                          sx={{ fontSize: '0.65rem', height: 20 }}
                        />
                      )}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={ASSET_CLASS_LABELS[instrument.assetClass] || instrument.assetClass}
                      size="small"
                      color={
                        instrument.assetClass === 'STOCK'
                          ? 'primary'
                          : instrument.assetClass === 'ETF'
                          ? 'secondary'
                          : instrument.assetClass === 'CRYPTO'
                          ? 'warning'
                          : 'default'
                      }
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell>
                    {instrument.ticker ? (
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {instrument.ticker}
                      </Typography>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        —
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    {instrument.isin ? (
                      <Typography
                        variant="body2"
                        sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}
                      >
                        {instrument.isin}
                      </Typography>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        —
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {instrument.latestPrice != null ? (
                      <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end', alignItems: 'center' }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {Number(instrument.latestPrice).toLocaleString(undefined, {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 4,
                          })}{' '}
                          {instrument.priceCurrency || instrument.currency}
                        </Typography>
                        {instrument.isStale && (
                          <Tooltip title="Quote is older than 24 hours">
                            <Chip label="Stale" size="small" color="warning" sx={{ height: 18, fontSize: '0.65rem' }} />
                          </Tooltip>
                        )}
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        —
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {instrument.priceAsOf ? (
                      <Typography variant="caption" color="text.secondary">
                        {new Date(instrument.priceAsOf).toLocaleDateString()} {new Date(instrument.priceAsOf).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </Typography>
                    ) : (
                      <Typography variant="caption" color="text.secondary">
                        Never
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip label={instrument.currency} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', alignItems: 'center' }}>
                      <Tooltip title="Fetch latest quote for this instrument">
                        <span>
                          <IconButton
                            size="small"
                            color="primary"
                            disabled={refreshPriceMutation.isPending}
                            onClick={() => refreshPriceMutation.mutate(instrument.id)}
                          >
                            <RefreshIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Button
                        size="small"
                        startIcon={<EditOutlinedIcon fontSize="small" />}
                        onClick={() => handleOpenEditModal(instrument)}
                      >
                        Edit
                      </Button>
                      <Button
                        size="small"
                        startIcon={<PriceChangeOutlinedIcon fontSize="small" />}
                        onClick={() => handleOpenPriceModal(instrument)}
                      >
                        Set Price
                      </Button>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Register / Edit Instrument Modal */}
      <InstrumentFormModal
        open={modalOpen}
        instrument={editingInstrument}
        isPending={createMutation.isPending || updateMutation.isPending}
        error={editingInstrument ? updateMutation.error : createMutation.error}
        onClose={() => {
          setModalOpen(false);
          setEditingInstrument(null);
        }}
        onSubmit={handleCreateOrEditSubmit}
      />

      {/* Manual Price Override Modal */}
      <ManualPriceModal
        open={priceModalOpen}
        instrument={selectedInstrument}
        onClose={() => {
          setPriceModalOpen(false);
          setSelectedInstrument(null);
        }}
      />
    </Box>
  );
}


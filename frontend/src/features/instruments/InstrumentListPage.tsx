import React, { useState, useMemo } from 'react';
import {
  Box,
  Button,
  Chip,
  FormControl,
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
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import PriceChangeOutlinedIcon from '@mui/icons-material/PriceChangeOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';

import { useInstrumentsList, useCreateInstrument, useUpdateInstrument } from './useInstruments';
import { InstrumentFormModal } from './InstrumentFormModal';
import { ManualPriceModal } from '../market/ManualPriceModal';
import { MarketRatesCard } from '../market/MarketRatesCard';
import { EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { AssetClass, Instrument, InstrumentCreateInput } from '../../types';

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

  const [modalOpen, setModalOpen] = useState(false);
  const [editingInstrument, setEditingInstrument] = useState<Instrument | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedAssetClass, setSelectedAssetClass] = useState<string>('ALL');
  const [priceModalOpen, setPriceModalOpen] = useState(false);
  const [selectedInstrument, setSelectedInstrument] = useState<Instrument | null>(null);

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
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setModalOpen(true)}
          sx={{ alignSelf: { xs: 'flex-start', sm: 'auto' } }}
        >
          Register Instrument
        </Button>
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
            <TableHead>
              <TableRow>
                <TableCell>Instrument Name</TableCell>
                <TableCell>Asset Class</TableCell>
                <TableCell>Ticker / Symbol</TableCell>
                <TableCell>ISIN</TableCell>
                <TableCell>Exchange</TableCell>
                <TableCell>Currency</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredInstruments.map((instrument) => (
                <TableRow key={instrument.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{instrument.name}</TableCell>
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
                  <TableCell>
                    {instrument.exchange || (
                      <Typography variant="body2" color="text.secondary">
                        —
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip label={instrument.currency} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
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

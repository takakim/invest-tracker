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

import { useInstrumentsList, useCreateInstrument } from './useInstruments';
import { InstrumentFormModal } from './InstrumentFormModal';
import { EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { AssetClass, InstrumentCreateInput } from '../../types';

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

  const [modalOpen, setModalOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedAssetClass, setSelectedAssetClass] = useState<string>('ALL');

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

  const handleCreateSubmit = async (formData: InstrumentCreateInput) => {
    await createMutation.mutateAsync(formData, {
      onSuccess: () => setModalOpen(false),
    });
  };

  return (
    <Box>
      {/* Header */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 4, gap: 2 }}
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

      <ErrorAlert error={error} onClose={() => refetch()} />

      {/* Filter and Search Bar */}
      <Paper sx={{ p: 2, mb: 3, borderRadius: 2 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField
            placeholder="Search by name, ticker, or ISIN..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            fullWidth
            size="small"
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="action" />
                  </InputAdornment>
                ),
              },
            }}
          />

          <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 180 } }}>
            <InputLabel id="asset-class-filter-label">Asset Class</InputLabel>
            <Select
              labelId="asset-class-filter-label"
              label="Asset Class"
              value={selectedAssetClass}
              onChange={(e) => setSelectedAssetClass(e.target.value)}
            >
              <MenuItem value="ALL">All Asset Classes</MenuItem>
              {Object.entries(ASSET_CLASS_LABELS).map(([key, label]) => (
                <MenuItem key={key} value={key}>
                  {label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
      </Paper>

      {/* Content */}
      {isLoading ? (
        <LoadingState variant="table" count={4} />
      ) : instruments.length === 0 ? (
        <EmptyState
          title="No Instruments Registered"
          description="Register instruments into your master catalogue to use them across any portfolio account."
          actionLabel="Register Instrument"
          onAction={() => setModalOpen(true)}
          icon={<ShowChartOutlinedIcon sx={{ fontSize: 56, opacity: 0.7 }} />}
        />
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
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Register Instrument Modal */}
      <InstrumentFormModal
        open={modalOpen}
        isPending={createMutation.isPending}
        error={createMutation.error}
        onClose={() => setModalOpen(false)}
        onSubmit={handleCreateSubmit}
      />
    </Box>
  );
}

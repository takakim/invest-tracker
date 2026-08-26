import React, { useState } from 'react';
import {
  Box,
  Typography,
  Stack,
  Grid,
  Card,
  CardContent,
  Button,
  Divider,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
} from '@mui/material';
import CurrencyExchangeIcon from '@mui/icons-material/CurrencyExchange';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import { MarketRatesCard } from './MarketRatesCard';
import { ManualPriceModal } from './ManualPriceModal';
import { ManualFxModal } from './ManualFxModal';
import { useInstrumentsList } from '../instruments/useInstruments';
import { useLatestQuote } from './useMarketData';
import { LoadingState } from '../../components';
import type { Instrument } from '../../types';

function InstrumentQuoteRow({ instrument, onOverride }: { instrument: Instrument; onOverride: (inst: Instrument) => void }) {
  const { data: quote, isLoading } = useLatestQuote(instrument.id);

  return (
    <TableRow hover>
      <TableCell sx={{ fontWeight: 600 }}>{instrument.name}</TableCell>
      <TableCell>{instrument.ticker || '—'}</TableCell>
      <TableCell>{instrument.assetClass}</TableCell>
      <TableCell>{instrument.currency}</TableCell>
      <TableCell sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
        {isLoading ? '...' : quote ? Number(quote.price).toFixed(2) : '—'}
      </TableCell>
      <TableCell>
        {quote?.sourceType === 'MANUAL' ? (
          <Chip label="MANUAL" size="small" color="primary" variant="outlined" />
        ) : (
          <Chip label="PROVIDER" size="small" variant="outlined" />
        )}
      </TableCell>
      <TableCell align="right">
        <Button
          size="small"
          startIcon={<EditOutlinedIcon fontSize="small" />}
          onClick={() => onOverride(instrument)}
        >
          Override
        </Button>
      </TableCell>
    </TableRow>
  );
}

export function MarketRatesPage() {
  const { data: instruments = [], isLoading: isInstrumentsLoading } = useInstrumentsList();
  const [selectedInstrument, setSelectedInstrument] = useState<Instrument | null>(null);
  const [priceModalOpen, setPriceModalOpen] = useState(false);
  const [fxModalOpen, setFxModalOpen] = useState(false);

  const handleOpenPriceOverride = (inst: Instrument) => {
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
            Market Data & FX Center
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Foreign exchange conversion rates, historical quotes, and manual audit overrides.
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<CurrencyExchangeIcon />}
          onClick={() => setFxModalOpen(true)}
        >
          New FX Override
        </Button>
      </Stack>

      <Grid container spacing={3} sx={{ mb: 4 }}>
        {/* FX Rates Card */}
        <Grid size={{ xs: 12, md: 5 }}>
          <MarketRatesCard />
        </Grid>

        {/* Live Securities Quotes */}
        <Grid size={{ xs: 12, md: 7 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
                <ShowChartOutlinedIcon color="primary" fontSize="small" />
                <Typography variant="h6" sx={{ fontSize: '1rem', fontWeight: 600 }}>
                  Securities & Instruments Live Quotes
                </Typography>
              </Stack>

              {isInstrumentsLoading ? (
                <LoadingState variant="skeleton" count={3} />
              ) : instruments.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  No instruments registered yet. Add instruments in the Instrument Directory.
                </Typography>
              ) : (
                <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
                  <Table size="small" aria-label="market quotes table">
                    <TableHead>
                      <TableRow>
                        <TableCell>Security</TableCell>
                        <TableCell>Ticker</TableCell>
                        <TableCell>Class</TableCell>
                        <TableCell>Currency</TableCell>
                        <TableCell>Price</TableCell>
                        <TableCell>Source</TableCell>
                        <TableCell align="right">Action</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {instruments.slice(0, 8).map((inst) => (
                        <InstrumentQuoteRow
                          key={inst.id}
                          instrument={inst}
                          onOverride={handleOpenPriceOverride}
                        />
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Manual Modals */}
      {selectedInstrument && (
        <ManualPriceModal
          open={priceModalOpen}
          instrument={selectedInstrument}
          onClose={() => {
            setPriceModalOpen(false);
            setSelectedInstrument(null);
          }}
        />
      )}

      <ManualFxModal
        open={fxModalOpen}
        initialBase="GBP"
        initialQuote="USD"
        onClose={() => setFxModalOpen(false)}
      />
    </Box>
  );
}

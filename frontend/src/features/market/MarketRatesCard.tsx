import React, { useState } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Stack,
  Button,
  Chip,
  Box,
  Divider,
} from '@mui/material';
import CurrencyExchangeIcon from '@mui/icons-material/CurrencyExchange';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import { useFxRate } from './useFxRates';
import { ManualFxModal } from './ManualFxModal';

interface FxPairRowProps {
  base: string;
  quote: string;
  onOverride: (base: string, quote: string) => void;
}

function FxPairRow({ base, quote, onOverride }: FxPairRowProps) {
  const { data: fxQuote, isLoading } = useFxRate(base, quote);

  return (
    <Stack
      direction="row"
      sx={{ justifyContent: 'space-between', alignItems: 'center', py: 1 }}
    >
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {base}/{quote}
        </Typography>
        {fxQuote?.sourceType === 'MANUAL' && (
          <Chip label="MANUAL" size="small" color="primary" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />
        )}
        {fxQuote?.isDerived && (
          <Chip label="DERIVED" size="small" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />
        )}
      </Stack>

      <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
        <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 500 }}>
          {isLoading ? '...' : fxQuote ? Number(fxQuote.rate).toFixed(4) : 'N/A'}
        </Typography>
        <Button
          size="small"
          startIcon={<EditOutlinedIcon fontSize="small" />}
          onClick={() => onOverride(base, quote)}
          sx={{ minWidth: 'auto', p: 0.5 }}
        >
          Override
        </Button>
      </Stack>
    </Stack>
  );
}

export function MarketRatesCard() {
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedPair, setSelectedPair] = useState<{ base: string; quote: string }>({ base: 'GBP', quote: 'USD' });

  const handleOpenOverride = (base: string, quote: string) => {
    setSelectedPair({ base, quote });
    setModalOpen(true);
  };

  const PAIRS = [
    { base: 'GBP', quote: 'USD' },
    { base: 'EUR', quote: 'USD' },
    { base: 'GBP', quote: 'EUR' },
    { base: 'USD', quote: 'CAD' },
  ];

  return (
    <>
      <Card variant="outlined">
        <CardContent>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <CurrencyExchangeIcon color="primary" fontSize="small" />
              <Typography variant="h6" sx={{ fontSize: '1rem', fontWeight: 600 }}>
                Exchange Rates (FX)
              </Typography>
            </Stack>
            <Button size="small" variant="outlined" onClick={() => handleOpenOverride('GBP', 'USD')}>
              Custom Rate
            </Button>
          </Stack>

          <Box>
            {PAIRS.map((pair, idx) => (
              <React.Fragment key={`${pair.base}-${pair.quote}`}>
                <FxPairRow base={pair.base} quote={pair.quote} onOverride={handleOpenOverride} />
                {idx < PAIRS.length - 1 && <Divider />}
              </React.Fragment>
            ))}
          </Box>
        </CardContent>
      </Card>

      <ManualFxModal
        open={modalOpen}
        initialBase={selectedPair.base}
        initialQuote={selectedPair.quote}
        onClose={() => setModalOpen(false)}
      />
    </>
  );
}

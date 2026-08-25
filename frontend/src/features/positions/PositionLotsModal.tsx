import React from 'react';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';

import { usePositionLots } from './usePositions';
import { EmptyState, ErrorAlert, LoadingState } from '../../components';
import type { Position } from '../../types';

interface PositionLotsModalProps {
  open: boolean;
  portfolioId: string;
  accountId: string;
  position: Position | null;
  onClose: () => void;
}

export function PositionLotsModal({
  open,
  portfolioId,
  accountId,
  position,
  onClose,
}: PositionLotsModalProps) {
  const positionId = position?.id || '';
  const {
    data: lotsDetail,
    isLoading,
    error,
    refetch,
  } = usePositionLots(portfolioId, accountId, positionId);

  if (!position) return null;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <LayersOutlinedIcon color="primary" />
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Tax Lots — {position.instrumentName}
          </Typography>
        </Stack>
        <Typography variant="caption" color="text.secondary">
          Derived acquisition lots and cost basis breakdown based on portfolio methodology.
        </Typography>
      </DialogTitle>

      <DialogContent dividers>
        <ErrorAlert error={error} onClose={() => refetch()} />

        {isLoading ? (
          <LoadingState variant="table" count={2} />
        ) : !lotsDetail || lotsDetail.openLots.length === 0 ? (
          <EmptyState
            title="No Open Tax Lots"
            description="All acquisition lots for this position have been fully disposed or closed."
            icon={<LayersOutlinedIcon sx={{ fontSize: 48, opacity: 0.7 }} />}
          />
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={3}
                sx={{ justifyContent: 'space-between' }}
              >
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Cost Basis Method
                  </Typography>
                  <Box sx={{ mt: 0.5 }}>
                    <Chip label={lotsDetail.costBasisMethod} size="small" color="primary" />
                  </Box>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Total Quantity
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {lotsDetail.totalQuantity.toLocaleString()}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Average Unit Cost
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {lotsDetail.currency} {lotsDetail.averageUnitCostAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Total Cost Basis
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {lotsDetail.currency} {lotsDetail.totalCostBasisAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Realized P&L
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{
                      fontWeight: 600,
                      color:
                        lotsDetail.realizedGainLossAmount >= 0
                          ? 'success.main'
                          : 'error.main',
                    }}
                  >
                    {lotsDetail.currency} {lotsDetail.realizedGainLossAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                  </Typography>
                </Box>
              </Stack>
            </Paper>

            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              Open Acquisition Lots ({lotsDetail.openLots.length})
            </Typography>

            <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Acquisition Date</TableCell>
                    <TableCell align="right">Original Qty</TableCell>
                    <TableCell align="right">Remaining Qty</TableCell>
                    <TableCell align="right">Unit Cost</TableCell>
                    <TableCell align="right">Total Cost</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {lotsDetail.openLots.map((lot) => (
                    <TableRow key={lot.lotId} hover>
                      <TableCell>{new Date(lot.acquisitionDate).toLocaleDateString()}</TableCell>
                      <TableCell align="right">{lot.originalQuantity}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>
                        {lot.remainingQuantity}
                      </TableCell>
                      <TableCell align="right">
                        {lot.currency} {lot.unitCostAmount.toFixed(4)}
                      </TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>
                        {lot.currency} {lot.totalCostAmount.toFixed(2)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

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
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';

import {
  usePositionsList,
  useCreatePosition,
  useUpdatePosition,
  useArchivePosition,
  useRecalculatePositions,
} from './usePositions';
import { PositionFormModal } from './PositionFormModal';
import { PositionLotsModal } from './PositionLotsModal';
import { ConfirmDialog, EmptyState, ErrorAlert, LoadingState, SortableTableHead } from '../../components';
import type { Position, PositionCreateInput, PositionUpdateInput } from '../../types';
import type { PositionFormData } from '../../forms/schemas';
import { Order, sortRows } from '../../utils/sorting';

interface PositionTableProps {
  portfolioId: string;
  accountId: string;
  defaultCurrency?: string;
  isReadOnly?: boolean;
}

export function PositionTable({
  portfolioId,
  accountId,
  defaultCurrency = 'GBP',
  isReadOnly = false,
}: PositionTableProps) {
  const {
    data: positions = [],
    isLoading,
    error,
    refetch,
  } = usePositionsList(portfolioId, accountId);

  const createMutation = useCreatePosition(portfolioId, accountId);
  const updateMutation = useUpdatePosition(portfolioId, accountId);
  const archiveMutation = useArchivePosition(portfolioId, accountId);
  const recalculateMutation = useRecalculatePositions(portfolioId);

  const [formOpen, setFormOpen] = useState(false);
  const [editingPosition, setEditingPosition] = useState<Position | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Position | null>(null);
  const [selectedLotsPosition, setSelectedLotsPosition] = useState<Position | null>(null);
  const [order, setOrder] = useState<Order>('asc');
  const [orderBy, setOrderBy] = useState<string>('instrumentName');

  const handleRequestSort = (property: string) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  const sortedPositions = sortRows(positions, order, orderBy);

  const handleOpenCreate = () => {
    setEditingPosition(null);
    setFormOpen(true);
  };

  const handleOpenEdit = (position: Position) => {
    setEditingPosition(position);
    setFormOpen(true);
  };

  const handleFormSubmit = async (formData: PositionFormData) => {
    if (editingPosition) {
      const updatePayload: PositionUpdateInput = {
        quantity: Number(formData.quantity),
        costBasisAmount: formData.costBasisAmount ? Number(formData.costBasisAmount) : undefined,
        costBasisCurrency: formData.costBasisCurrency || undefined,
      };

      await updateMutation.mutateAsync(
        { positionId: editingPosition.id, input: updatePayload },
        {
          onSuccess: () => setFormOpen(false),
        },
      );
    } else {
      const createPayload: PositionCreateInput = {
        instrumentId: formData.instrumentId,
        quantity: Number(formData.quantity),
        costBasisAmount: formData.costBasisAmount ? Number(formData.costBasisAmount) : undefined,
        costBasisCurrency: formData.costBasisCurrency || undefined,
      };

      await createMutation.mutateAsync(createPayload, {
        onSuccess: () => setFormOpen(false),
      });
    }
  };

  const handleConfirmArchive = async () => {
    if (!archiveTarget) return;
    await archiveMutation.mutateAsync(archiveTarget.id, {
      onSuccess: () => setArchiveTarget(null),
    });
  };

  return (
    <Box sx={{ mt: 3 }}>
      <Stack
        direction="row"
        sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}
      >
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Position Holdings
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Instruments and holdings owned in this account.
          </Typography>
        </Box>
        {!isReadOnly && (
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={() => recalculateMutation.mutate()}
              disabled={recalculateMutation.isPending}
            >
              Recalculate
            </Button>
            <Button
              variant="contained"
              size="small"
              startIcon={<AddIcon />}
              onClick={handleOpenCreate}
            >
              Add Holding
            </Button>
          </Stack>
        )}
      </Stack>

      <ErrorAlert error={error} onClose={() => refetch()} />

      {isLoading ? (
        <LoadingState variant="table" count={2} />
      ) : positions.length === 0 ? (
        <EmptyState
          title="No Positions in this Account"
          description="Add instrument holdings (e.g. Stocks, ETFs, Mutual Funds) owned in this account."
          actionLabel={!isReadOnly ? 'Add Holding' : undefined}
          onAction={handleOpenCreate}
          icon={<ShowChartOutlinedIcon sx={{ fontSize: 48, opacity: 0.7 }} />}
        />
      ) : (
        <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
          <Table aria-label="positions table" size="small">
            <SortableTableHead<Position>
              headCells={[
                { id: 'instrumentName', label: 'Instrument', sortable: true },
                { id: 'assetClass', label: 'Asset Class', sortable: true },
                { id: 'instrumentTicker', label: 'Ticker / ISIN', sortable: true },
                { id: 'quantity', label: 'Quantity', align: 'right', sortable: true },
                { id: 'costBasisAmount', label: 'Cost Basis', align: 'right', sortable: true },
                { id: 'actions', label: 'Actions', align: 'right', sortable: false },
              ]}
              order={order}
              orderBy={orderBy}
              onRequestSort={handleRequestSort}
            />
            <TableBody>
              {sortedPositions.map((pos) => (
                <TableRow key={pos.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{pos.instrumentName}</TableCell>
                  <TableCell>
                    <Chip label={pos.assetClass} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell>
                    {pos.instrumentTicker ? (
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {pos.instrumentTicker}
                      </Typography>
                    ) : pos.instrumentIsin ? (
                      <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                        {pos.instrumentIsin}
                      </Typography>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        —
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>
                    {pos.quantity}
                  </TableCell>
                  <TableCell align="right">
                    {pos.costBasisAmount != null ? (
                      `${pos.costBasisCurrency || defaultCurrency} ${pos.costBasisAmount.toLocaleString()}`
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        —
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                      <Tooltip title="View tax lots">
                        <IconButton
                          size="small"
                          color="primary"
                          onClick={() => setSelectedLotsPosition(pos)}
                          aria-label={`view tax lots for ${pos.instrumentName}`}
                        >
                          <LayersOutlinedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      {!isReadOnly && (
                        <>
                          <Tooltip title="Edit position">
                            <IconButton
                              size="small"
                              onClick={() => handleOpenEdit(pos)}
                              aria-label={`edit position ${pos.instrumentName}`}
                            >
                              <EditOutlinedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Archive position">
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => setArchiveTarget(pos)}
                              aria-label={`archive position ${pos.instrumentName}`}
                            >
                              <ArchiveOutlinedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Position Create / Edit Modal */}
      <PositionFormModal
        open={formOpen}
        position={editingPosition}
        defaultCurrency={defaultCurrency}
        isPending={createMutation.isPending || updateMutation.isPending}
        error={createMutation.error || updateMutation.error}
        onClose={() => setFormOpen(false)}
        onSubmit={handleFormSubmit}
      />

      {/* Tax Lots Detail Modal */}
      <PositionLotsModal
        open={Boolean(selectedLotsPosition)}
        portfolioId={portfolioId}
        accountId={accountId}
        position={selectedLotsPosition}
        onClose={() => setSelectedLotsPosition(null)}
      />

      {/* Archive Confirm Modal */}
      <ConfirmDialog
        open={Boolean(archiveTarget)}
        title="Archive Position"
        message={`Are you sure you want to archive position "${archiveTarget?.instrumentName}"? History is preserved.`}
        confirmLabel="Archive Position"
        confirmColor="error"
        isPending={archiveMutation.isPending}
        onConfirm={handleConfirmArchive}
        onCancel={() => setArchiveTarget(null)}
      />
    </Box>
  );
}


import React, { useMemo, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  IconButton,
  InputAdornment,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import LayersOutlinedIcon from '@mui/icons-material/LayersOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';

import {
  usePositionsList,
  useAccountPositionsPerformance,
  useCreatePosition,
  useUpdatePosition,
  useArchivePosition,
  useRecalculatePositions,
} from './usePositions';
import { PositionFormModal } from './PositionFormModal';
import { PositionLotsModal } from './PositionLotsModal';
import { PositionPerformanceModal } from './PositionPerformanceModal';
import { HoldingAiEvaluationModal } from '../ai';
import { ConfirmDialog, EmptyState, ErrorAlert, LoadingState, SortableTableHead } from '../../components';
import type { Position, PositionCreateInput, PositionPerformance, PositionUpdateInput } from '../../types';
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
  const [includeClosed, setIncludeClosed] = useState(false);

  const {
    data: performanceItems = [],
    isLoading,
    error,
    refetch,
  } = useAccountPositionsPerformance(portfolioId, accountId, includeClosed);

  const createMutation = useCreatePosition(portfolioId, accountId);
  const updateMutation = useUpdatePosition(portfolioId, accountId);
  const archiveMutation = useArchivePosition(portfolioId, accountId);
  const recalculateMutation = useRecalculatePositions(portfolioId);

  const [formOpen, setFormOpen] = useState(false);
  const [editingPosition, setEditingPosition] = useState<Position | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<PositionPerformance | null>(null);
  const [selectedLotsPosition, setSelectedLotsPosition] = useState<Position | null>(null);
  const [selectedPerfPosition, setSelectedPerfPosition] = useState<Position | null>(null);
  const [aiTarget, setAiTarget] = useState<PositionPerformance | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [order, setOrder] = useState<Order>('asc');
  const [orderBy, setOrderBy] = useState<string>('instrumentName');

  const handleRequestSort = (property: string) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  const filteredItems = useMemo(() => {
    if (!searchQuery.trim()) return performanceItems;
    const q = searchQuery.toLowerCase().trim();
    return performanceItems.filter((item) => {
      return (
        item.instrumentName.toLowerCase().includes(q) ||
        (item.ticker && item.ticker.toLowerCase().includes(q)) ||
        (item.isin && item.isin.toLowerCase().includes(q)) ||
        (item.assetClass && item.assetClass.toLowerCase().includes(q))
      );
    });
  }, [performanceItems, searchQuery]);

  const sortedItems = useMemo(() => {
    return sortRows(filteredItems, order, orderBy);
  }, [filteredItems, order, orderBy]);

  const handleOpenCreate = () => {
    setEditingPosition(null);
    setFormOpen(true);
  };

  const handleOpenEdit = (perf: PositionPerformance) => {
    if (!perf.positionId) return;
    const pos: Position = {
      id: perf.positionId,
      accountId: perf.accountId,
      instrumentId: perf.instrumentId,
      instrumentName: perf.instrumentName,
      instrumentTicker: perf.ticker,
      instrumentIsin: perf.isin,
      assetClass: perf.assetClass,
      quantity: perf.currentQuantity,
      costBasisAmount: perf.currentCostBasis,
      costBasisCurrency: perf.currency,
      status: perf.status === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    setEditingPosition(pos);
    setFormOpen(true);
  };

  const handleOpenLots = (perf: PositionPerformance) => {
    if (!perf.positionId) return;
    const pos: Position = {
      id: perf.positionId,
      accountId: perf.accountId,
      instrumentId: perf.instrumentId,
      instrumentName: perf.instrumentName,
      instrumentTicker: perf.ticker,
      instrumentIsin: perf.isin,
      assetClass: perf.assetClass,
      quantity: perf.currentQuantity,
      costBasisAmount: perf.currentCostBasis,
      costBasisCurrency: perf.currency,
      status: perf.status === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    setSelectedLotsPosition(pos);
  };

  const handleOpenPerformance = (perf: PositionPerformance) => {
    const pos: Position = {
      id: perf.positionId || perf.instrumentId,
      accountId: perf.accountId,
      instrumentId: perf.instrumentId,
      instrumentName: perf.instrumentName,
      instrumentTicker: perf.ticker,
      instrumentIsin: perf.isin,
      assetClass: perf.assetClass,
      quantity: perf.currentQuantity,
      costBasisAmount: perf.currentCostBasis,
      costBasisCurrency: perf.currency,
      status: perf.status === 'CLOSED' ? 'ARCHIVED' : 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    setSelectedPerfPosition(pos);
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
    if (!archiveTarget || !archiveTarget.positionId) return;
    await archiveMutation.mutateAsync(archiveTarget.positionId, {
      onSuccess: () => setArchiveTarget(null),
    });
  };

  return (
    <Box sx={{ mt: 3 }}>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, mb: 2, gap: 2 }}
      >
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            Position Holdings & Performance
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Instruments, live valuation, cost basis, unrealized P&L, and total return.
          </Typography>
        </Box>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
          <Tabs
            value={includeClosed ? 1 : 0}
            onChange={(_, val) => setIncludeClosed(val === 1)}
            sx={{ minHeight: 36, '& .MuiTab-root': { minHeight: 36, py: 0.5, px: 1.5, fontSize: '0.85rem' } }}
          >
            <Tab label="Active Holdings" />
            <Tab label="All (incl. Closed)" />
          </Tabs>
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
      </Stack>

      <Box sx={{ mb: 2 }}>
        <TextField
          placeholder="Filter holdings by name, ticker, ISIN, asset class..."
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
      </Box>

      <ErrorAlert error={error} onClose={() => refetch()} />

      {isLoading ? (
        <LoadingState variant="table" count={2} />
      ) : performanceItems.length === 0 ? (
        <EmptyState
          title={includeClosed ? "No Position History in this Account" : "No Active Holdings in this Account"}
          description={includeClosed ? "Import or record transactions to track portfolio holdings and historical performance." : "Add instrument holdings or switch to view past closed positions."}
          actionLabel={!isReadOnly ? 'Add Holding' : undefined}
          onAction={handleOpenCreate}
          icon={<ShowChartOutlinedIcon sx={{ fontSize: 48, opacity: 0.7 }} />}
        />
      ) : sortedItems.length === 0 ? (
        <EmptyState
          title="No Matching Holdings"
          description={`No positions matched your search query "${searchQuery}".`}
          actionLabel="Clear Filter"
          onAction={() => setSearchQuery('')}
          icon={<SearchIcon sx={{ fontSize: 48, opacity: 0.7 }} />}
        />
      ) : (
        <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
          <Table aria-label="positions table" size="small">
            <SortableTableHead<PositionPerformance>
              headCells={[
                { id: 'instrumentName', label: 'Instrument', sortable: true },
                { id: 'ticker', label: 'Ticker / ISIN', sortable: true },
                { id: 'status', label: 'Status', sortable: true },
                { id: 'currentQuantity', label: 'Shares', align: 'right', sortable: true },
                { id: 'currentPrice', label: 'Live Price', align: 'right', sortable: true },
                { id: 'currentMarketValue', label: 'Market Value', align: 'right', sortable: true },
                { id: 'currentCostBasis', label: 'Cost Basis', align: 'right', sortable: true },
                { id: 'unrealizedGainLoss', label: 'Unrealized P&L', align: 'right', sortable: true },
                { id: 'netTotalReturnAmount', label: 'Total Return', align: 'right', sortable: true },
                { id: 'actions', label: 'Actions', align: 'right', sortable: false },
              ]}
              order={order}
              orderBy={orderBy}
              onRequestSort={handleRequestSort}
            />
            <TableBody>
              {sortedItems.map((item, idx) => {
                const isProfitable = item.netTotalReturnAmount >= 0;
                const returnColor = isProfitable ? 'success.main' : 'error.main';
                const isItemClosed = item.status === 'CLOSED' || item.currentQuantity === 0;

                const unrealized = item.unrealizedGainLoss ?? 0;
                const unrealizedPct = item.currentCostBasis > 0 ? (unrealized / item.currentCostBasis) * 100 : 0;
                const unrealizedColor = unrealized >= 0 ? 'success.main' : 'error.main';

                return (
                  <TableRow key={item.positionId || `${item.instrumentId}-${idx}`} hover>
                    <TableCell sx={{ fontWeight: 600, minWidth: 160 }}>
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {item.instrumentName}
                        </Typography>
                        <Chip label={item.assetClass} size="small" variant="outlined" sx={{ height: 18, fontSize: '0.65rem', mt: 0.25 }} />
                      </Box>
                    </TableCell>
                    <TableCell>
                      {item.ticker ? (
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {item.ticker}
                        </Typography>
                      ) : item.isin ? (
                        <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                          {item.isin}
                        </Typography>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={isItemClosed ? 'Closed' : 'Active'}
                        size="small"
                        color={isItemClosed ? 'default' : 'success'}
                        variant={isItemClosed ? 'outlined' : 'filled'}
                        sx={{ height: 20, fontSize: '0.7rem' }}
                      />
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>
                      {item.currentQuantity.toLocaleString(undefined, {
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 4,
                      })}
                    </TableCell>
                    <TableCell align="right">
                      {item.currentPrice != null && item.currentPrice > 0 ? (
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {item.currency} {item.currentPrice.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}
                        </Typography>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      {item.currentMarketValue != null && item.currentMarketValue > 0 ? (
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {item.currency} {item.currentMarketValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </Typography>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      {item.currentCostBasis != null && item.currentCostBasis > 0 ? (
                        `${item.currency} ${item.currentCostBasis.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="column" sx={{ alignItems: 'flex-end' }}>
                        <Typography variant="body2" sx={{ fontWeight: 600, color: unrealizedColor }}>
                          {unrealized >= 0 ? '+' : ''}
                          {item.currency} {unrealized.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </Typography>
                        <Typography variant="caption" sx={{ fontWeight: 500, color: unrealizedColor }}>
                          ({unrealizedPct >= 0 ? '+' : ''}
                          {unrealizedPct.toFixed(2)}%)
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="column" sx={{ alignItems: 'flex-end' }}>
                        <Typography variant="body2" sx={{ fontWeight: 700, color: returnColor }}>
                          {item.netTotalReturnAmount >= 0 ? '+' : ''}
                          {item.currency} {item.netTotalReturnAmount.toFixed(2)}
                        </Typography>
                        <Typography variant="caption" sx={{ fontWeight: 600, color: returnColor }}>
                          ({item.totalReturnPercentage >= 0 ? '+' : ''}
                          {item.totalReturnPercentage.toFixed(2)}%)
                        </Typography>
                      </Stack>
                    </TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end', alignItems: 'center' }}>
                        <Tooltip title="AI fundamental & risk evaluation (Gemma 4 12B)">
                          <IconButton
                            size="small"
                            color="primary"
                            onClick={() => setAiTarget(item)}
                            aria-label={`ai evaluate ${item.instrumentName}`}
                          >
                            <AutoAwesomeIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>

                        <Tooltip title="View performance, dividends, and full ledger">
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<ShowChartOutlinedIcon fontSize="small" />}
                            onClick={() => handleOpenPerformance(item)}
                            sx={{ py: 0.25, px: 1, fontSize: '0.75rem' }}
                          >
                            Performance
                          </Button>
                        </Tooltip>

                        {item.positionId && (
                          <Tooltip title="View open tax lots">
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={() => handleOpenLots(item)}
                              aria-label={`view tax lots for ${item.instrumentName}`}
                            >
                              <LayersOutlinedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}

                        {!isReadOnly && item.positionId && (
                          <>
                            <Tooltip title="Edit position">
                              <IconButton
                                size="small"
                                onClick={() => handleOpenEdit(item)}
                                aria-label={`edit position ${item.instrumentName}`}
                              >
                                <EditOutlinedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Archive position">
                              <IconButton
                                size="small"
                                color="error"
                                onClick={() => setArchiveTarget(item)}
                                aria-label={`archive position ${item.instrumentName}`}
                              >
                                <ArchiveOutlinedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Position Create / Edit Modal */}
      {formOpen && (
        <PositionFormModal
          open={formOpen}
          position={editingPosition}
          defaultCurrency={defaultCurrency}
          isPending={createMutation.isPending || updateMutation.isPending}
          error={createMutation.error || updateMutation.error}
          onClose={() => setFormOpen(false)}
          onSubmit={handleFormSubmit}
        />
      )}

      {/* Tax Lots Detail Modal */}
      <PositionLotsModal
        open={Boolean(selectedLotsPosition)}
        portfolioId={portfolioId}
        accountId={accountId}
        position={selectedLotsPosition}
        onClose={() => setSelectedLotsPosition(null)}
      />

      {/* Performance Detail Modal */}
      <PositionPerformanceModal
        open={Boolean(selectedPerfPosition)}
        portfolioId={portfolioId}
        accountId={accountId}
        position={selectedPerfPosition}
        onClose={() => setSelectedPerfPosition(null)}
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

      {/* AI Holding Evaluation Modal */}
      {aiTarget && (
        <HoldingAiEvaluationModal
          open={Boolean(aiTarget)}
          onClose={() => setAiTarget(null)}
          portfolioId={portfolioId}
          instrumentId={aiTarget.instrumentId}
          symbol={aiTarget.ticker || aiTarget.instrumentName}
          instrumentName={aiTarget.instrumentName}
        />
      )}
    </Box>
  );
}



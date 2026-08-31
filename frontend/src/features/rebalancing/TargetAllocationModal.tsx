import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Stack,
  Box,
  Typography,
  IconButton,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  ToggleButtonGroup,
  ToggleButton,
  Alert,
  Grid,
  Divider,
} from '@mui/material';
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import AddCircleOutlineOutlinedIcon from '@mui/icons-material/AddCircleOutlineOutlined';
import AutoFixHighOutlinedIcon from '@mui/icons-material/AutoFixHighOutlined';
import { useInstrumentsList } from '../instruments/useInstruments';
import { useSaveTargetAllocation, useDeleteTargetAllocation } from './useRebalancing';
import type { TargetAllocationPlan, AllocationType, AssetClass } from '../../types';

interface TargetAllocationModalProps {
  open: boolean;
  onClose: () => void;
  portfolioId: string;
  existingPlan?: TargetAllocationPlan | null;
}

interface AllocationRowItem {
  id: string;
  categoryKey: string;
  categoryLabel: string;
  targetPercentage: string;
  instrumentId?: string;
}

const DEFAULT_ASSET_CLASSES: { key: string; label: string }[] = [
  { key: 'STOCK', label: 'Stocks & Equities' },
  { key: 'ETF', label: 'Exchange Traded Funds (ETFs)' },
  { key: 'BOND', label: 'Bonds & Fixed Income' },
  { key: 'CASH', label: 'Cash Buffer' },
  { key: 'REIT', label: 'Real Estate (REITs)' },
  { key: 'CRYPTO', label: 'Crypto Assets' },
  { key: 'MUTUAL_FUND', label: 'Mutual Funds' },
  { key: 'OTHER', label: 'Other Alternatives' },
];

export function TargetAllocationModal({
  open,
  onClose,
  portfolioId,
  existingPlan,
}: TargetAllocationModalProps) {
  const { data: instruments = [] } = useInstrumentsList({ enabled: open });
  const saveMutation = useSaveTargetAllocation(portfolioId);
  const deleteMutation = useDeleteTargetAllocation(portfolioId);

  const [name, setName] = useState<string>('Target Allocation Plan');
  const [allocationType, setAllocationType] = useState<AllocationType>('ASSET_CLASS');
  const [driftTolerance, setDriftTolerance] = useState<string>('5.00');
  const [rows, setRows] = useState<AllocationRowItem[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setErrorMessage(null);
      if (existingPlan) {
        setName(existingPlan.name);
        setAllocationType(existingPlan.allocationType);
        setDriftTolerance(existingPlan.driftTolerancePercentage.toString());
        setRows(
          existingPlan.items.map((item, idx) => ({
            id: item.id || `row-${idx}`,
            categoryKey: item.categoryKey,
            categoryLabel: item.categoryLabel,
            targetPercentage: item.targetPercentage.toString(),
            instrumentId: item.instrumentId || undefined,
          }))
        );
      } else {
        setName('Strategic Target Allocation');
        setAllocationType('ASSET_CLASS');
        setDriftTolerance('5.00');
        setRows([
          { id: '1', categoryKey: 'STOCK', categoryLabel: 'Stocks & Equities', targetPercentage: '60.00' },
          { id: '2', categoryKey: 'ETF', categoryLabel: 'Exchange Traded Funds (ETFs)', targetPercentage: '30.00' },
          { id: '3', categoryKey: 'CASH', categoryLabel: 'Cash Buffer', targetPercentage: '10.00' },
        ]);
      }
    }
  }, [open, existingPlan]);

  const totalPercentage = rows.reduce((sum, r) => sum + (parseFloat(r.targetPercentage) || 0), 0);
  const isExact100 = Math.abs(totalPercentage - 100.0) < 0.05;

  const handleAddRow = () => {
    if (allocationType === 'ASSET_CLASS') {
      const unusedClass = DEFAULT_ASSET_CLASSES.find((c) => !rows.some((r) => r.categoryKey === c.key));
      const chosen = unusedClass || DEFAULT_ASSET_CLASSES[0];
      setRows([
        ...rows,
        {
          id: `row-${Date.now()}`,
          categoryKey: chosen.key,
          categoryLabel: chosen.label,
          targetPercentage: '0.00',
        },
      ]);
    } else {
      const unusedInst = instruments.find((i) => !rows.some((r) => r.instrumentId === i.id));
      const inst = unusedInst || instruments[0];
      setRows([
        ...rows,
        {
          id: `row-${Date.now()}`,
          categoryKey: inst ? inst.id : `inst-${Date.now()}`,
          categoryLabel: inst ? (inst.ticker ? `${inst.ticker} - ${inst.name}` : inst.name) : 'Instrument',
          targetPercentage: '0.00',
          instrumentId: inst ? inst.id : undefined,
        },
      ]);
    }
  };

  const handleRemoveRow = (idx: number) => {
    setRows(rows.filter((_, i) => i !== idx));
  };

  const handleRowChange = (idx: number, field: keyof AllocationRowItem, value: string) => {
    const updated = [...rows];
    updated[idx] = { ...updated[idx], [field]: value };

    if (field === 'categoryKey' && allocationType === 'ASSET_CLASS') {
      const found = DEFAULT_ASSET_CLASSES.find((c) => c.key === value);
      if (found) {
        updated[idx].categoryLabel = found.label;
      }
    } else if (field === 'instrumentId' && allocationType === 'INSTRUMENT') {
      const found = instruments.find((i) => i.id === value);
      if (found) {
        updated[idx].categoryKey = found.id;
        updated[idx].categoryLabel = found.ticker ? `${found.ticker} - ${found.name}` : found.name;
        updated[idx].instrumentId = found.id;
      }
    }

    setRows(updated);
  };

  const handleTypeChange = (_: any, newType: AllocationType | null) => {
    if (!newType || newType === allocationType) return;
    setAllocationType(newType);
    if (newType === 'ASSET_CLASS') {
      setRows([
        { id: '1', categoryKey: 'STOCK', categoryLabel: 'Stocks & Equities', targetPercentage: '60.00' },
        { id: '2', categoryKey: 'ETF', categoryLabel: 'Exchange Traded Funds (ETFs)', targetPercentage: '30.00' },
        { id: '3', categoryKey: 'CASH', categoryLabel: 'Cash Buffer', targetPercentage: '10.00' },
      ]);
    } else {
      if (instruments.length > 0) {
        setRows(
          instruments.slice(0, 2).map((inst, idx) => ({
            id: `inst-${idx}`,
            categoryKey: inst.id,
            categoryLabel: inst.ticker ? `${inst.ticker} - ${inst.name}` : inst.name,
            targetPercentage: idx === 0 ? '60.00' : '40.00',
            instrumentId: inst.id,
          }))
        );
      } else {
        setRows([]);
      }
    }
  };

  const handleEvenlyDistribute = () => {
    if (rows.length === 0) return;
    const share = (100 / rows.length).toFixed(2);
    const updated = rows.map((r) => ({ ...r, targetPercentage: share }));
    // Adjust rounding difference on last item
    const currentSum = parseFloat(share) * rows.length;
    const diff = (100 - currentSum).toFixed(2);
    if (Math.abs(parseFloat(diff)) > 0) {
      const last = updated[updated.length - 1];
      updated[updated.length - 1] = {
        ...last,
        targetPercentage: (parseFloat(last.targetPercentage) + parseFloat(diff)).toFixed(2),
      };
    }
    setRows(updated);
  };

  const handleSave = async () => {
    try {
      setErrorMessage(null);
      if (!name.trim()) {
        setErrorMessage('Please provide a name for the target allocation plan.');
        return;
      }
      if (rows.length === 0) {
        setErrorMessage('Please add at least one allocation target.');
        return;
      }
      if (!isExact100) {
        setErrorMessage(`Target percentages must sum to 100.00% (currently ${totalPercentage.toFixed(2)}%).`);
        return;
      }

      await saveMutation.mutateAsync({
        name: name.trim(),
        allocationType,
        driftTolerancePercentage: parseFloat(driftTolerance) || 5.0,
        items: rows.map((r) => ({
          categoryKey: r.categoryKey,
          categoryLabel: r.categoryLabel,
          targetPercentage: parseFloat(r.targetPercentage) || 0,
          instrumentId: r.instrumentId || null,
        })),
      });

      onClose();
    } catch (err: any) {
      setErrorMessage(err?.detail || err?.message || 'Failed to save target allocation plan');
    }
  };

  const handleDelete = async () => {
    try {
      setErrorMessage(null);
      await deleteMutation.mutateAsync();
      onClose();
    } catch (err: any) {
      setErrorMessage(err?.detail || err?.message || 'Failed to delete target allocation plan');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>
        {existingPlan ? 'Edit Target Asset Allocation' : 'Configure Target Asset Allocation'}
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3}>
          {errorMessage && <Alert severity="error">{errorMessage}</Alert>}

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 7 }}>
              <TextField
                label="Plan Name"
                fullWidth
                size="small"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Growth Strategy, 60/40 Core"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 5 }}>
              <TextField
                label="Drift Tolerance (±%)"
                fullWidth
                size="small"
                type="number"
                value={driftTolerance}
                onChange={(e) => setDriftTolerance(e.target.value)}
                helperText="Deviation % threshold before flagging rebalance alert"
              />
            </Grid>
          </Grid>

          <Box>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
              Allocation Level
            </Typography>
            <ToggleButtonGroup
              value={allocationType}
              exclusive
              onChange={handleTypeChange}
              size="small"
              color="primary"
            >
              <ToggleButton value="ASSET_CLASS" sx={{ px: 2, fontWeight: 600 }}>
                By Asset Class
              </ToggleButton>
              <ToggleButton value="INSTRUMENT" sx={{ px: 2, fontWeight: 600 }}>
                By Specific Holding / Instrument
              </ToggleButton>
            </ToggleButtonGroup>
          </Box>

          <Divider />

          {/* Allocation Rows */}
          <Box>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                Target Weights
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  startIcon={<AutoFixHighOutlinedIcon />}
                  onClick={handleEvenlyDistribute}
                  disabled={rows.length === 0}
                >
                  Distribute Evenly
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<AddCircleOutlineOutlinedIcon />}
                  onClick={handleAddRow}
                >
                  Add Target
                </Button>
              </Stack>
            </Stack>

            <Stack spacing={1.5}>
              {rows.map((row, idx) => (
                <Stack
                  key={row.id}
                  direction={{ xs: 'column', sm: 'row' }}
                  spacing={1.5}
                  sx={{ alignItems: { sm: 'center' }, p: 1, border: '1px solid #eee', borderRadius: 1.5 }}
                >
                  {allocationType === 'ASSET_CLASS' ? (
                    <FormControl size="small" sx={{ minWidth: 200, flex: 1 }}>
                      <InputLabel id={`class-select-${idx}`}>Asset Class</InputLabel>
                      <Select
                        labelId={`class-select-${idx}`}
                        label="Asset Class"
                        value={row.categoryKey}
                        onChange={(e) => handleRowChange(idx, 'categoryKey', e.target.value)}
                      >
                        {DEFAULT_ASSET_CLASSES.map((c) => (
                          <MenuItem key={c.key} value={c.key}>
                            {c.label}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  ) : (
                    <FormControl size="small" sx={{ minWidth: 220, flex: 1 }}>
                      <InputLabel id={`inst-select-${idx}`}>Holding / Instrument</InputLabel>
                      <Select
                        labelId={`inst-select-${idx}`}
                        label="Holding / Instrument"
                        value={row.instrumentId || ''}
                        onChange={(e) => handleRowChange(idx, 'instrumentId', e.target.value)}
                      >
                        {instruments.map((inst) => (
                          <MenuItem key={inst.id} value={inst.id}>
                            {inst.ticker ? `${inst.ticker} - ${inst.name}` : inst.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  )}

                  <TextField
                    label="Label"
                    size="small"
                    value={row.categoryLabel}
                    onChange={(e) => handleRowChange(idx, 'categoryLabel', e.target.value)}
                    sx={{ flex: 1 }}
                  />

                  <TextField
                    label="Target %"
                    size="small"
                    type="number"
                    value={row.targetPercentage}
                    onChange={(e) => handleRowChange(idx, 'targetPercentage', e.target.value)}
                    sx={{ width: 120 }}
                    slotProps={{
                      input: {
                        endAdornment: <Typography variant="caption">%</Typography>,
                      },
                    }}
                  />

                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => handleRemoveRow(idx)}
                    disabled={rows.length <= 1}
                  >
                    <DeleteOutlinedIcon fontSize="small" />
                  </IconButton>
                </Stack>
              ))}
            </Stack>

            {/* Total Percentage Bar */}
            <Box
              sx={{
                mt: 2,
                p: 1.5,
                borderRadius: 1.5,
                bgcolor: isExact100 ? 'success.lighter' : 'warning.lighter',
                border: `1px solid ${isExact100 ? '#a5d6a7' : '#ffe082'}`,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                Total Target Allocation:
              </Typography>
              <Typography
                variant="h6"
                sx={{
                  fontWeight: 700,
                  color: isExact100 ? 'success.main' : 'warning.dark',
                }}
              >
                {totalPercentage.toFixed(2)}% {isExact100 ? '✓' : `(Must equal 100.00%)`}
              </Typography>
            </Box>
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2, justifyContent: 'space-between' }}>
        <Box>
          {existingPlan && (
            <Button
              color="error"
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
            >
              Delete Plan
            </Button>
          )}
        </Box>
        <Stack direction="row" spacing={1}>
          <Button onClick={onClose} disabled={saveMutation.isPending}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={!isExact100 || saveMutation.isPending}
          >
            {saveMutation.isPending ? 'Saving...' : 'Save Target Plan'}
          </Button>
        </Stack>
      </DialogActions>
    </Dialog>
  );
}

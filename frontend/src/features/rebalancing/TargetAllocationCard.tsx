import React, { useState } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Stack,
  Box,
  Button,
  Chip,
  Grid,
  Paper,
  LinearProgress,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import DonutLargeOutlinedIcon from '@mui/icons-material/DonutLargeOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined';
import CheckCircleOutlineOutlinedIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import { useTargetAllocation, useRebalancingAnalysis } from './useRebalancing';
import { TargetAllocationModal } from './TargetAllocationModal';

interface TargetAllocationCardProps {
  portfolioId: string;
  currency: string;
}

export function TargetAllocationCard({ portfolioId, currency }: TargetAllocationCardProps) {
  const [modalOpen, setModalOpen] = useState(false);
  const { data: targetPlan, isLoading: isPlanLoading } = useTargetAllocation(portfolioId);
  const { data: rebalanceAnalysis, isLoading: isRebalanceLoading } = useRebalancingAnalysis(portfolioId);

  if (isPlanLoading || isRebalanceLoading) {
    return (
      <Card variant="outlined" sx={{ borderRadius: 2 }}>
        <CardContent>
          <Skeleton variant="text" width="40%" height={32} />
          <Skeleton variant="rectangular" height={100} sx={{ my: 1, borderRadius: 1 }} />
        </CardContent>
      </Card>
    );
  }

  // Not configured state
  if (!targetPlan) {
    return (
      <>
        <Card
          variant="outlined"
          sx={{
            borderRadius: 2,
            borderStyle: 'dashed',
            borderColor: 'divider',
            bgcolor: 'action.hover',
          }}
          data-testid="target-allocation-empty-card"
        >
          <CardContent sx={{ py: 3 }}>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={2}
              sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}
            >
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                <TuneOutlinedIcon color="primary" sx={{ fontSize: 36 }} />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    Target Asset Allocation Strategy
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Set strategic portfolio target weights by asset class or holdings to monitor drift and generate rebalancing trade orders.
                  </Typography>
                </Box>
              </Stack>
              <Button
                variant="contained"
                startIcon={<TuneOutlinedIcon />}
                onClick={() => setModalOpen(true)}
                sx={{ whiteSpace: 'nowrap' }}
              >
                Configure Targets
              </Button>
            </Stack>
          </CardContent>
        </Card>
        {modalOpen && (
          <TargetAllocationModal
            open={modalOpen}
            onClose={() => setModalOpen(false)}
            portfolioId={portfolioId}
            existingPlan={null}
          />
        )}
      </>
    );
  }

  const items = rebalanceAnalysis?.items || [];
  const hasExceeded = rebalanceAnalysis?.hasDriftToleranceExceeded ?? false;

  return (
    <>
      <Card variant="outlined" sx={{ borderRadius: 2 }} data-testid="target-allocation-card">
        <CardContent>
          <Stack spacing={2.5}>
            {/* Header */}
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}
            >
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                <DonutLargeOutlinedIcon color="primary" sx={{ fontSize: 26 }} />
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1.15rem' }}>
                    {targetPlan.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Target Allocation Strategy ({targetPlan.allocationType === 'ASSET_CLASS' ? 'Asset Class' : 'Specific Holdings'})
                  </Typography>
                </Box>
              </Stack>

              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Chip
                  icon={hasExceeded ? <WarningAmberOutlinedIcon /> : <CheckCircleOutlineOutlinedIcon />}
                  label={hasExceeded ? 'Rebalance Drift Detected' : 'In Balance'}
                  color={hasExceeded ? 'warning' : 'success'}
                  variant="outlined"
                  size="small"
                  sx={{ fontWeight: 600 }}
                />
                <Chip
                  label={`Tolerance: ±${targetPlan.driftTolerancePercentage}%`}
                  size="small"
                  variant="outlined"
                />
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<EditOutlinedIcon />}
                  onClick={() => setModalOpen(true)}
                >
                  Edit Targets
                </Button>
              </Stack>
            </Stack>

            {/* Target vs Current Comparison Table */}
            <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
              <Table size="small" aria-label="target allocation comparison table">
                <TableHead sx={{ bgcolor: 'action.hover' }}>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>Category / Asset</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>Current Value ({currency})</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>Current Weight</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>Target Weight</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>Drift %</TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600 }}>Drift Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {items.map((item) => {
                    const isOverweight = item.driftStatus === 'OVERWEIGHT';
                    const isUnderweight = item.driftStatus === 'UNDERWEIGHT';
                    const driftLabel = `${item.driftPercentage > 0 ? '+' : ''}${Number(item.driftPercentage).toFixed(2)}%`;

                    return (
                      <TableRow key={item.categoryKey} hover>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {item.categoryLabel}
                          </Typography>
                          {item.instrumentTicker && (
                            <Typography variant="caption" color="text.secondary">
                              {item.instrumentTicker}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="right" sx={{ fontFamily: 'monospace' }}>
                          {Number(item.currentMarketValue).toLocaleString(undefined, {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2,
                          })}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>
                          {Number(item.currentWeightPercentage).toFixed(2)}%
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600, color: 'primary.main' }}>
                          {Number(item.targetWeightPercentage).toFixed(2)}%
                        </TableCell>
                        <TableCell
                          align="right"
                          sx={{
                            fontWeight: 700,
                            fontFamily: 'monospace',
                            color: isOverweight ? 'warning.main' : isUnderweight ? 'info.main' : 'success.main',
                          }}
                        >
                          {driftLabel}
                        </TableCell>
                        <TableCell align="center">
                          <Chip
                            label={
                              item.driftStatus === 'IN_TOLERANCE'
                                ? 'Balanced'
                                : item.driftStatus === 'OVERWEIGHT'
                                ? 'Overweight'
                                : 'Underweight'
                            }
                            color={
                              item.driftStatus === 'IN_TOLERANCE'
                                ? 'success'
                                : item.isDriftExceeded
                                ? (isOverweight ? 'warning' : 'info')
                                : 'default'
                            }
                            size="small"
                            variant={item.isDriftExceeded ? 'filled' : 'outlined'}
                            sx={{ fontWeight: 600, fontSize: '0.75rem' }}
                          />
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </Stack>
        </CardContent>
      </Card>

      {modalOpen && (
        <TargetAllocationModal
          open={modalOpen}
          onClose={() => setModalOpen(false)}
          portfolioId={portfolioId}
          existingPlan={targetPlan}
        />
      )}
    </>
  );
}

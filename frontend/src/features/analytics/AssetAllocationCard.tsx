import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  CardContent,
  Typography,
  Stack,
  Tabs,
  Tab,
  Box,
  Button,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableRow,
  Chip,
  Paper,
  Skeleton,
  Grid,
} from '@mui/material';
import PieChartOutlinedIcon from '@mui/icons-material/PieChartOutlined';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { usePortfolioAnalytics } from './useAnalytics';
import { DonutChart, DONUT_PALETTE, DonutSliceItem } from './DonutChart';
import { AllocationItem, HoldingExposure } from '../../types';
import { SortableTableHead } from '../../components/SortableTableHead';
import { Order, sortRows } from '../../utils/sorting';

interface AssetAllocationCardProps {
  portfolioId: string;
  currency: string;
}

export function AssetAllocationCard({ portfolioId, currency }: AssetAllocationCardProps) {
  const navigate = useNavigate();
  const { data: analytics, isLoading } = usePortfolioAnalytics(portfolioId);
  const [activeTab, setActiveTab] = useState<number>(0);
  const [order, setOrder] = useState<Order>('desc');
  const [orderBy, setOrderBy] = useState<string>('marketValue');
  const [hoveredSliceId, setHoveredSliceId] = useState<string | null>(null);

  const handleRequestSort = (property: string) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

  const top3Concentration = useMemo(() => {
    if (!analytics || !analytics.topHoldings || analytics.topHoldings.length === 0) return null;
    const top3 = analytics.topHoldings.slice(0, 3);
    const sum = top3.reduce((acc, h) => acc + (Number(h.weightPercentage) || 0), 0);
    return (sum * 100).toFixed(1);
  }, [analytics]);

  if (isLoading) {
    return (
      <Card variant="outlined">
        <CardContent>
          <Skeleton variant="text" width="30%" height={32} />
          <Skeleton variant="rectangular" height={150} sx={{ my: 1, borderRadius: 1 }} />
        </CardContent>
      </Card>
    );
  }

  if (!analytics || analytics.byAssetClass.length === 0) {
    return null;
  }

  const activeItems: AllocationItem[] =
    activeTab === 0
      ? analytics.byAssetClass
      : activeTab === 1
      ? analytics.byCurrency
      : activeTab === 2
      ? analytics.byAccount
      : [];

  const donutItems: DonutSliceItem[] = activeItems.map((item, idx) => ({
    id: item.category,
    label: item.category,
    value: Number(item.marketValue),
    percentage: Number(item.percentage),
    color: DONUT_PALETTE[idx % DONUT_PALETTE.length],
  }));

  const totalCurrentValueFormatted = Number(analytics.totalCurrentValue).toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  });

  const renderAllocationRows = (items: AllocationItem[]) => (
    <Grid container spacing={3} sx={{ mt: 1, alignItems: 'center' }}>
      {/* SVG Donut Chart Column */}
      <Grid size={{ xs: 12, md: 4 }} sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', py: 1 }}>
        <DonutChart
          items={donutItems}
          size={180}
          centerSubtitle="Total Value"
          centerTitle={`${totalCurrentValueFormatted} ${currency}`}
          currency={currency}
          hoveredId={hoveredSliceId}
          onHover={(slice) => setHoveredSliceId(slice ? slice.id : null)}
        />
      </Grid>

      {/* Progress Bars Column */}
      <Grid size={{ xs: 12, md: 8 }}>
        <Stack spacing={2}>
          {items.map((item, idx) => {
            const pct = (Number(item.percentage) * 100).toFixed(1);
            const mv = Number(item.marketValue).toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            });
            const sliceColor = DONUT_PALETTE[idx % DONUT_PALETTE.length];
            const isHovered = hoveredSliceId === item.category;

            return (
              <Box
                key={item.category}
                onMouseEnter={() => setHoveredSliceId(item.category)}
                onMouseLeave={() => setHoveredSliceId(null)}
                sx={{
                  p: 0.75,
                  borderRadius: 1,
                  bgcolor: isHovered ? 'action.hover' : 'transparent',
                  transition: 'background-color 0.15s ease',
                  cursor: 'pointer',
                }}
              >
                <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Box
                      sx={{
                        width: 10,
                        height: 10,
                        borderRadius: '50%',
                        bgcolor: sliceColor,
                        flexShrink: 0,
                      }}
                    />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {item.category}
                    </Typography>
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    <strong>{mv} {currency}</strong> ({pct}%)
                  </Typography>
                </Stack>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(100, Number(item.percentage) * 100)}
                  sx={{
                    height: 7,
                    borderRadius: 4,
                    bgcolor: 'action.selected',
                    '& .MuiLinearProgress-bar': {
                      bgcolor: sliceColor,
                      borderRadius: 4,
                    },
                  }}
                />
              </Box>
            );
          })}
        </Stack>
      </Grid>
    </Grid>
  );

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }} data-testid="asset-allocation-card">
      <CardContent>
        {/* Header with Title, Concentration Badge, and Detail CTA */}
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1.5, mb: 2 }}
        >
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <PieChartOutlinedIcon color="primary" />
            <Typography variant="h6" sx={{ fontWeight: 600, fontSize: '1.1rem' }}>
              Asset Allocation & Exposure Breakdown
            </Typography>
          </Stack>

          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            {top3Concentration && (
              <Chip
                size="small"
                label={`Top 3: ${Math.round(Number(top3Concentration))}%`}
                variant="outlined"
                color={Number(top3Concentration) > 60 ? 'warning' : 'default'}
                sx={{ fontWeight: 600, fontSize: '0.75rem' }}
              />
            )}

            <Button
              variant="outlined"
              size="small"
              endIcon={<ArrowForwardIcon fontSize="small" />}
              onClick={() => navigate(`/portfolios/${portfolioId}/allocation`)}
            >
              View Allocation Detail
            </Button>
          </Stack>
        </Stack>

        <Tabs
          value={activeTab}
          onChange={(_, val) => setActiveTab(val)}
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          <Tab label="Asset Class" />
          <Tab label="Currency Exposure" />
          <Tab label="Account Distribution" />
          <Tab label="Top Holdings" />
        </Tabs>

        {activeTab === 0 && renderAllocationRows(analytics.byAssetClass)}
        {activeTab === 1 && renderAllocationRows(analytics.byCurrency)}
        {activeTab === 2 && renderAllocationRows(analytics.byAccount)}

        {activeTab === 3 && (() => {
          const sortedHoldings = sortRows(analytics.topHoldings, order, orderBy);
          return (
            <TableContainer component={Paper} variant="outlined" sx={{ mt: 2, borderRadius: 1 }}>
              <Table size="small" aria-label="top holdings table">
                <SortableTableHead<HoldingExposure>
                  headCells={[
                    { id: 'ticker', label: 'Holding', sortable: true },
                    { id: 'assetClass', label: 'Asset Class', sortable: true },
                    { id: 'quantity', label: 'Qty', align: 'right', sortable: true },
                    { id: 'currentPrice', label: 'Price', align: 'right', sortable: true },
                    { id: 'marketValue', label: `Market Value (${currency})`, align: 'right', sortable: true },
                    { id: 'weightPercentage', label: 'Weight', align: 'right', sortable: true },
                    { id: 'unrealizedGainLoss', label: 'Unrealized P&L', align: 'right', sortable: true },
                  ]}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                />
                <TableBody>
                  {sortedHoldings.map((h) => {
                    const unPositive = Number(h.unrealizedGainLoss) >= 0;
                    const weightPct = (Number(h.weightPercentage) * 100).toFixed(1);
                    return (
                      <TableRow key={h.instrumentId} hover>
                        <TableCell sx={{ fontWeight: 600 }}>
                          {h.ticker} <Typography variant="caption" color="text.secondary">({h.instrumentName})</Typography>
                        </TableCell>
                        <TableCell>
                          <Chip label={h.assetClass} size="small" variant="outlined" sx={{ height: 20, fontSize: '0.7rem' }} />
                        </TableCell>
                        <TableCell align="right">{Number(h.quantity).toLocaleString()}</TableCell>
                        <TableCell align="right">
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {Number(h.currentPrice).toFixed(2)} {currency}
                          </Typography>
                          {h.nativeCurrency && h.nativeCurrency !== currency && h.nativePrice != null && (
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                              {Number(h.nativePrice).toFixed(2)} {h.nativeCurrency}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600 }}>
                          {Number(h.marketValue).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </TableCell>
                        <TableCell align="right">{weightPct}%</TableCell>
                        <TableCell align="right">
                          <Typography variant="body2" sx={{ color: unPositive ? 'success.main' : 'error.main', fontWeight: 600 }}>
                            {unPositive ? '+' : ''}{Number(h.unrealizedGainLoss).toFixed(2)} {currency}
                          </Typography>
                          {h.nativeGainLossPercentage != null && (
                            <Typography variant="caption" sx={{ color: Number(h.nativeGainLossPercentage) >= 0 ? 'success.main' : 'error.main', display: 'block' }}>
                              {Number(h.nativeGainLossPercentage) >= 0 ? '+' : ''}{Number(h.nativeGainLossPercentage).toFixed(2)}%
                              {h.nativeCurrency && h.nativeCurrency !== currency && h.nativeUnrealizedGainLoss != null && (
                                ` (${Number(h.nativeUnrealizedGainLoss) >= 0 ? '+' : ''}${Number(h.nativeUnrealizedGainLoss).toFixed(2)} ${h.nativeCurrency})`
                              )}
                            </Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          );
        })()}
      </CardContent>
    </Card>
  );
}

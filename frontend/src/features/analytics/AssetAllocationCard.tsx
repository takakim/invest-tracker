import React, { useState } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Stack,
  Tabs,
  Tab,
  Box,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Paper,
  Skeleton,
} from '@mui/material';
import PieChartOutlinedIcon from '@mui/icons-material/PieChartOutlined';
import { usePortfolioAnalytics } from './useAnalytics';
import { AllocationItem, HoldingExposure } from '../../types';
import { SortableTableHead } from '../../components/SortableTableHead';
import { Order, sortRows } from '../../utils/sorting';

interface AssetAllocationCardProps {
  portfolioId: string;
  currency: string;
}

export function AssetAllocationCard({ portfolioId, currency }: AssetAllocationCardProps) {
  const { data: analytics, isLoading } = usePortfolioAnalytics(portfolioId);
  const [activeTab, setActiveTab] = useState<number>(0);
  const [order, setOrder] = useState<Order>('desc');
  const [orderBy, setOrderBy] = useState<string>('marketValue');

  const handleRequestSort = (property: string) => {
    const isAsc = orderBy === property && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(property);
  };

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

  const renderAllocationRows = (items: AllocationItem[]) => (
    <Stack spacing={2} sx={{ mt: 2 }}>
      {items.map((item) => {
        const pct = (Number(item.percentage) * 100).toFixed(1);
        const mv = Number(item.marketValue).toLocaleString(undefined, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        });

        return (
          <Box key={item.category}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {item.category}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                <strong>{mv} {currency}</strong> ({pct}%)
              </Typography>
            </Stack>
            <LinearProgress
              variant="determinate"
              value={Math.min(100, Number(item.percentage) * 100)}
              sx={{ height: 8, borderRadius: 4 }}
            />
          </Box>
        );
      })}
    </Stack>
  );

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }}>
      <CardContent>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 2 }}>
          <PieChartOutlinedIcon color="primary" />
          <Typography variant="h6" sx={{ fontWeight: 600, fontSize: '1.1rem' }}>
            Asset Allocation & Exposure Breakdown
          </Typography>
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

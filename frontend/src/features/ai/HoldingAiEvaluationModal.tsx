import React, { useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Chip,
  Grid,
  Paper,
  Stack,
  LinearProgress,
  Divider,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Alert,
} from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import RefreshIcon from '@mui/icons-material/Refresh';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import HorizontalRuleOutlinedIcon from '@mui/icons-material/HorizontalRuleOutlined';
import StarIcon from '@mui/icons-material/Star';
import { useEvaluateHolding } from './useAi';
import type { HoldingAiEvaluation, AiStance, AiRiskLevel } from '../../types';

interface HoldingAiEvaluationModalProps {
  open: boolean;
  onClose: () => void;
  portfolioId: string;
  instrumentId: string;
  symbol: string;
  instrumentName: string;
  initialData?: HoldingAiEvaluation | null;
}

export const getStanceChipProps = (stance: AiStance) => {
  switch (stance) {
    case 'STRONG_BUY':
      return { color: 'success' as const, label: 'STRONG BUY', icon: <StarIcon /> };
    case 'ACCUMULATE':
      return { color: 'success' as const, label: 'ACCUMULATE', icon: <TrendingUpIcon /> };
    case 'HOLD':
      return { color: 'info' as const, label: 'HOLD', icon: <HorizontalRuleOutlinedIcon /> };
    case 'TRIM':
      return { color: 'warning' as const, label: 'TRIM', icon: <TrendingDownIcon /> };
    case 'SELL':
      return { color: 'error' as const, label: 'SELL', icon: <TrendingDownIcon /> };
    default:
      return { color: 'default' as const, label: stance, icon: undefined };
  }
};

export const getRiskLevelColor = (riskLevel: AiRiskLevel | string, score: number) => {
  if (score <= 3 || riskLevel === 'LOW') return '#16a34a'; // Green
  if (score <= 6 || riskLevel === 'MODERATE') return '#d97706'; // Amber
  if (score <= 8 || riskLevel === 'HIGH') return '#ea580c'; // Orange
  return '#dc2626'; // Red
};

export const HoldingAiEvaluationModal: React.FC<HoldingAiEvaluationModalProps> = ({
  open,
  onClose,
  portfolioId,
  instrumentId,
  symbol,
  instrumentName,
  initialData,
}) => {
  const [evaluation, setEvaluation] = useState<HoldingAiEvaluation | null>(initialData || null);
  const evaluateMutation = useEvaluateHolding(portfolioId);

  useEffect(() => {
    if (open) {
      if (initialData) {
        setEvaluation(initialData);
      } else {
        evaluateMutation.mutate(instrumentId, {
          onSuccess: (data) => setEvaluation(data),
        });
      }
    }
  }, [open, instrumentId, initialData]);

  const handleRefresh = () => {
    evaluateMutation.mutate(instrumentId, {
      onSuccess: (data) => setEvaluation(data),
    });
  };

  const isLoading = evaluateMutation.isPending;
  const isError = evaluateMutation.isError;
  const error = evaluateMutation.error;

  const stanceProps = evaluation ? getStanceChipProps(evaluation.stance) : null;
  const riskColor = evaluation
    ? getRiskLevelColor(evaluation.riskLevel, evaluation.riskScore)
    : '#16a34a';

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>
        <Stack
          direction="row"
          spacing={2}
          sx={{ alignItems: 'center', justifyContent: 'space-between' }}
        >
          <Box>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <AutoAwesomeIcon sx={{ color: 'primary.main', fontSize: 28 }} />
              <Typography variant="h5" component="span" sx={{ fontWeight: 700 }}>
                {symbol} — {instrumentName}
              </Typography>
            </Stack>
            <Typography variant="caption" color="text.secondary">
              AI Fundamental & Risk Evaluation powered by Gemma 4 12B
            </Typography>
          </Box>
          {stanceProps && (
            <Chip
              icon={stanceProps.icon}
              label={stanceProps.label}
              color={stanceProps.color}
              sx={{ fontWeight: 700, fontSize: '0.85rem', px: 1, py: 2 }}
            />
          )}
        </Stack>
      </DialogTitle>

      <DialogContent dividers sx={{ p: 3 }}>
        {isLoading && (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <LinearProgress sx={{ mb: 3, borderRadius: 2, height: 6 }} />
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>
              Synthesizing Fundamental & Risk Analysis...
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Querying Gemma 4 12B on LM Studio with financial valuation multiples, portfolio weighting, and 52-week quote ranges.
            </Typography>
          </Box>
        )}

        {isError && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={handleRefresh}>
                Retry
              </Button>
            }
            sx={{ mb: 2 }}
          >
            Failed to evaluate holding: {error?.message || 'Connection to LM Studio failed'}.
            Make sure LM Studio is running locally with Gemma 4 12B loaded.
          </Alert>
        )}

        {!isLoading && evaluation && (
          <Stack spacing={3}>
            {/* Risk & Allocation Bar */}
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
              <Grid container spacing={2} sx={{ alignItems: 'center' }}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Typography variant="caption" color="text.secondary">
                    Risk Assessment: <strong>{evaluation.riskScore} / 10</strong> ({evaluation.riskLevel})
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={evaluation.riskScore * 10}
                    sx={{
                      mt: 1,
                      height: 8,
                      borderRadius: 4,
                      bgcolor: 'action.hover',
                      '& .MuiLinearProgress-bar': {
                        bgcolor: riskColor,
                      },
                    }}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <Stack
                    direction="row"
                    spacing={3}
                    sx={{ justifyContent: { xs: 'flex-start', sm: 'flex-end' } }}
                  >
                    <Box sx={{ textAlign: 'right' }}>
                      <Typography variant="caption" color="text.secondary">
                        Portfolio Weight
                      </Typography>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                        {evaluation.portfolioWeightPercentage.toFixed(2)}%
                      </Typography>
                    </Box>
                    <Box sx={{ textAlign: 'right' }}>
                      <Typography variant="caption" color="text.secondary">
                        Unrealized Return
                      </Typography>
                      <Typography
                        variant="subtitle2"
                        sx={{
                          fontWeight: 700,
                          color: evaluation.unrealizedGainLossPercentage >= 0 ? 'success.main' : 'error.main',
                        }}
                      >
                        {evaluation.unrealizedGainLossPercentage >= 0 ? '+' : ''}
                        {evaluation.unrealizedGainLossPercentage.toFixed(2)}%
                      </Typography>
                    </Box>
                  </Stack>
                </Grid>
              </Grid>
            </Paper>

            {/* Executive Summary */}
            <Paper
              sx={{
                p: 2.5,
                bgcolor: 'primary.50',
                borderLeft: 4,
                borderColor: 'primary.main',
                borderRadius: 2,
              }}
            >
              <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.dark', mb: 0.5 }}>
                Executive Thesis
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.primary', lineHeight: 1.6 }}>
                {evaluation.executiveSummary}
              </Typography>
            </Paper>

            {/* Strengths & Risks */}
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper variant="outlined" sx={{ p: 2, height: '100%', borderColor: 'success.light' }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'success.dark', mb: 1 }}>
                    Key Strengths & Catalysts
                  </Typography>
                  <List dense disablePadding>
                    {evaluation.strengths.map((str, idx) => (
                      <ListItem key={idx} disableGutters sx={{ alignItems: 'flex-start', py: 0.5 }}>
                        <ListItemIcon sx={{ minWidth: 28, mt: 0.5 }}>
                          <CheckCircleOutlinedIcon color="success" fontSize="small" />
                        </ListItemIcon>
                        <ListItemText
                          primary={<Typography variant="body2">{str}</Typography>}
                        />
                      </ListItem>
                    ))}
                  </List>
                </Paper>
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <Paper variant="outlined" sx={{ p: 2, height: '100%', borderColor: 'warning.light' }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'warning.dark', mb: 1 }}>
                    Risks & Headwinds
                  </Typography>
                  <List dense disablePadding>
                    {evaluation.risks.map((risk, idx) => (
                      <ListItem key={idx} disableGutters sx={{ alignItems: 'flex-start', py: 0.5 }}>
                        <ListItemIcon sx={{ minWidth: 28, mt: 0.5 }}>
                          <WarningAmberIcon color="warning" fontSize="small" />
                        </ListItemIcon>
                        <ListItemText
                          primary={<Typography variant="body2">{risk}</Typography>}
                        />
                      </ListItem>
                    ))}
                  </List>
                </Paper>
              </Grid>
            </Grid>

            {/* Holding vs Selling Tradeoff */}
            <Paper variant="outlined" sx={{ p: 2.5, bgcolor: 'background.default' }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                Hold vs. Sell Trade-off Analysis
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
                {evaluation.holdingVsSellingTradeoff}
              </Typography>
            </Paper>

            {/* Financial Multiples & Valuation */}
            {evaluation.fundamentalMetrics && (
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1.5 }}>
                  Financial Valuation Multiples & Reference Metrics
                </Typography>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">
                      P/E (TTM)
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {evaluation.fundamentalMetrics.peRatio != null
                        ? evaluation.fundamentalMetrics.peRatio.toFixed(1)
                        : 'N/A'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">
                      Forward P/E
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {evaluation.fundamentalMetrics.forwardPe != null
                        ? evaluation.fundamentalMetrics.forwardPe.toFixed(1)
                        : 'N/A'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">
                      PEG Ratio
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {evaluation.fundamentalMetrics.pegRatio != null
                        ? evaluation.fundamentalMetrics.pegRatio.toFixed(2)
                        : 'N/A'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">
                      Dividend Yield
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {evaluation.fundamentalMetrics.dividendYield != null
                        ? `${(evaluation.fundamentalMetrics.dividendYield * 100).toFixed(2)}%`
                        : '0.00%'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">
                      Debt / Equity
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {evaluation.fundamentalMetrics.debtToEquity != null
                        ? evaluation.fundamentalMetrics.debtToEquity.toFixed(2)
                        : 'N/A'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">
                      52-Week Range
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {evaluation.fundamentalMetrics.fiftyTwoWeekLow != null &&
                      evaluation.fundamentalMetrics.fiftyTwoWeekHigh != null
                        ? `${evaluation.fundamentalMetrics.fiftyTwoWeekLow.toFixed(2)} - ${evaluation.fundamentalMetrics.fiftyTwoWeekHigh.toFixed(2)}`
                        : 'N/A'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">
                      ROE
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {evaluation.fundamentalMetrics.returnOnEquity != null
                        ? `${(evaluation.fundamentalMetrics.returnOnEquity * 100).toFixed(1)}%`
                        : 'N/A'}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                    <Typography variant="caption" color="text.secondary">
                      Price / Book
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {evaluation.fundamentalMetrics.priceToBook != null
                        ? evaluation.fundamentalMetrics.priceToBook.toFixed(2)
                        : 'N/A'}
                    </Typography>
                  </Grid>
                  {evaluation.fundamentalMetrics.expenseRatio != null && (
                    <Grid size={{ xs: 6, sm: 4, md: 2.4 }}>
                      <Typography variant="caption" color="text.secondary">
                        Expense Ratio
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {(evaluation.fundamentalMetrics.expenseRatio * 100).toFixed(2)}%
                      </Typography>
                    </Grid>
                  )}
                </Grid>
              </Paper>
            )}

            <Divider />

            {/* Footer Metadata */}
            <Stack
              direction="row"
              sx={{
                justifyContent: 'space-between',
                alignItems: 'center',
                color: 'text.secondary',
                fontSize: '0.75rem',
              }}
            >
              <Typography variant="caption">
                Model: <strong>{evaluation.modelUsed}</strong> • Evaluated:{' '}
                {new Date(evaluation.evaluatedAt).toLocaleString()}
              </Typography>
              <Button
                size="small"
                startIcon={<RefreshIcon />}
                onClick={handleRefresh}
                disabled={isLoading}
              >
                Re-evaluate
              </Button>
            </Stack>
          </Stack>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} variant="contained">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
};

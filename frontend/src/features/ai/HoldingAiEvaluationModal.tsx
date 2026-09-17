import React, { useState } from 'react';
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
  CircularProgress,
  Divider,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Alert,
  Tooltip,
} from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import RefreshIcon from '@mui/icons-material/Refresh';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import HorizontalRuleOutlinedIcon from '@mui/icons-material/HorizontalRuleOutlined';
import StarIcon from '@mui/icons-material/Star';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import SettingsIcon from '@mui/icons-material/Settings';
import { useEvaluateHolding, useLatestHoldingEvaluation } from './useAi';
import { isEvaluationStale } from './aiStaleness';
import { AiSettingsModal } from './AiSettingsModal';
import type { HoldingAiEvaluation, AiStance, AiRiskLevel } from '../../types';


interface HoldingAiEvaluationModalProps {
  open: boolean;
  onClose: () => void;
  portfolioId: string;
  instrumentId: string;
  symbol: string;
  instrumentName: string;
  initialData?: HoldingAiEvaluation;
}

export const getStanceChipProps = (stance: AiStance | string) => {
  switch (stance) {
    case 'STRONG_BUY':
      return { label: 'STRONG BUY', color: 'success' as const, icon: <StarIcon fontSize="small" /> };
    case 'ACCUMULATE':
      return { label: 'ACCUMULATE', color: 'success' as const, icon: <TrendingUpIcon fontSize="small" /> };
    case 'HOLD':
      return { label: 'HOLD', color: 'info' as const, icon: <HorizontalRuleOutlinedIcon fontSize="small" /> };
    case 'TRIM':
      return { label: 'TRIM', color: 'warning' as const, icon: <TrendingDownIcon fontSize="small" /> };
    case 'SELL':
      return { label: 'SELL', color: 'error' as const, icon: <TrendingDownIcon fontSize="small" /> };
    default:
      return { label: stance, color: 'default' as const, icon: undefined };
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
  const [settingsOpen, setSettingsOpen] = useState(false);

  // Load cached evaluation from DB on open
  const cachedQuery = useLatestHoldingEvaluation(portfolioId, instrumentId);
  const evaluateMutation = useEvaluateHolding(portfolioId);

  // Prefer fresh mutation result, then compare timestamps between DB cache and initial prop
  const evaluation: HoldingAiEvaluation | null = (() => {
    if (evaluateMutation.data) return evaluateMutation.data;
    if (cachedQuery.data && initialData) {
      const cachedTime = new Date(cachedQuery.data.evaluatedAt).getTime();
      const initialTime = new Date(initialData.evaluatedAt).getTime();
      return cachedTime >= initialTime ? cachedQuery.data : initialData;
    }
    return cachedQuery.data ?? initialData ?? null;
  })();

  const handleRefresh = () => {
    evaluateMutation.mutate(instrumentId);
  };

  const isEvaluating = evaluateMutation.isPending;
  const isInitialLoading = cachedQuery.isLoading && !evaluation;
  const isError = evaluateMutation.isError;
  const error = evaluateMutation.error;


  const isStale = isEvaluationStale(evaluation?.evaluatedAt, evaluation?.isStale);

  const stanceProps = evaluation ? getStanceChipProps(evaluation.stance) : null;
  const riskColor = evaluation
    ? getRiskLevelColor(evaluation.riskLevel, evaluation.riskScore)
    : '#16a34a';

  const providerLabel = evaluation?.modelUsed
    ? evaluation.modelUsed.toUpperCase().replace('_', ' ')
    : null;

  const lastEvaluatedLabel = evaluation?.evaluatedAt
    ? (() => {
        const diff = Date.now() - new Date(evaluation.evaluatedAt).getTime();
        const mins = Math.floor(diff / 60000);
        const hrs = Math.floor(mins / 60);
        const days = Math.floor(hrs / 24);
        if (days > 0) return `${days}d ago`;
        if (hrs > 0) return `${hrs}h ago`;
        if (mins > 0) return `${mins}m ago`;
        return 'just now';
      })()
    : null;

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
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              {providerLabel && (
                <Chip
                  label={providerLabel}
                  size="small"
                  variant="outlined"
                  sx={{ fontWeight: 600, fontSize: '0.68rem', letterSpacing: 0.4 }}
                />
              )}
              {isStale && (
                <Chip
                  label="Stale (>7d)"
                  size="small"
                  color="warning"
                  sx={{ fontWeight: 700, fontSize: '0.68rem', letterSpacing: 0.3 }}
                />
              )}
              {lastEvaluatedLabel && (
                <Tooltip title={`Last evaluated: ${new Date(evaluation!.evaluatedAt).toLocaleString()}`}>
                  <Stack direction="row" spacing={0.4} sx={{ alignItems: 'center' }}>
                    <AccessTimeIcon sx={{ fontSize: 12, color: 'text.secondary' }} />
                    <Typography variant="caption" color="text.secondary">
                      {lastEvaluatedLabel}
                    </Typography>
                  </Stack>
                </Tooltip>
              )}
              {!providerLabel && (
                <Typography variant="caption" color="text.secondary">
                  AI Fundamental &amp; Risk Evaluation
                </Typography>
              )}
            </Stack>
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
        {isInitialLoading && (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <CircularProgress size={36} sx={{ mb: 2 }} />
            <Typography variant="body1" color="text.secondary">
              Loading previous evaluation...
            </Typography>
          </Box>
        )}

        {isEvaluating && (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <LinearProgress sx={{ mb: 3, borderRadius: 2, height: 6 }} />
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>
              Synthesizing Fundamental &amp; Risk Analysis...
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Querying your AI provider with financial valuation multiples, portfolio weighting, and 52-week quote ranges.
            </Typography>
          </Box>
        )}

        {!evaluation && !isEvaluating && !isError && !isInitialLoading && (
          <Paper
            variant="outlined"
            sx={{
              p: 4,
              textAlign: 'center',
              bgcolor: 'background.default',
              borderStyle: 'dashed',
              borderRadius: 2,
            }}
          >
            <AutoAwesomeIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1.5 }} />
            <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
              Fundamental &amp; Risk Evaluation
            </Typography>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ maxWidth: 550, mx: 'auto', mb: 3, lineHeight: 1.6 }}
            >
              Generate an AI assessment of valuation multiples, balance sheet solvency, financial health metrics, and holding vs selling tradeoffs for {symbol} ({instrumentName}).
            </Typography>
            <Button
              variant="contained"
              size="large"
              startIcon={<AutoAwesomeIcon />}
              onClick={handleRefresh}
              sx={{ px: 4, py: 1.2, fontWeight: 700 }}
              data-testid="evaluate-holding-btn"
            >
              Evaluate {symbol}
            </Button>
          </Paper>
        )}

        {isError && (
          <Alert
            severity="error"
            action={
              <Stack direction="row" spacing={1}>
                <Button color="inherit" size="small" onClick={() => setSettingsOpen(true)}>
                  Adjust Timeout
                </Button>
                <Button color="inherit" size="small" onClick={handleRefresh}>
                  Retry
                </Button>
              </Stack>
            }
            sx={{ mb: 2 }}
          >
            Failed to evaluate holding: {error?.message || 'Connection to AI provider failed'}.
            Check that your AI provider is reachable or increase the inference timeout.
          </Alert>
        )}

        {!isEvaluating && evaluation && (
          <Stack spacing={3}>

            {isStale && (
              <Alert
                severity="warning"
                action={
                  <Button
                    color="inherit"
                    size="small"
                    onClick={handleRefresh}
                    disabled={isEvaluating}
                    startIcon={<RefreshIcon />}
                  >
                    Re-evaluate
                  </Button>

                }
              >
                <strong>Outdated Evaluation (&gt;7 days):</strong> This holding was evaluated on{' '}
                {new Date(evaluation.evaluatedAt).toLocaleDateString()} ({lastEvaluatedLabel}).
                Market valuation multiples, prices, and balance sheet conditions may have changed.
              </Alert>
            )}

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
                disabled={isEvaluating}
              >
                Re-evaluate
              </Button>
            </Stack>
          </Stack>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2, justifyContent: 'space-between' }}>
        <Button
          size="small"
          color="inherit"
          startIcon={<SettingsIcon />}
          onClick={() => setSettingsOpen(true)}
          data-testid="holding-ai-settings-btn"
        >
          AI Settings
        </Button>
        <Stack direction="row" spacing={1}>
          {evaluation && !isEvaluating && (
            <Button
              variant="outlined"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={handleRefresh}
              disabled={isEvaluating}
            >
              Re-evaluate
            </Button>
          )}
          <Button onClick={onClose} variant="contained">
            Close
          </Button>
        </Stack>
      </DialogActions>
      <AiSettingsModal open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </Dialog>
  );
};


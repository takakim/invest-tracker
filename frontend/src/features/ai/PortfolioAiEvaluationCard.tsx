import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  CardHeader,
  Typography,
  Button,
  Chip,
  Grid,
  Stack,
  LinearProgress,
  Paper,
  Divider,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  IconButton,
} from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import RefreshIcon from '@mui/icons-material/Refresh';
import PsychologyIcon from '@mui/icons-material/Psychology';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import LaunchIcon from '@mui/icons-material/Launch';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import SettingsIcon from '@mui/icons-material/Settings';

import { useEvaluatePortfolio, useLatestPortfolioEvaluation } from './useAi';
import { HoldingAiEvaluationModal, getStanceChipProps, getRiskLevelColor } from './HoldingAiEvaluationModal';
import { AiStatusIndicator } from './AiStatusIndicator';
import { AiSettingsModal } from './AiSettingsModal';
import { isEvaluationStale } from './aiStaleness';
import type { PortfolioAiEvaluation, HoldingAiEvaluation } from '../../types';


interface PortfolioAiEvaluationCardProps {
  portfolioId: string;
  portfolioName?: string;
  baseCurrency?: string;
}

export const PortfolioAiEvaluationCard: React.FC<PortfolioAiEvaluationCardProps> = ({
  portfolioId,
  portfolioName = 'Portfolio',
}) => {
  const [selectedHolding, setSelectedHolding] = useState<HoldingAiEvaluation | null>(null);
  const [holdingModalOpen, setHoldingModalOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  // Load cached evaluation from DB on mount
  const cachedQuery = useLatestPortfolioEvaluation(portfolioId);
  const evaluateMutation = useEvaluatePortfolio(portfolioId);

  // Prefer fresh mutation result, fall back to DB cache
  const evaluation: PortfolioAiEvaluation | null =
    evaluateMutation.data ?? cachedQuery.data ?? null;

  const handleRunAnalysis = () => {
    evaluateMutation.mutate(undefined);
  };

  const handleOpenHoldingModal = (holding: HoldingAiEvaluation) => {
    setSelectedHolding(holding);
    setHoldingModalOpen(true);
  };

  const isLoading = evaluateMutation.isPending;
  const isInitialLoading = cachedQuery.isLoading;
  const isError = evaluateMutation.isError;
  const error = evaluateMutation.error;

  const riskColor = evaluation
    ? getRiskLevelColor(evaluation.overallRiskLevel, evaluation.overallRiskScore)
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

  const isStale = evaluation
    ? isEvaluationStale(evaluation.evaluatedAt, evaluation.isStale)
    : false;

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }}>
      <CardHeader
        avatar={<AutoAwesomeIcon sx={{ color: 'primary.main', fontSize: 26 }} />}
        action={
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            {providerLabel && (
              <Chip
                label={providerLabel}
                size="small"
                variant="outlined"
                sx={{ fontWeight: 600, fontSize: '0.7rem', letterSpacing: 0.5 }}
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
                <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                  <AccessTimeIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
                  <Typography variant="caption" color="text.secondary">
                    {lastEvaluatedLabel}
                  </Typography>
                </Stack>
              </Tooltip>
            )}
            <AiStatusIndicator />
            <Tooltip title="AI Gateway Settings">
              <IconButton
                size="small"
                onClick={() => setSettingsOpen(true)}
                data-testid="portfolio-ai-settings-btn"
                aria-label="AI Gateway Settings"
              >
                <SettingsIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            {evaluation && (
              <Button
                variant="outlined"
                size="small"
                startIcon={<RefreshIcon />}
                onClick={handleRunAnalysis}
                disabled={isLoading}
              >
                Re-evaluate
              </Button>
            )}
          </Stack>
        }

        title={
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            AI Portfolio Intelligence
          </Typography>
        }
        subheader="Deep portfolio synthesis, asset concentration, and macro stress tests powered by AI"
      />
      <Divider />

      <CardContent sx={{ p: 3 }}>
        {/* Initial prompt state: loading from DB */}
        {isInitialLoading && (
          <Box sx={{ py: 4, textAlign: 'center' }}>
            <LinearProgress sx={{ mb: 2, borderRadius: 2, height: 4 }} />
            <Typography variant="body2" color="text.secondary">
              Loading previous evaluation...
            </Typography>
          </Box>
        )}

        {/* Initial Prompt State when not yet analyzed */}
        {!evaluation && !isLoading && !isError && !isInitialLoading && (
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
            <PsychologyIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1.5 }} />
            <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
              Comprehensive Portfolio Intelligence
            </Typography>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ maxWidth: 650, mx: 'auto', mb: 3, lineHeight: 1.6 }}
            >
              Analyze your current asset allocation, single-stock concentrations, tax wrapper distribution (ISA, SIPP, GIA), and forward macro stress scenarios using your configured AI provider.
            </Typography>
            <Button
              variant="contained"
              size="large"
              startIcon={<AutoAwesomeIcon />}
              onClick={handleRunAnalysis}
              sx={{ px: 4, py: 1.2, fontWeight: 700 }}
            >
              Analyze {portfolioName}
            </Button>
          </Paper>
        )}

        {/* Loading Progress State — LLM evaluation in flight */}
        {isLoading && (
          <Box sx={{ py: 6, textAlign: 'center' }}>
            <LinearProgress sx={{ mb: 3, borderRadius: 2, height: 6 }} />
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 1 }}>
              Running AI Evaluation...
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Synthesizing asset allocations, concentration risk, tax wrapper placement, and macro stress scenarios.
            </Typography>
          </Box>
        )}

        {/* Error Alert State */}
        {isError && (
          <Alert
            severity="error"
            action={
              <Stack direction="row" spacing={1}>
                <Button color="inherit" size="small" onClick={() => setSettingsOpen(true)}>
                  Adjust Timeout
                </Button>
                <Button color="inherit" size="small" onClick={handleRunAnalysis}>
                  Retry
                </Button>
              </Stack>
            }
            sx={{ mb: 2 }}
          >
            Failed to evaluate portfolio: {error?.message || 'Connection to AI provider failed'}.
            Verify that your AI provider is reachable or increase the inference timeout.
          </Alert>
        )}


        {/* Evaluated Results View */}
        {evaluation && (
          <Stack spacing={3}>
            {isStale && (
              <Alert
                severity="warning"
                action={
                  <Button
                    color="inherit"
                    size="small"
                    onClick={handleRunAnalysis}
                    disabled={isLoading}
                    startIcon={<RefreshIcon />}
                  >
                    Re-evaluate
                  </Button>
                }
              >
                <strong>Outdated Evaluation (&gt;7 days):</strong> This portfolio was evaluated on{' '}
                {new Date(evaluation.evaluatedAt).toLocaleDateString()} ({lastEvaluatedLabel}).
                Market conditions, asset weights, and holding valuations may have shifted.
              </Alert>
            )}

            {/* Top Score Bar */}
            <Paper variant="outlined" sx={{ p: 2.5, bgcolor: 'background.default' }}>
              <Grid container spacing={3} sx={{ alignItems: 'center' }}>
                <Grid size={{ xs: 12, md: 4 }}>
                  <Typography variant="caption" color="text.secondary">
                    Overall Risk Rating
                  </Typography>
                  <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mt: 0.5 }}>
                    <Typography variant="h4" sx={{ fontWeight: 800 }}>
                      {evaluation.overallRiskScore}
                    </Typography>
                    <Typography variant="h6" color="text.secondary">
                      / 10
                    </Typography>
                    <Chip
                      label={evaluation.overallRiskLevel}
                      size="small"
                      sx={{
                        bgcolor: riskColor,
                        color: '#fff',
                        fontWeight: 700,
                        fontSize: '0.75rem',
                      }}
                    />
                  </Stack>
                  <LinearProgress
                    variant="determinate"
                    value={evaluation.overallRiskScore * 10}
                    sx={{
                      mt: 1,
                      height: 6,
                      borderRadius: 3,
                      bgcolor: 'action.hover',
                      '& .MuiLinearProgress-bar': {
                        bgcolor: riskColor,
                      },
                    }}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 8 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                    Portfolio Executive Thesis
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
                    {evaluation.executiveSummary}
                  </Typography>
                </Grid>
              </Grid>
            </Paper>

            {/* 2-Column: Diversification Assessment & Concentration Risks */}
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <ShieldOutlinedIcon color="primary" />
                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                      Diversification Assessment
                    </Typography>
                  </Stack>
                  <Typography variant="body2" sx={{ lineHeight: 1.6, color: 'text.secondary' }}>
                    {evaluation.diversificationAssessment}
                  </Typography>
                </Paper>
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <Paper variant="outlined" sx={{ p: 2.5, height: '100%', borderColor: 'warning.light' }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <WarningAmberIcon color="warning" />
                    <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'warning.dark' }}>
                      Concentration Risks
                    </Typography>
                  </Stack>
                  <List dense disablePadding>
                    {evaluation.concentrationRisks.map((risk, idx) => (
                      <ListItem key={idx} disableGutters sx={{ alignItems: 'flex-start', py: 0.5 }}>
                        <ListItemIcon sx={{ minWidth: 26, mt: 0.5 }}>
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

            {/* 2-Column: Tax & Location Optimization & Macro Stress Scenarios */}
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <AccountBalanceIcon color="secondary" />
                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                      Tax Wrapper & Asset Location Optimization
                    </Typography>
                  </Stack>
                  <List dense disablePadding>
                    {evaluation.taxAndLocationOptimization.map((opt, idx) => (
                      <ListItem key={idx} disableGutters sx={{ alignItems: 'flex-start', py: 0.5 }}>
                        <ListItemIcon sx={{ minWidth: 26, mt: 0.5 }}>
                          <CheckCircleOutlinedIcon color="secondary" fontSize="small" />
                        </ListItemIcon>
                        <ListItemText
                          primary={<Typography variant="body2">{opt}</Typography>}
                        />
                      </ListItem>
                    ))}
                  </List>
                </Paper>
              </Grid>

              <Grid size={{ xs: 12, md: 6 }}>
                <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <ShowChartIcon color="info" />
                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                      Macro Stress Scenarios
                    </Typography>
                  </Stack>
                  <List dense disablePadding>
                    {evaluation.macroStressScenarios.map((scenario, idx) => (
                      <ListItem key={idx} disableGutters sx={{ alignItems: 'flex-start', py: 0.5 }}>
                        <ListItemIcon sx={{ minWidth: 26, mt: 0.5 }}>
                          <ShowChartIcon color="info" fontSize="small" />
                        </ListItemIcon>
                        <ListItemText
                          primary={<Typography variant="body2">{scenario}</Typography>}
                        />
                      </ListItem>
                    ))}
                  </List>
                </Paper>
              </Grid>
            </Grid>

            {/* Top Recommendations */}
            <Paper
              sx={{
                p: 2.5,
                bgcolor: 'primary.50',
                borderLeft: 4,
                borderColor: 'primary.main',
                borderRadius: 2,
              }}
            >
              <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'primary.dark', mb: 1 }}>
                Strategic Action Items & Recommendations
              </Typography>
              <List dense disablePadding>
                {evaluation.topRecommendations.map((rec, idx) => (
                  <ListItem key={idx} disableGutters sx={{ alignItems: 'flex-start', py: 0.5 }}>
                    <ListItemIcon sx={{ minWidth: 28, mt: 0.5 }}>
                      <CheckCircleOutlinedIcon color="primary" fontSize="small" />
                    </ListItemIcon>
                    <ListItemText
                      primary={<Typography variant="body2">{rec}</Typography>}
                    />
                  </ListItem>
                ))}
              </List>
            </Paper>

            {/* Top Holdings Quick Stance Matrix */}
            {evaluation.topHoldingEvaluations && evaluation.topHoldingEvaluations.length > 0 && (
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                  Top Holdings AI Stance & Risk Matrix
                </Typography>
                <TableContainer component={Paper} variant="outlined">
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: 'action.hover' }}>
                        <TableCell sx={{ fontWeight: 700 }}>Holding</TableCell>
                        <TableCell sx={{ fontWeight: 700 }} align="right">
                          Weight
                        </TableCell>
                        <TableCell sx={{ fontWeight: 700 }} align="center">
                          AI Stance
                        </TableCell>
                        <TableCell sx={{ fontWeight: 700 }} align="center">
                          Risk Score
                        </TableCell>
                        <TableCell sx={{ fontWeight: 700 }} align="right">
                          Deep Dive
                        </TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {evaluation.topHoldingEvaluations.map((holding) => {
                        const stanceProps = getStanceChipProps(holding.stance);
                        const holdingRiskColor = getRiskLevelColor(holding.riskLevel, holding.riskScore);
                        const isHoldingStale = isEvaluationStale(holding.evaluatedAt, holding.isStale);
                        return (
                          <TableRow key={holding.instrumentId} hover>
                            <TableCell>
                              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                                <Box>
                                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                    {holding.symbol}
                                  </Typography>
                                  <Typography variant="caption" color="text.secondary">
                                    {holding.name}
                                  </Typography>
                                </Box>
                                {isHoldingStale && (
                                  <Tooltip title="Evaluation is older than 7 days">
                                    <Chip
                                      label="Stale (>7d)"
                                      size="small"
                                      color="warning"
                                      sx={{ height: 20, fontSize: '0.65rem', fontWeight: 700 }}
                                    />
                                  </Tooltip>
                                )}
                              </Stack>
                            </TableCell>
                            <TableCell align="right">
                              <Typography variant="body2">
                                {holding.portfolioWeightPercentage.toFixed(2)}%
                              </Typography>
                            </TableCell>
                            <TableCell align="center">
                              <Chip
                                label={stanceProps.label}
                                color={stanceProps.color}
                                size="small"
                                sx={{ fontWeight: 700, fontSize: '0.7rem' }}
                              />
                            </TableCell>
                            <TableCell align="center">
                              <Chip
                                label={`${holding.riskScore}/10`}
                                size="small"
                                sx={{
                                  bgcolor: holdingRiskColor,
                                  color: '#fff',
                                  fontWeight: 700,
                                  fontSize: '0.7rem',
                                }}
                              />
                            </TableCell>
                            <TableCell align="right">
                              <Button
                                size="small"
                                variant="outlined"
                                endIcon={<LaunchIcon fontSize="small" />}
                                onClick={() => handleOpenHoldingModal(holding)}
                                sx={{ py: 0.25, fontSize: '0.75rem' }}
                              >
                                View Details
                              </Button>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
            )}

            <Divider />

            {/* Footer Metadata */}
            <Typography variant="caption" color="text.secondary">
              Evaluated with <strong>{evaluation.modelUsed}</strong> • Timestamp:{' '}
              {new Date(evaluation.evaluatedAt).toLocaleString()}
            </Typography>
          </Stack>
        )}

        {/* Individual Holding Evaluation Modal */}
        {selectedHolding && (
          <HoldingAiEvaluationModal
            open={holdingModalOpen}
            onClose={() => setHoldingModalOpen(false)}
            portfolioId={portfolioId}
            instrumentId={selectedHolding.instrumentId}
            symbol={selectedHolding.symbol}
            instrumentName={selectedHolding.name}
            initialData={selectedHolding}
          />
        )}
        <AiSettingsModal open={settingsOpen} onClose={() => setSettingsOpen(false)} />
      </CardContent>
    </Card>
  );
};


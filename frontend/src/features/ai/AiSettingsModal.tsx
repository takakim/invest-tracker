import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  TextField,
  Chip,
  Stack,
  Alert,
  CircularProgress,
  IconButton,
  Divider,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import SettingsIcon from '@mui/icons-material/Settings';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import ErrorOutlineOutlinedIcon from '@mui/icons-material/ErrorOutlineOutlined';

import { useAiStatus, useUpdateAiConfig } from './useAi';

interface AiSettingsModalProps {
  open: boolean;
  onClose: () => void;
}

const TIMEOUT_PRESETS = [
  { label: '1m (60s)', value: 60 },
  { label: '2m (120s)', value: 120 },
  { label: '5m (300s)', value: 300 },
  { label: '10m (600s)', value: 600 },
  { label: '20m (1200s)', value: 1200 },
];

export const AiSettingsModal: React.FC<AiSettingsModalProps> = ({ open, onClose }) => {
  const { data: status } = useAiStatus();
  const updateConfigMutation = useUpdateAiConfig();

  const currentTimeout = status?.timeoutSeconds ?? 60;
  const [timeoutValue, setTimeoutValue] = useState<number>(currentTimeout);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [hasUserEdited, setHasUserEdited] = useState<boolean>(false);

  useEffect(() => {
    if (open) {
      setTimeoutValue(status?.timeoutSeconds ?? 60);
      setValidationError(null);
      setSuccessMessage(null);
      setHasUserEdited(false);
    }
  }, [open]);

  useEffect(() => {
    if (open && !hasUserEdited && status?.timeoutSeconds !== undefined) {
      setTimeoutValue(status.timeoutSeconds);
    }
  }, [open, hasUserEdited, status?.timeoutSeconds]);

  const handleTimeoutChange = (val: number) => {
    setHasUserEdited(true);
    setTimeoutValue(val);
    if (isNaN(val) || val < 5 || val > 3600) {
      setValidationError('Timeout must be between 5 and 3600 seconds (1 hour)');
    } else {
      setValidationError(null);
    }
    setSuccessMessage(null);
  };


  const handleSave = () => {
    if (timeoutValue < 5 || timeoutValue > 3600) {
      setValidationError('Timeout must be between 5 and 3600 seconds (1 hour)');
      return;
    }
    updateConfigMutation.mutate(
      { timeoutSeconds: timeoutValue },
      {
        onSuccess: () => {
          setSuccessMessage(`AI Gateway timeout updated to ${timeoutValue} seconds`);
          setTimeout(() => {
            onClose();
          }, 1200);
        },
      }
    );
  };

  const isConnected = Boolean(status?.connected);
  const providerName = status?.provider ? status.provider.replace('_', ' ') : 'AI Provider';

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      data-testid="ai-settings-modal"
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pb: 1 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <SettingsIcon color="primary" />
          <Typography variant="h6" component="span" sx={{ fontWeight: 600 }}>
            AI Gateway Settings
          </Typography>
        </Stack>
        <IconButton onClick={onClose} size="small" aria-label="close">
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers>
        <Stack spacing={2.5}>
          {/* Status info banner */}
          <Box sx={{ p: 2, bgcolor: 'background.default', borderRadius: 2, border: 1, borderColor: 'divider' }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                {providerName}
              </Typography>
              <Chip
                size="small"
                icon={isConnected ? <CheckCircleOutlinedIcon /> : <ErrorOutlineOutlinedIcon />}
                label={isConnected ? 'Connected' : 'Offline'}
                color={isConnected ? 'success' : 'default'}
                variant="outlined"
              />

            </Stack>
            <Typography variant="body2" color="text.secondary">
              Model: <strong>{status?.configuredModel || 'N/A'}</strong>
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Endpoint: <code>{status?.baseUrl || 'N/A'}</code>
            </Typography>
            {status?.availableModels && status.availableModels.length > 0 && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                Available models: {status.availableModels.join(', ')}
              </Typography>
            )}
          </Box>

          <Divider />

          {/* Timeout controls */}
          <Box>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
              <TimerOutlinedIcon color="action" fontSize="small" />
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                Inference Timeout
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Local models (LM Studio, Ollama) and complex portfolio stress analysis may require more time
              to generate comprehensive fundamental metrics and risk assessments.
            </Typography>

            {/* Presets */}
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1, fontWeight: 500 }}>
              Quick Presets:
            </Typography>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', mb: 2 }}>
              {TIMEOUT_PRESETS.map((preset) => {
                const isSelected = timeoutValue === preset.value;
                return (
                  <Chip
                    key={preset.value}
                    label={preset.label}
                    onClick={() => handleTimeoutChange(preset.value)}
                    color={isSelected ? 'primary' : 'default'}
                    variant={isSelected ? 'filled' : 'outlined'}
                    clickable
                    size="small"
                    sx={{ fontWeight: isSelected ? 600 : 400 }}
                  />
                );
              })}
            </Stack>


            {/* Custom Input */}
            <TextField
              label="Timeout (seconds)"
              type="number"
              size="small"
              value={timeoutValue}
              onChange={(e) => handleTimeoutChange(Number(e.target.value))}
              slotProps={{
                htmlInput: { min: 5, max: 3600, step: 10, 'data-testid': 'ai-timeout-input' },
              }}
              error={Boolean(validationError)}
              helperText={validationError || 'Valid range: 5 to 3600 seconds (1 hour)'}
              fullWidth
            />
          </Box>

          {/* Alerts */}
          {successMessage && (
            <Alert severity="success" data-testid="ai-settings-success-alert">
              {successMessage}
            </Alert>
          )}
          {updateConfigMutation.isError && (
            <Alert severity="error" data-testid="ai-settings-error-alert">
              {updateConfigMutation.error?.message || 'Failed to update AI configuration'}
            </Alert>
          )}
        </Stack>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} color="inherit">
          Cancel
        </Button>
        <Button
          onClick={handleSave}
          variant="contained"
          color="primary"
          disabled={Boolean(validationError) || updateConfigMutation.isPending}
          startIcon={updateConfigMutation.isPending ? <CircularProgress size={16} color="inherit" /> : null}
          data-testid="ai-save-settings-btn"
        >
          {updateConfigMutation.isPending ? 'Saving...' : 'Save Settings'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

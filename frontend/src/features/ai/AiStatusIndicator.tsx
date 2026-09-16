import React, { useState } from 'react';
import { Chip, Tooltip, Box, CircularProgress } from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ErrorOutlineOutlinedIcon from '@mui/icons-material/ErrorOutlineOutlined';
import { useAiStatus } from './useAi';
import { AiSettingsModal } from './AiSettingsModal';

interface AiStatusIndicatorProps {
  size?: 'small' | 'medium';
}

export const AiStatusIndicator: React.FC<AiStatusIndicatorProps> = ({ size = 'small' }) => {
  const { data: status, isLoading } = useAiStatus();
  const [settingsOpen, setSettingsOpen] = useState(false);

  if (isLoading) {
    return (
      <Chip
        icon={<CircularProgress size={12} color="inherit" />}
        label="AI: Checking..."
        size={size}
        variant="outlined"
        sx={{ fontSize: '0.75rem' }}
      />
    );
  }

  if (!status || !status.enabled) {
    return null;
  }

  const isConnected = Boolean(status.connected);
  const modelName = status.configuredModel || 'N/A';
  const providerName = status.provider
    ? status.provider.replace('_', ' ')
    : 'AI Provider';

  const tooltipTitle = isConnected ? (
    <Box sx={{ p: 0.5 }}>
      <strong>{providerName} Connected</strong>
      <div>Model: {modelName}</div>
      <div>Endpoint: {status.baseUrl}</div>
      {status.availableModels && status.availableModels.length > 0 && (
        <div style={{ marginTop: 4, fontSize: '0.75rem', opacity: 0.85 }}>
          Available: {status.availableModels.join(', ')}
        </div>
      )}
      <div style={{ marginTop: 6, fontSize: '0.7rem', opacity: 0.75, fontStyle: 'italic' }}>
        Click to adjust timeout or view settings
      </div>
    </Box>
  ) : (
    <Box sx={{ p: 0.5 }}>
      <strong>{providerName} Offline</strong>
      <div>Endpoint: {status.baseUrl}</div>
      <div style={{ marginTop: 4, fontSize: '0.75rem', opacity: 0.85 }}>
        Configure {providerName} and ensure it is reachable.
      </div>
      {status.errorMessage && (
        <div style={{ marginTop: 4, color: '#fca5a5', fontSize: '0.75rem' }}>
          {status.errorMessage}
        </div>
      )}
      <div style={{ marginTop: 6, fontSize: '0.7rem', opacity: 0.75, fontStyle: 'italic' }}>
        Click to adjust timeout or view settings
      </div>
    </Box>
  );

  return (
    <>
      <Tooltip title={tooltipTitle} arrow>
        <Chip
          icon={
            isConnected ? (
              <AutoAwesomeIcon sx={{ fontSize: '14px !important', color: 'success.main' }} />
            ) : (
              <ErrorOutlineOutlinedIcon sx={{ fontSize: '14px !important', color: 'text.secondary' }} />
            )
          }
          label={isConnected ? `${providerName}: ${modelName}` : `${providerName}: Offline`}
          size={size}
          variant="outlined"
          color={isConnected ? 'success' : 'default'}
          onClick={() => setSettingsOpen(true)}
          data-testid="ai-status-chip"
          sx={{
            fontWeight: 600,
            fontSize: '0.75rem',
            cursor: 'pointer',
            borderRadius: 2,
            borderColor: isConnected ? 'success.main' : 'divider',
            bgcolor: isConnected ? 'success.50' : 'action.hover',
            '& .MuiChip-label': {
              px: 1,
            },
          }}
        />
      </Tooltip>
      <AiSettingsModal open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </>
  );
};


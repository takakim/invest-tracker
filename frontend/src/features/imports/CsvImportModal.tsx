import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined';

import { usePreviewCsvImport, useExecuteCsvImport, useSupportedBrokers, useDetectBroker } from './useCsvImport';
import { ErrorAlert, LoadingState } from '../../components';
import type { CsvImportPreview, ImportBatch, BrokerDetectionResponse } from '../../types';

interface CsvImportModalProps {
  open: boolean;
  portfolioId: string;
  accountId: string;
  onClose: () => void;
}

export function CsvImportModal({
  open,
  portfolioId,
  accountId,
  onClose,
}: CsvImportModalProps) {
  const [file, setFile] = useState<File | null>(null);
  const [csvContent, setCsvContent] = useState<string>('');
  const [selectedBroker, setSelectedBroker] = useState<string>('AUTO');
  const [detectionResult, setDetectionResult] = useState<BrokerDetectionResponse | null>(null);
  const [previewData, setPreviewData] = useState<CsvImportPreview | null>(null);
  const [completedBatch, setCompletedBatch] = useState<ImportBatch | null>(null);

  const { data: supportedBrokers = [
    'AJ Bell',
    'DEGIRO',
    'Freetrade',
    'Interactive Brokers',
    'InvestEngine',
    'Trading 212',
    'Vanguard UK',
  ] } = useSupportedBrokers();

  const previewMutation = usePreviewCsvImport(portfolioId, accountId);
  const executeMutation = useExecuteCsvImport(portfolioId, accountId);
  const detectMutation = useDetectBroker();

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      setFile(selectedFile);
      const reader = new FileReader();
      reader.onload = (evt) => {
        const content = evt.target?.result as string;
        setCsvContent(content);

        // Run broker auto-detection
        detectMutation.mutate(content, {
          onSuccess: (det) => setDetectionResult(det),
        });

        // Trigger preview
        previewMutation.mutate(
          {
            fileName: selectedFile.name,
            csvContent: content,
            overrideBroker: selectedBroker !== 'AUTO' ? selectedBroker : undefined,
          },
          {
            onSuccess: (data) => setPreviewData(data),
          },
        );
      };
      reader.readAsText(selectedFile);
    }
  };

  const handleBrokerChange = (newBroker: string) => {
    setSelectedBroker(newBroker);
    if (file && csvContent) {
      previewMutation.mutate(
        {
          fileName: file.name,
          csvContent,
          overrideBroker: newBroker !== 'AUTO' ? newBroker : undefined,
        },
        {
          onSuccess: (data) => setPreviewData(data),
        },
      );
    }
  };

  const handleExecuteImport = () => {
    if (!file || !csvContent) return;
    executeMutation.mutate(
      {
        fileName: file.name,
        csvContent,
        overrideBroker: selectedBroker !== 'AUTO' ? selectedBroker : undefined,
      },
      {
        onSuccess: (batch) => {
          setCompletedBatch(batch);
        },
      },
    );
  };

  const handleReset = () => {
    setFile(null);
    setCsvContent('');
    setSelectedBroker('AUTO');
    setDetectionResult(null);
    setPreviewData(null);
    setCompletedBatch(null);
  };

  const handleCloseModal = () => {
    handleReset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleCloseModal} maxWidth="lg" fullWidth>
      <DialogTitle sx={{ fontWeight: 600 }}>Import Broker CSV</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <ErrorAlert error={previewMutation.error || executeMutation.error} />

          {/* STEP 1: Upload File */}
          {!previewData && !completedBatch && (
            <Box
              sx={{
                p: 4,
                border: '2px dashed',
                borderColor: 'divider',
                borderRadius: 2,
                textAlign: 'center',
                bgcolor: 'action.hover',
                cursor: 'pointer',
              }}
              component="label"
            >
              <input
                type="file"
                accept=".csv"
                hidden
                data-testid="csv-file-input"
                onChange={handleFileChange}
              />
              <UploadFileOutlinedIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1 }} />
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                Select a broker CSV file to import
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Intelligent auto-detection for Vanguard UK, Interactive Brokers, DEGIRO, AJ Bell, Trading 212, Freetrade, and InvestEngine statement exports.
              </Typography>
              <Button variant="outlined" sx={{ mt: 2 }} component="span">
                Choose CSV File
              </Button>
            </Box>
          )}

          {previewMutation.isPending && <LoadingState variant="table" count={3} />}

          {/* STEP 2: Preview Results Table */}
          {previewData && !completedBatch && (
            <Box>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2, mb: 2 }}
              >
                <Box>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    <Typography variant="h6" sx={{ fontWeight: 600 }}>
                      Import Preview
                    </Typography>
                    <Chip
                      icon={<AutoAwesomeOutlinedIcon />}
                      label={previewData.brokerName}
                      color="primary"
                      size="small"
                    />
                    {detectionResult && detectionResult.confidence === 'HIGH' && (
                      <Chip
                        label="Auto-detected"
                        color="success"
                        variant="outlined"
                        size="small"
                      />
                    )}
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    File: {previewData.fileName}
                  </Typography>
                </Box>

                <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                  <FormControl size="small" sx={{ minWidth: 200 }}>
                    <InputLabel id="broker-select-label">Broker Parser</InputLabel>
                    <Select
                      labelId="broker-select-label"
                      value={selectedBroker}
                      label="Broker Parser"
                      onChange={(e) => handleBrokerChange(e.target.value)}
                    >
                      <MenuItem value="AUTO">✨ Auto-Detect Format</MenuItem>
                      <Divider />
                      {supportedBrokers.map((b) => (
                        <MenuItem key={b} value={b}>
                          {b}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>

                  <Stack direction="row" spacing={1}>
                    <Chip
                      label={`${previewData.importableRows} Ready`}
                      color="success"
                      variant="outlined"
                    />
                    <Chip
                      label={`${previewData.duplicateRows} Duplicates`}
                      color="warning"
                      variant="outlined"
                    />
                    <Chip
                      label={`${previewData.ignoredRows} Ignored`}
                      color="default"
                      variant="outlined"
                    />
                  </Stack>
                </Stack>
              </Stack>

              {previewData.duplicateRows > 0 && (
                <Alert severity="warning" sx={{ mb: 2 }} icon={<WarningAmberOutlinedIcon />}>
                  {previewData.duplicateRows} duplicate transaction(s) were detected and will be safely skipped to avoid duplicating financial records.
                </Alert>
              )}

              <TableContainer component={Paper} sx={{ maxHeight: 400, borderRadius: 2 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Row</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>Instrument / Title</TableCell>
                      <TableCell align="right">Qty</TableCell>
                      <TableCell align="right">Price</TableCell>
                      <TableCell align="right">Gross</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {previewData.rows.map((r) => (
                      <TableRow
                        key={r.rowNumber}
                        sx={{ opacity: r.isDuplicate || r.isIgnored ? 0.6 : 1 }}
                      >
                        <TableCell variant="body">{r.rowNumber}</TableCell>
                        <TableCell>
                          {r.mappedType ? (
                            <Chip label={r.mappedType} size="small" color="primary" />
                          ) : (
                            <Typography variant="caption" color="text.secondary">
                              {r.rawType}
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>
                          {r.instrumentTitle || r.ticker || 'Cash Movement'}
                          {r.ticker && (
                            <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                              ({r.ticker})
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="right">{r.quantity != null ? r.quantity : '—'}</TableCell>
                        <TableCell align="right">{r.price != null ? r.price : '—'}</TableCell>
                        <TableCell align="right">
                          {r.grossAmount != null ? `${r.currency || ''} ${r.grossAmount}` : '—'}
                        </TableCell>
                        <TableCell>
                          {r.isDuplicate ? (
                            <Chip label="Duplicate (Skip)" size="small" color="warning" />
                          ) : r.isIgnored ? (
                            <Chip label="Ignored" size="small" variant="outlined" />
                          ) : (
                            <Chip label="Ready" size="small" color="success" />
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Box>
          )}

          {/* STEP 3: Completed Batch */}
          {completedBatch && (
            <Box sx={{ textAlign: 'center', py: 3 }}>
              <CheckCircleOutlinedIcon sx={{ fontSize: 56, color: 'success.main', mb: 1 }} />
              <Typography variant="h5" sx={{ fontWeight: 700 }}>
                Import Completed
              </Typography>
              <Typography variant="body1" color="text.secondary" sx={{ mt: 1 }}>
                Successfully imported {completedBatch.importedRows} transaction(s) from {completedBatch.fileName}.
              </Typography>

              <Box sx={{ mt: 3, display: 'inline-flex', gap: 2 }}>
                <Chip label={`Total Rows: ${completedBatch.totalRows}`} />
                <Chip label={`Imported: ${completedBatch.importedRows}`} color="success" />
                <Chip label={`Skipped / Duplicates: ${completedBatch.skippedRows}`} color="warning" />
              </Box>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        {!completedBatch ? (
          <>
            <Button onClick={handleCloseModal} color="inherit">
              Cancel
            </Button>
            {previewData && (
              <Button
                variant="contained"
                onClick={handleExecuteImport}
                disabled={executeMutation.isPending || previewData.importableRows === 0}
              >
                {executeMutation.isPending
                  ? 'Importing...'
                  : `Confirm & Import ${previewData.importableRows} Transactions`}
              </Button>
            )}
          </>
        ) : (
          <Button variant="contained" onClick={handleCloseModal}>
            Done
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}

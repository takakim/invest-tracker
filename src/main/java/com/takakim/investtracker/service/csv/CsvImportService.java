package com.takakim.investtracker.service.csv;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.ImportBatch;
import com.takakim.investtracker.domain.ImportBatchStatus;
import com.takakim.investtracker.domain.ImportRecord;
import com.takakim.investtracker.domain.ImportRecordStatus;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.ImportBatchRepository;
import com.takakim.investtracker.repository.ImportRecordRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.TransactionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CsvImportService {

    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ImportRecordRepository importRecordRepository;
    private final TransactionService transactionService;
    private final List<BrokerCsvParser> parsers;

    public CsvImportService(
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            ImportBatchRepository importBatchRepository,
            ImportRecordRepository importRecordRepository,
            TransactionService transactionService,
            List<BrokerCsvParser> parsers) {
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.importBatchRepository = importBatchRepository;
        this.importRecordRepository = importRecordRepository;
        this.transactionService = transactionService;
        this.parsers = parsers;
    }

    public record PreviewRow(
            int rowNumber,
            String rawType,
            String mappedType,
            String instrumentTitle,
            String ticker,
            String isin,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal taxAmount,
            String currency,
            boolean isDuplicate,
            boolean isIgnored,
            String diagnosticMessage
    ) {}

    public record PreviewResult(
            String brokerName,
            String fileName,
            int totalRows,
            int importableRows,
            int duplicateRows,
            int ignoredRows,
            List<PreviewRow> rows
    ) {}

    @Transactional(readOnly = true)
    public PreviewResult previewImport(UUID portfolioId, UUID accountId, String fileName, String csvContent) {
        Account account = getAccount(portfolioId, accountId);
        BrokerCsvParser parser = selectParser(csvContent);
        List<ParsedTransactionRow> parsedRows = parser.parse(csvContent);

        List<PreviewRow> previewRows = new ArrayList<>();
        int importableCount = 0;
        int duplicateCount = 0;
        int ignoredCount = 0;

        for (ParsedTransactionRow row : parsedRows) {
            if (row.isIgnored()) {
                ignoredCount++;
                previewRows.add(new PreviewRow(
                        row.rowNumber(), row.rawType(), null, row.instrumentTitle(),
                        row.ticker(), row.isin(), row.quantity(), row.price(), row.grossAmount(),
                        row.feeAmount(), row.taxAmount(), row.currency(), false, true, row.ignoreReason()
                ));
                continue;
            }

            String fingerprint = calculateFingerprint(accountId, parser.getBrokerName(), row);
            boolean isDuplicate = importRecordRepository.existsByAccountIdAndFingerprint(accountId, fingerprint);

            if (isDuplicate) {
                duplicateCount++;
                previewRows.add(new PreviewRow(
                        row.rowNumber(), row.rawType(), row.mappedType().name(), row.instrumentTitle(),
                        row.ticker(), row.isin(), row.quantity(), row.price(), row.grossAmount(),
                        row.feeAmount(), row.taxAmount(), row.currency(), true, false, "Duplicate transaction already imported"
                ));
            } else {
                importableCount++;
                previewRows.add(new PreviewRow(
                        row.rowNumber(), row.rawType(), row.mappedType().name(), row.instrumentTitle(),
                        row.ticker(), row.isin(), row.quantity(), row.price(), row.grossAmount(),
                        row.feeAmount(), row.taxAmount(), row.currency(), false, false, "Ready for import"
                ));
            }
        }

        return new PreviewResult(
                parser.getBrokerName(),
                fileName,
                parsedRows.size(),
                importableCount,
                duplicateCount,
                ignoredCount,
                previewRows
        );
    }

    @Transactional
    public ImportBatch executeImport(UUID portfolioId, UUID accountId, String fileName, String csvContent) {
        Account account = getAccount(portfolioId, accountId);
        BrokerCsvParser parser = selectParser(csvContent);
        List<ParsedTransactionRow> parsedRows = parser.parse(csvContent);

        ImportBatch batch = new ImportBatch(account, fileName, parser.getBrokerName(), parsedRows.size());
        batch = importBatchRepository.save(batch);

        int importedCount = 0;
        int skippedCount = 0;

        for (ParsedTransactionRow row : parsedRows) {
            String fingerprint = calculateFingerprint(accountId, parser.getBrokerName(), row);

            if (row.isIgnored()) {
                skippedCount++;
                ImportRecord rec = new ImportRecord(
                        batch, account, row.rowNumber(), row.rawLine(), fingerprint,
                        ImportRecordStatus.SKIPPED_IGNORED, row.ignoreReason(), null
                );
                importRecordRepository.save(rec);
                continue;
            }

            if (importRecordRepository.existsByAccountIdAndFingerprint(accountId, fingerprint)) {
                skippedCount++;
                ImportRecord rec = new ImportRecord(
                        batch, account, row.rowNumber(), row.rawLine(), fingerprint,
                        ImportRecordStatus.SKIPPED_DUPLICATE, "Duplicate transaction skipped", null
                );
                importRecordRepository.save(rec);
                continue;
            }

            try {
                // Auto-resolve or create Instrument master data if ticker/ISIN is present
                Instrument instrument = resolveOrCreateInstrument(row);

                // Record transaction
                Transaction tx = transactionService.recordTransaction(
                        portfolioId,
                        accountId,
                        instrument != null ? instrument.getId() : null,
                        row.mappedType(),
                        row.timestamp(),
                        null,
                        row.quantity(),
                        row.price(),
                        row.grossAmount(),
                        row.feeAmount(),
                        row.taxAmount(),
                        row.currency(),
                        row.fxRate(),
                        row.counterCurrency(),
                        row.notes(),
                        null
                );

                ImportRecord rec = new ImportRecord(
                        batch, account, row.rowNumber(), row.rawLine(), fingerprint,
                        ImportRecordStatus.IMPORTED, null, tx
                );
                importRecordRepository.save(rec);
                importedCount++;
            } catch (Exception e) {
                skippedCount++;
                ImportRecord rec = new ImportRecord(
                        batch, account, row.rowNumber(), row.rawLine(), fingerprint,
                        ImportRecordStatus.ERROR, e.getMessage(), null
                );
                importRecordRepository.save(rec);
            }
        }

        batch.updateProgress(importedCount, skippedCount, ImportBatchStatus.COMPLETED);
        return importBatchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public List<ImportBatch> listImportBatches(UUID portfolioId, UUID accountId) {
        getAccount(portfolioId, accountId);
        return importBatchRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    private Instrument resolveOrCreateInstrument(ParsedTransactionRow row) {
        if (row.ticker() == null && row.isin() == null) {
            return null;
        }

        if (row.ticker() != null && !row.ticker().isBlank()) {
            Optional<Instrument> existing = instrumentRepository.findByTicker(row.ticker());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (row.isin() != null && !row.isin().isBlank()) {
            Optional<Instrument> existing = instrumentRepository.findByIsin(row.isin());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String name = row.instrumentTitle() != null && !row.instrumentTitle().isBlank()
                ? row.instrumentTitle()
                : (row.ticker() != null && !row.ticker().isBlank() ? row.ticker() : "Imported Asset");

        String currencyCode = row.instrumentCurrency() != null && !row.instrumentCurrency().isBlank()
                ? row.instrumentCurrency()
                : row.currency();

        AssetClass inferredClass = inferAssetClass(name, row.ticker(), row.isin());

        Instrument inst = new Instrument(
                name,
                inferredClass,
                row.ticker(),
                row.isin(),
                null,
                new Currency(currencyCode != null ? currencyCode : "GBP")
        );
        return instrumentRepository.save(inst);
    }

    public static AssetClass inferAssetClass(String name, String ticker, String isin) {
        String n = name != null ? name.toLowerCase() : "";
        String t = ticker != null ? ticker.toLowerCase() : "";

        if (n.contains("t-bill") || n.contains("treasury") || n.contains("gilt") || n.contains("bond") || t.startsWith("gb00b")) {
            return AssetClass.BOND;
        }
        if (n.contains("reit") || n.contains("property")) {
            return AssetClass.REIT;
        }
        if (n.contains("etf") || n.contains("vanguard") || n.contains("ishares") || n.contains("spdr") || n.contains("ftse") || n.contains("s&p 500") || t.equals("vwrl") || t.equals("xdpg")) {
            return AssetClass.ETF;
        }
        if (n.contains("fund") || n.contains("oeic")) {
            return AssetClass.MUTUAL_FUND;
        }
        if (n.contains("crypto") || n.contains("bitcoin") || t.equals("btc")) {
            return AssetClass.CRYPTO;
        }
        if (n.contains("cash")) {
            return AssetClass.CASH;
        }

        return AssetClass.STOCK;
    }

    private BrokerCsvParser selectParser(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new IllegalArgumentException("CSV file content must not be empty");
        }
        String firstLine = csvContent.split("\r?\n")[0];
        List<String> headers = FreetradeCsvParser.parseCsvLine(firstLine);

        for (BrokerCsvParser p : parsers) {
            if (p.supports(headers)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unsupported broker CSV format. Headers do not match known broker templates.");
    }

    private Account getAccount(UUID portfolioId, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        if (!account.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException("Account " + accountId + " does not belong to portfolio " + portfolioId);
        }
        return account;
    }

    public String calculateFingerprint(UUID accountId, String brokerName, ParsedTransactionRow row) {
        String raw = String.format("%s|%s|%s|%s|%s|%s|%s|%s",
                accountId,
                brokerName,
                row.timestamp() != null ? row.timestamp().toString() : "",
                row.rawType() != null ? row.rawType() : "",
                row.ticker() != null ? row.ticker() : "",
                row.externalReference() != null ? row.externalReference() : "",
                row.grossAmount() != null ? row.grossAmount().toPlainString() : "",
                row.quantity() != null ? row.quantity().toPlainString() : ""
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}

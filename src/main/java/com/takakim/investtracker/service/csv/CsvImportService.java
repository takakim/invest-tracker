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
    private final com.takakim.investtracker.repository.TransactionRepository transactionRepository;
    private final com.takakim.investtracker.service.position.PositionEngine positionEngine;
    private final List<BrokerCsvParser> parsers;

    public CsvImportService(
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            ImportBatchRepository importBatchRepository,
            ImportRecordRepository importRecordRepository,
            TransactionService transactionService,
            List<BrokerCsvParser> parsers) {
        this(accountRepository, instrumentRepository, importBatchRepository, importRecordRepository, transactionService, null, null, parsers);
    }

    public CsvImportService(
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            ImportBatchRepository importBatchRepository,
            ImportRecordRepository importRecordRepository,
            TransactionService transactionService,
            com.takakim.investtracker.repository.TransactionRepository transactionRepository,
            List<BrokerCsvParser> parsers) {
        this(accountRepository, instrumentRepository, importBatchRepository, importRecordRepository, transactionService, transactionRepository, null, parsers);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CsvImportService(
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            ImportBatchRepository importBatchRepository,
            ImportRecordRepository importRecordRepository,
            TransactionService transactionService,
            com.takakim.investtracker.repository.TransactionRepository transactionRepository,
            com.takakim.investtracker.service.position.PositionEngine positionEngine,
            List<BrokerCsvParser> parsers) {
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.importBatchRepository = importBatchRepository;
        this.importRecordRepository = importRecordRepository;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.positionEngine = positionEngine;
        this.parsers = parsers;
    }

    public record BrokerDetectionResult(
            String brokerName,
            String confidence,
            boolean isSupported,
            List<String> supportedBrokers
    ) {}

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

    public List<String> getSupportedBrokers() {
        return parsers.stream()
                .map(BrokerCsvParser::getBrokerName)
                .sorted()
                .toList();
    }

    public BrokerDetectionResult detectBroker(String csvContent) {
        List<String> supported = getSupportedBrokers();
        if (csvContent == null || csvContent.isBlank()) {
            return new BrokerDetectionResult("Unknown", "LOW", false, supported);
        }

        String cleanContent = stripBom(csvContent);
        String[] lines = cleanContent.split("\r?\n");
        for (int i = 0; i < Math.min(8, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> headers = FreetradeCsvParser.parseCsvLine(line);
            for (BrokerCsvParser p : parsers) {
                if (p.supports(headers)) {
                    return new BrokerDetectionResult(p.getBrokerName(), "HIGH", true, supported);
                }
            }
        }

        return new BrokerDetectionResult("Unknown", "NONE", false, supported);
    }

    @Transactional(readOnly = true)
    public PreviewResult previewImport(UUID portfolioId, UUID accountId, String fileName, String csvContent) {
        return previewImport(portfolioId, accountId, fileName, csvContent, null);
    }

    @Transactional(readOnly = true)
    public PreviewResult previewImport(UUID portfolioId, UUID accountId, String fileName, String csvContent, String overrideBroker) {
        Account account = getAccount(portfolioId, accountId);
        BrokerCsvParser parser = selectParser(csvContent, overrideBroker);
        List<ParsedTransactionRow> parsedRows = parser.parse(stripBom(csvContent));

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
                        row.rowNumber(), row.rawType(), row.mappedType() != null ? row.mappedType().name() : null, row.instrumentTitle(),
                        row.ticker(), row.isin(), row.quantity(), row.price(), row.grossAmount(),
                        row.feeAmount(), row.taxAmount(), row.currency(), true, false, "Duplicate transaction already imported"
                ));
            } else {
                importableCount++;
                previewRows.add(new PreviewRow(
                        row.rowNumber(), row.rawType(), row.mappedType() != null ? row.mappedType().name() : null, row.instrumentTitle(),
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
        return executeImport(portfolioId, accountId, fileName, csvContent, null);
    }

    @Transactional
    public ImportBatch executeImport(UUID portfolioId, UUID accountId, String fileName, String csvContent, String overrideBroker) {
        Account account = getAccount(portfolioId, accountId);
        BrokerCsvParser parser = selectParser(csvContent, overrideBroker);
        List<ParsedTransactionRow> parsedRows = parser.parse(stripBom(csvContent));

        ImportBatch batch = new ImportBatch(account, fileName, parser.getBrokerName(), parsedRows.size());
        batch = importBatchRepository.save(batch);

        int importedCount = 0;
        int skippedCount = 0;
        java.util.Map<UUID, Instrument> affectedInstruments = new java.util.TreeMap<>();

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

                // Record transaction without per-row position recalculation
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
                        null,
                        false
                );

                if (instrument != null) {
                    affectedInstruments.put(instrument.getId(), instrument);
                }

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

        // Recalculate and sync positions for all affected instruments in deterministic sorted order
        if (positionEngine != null) {
            for (Instrument instrument : affectedInstruments.values()) {
                try {
                    positionEngine.recalculateAndSync(account, instrument);
                } catch (Exception ignored) {
                }
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

    @Transactional
    public void deleteImportBatch(UUID portfolioId, UUID accountId, UUID batchId) {
        Account account = getAccount(portfolioId, accountId);
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + batchId));
        if (!batch.getAccount().getId().equals(accountId)) {
            throw new ResourceNotFoundException("Import batch " + batchId + " does not belong to account " + accountId);
        }

        List<ImportRecord> records = importRecordRepository.findByBatchIdOrderByRowNumberAsc(batchId);
        java.util.Map<UUID, Instrument> affectedInstruments = new java.util.TreeMap<>();
        List<Transaction> txsToDelete = new java.util.ArrayList<>();

        for (ImportRecord record : records) {
            if (record.getTransaction() != null) {
                Transaction tx = record.getTransaction();
                if (tx.getInstrument() != null) {
                    affectedInstruments.put(tx.getInstrument().getId(), tx.getInstrument());
                }
                txsToDelete.add(tx);
            }
        }

        importRecordRepository.deleteAll(records);
        importBatchRepository.delete(batch);
        transactionRepository.deleteAll(txsToDelete);

        if (positionEngine != null) {
            for (Instrument instrument : affectedInstruments.values()) {
                try {
                    positionEngine.recalculateAndSync(account, instrument);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Instrument resolveOrCreateInstrument(ParsedTransactionRow row) {
        if (row.ticker() == null && row.isin() == null) {
            return null;
        }

        String ticker = (row.ticker() != null && !row.ticker().isBlank())
                ? row.ticker().trim()
                : inferTicker(row.isin(), row.instrumentTitle());

        if (ticker != null && !ticker.isBlank()) {
            Optional<Instrument> existing = instrumentRepository.findByTicker(ticker);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (row.isin() != null && !row.isin().isBlank()) {
            Optional<Instrument> existing = instrumentRepository.findByIsin(row.isin().trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String name = row.instrumentTitle() != null && !row.instrumentTitle().isBlank()
                ? row.instrumentTitle().trim()
                : (ticker != null && !ticker.isBlank() ? ticker : "Imported Asset");

        String currencyCode = row.instrumentCurrency() != null && !row.instrumentCurrency().isBlank()
                ? row.instrumentCurrency()
                : row.currency();

        AssetClass inferredClass = inferAssetClass(name, ticker, row.isin());
        String nativeCurrency = inferNativeCurrency(ticker, row.isin(), row.instrumentCurrency(), row.currency());
        String inferredExchange = inferExchange(ticker, row.isin(), name, nativeCurrency);

        Instrument inst = new Instrument(
                name,
                inferredClass,
                ticker,
                row.isin(),
                inferredExchange,
                new Currency(nativeCurrency)
        );
        return instrumentRepository.save(inst);
    }

    private static final java.util.Map<String, String> KNOWN_TICKER_EXCHANGES = java.util.Map.ofEntries(
            java.util.Map.entry("AAPL", "NASDAQ"),
            java.util.Map.entry("AMD", "NASDAQ"),
            java.util.Map.entry("AMZN", "NASDAQ"),
            java.util.Map.entry("ALAB", "NASDAQ"),
            java.util.Map.entry("BBAI", "NASDAQ"),
            java.util.Map.entry("BIRD", "NASDAQ"),
            java.util.Map.entry("CARL", "NASDAQ"),
            java.util.Map.entry("CBRS", "NASDAQ"),
            java.util.Map.entry("COIN", "NASDAQ"),
            java.util.Map.entry("COST", "NASDAQ"),
            java.util.Map.entry("CRWD", "NASDAQ"),
            java.util.Map.entry("FIG", "NASDAQ"),
            java.util.Map.entry("FLY", "NASDAQ"),
            java.util.Map.entry("GOOG", "NASDAQ"),
            java.util.Map.entry("HOOD", "NASDAQ"),
            java.util.Map.entry("HON", "NASDAQ"),
            java.util.Map.entry("HONA", "NASDAQ"),
            java.util.Map.entry("LUNR", "NASDAQ"),
            java.util.Map.entry("MRNA", "NASDAQ"),
            java.util.Map.entry("MRVL", "NASDAQ"),
            java.util.Map.entry("MSFT", "NASDAQ"),
            java.util.Map.entry("MU", "NASDAQ"),
            java.util.Map.entry("NET", "NASDAQ"),
            java.util.Map.entry("NTDOY", "OTC"),
            java.util.Map.entry("NVDA", "NASDAQ"),
            java.util.Map.entry("QBTS", "NASDAQ"),
            java.util.Map.entry("QCOM", "NASDAQ"),
            java.util.Map.entry("QS", "NASDAQ"),
            java.util.Map.entry("RDDT", "NYSE"),
            java.util.Map.entry("RIVN", "NASDAQ"),
            java.util.Map.entry("SFTBY", "OTC"),
            java.util.Map.entry("SMCI", "NASDAQ"),
            java.util.Map.entry("SMLR", "NASDAQ"),
            java.util.Map.entry("SOLS", "OTC"),
            java.util.Map.entry("SOUN", "NASDAQ"),
            java.util.Map.entry("SPCX", "OTC"),
            java.util.Map.entry("STX", "NASDAQ"),
            java.util.Map.entry("TSLA", "NASDAQ"),
            java.util.Map.entry("BBD", "NYSE"),
            java.util.Map.entry("BRK.B", "NYSE"),
            java.util.Map.entry("LUMN", "NYSE"),
            java.util.Map.entry("NU", "NYSE"),
            java.util.Map.entry("NVO", "NYSE"),
            java.util.Map.entry("ORCL", "NYSE"),
            java.util.Map.entry("PLTR", "NYSE"),
            java.util.Map.entry("RTX", "NYSE"),
            java.util.Map.entry("SPOT", "NYSE"),
            java.util.Map.entry("STLA", "NYSE"),
            java.util.Map.entry("TM", "NYSE"),
            java.util.Map.entry("VALE", "NYSE"),
            java.util.Map.entry("BNPP", "EURONEXT")
    );

    private static final java.util.Map<String, String> PREFIX_EXCHANGES = java.util.Map.of(
            "GB", "LSE",
            "GG", "LSE",
            "IE", "LSE",
            "LU", "LSE",
            "US", "NASDAQ",
            "FR", "EURONEXT",
            "DE", "EURONEXT",
            "NL", "EURONEXT",
            "KY", "NYSE"
    );

    private static final java.util.Map<String, String> PREFIX_CURRENCIES = java.util.Map.of(
            "GB", "GBP",
            "GG", "GBP",
            "US", "USD",
            "KY", "USD",
            "FR", "EUR",
            "DE", "EUR",
            "NL", "EUR"
    );

    public static String inferExchange(String ticker, String isin, String title, String currencyCode) {
        if (ticker != null) {
            String known = KNOWN_TICKER_EXCHANGES.get(ticker.trim().toUpperCase());
            if (known != null) return known;
        }
        if (isin != null && isin.length() >= 2) {
            String prefix = isin.trim().substring(0, 2).toUpperCase();
            String exch = PREFIX_EXCHANGES.get(prefix);
            if (exch != null) return exch;
        }
        if ("USD".equalsIgnoreCase(currencyCode)) return "NASDAQ";
        if ("EUR".equalsIgnoreCase(currencyCode)) return "EURONEXT";
        return "LSE";
    }

    public static String inferNativeCurrency(String ticker, String isin, String rawInstCurrency, String rawAccCurrency) {
        if (ticker != null) {
            String upperTicker = ticker.trim().toUpperCase();
            if ("SGLD".equals(upperTicker)) return "USD";
            String exch = KNOWN_TICKER_EXCHANGES.get(upperTicker);
            if (exch != null) {
                if ("EURONEXT".equals(exch)) return "EUR";
                return "USD";
            }
        }
        if (isin != null && isin.length() >= 2) {
            String prefix = isin.trim().substring(0, 2).toUpperCase();
            String curr = PREFIX_CURRENCIES.get(prefix);
            if (curr != null) return curr;
        }
        if (rawInstCurrency != null && !rawInstCurrency.isBlank() && !"GBP/USD".equalsIgnoreCase(rawInstCurrency)) {
            return rawInstCurrency.trim().toUpperCase();
        }
        return (rawAccCurrency != null && !rawAccCurrency.isBlank()) ? rawAccCurrency.trim().toUpperCase() : "GBP";
    }

    private static final java.util.Map<String, String> KNOWN_ISIN_TICKERS = java.util.Map.ofEntries(
            java.util.Map.entry("IE000716YHJ7", "FWRG"),
            java.util.Map.entry("IE0032077012", "EQQQ"),
            java.util.Map.entry("IE00B3YCGJ38", "SPXP"),
            java.util.Map.entry("IE00BHZRQZ17", "FLXI"),
            java.util.Map.entry("IE00BK5BQT80", "VWRP"),
            java.util.Map.entry("IE00BK5BQV03", "VEVE"),
            java.util.Map.entry("IE00BK5BR733", "VFEM"),
            java.util.Map.entry("IE00BM8R0J59", "QYLD"),
            java.util.Map.entry("IE00B3XXRP09", "VUSA"),
            java.util.Map.entry("IE00BFMXXD54", "VUAG"),
            java.util.Map.entry("IE00B3RBWM25", "VWRL"),
            java.util.Map.entry("GB00B59G4H30", "V80A"),
            java.util.Map.entry("IE00B4L5Y983", "SWDA"),
            java.util.Map.entry("IE00B5BMR087", "CSPX"),
            java.util.Map.entry("IE00BLPK3577", "CYSE"),
            java.util.Map.entry("IE00BDVPNG13", "INTL"),
            java.util.Map.entry("IE000940RNE6", "BKCN"),
            java.util.Map.entry("IE00BJGWQN72", "KLWD"),
            java.util.Map.entry("IE000W8WMSL2", "QWTM"),
            java.util.Map.entry("IE000O8KMPM1", "WBIO"),
            java.util.Map.entry("IE000MO2MB07", "WTNR"),
            java.util.Map.entry("IE000YDZG487", "HNSS"),
            java.util.Map.entry("IE00BMC38736", "SMGB"),
            java.util.Map.entry("IE00B579F325", "SGLD"),
            java.util.Map.entry("IE00BM67HX07", "XDPG")
    );

    public static String inferTicker(String isin, String name) {
        if (isin == null) return null;
        return KNOWN_ISIN_TICKERS.get(isin.trim().toUpperCase());
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
        if (n.contains("fund") || n.contains("oeic") || n.contains("unit trust")) {
            return AssetClass.MUTUAL_FUND;
        }
        if (n.contains("etf") || n.contains("vanguard") || n.contains("ishares") || n.contains("spdr") || n.contains("ftse") || n.contains("s&p 500") || t.equals("vwrl") || t.equals("xdpg")) {
            return AssetClass.ETF;
        }
        if (n.contains("crypto") || n.contains("bitcoin") || t.equals("btc")) {
            return AssetClass.CRYPTO;
        }
        if (n.contains("cash")) {
            return AssetClass.CASH;
        }

        return AssetClass.STOCK;
    }

    private String stripBom(String s) {
        if (s != null && s.startsWith("\uFEFF")) {
            return s.substring(1);
        }
        return s;
    }

    private BrokerCsvParser selectParser(String csvContent, String overrideBroker) {
        if (overrideBroker != null && !overrideBroker.isBlank()) {
            for (BrokerCsvParser p : parsers) {
                if (p.getBrokerName().equalsIgnoreCase(overrideBroker.trim())) {
                    return p;
                }
            }
            throw new IllegalArgumentException("Unsupported broker: " + overrideBroker);
        }

        if (csvContent == null || csvContent.isBlank()) {
            throw new IllegalArgumentException("CSV file content must not be empty");
        }

        String cleanContent = stripBom(csvContent);
        String[] lines = cleanContent.split("\r?\n");
        for (int i = 0; i < Math.min(8, lines.length); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> headers = FreetradeCsvParser.parseCsvLine(line);
            for (BrokerCsvParser p : parsers) {
                if (p.supports(headers)) {
                    return p;
                }
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

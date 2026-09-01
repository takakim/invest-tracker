package com.takakim.investtracker.service.csv;

import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InvestEngineCsvParser implements BrokerCsvParser {

    private static final DateTimeFormatter INVEST_ENGINE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss");

    @Override
    public String getBrokerName() {
        return "InvestEngine";
    }

    @Override
    public boolean supports(List<String> headerColumns) {
        if (headerColumns == null || headerColumns.size() < 4) {
            return false;
        }
        String full = String.join(" ", headerColumns).toLowerCase();
        return full.contains("security / isin") || (full.contains("security") && full.contains("total trade value"));
    }

    @Override
    public List<ParsedTransactionRow> parse(String csvContent) {
        List<ParsedTransactionRow> rows = new ArrayList<>();
        if (csvContent == null || csvContent.isBlank()) {
            return rows;
        }

        String[] lines = csvContent.split("\r?\n");
        if (lines.length <= 1) {
            return rows;
        }

        int headerIndex = 0;
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            List<String> cols = FreetradeCsvParser.parseCsvLine(lines[i]);
            if (supports(cols)) {
                headerIndex = i;
                break;
            }
        }

        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            List<String> cols = FreetradeCsvParser.parseCsvLine(line);
            if (cols.size() < 4) {
                continue;
            }

            int rowNumber = i + 1;
            try {
                ParsedTransactionRow parsed = parseRow(rowNumber, cols, line);
                if (parsed != null) {
                    rows.add(parsed);
                }
            } catch (Exception e) {
                rows.add(new ParsedTransactionRow(
                        rowNumber,
                        null,
                        col(cols, 1),
                        null,
                        col(cols, 0),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "GBP",
                        null,
                        null,
                        null,
                        null,
                        true,
                        "Error parsing InvestEngine row: " + e.getMessage(),
                        line
                ));
            }
        }
        return rows;
    }

    private ParsedTransactionRow parseRow(int rowNumber, List<String> cols, String rawLine) {
        String securityIsin = col(cols, 0).trim();
        String rawType = col(cols, 1).trim();
        BigDecimal quantity = parseDecimal(col(cols, 2));
        BigDecimal sharePrice = parseDecimal(col(cols, 3));
        BigDecimal totalValue = parseDecimal(col(cols, 4));
        String dateTimeStr = col(cols, 5).trim();
        String broker = col(cols, 7).trim();

        String name = securityIsin;
        String isin = null;
        if (securityIsin.contains("/ ISIN")) {
            String[] parts = securityIsin.split("/ ISIN:?");
            name = parts[0].trim();
            if (parts.length > 1) {
                isin = parts[1].trim();
            }
        }

        Instant timestamp = parseTimestamp(dateTimeStr);
        String currency = "GBP";

        TransactionType mappedType = null;
        if ("Buy".equalsIgnoreCase(rawType)) {
            mappedType = TransactionType.BUY;
        } else if ("Sell".equalsIgnoreCase(rawType)) {
            mappedType = TransactionType.SELL;
        } else if ("Dividend".equalsIgnoreCase(rawType)) {
            mappedType = TransactionType.DIVIDEND;
        } else if ("Stock Split".equalsIgnoreCase(rawType) || "STOCK_SPLIT".equalsIgnoreCase(rawType) || "Split".equalsIgnoreCase(rawType)) {
            mappedType = TransactionType.STOCK_SPLIT;
            sharePrice = null;
            totalValue = BigDecimal.ZERO;
        } else if ("Reverse Stock Split".equalsIgnoreCase(rawType) || "REVERSE_STOCK_SPLIT".equalsIgnoreCase(rawType)) {
            mappedType = TransactionType.REVERSE_STOCK_SPLIT;
            sharePrice = null;
            totalValue = BigDecimal.ZERO;
        }

        boolean isIgnored = mappedType == null;
        String ignoreReason = isIgnored ? "Unsupported InvestEngine transaction type: " + rawType : null;

        String ticker = CsvImportService.inferTicker(isin, name);

        return new ParsedTransactionRow(
                rowNumber,
                timestamp,
                rawType,
                mappedType,
                name,
                ticker,
                isin,
                currency,
                quantity,
                sharePrice,
                totalValue != null ? totalValue : BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                currency,
                BigDecimal.ONE,
                currency,
                "IE-" + rowNumber,
                "InvestEngine " + rawType + " - " + broker,
                isIgnored,
                ignoreReason,
                rawLine
        );
    }

    private Instant parseTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(timeStr, INVEST_ENGINE_DATE_FORMAT).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String col(List<String> cols, int idx) {
        if (idx < cols.size()) {
            return cols.get(idx).trim();
        }
        return "";
    }

    private BigDecimal parseDecimal(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        String clean = str.replace("£", "").replace("$", "").replace("€", "").replace(",", "").trim();
        try {
            return new BigDecimal(clean);
        } catch (Exception e) {
            return null;
        }
    }
}

package com.takakim.investtracker.service.csv;

import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class VanguardUkCsvParser implements BrokerCsvParser {

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd MMM yyyy").toFormatter(Locale.ENGLISH)
    };

    @Override
    public String getBrokerName() {
        return "Vanguard UK";
    }

    @Override
    public boolean supports(List<String> headerColumns) {
        if (headerColumns == null || headerColumns.size() < 4) {
            return false;
        }
        String full = String.join(" ", headerColumns).toLowerCase();
        return full.contains("investment")
                && (full.contains("isin") || full.contains("units") || full.contains("price") || full.contains("charges"));
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
        for (int i = 0; i < Math.min(6, lines.length); i++) {
            List<String> cols = FreetradeCsvParser.parseCsvLine(lines[i]);
            if (supports(cols)) {
                headerIndex = i;
                break;
            }
        }

        List<String> headerCols = FreetradeCsvParser.parseCsvLine(lines[headerIndex]);

        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            List<String> cols = FreetradeCsvParser.parseCsvLine(line);
            if (cols.size() < 3) {
                continue;
            }

            int rowNumber = i + 1;
            try {
                ParsedTransactionRow parsed = parseRow(rowNumber, headerCols, cols, line);
                rows.add(parsed);
            } catch (Exception e) {
                rows.add(new ParsedTransactionRow(
                        rowNumber,
                        null,
                        col(cols, 1),
                        null,
                        col(cols, 2),
                        null,
                        col(cols, 3),
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
                        "Error parsing Vanguard UK row: " + e.getMessage(),
                        line
                ));
            }
        }
        return rows;
    }

    private ParsedTransactionRow parseRow(int rowNumber, List<String> headerCols, List<String> cols, String rawLine) {
        String dateStr = col(cols, 0).trim();
        String rawType = col(cols, 1).trim();
        String investmentName = col(cols, 2).trim();
        String isin = col(cols, 3).trim();

        BigDecimal units = parseCleanDecimal(col(cols, 4));
        BigDecimal unitPrice = parseCleanDecimal(col(cols, 5));
        BigDecimal amount = parseCleanDecimal(col(cols, 6));
        BigDecimal charges = parseCleanDecimal(col(cols, 7));
        BigDecimal netAmount = cols.size() > 8 ? parseCleanDecimal(col(cols, 8)) : amount;

        Instant timestamp = parseDate(dateStr);
        TransactionType mappedType = mapType(rawType);

        if (mappedType == null) {
            return new ParsedTransactionRow(
                    rowNumber, timestamp, rawType, null, investmentName, null, isin,
                    "GBP", units, unitPrice, amount, charges, BigDecimal.ZERO, "GBP",
                    null, null, null, null, true, "Unrecognized Vanguard transaction type: " + rawType, rawLine
            );
        }

        // Infer ticker if possible
        String ticker = CsvImportService.inferTicker(isin, investmentName);
        if (ticker == null && investmentName != null) {
            String lower = investmentName.toLowerCase();
            if (lower.contains("s&p 500")) {
                ticker = "VUSA";
            } else if (lower.contains("all-world")) {
                ticker = "VWRP";
            } else if (lower.contains("lifestrategy 80")) {
                ticker = "V80A";
            }
        }

        BigDecimal fee = charges != null ? charges.abs() : BigDecimal.ZERO;
        BigDecimal gross = amount != null ? amount.abs() : (units != null && unitPrice != null ? units.multiply(unitPrice) : BigDecimal.ZERO);

        return new ParsedTransactionRow(
                rowNumber,
                timestamp,
                rawType,
                mappedType,
                investmentName.isEmpty() ? null : investmentName,
                ticker,
                isin.isEmpty() ? null : isin,
                "GBP",
                units != null ? units.abs() : null,
                unitPrice != null ? unitPrice.abs() : null,
                gross,
                fee,
                BigDecimal.ZERO,
                "GBP",
                null,
                null,
                null,
                null,
                false,
                null,
                rawLine
        );
    }

    private TransactionType mapType(String rawType) {
        String t = rawType.toLowerCase();
        if (t.contains("buy") || t.contains("purchase") || t.contains("reinvestment") || t.contains("invest")) {
            return TransactionType.BUY;
        }
        if (t.contains("sell") || t.contains("sale") || t.contains("redemption")) {
            return TransactionType.SELL;
        }
        if (t.contains("dividend") || t.contains("distribution") || t.contains("income")) {
            return TransactionType.DIVIDEND;
        }
        if (t.contains("interest")) {
            return TransactionType.INTEREST;
        }
        if (t.contains("deposit") || t.contains("contribution") || t.contains("cash in") || t.contains("direct debit")) {
            return TransactionType.DEPOSIT;
        }
        if (t.contains("withdrawal") || t.contains("cash out") || t.contains("transfer out")) {
            return TransactionType.WITHDRAWAL;
        }
        if (t.contains("fee") || t.contains("charge") || t.contains("account fee")) {
            return TransactionType.FEE;
        }
        return null;
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                LocalDate d = LocalDate.parse(dateStr.trim(), fmt);
                return d.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("Cannot parse Vanguard date: " + dateStr);
    }

    private BigDecimal parseCleanDecimal(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        String clean = val.replace("£", "")
                .replace("$", "")
                .replace("€", "")
                .replace(",", "")
                .replace("p", "")
                .trim();
        if (clean.isEmpty() || "-".equals(clean)) {
            return null;
        }
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String col(List<String> cols, int index) {
        if (index < cols.size()) {
            return cols.get(index) != null ? cols.get(index).trim() : "";
        }
        return "";
    }
}

package com.takakim.investtracker.service.csv;

import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AjBellCsvParser implements BrokerCsvParser {

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    @Override
    public String getBrokerName() {
        return "AJ Bell";
    }

    @Override
    public boolean supports(List<String> headerColumns) {
        if (headerColumns == null || headerColumns.size() < 4) {
            return false;
        }
        String full = String.join(" ", headerColumns).toLowerCase();
        return (full.contains("net value") || (full.contains("charges") && full.contains("security")))
                && (full.contains("isin") || full.contains("ticker") || full.contains("sedol") || full.contains("transaction"));
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

        List<String> headers = FreetradeCsvParser.parseCsvLine(lines[headerIndex]);
        int dateIdx = Math.max(0, findCol(headers, "date"));
        int typeIdx = Math.max(1, findCol(headers, "transaction", "type", "description"));
        int secIdx = Math.max(2, findCol(headers, "security", "investment", "name"));
        int tickerIdx = Math.max(3, findCol(headers, "ticker", "symbol", "sedol"));
        int isinIdx = Math.max(4, findCol(headers, "isin"));
        int qtyIdx = Math.max(5, findCol(headers, "quantity", "units", "shares"));
        int priceIdx = Math.max(6, findCol(headers, "price", "unit price"));
        int valueIdx = Math.max(7, findCol(headers, "value", "amount", "gross"));
        int feeIdx = Math.max(8, findCol(headers, "charges", "commission", "fee"));

        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> cols = FreetradeCsvParser.parseCsvLine(line);
            if (cols.size() < 3) continue;

            int rowNumber = i + 1;
            try {
                String dateStr = col(cols, dateIdx);
                String rawType = col(cols, typeIdx);
                String security = col(cols, secIdx);
                String ticker = col(cols, tickerIdx);
                String isin = col(cols, isinIdx);
                BigDecimal qty = parseDecimal(col(cols, qtyIdx));
                BigDecimal price = parseDecimal(col(cols, priceIdx));
                BigDecimal val = parseDecimal(col(cols, valueIdx));
                BigDecimal fee = parseDecimal(col(cols, feeIdx));

                Instant timestamp = parseDate(dateStr);
                TransactionType type = mapType(rawType, qty);

                BigDecimal feeVal = fee != null ? fee.abs() : BigDecimal.ZERO;
                BigDecimal gross = val != null ? val.abs() : (qty != null && price != null ? qty.abs().multiply(price) : BigDecimal.ZERO);

                rows.add(new ParsedTransactionRow(
                        rowNumber,
                        timestamp,
                        rawType,
                        type,
                        security.isEmpty() ? null : security,
                        ticker.isEmpty() ? null : ticker,
                        isin.isEmpty() ? null : isin,
                        "GBP",
                        qty != null ? qty.abs() : null,
                        price != null ? price.abs() : null,
                        gross,
                        feeVal,
                        BigDecimal.ZERO,
                        "GBP",
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        line
                ));
            } catch (Exception e) {
                rows.add(new ParsedTransactionRow(
                        rowNumber, null, col(cols, 1), null, col(cols, 2), null, null,
                        "GBP", null, null, null, null, null, "GBP", null, null, null, null,
                        true, "Error parsing AJ Bell row: " + e.getMessage(), line
                ));
            }
        }
        return rows;
    }

    private TransactionType mapType(String rawType, BigDecimal qty) {
        String t = rawType.toLowerCase();
        if (t.equals("b") || t.contains("buy") || t.contains("purchase")) return TransactionType.BUY;
        if (t.equals("s") || t.contains("sell") || t.contains("sale")) return TransactionType.SELL;
        if (t.contains("dividend") || t.contains("income") || t.contains("distribution")) return TransactionType.DIVIDEND;
        if (t.contains("subscription") || t.contains("deposit") || t.contains("cash in")) return TransactionType.DEPOSIT;
        if (t.contains("withdrawal") || t.contains("transfer out") || t.contains("cash out")) return TransactionType.WITHDRAWAL;
        if (t.contains("fee") || t.contains("charge") || t.contains("custody")) return TransactionType.FEE;
        if (t.contains("interest")) return TransactionType.INTEREST;
        if (qty != null && qty.compareTo(BigDecimal.ZERO) < 0) return TransactionType.SELL;
        return TransactionType.BUY;
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                LocalDate ld = LocalDate.parse(dateStr.trim(), fmt);
                return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("Cannot parse AJ Bell date: " + dateStr);
    }

    private int findCol(List<String> cols, String... names) {
        for (int i = 0; i < cols.size(); i++) {
            String c = cols.get(i).trim().toLowerCase();
            for (String n : names) {
                if (c.contains(n.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private BigDecimal parseDecimal(String val) {
        if (val == null || val.isBlank() || "-".equals(val.trim())) return null;
        try {
            return new BigDecimal(val.replace("£", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String col(List<String> cols, int index) {
        if (index >= 0 && index < cols.size()) {
            return cols.get(index) != null ? cols.get(index).trim() : "";
        }
        return "";
    }
}

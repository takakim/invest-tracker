package com.takakim.investtracker.service.csv;

import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DegiroCsvParser implements BrokerCsvParser {

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private static final DateTimeFormatter[] TIME_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm")
    };

    @Override
    public String getBrokerName() {
        return "DEGIRO";
    }

    @Override
    public boolean supports(List<String> headerColumns) {
        if (headerColumns == null || headerColumns.size() < 4) {
            return false;
        }
        String full = String.join(" ", headerColumns).toLowerCase();
        return (full.contains("product") || full.contains("produkt"))
                && (full.contains("isin") || full.contains("beurs") || full.contains("venue"))
                && (full.contains("waarde") || full.contains("kurs") || full.contains("wisselkoers") || full.contains("value") || full.contains("rate"));
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

        List<String> headers = FreetradeCsvParser.parseCsvLine(lines[headerIndex]);
        int dateIdx = Math.max(0, findCol(headers, "date", "datum"));
        int timeIdx = Math.max(1, findCol(headers, "time", "tijd", "uhrzeit"));
        int prodIdx = Math.max(2, findCol(headers, "product", "produkt"));
        int isinIdx = Math.max(3, findCol(headers, "isin"));
        int refIdx = Math.max(4, findCol(headers, "reference", "referentie", "referenz", "order id"));
        int qtyIdx = Math.max(6, findCol(headers, "quantity", "aantal", "anzahl"));
        int priceIdx = Math.max(7, findCol(headers, "price", "koers", "kurs"));
        int feeIdx = Math.max(11, findCol(headers, "fee", "kosten", "gebühren", "transaction and/or"));
        int totalIdx = Math.max(12, findCol(headers, "total", "totaal", "gesamt", "value", "waarde"));
        int fxIdx = Math.max(10, findCol(headers, "exchange rate", "wisselkoers", "wechselkurs"));

        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> cols = FreetradeCsvParser.parseCsvLine(line);
            if (cols.size() < 4) continue;

            int rowNumber = i + 1;
            try {
                String dateStr = col(cols, dateIdx);
                String timeStr = col(cols, timeIdx);
                String product = col(cols, prodIdx);
                String isin = col(cols, isinIdx);
                String ref = col(cols, refIdx);
                BigDecimal qty = parseDecimal(col(cols, qtyIdx));
                BigDecimal price = parseDecimal(col(cols, priceIdx));
                BigDecimal fee = parseDecimal(col(cols, feeIdx));
                BigDecimal total = parseDecimal(col(cols, totalIdx));
                BigDecimal fxRate = parseDecimal(col(cols, fxIdx));

                Instant timestamp = parseDateTime(dateStr, timeStr);
                TransactionType type = determineType(product, qty, total);

                BigDecimal gross = total != null ? total.abs() : (qty != null && price != null ? qty.abs().multiply(price) : BigDecimal.ZERO);
                BigDecimal feeVal = fee != null ? fee.abs() : BigDecimal.ZERO;

                // Extract currency from product or total
                String currency = "EUR";
                if (product.toUpperCase().contains("GBP") || line.contains("£")) currency = "GBP";
                else if (product.toUpperCase().contains("USD") || line.contains("$")) currency = "USD";

                rows.add(new ParsedTransactionRow(
                        rowNumber,
                        timestamp,
                        type.name(),
                        type,
                        product,
                        null,
                        isin.isEmpty() ? null : isin,
                        currency,
                        qty != null ? qty.abs() : null,
                        price != null ? price.abs() : null,
                        gross,
                        feeVal,
                        BigDecimal.ZERO,
                        currency,
                        fxRate,
                        null,
                        ref.isEmpty() ? null : ref,
                        null,
                        false,
                        null,
                        line
                ));
            } catch (Exception e) {
                rows.add(new ParsedTransactionRow(
                        rowNumber, null, col(cols, 2), null, col(cols, 2), null, col(cols, 3),
                        "EUR", null, null, null, null, null, "EUR", null, null, null, null,
                        true, "Error parsing DEGIRO row: " + e.getMessage(), line
                ));
            }
        }
        return rows;
    }

    private TransactionType determineType(String product, BigDecimal qty, BigDecimal total) {
        String p = product.toLowerCase();
        if (p.contains("dividend") || p.contains("dividende") || p.contains("coupon")) {
            return TransactionType.DIVIDEND;
        }
        if (p.contains("rente") || p.contains("interest") || p.contains("zinsen")) {
            return TransactionType.INTEREST;
        }
        if (p.contains("terugstorting") || p.contains("auszahlung") || p.contains("withdrawal") || p.contains("cash sweep")) {
            return TransactionType.WITHDRAWAL;
        }
        if (p.contains("flatex") || p.contains("storting") || p.contains("einzahlung") || p.contains("deposit")) {
            return TransactionType.DEPOSIT;
        }
        if (p.contains("fee") || p.contains("kosten") || p.contains("aansluiting") || p.contains("tax")) {
            return TransactionType.FEE;
        }
        if (qty != null && qty.compareTo(BigDecimal.ZERO) < 0) {
            return TransactionType.SELL;
        }
        if (qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
            return TransactionType.BUY;
        }
        if (total != null && total.compareTo(BigDecimal.ZERO) > 0) {
            return TransactionType.SELL;
        }
        return TransactionType.BUY;
    }

    private Instant parseDateTime(String dateStr, String timeStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        for (DateTimeFormatter dFmt : DATE_FORMATTERS) {
            try {
                LocalDate ld = LocalDate.parse(dateStr.trim(), dFmt);
                if (timeStr != null && !timeStr.isBlank()) {
                    for (DateTimeFormatter tFmt : TIME_FORMATTERS) {
                        try {
                            LocalDateTime ldt = ld.atTime(java.time.LocalTime.parse(timeStr.trim(), tFmt));
                            return ldt.toInstant(ZoneOffset.UTC);
                        } catch (Exception ignored) {
                        }
                    }
                }
                return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("Cannot parse DEGIRO date: " + dateStr);
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
        if (val == null || val.isBlank() || "-".equals(val.trim())) {
            return null;
        }
        try {
            // Handle European number formats (1.234,56 or 1234,56)
            String clean = val.replace("€", "").replace("£", "").replace("$", "").trim();
            if (clean.contains(",") && clean.contains(".")) {
                if (clean.indexOf(".") < clean.indexOf(",")) {
                    clean = clean.replace(".", "").replace(",", ".");
                } else {
                    clean = clean.replace(",", "");
                }
            } else if (clean.contains(",")) {
                clean = clean.replace(",", ".");
            }
            return new BigDecimal(clean);
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

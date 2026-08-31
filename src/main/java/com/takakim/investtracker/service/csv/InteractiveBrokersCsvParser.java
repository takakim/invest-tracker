package com.takakim.investtracker.service.csv;

import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class InteractiveBrokersCsvParser implements BrokerCsvParser {

    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyyMMdd;HHmmss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    @Override
    public String getBrokerName() {
        return "Interactive Brokers";
    }

    @Override
    public boolean supports(List<String> headerColumns) {
        if (headerColumns == null || headerColumns.isEmpty()) {
            return false;
        }
        String full = String.join(" ", headerColumns).toLowerCase();
        return full.contains("datadiscriminator")
                || (full.contains("trades") && full.contains("header"))
                || (full.contains("proceeds") && full.contains("comm"))
                || (full.contains("conid") && full.contains("security id"));
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

        boolean isStatementFormat = false;
        int tradeHeaderIndex = -1;
        List<String> tradeHeaders = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> cols = FreetradeCsvParser.parseCsvLine(line);
            if (cols.size() >= 2 && "Header".equalsIgnoreCase(cols.get(1))) {
                isStatementFormat = true;
                if ("Trades".equalsIgnoreCase(cols.get(0))) {
                    tradeHeaderIndex = i;
                    tradeHeaders = cols;
                    break;
                }
            }
        }

        if (isStatementFormat) {
            parseStatementFormat(lines, tradeHeaders != null ? tradeHeaders : List.of(), rows);
        } else {
            parseStandardFormat(lines, rows);
        }

        return rows;
    }

    private void parseStatementFormat(String[] lines, List<String> tradeHeaders, List<ParsedTransactionRow> rows) {
        int symbolIdx = findCol(tradeHeaders, "Symbol");
        if (symbolIdx < 0) symbolIdx = 5;
        int dateIdx = findCol(tradeHeaders, "Date/Time", "Date");
        if (dateIdx < 0) dateIdx = 6;
        int qtyIdx = findCol(tradeHeaders, "Quantity");
        if (qtyIdx < 0) qtyIdx = 7;
        int priceIdx = findCol(tradeHeaders, "T. Price", "Price");
        if (priceIdx < 0) priceIdx = 8;
        int proceedsIdx = findCol(tradeHeaders, "Proceeds");
        if (proceedsIdx < 0) proceedsIdx = 10;
        int commIdx = findCol(tradeHeaders, "Comm/Fee", "Commission");
        if (commIdx < 0) commIdx = 11;
        int currencyIdx = findCol(tradeHeaders, "Currency");
        if (currencyIdx < 0) currencyIdx = 4;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> cols = FreetradeCsvParser.parseCsvLine(line);
            if (cols.size() < 3) continue;

            String section = cols.get(0).trim();
            String discriminator = cols.get(1).trim();

            if ("Data".equalsIgnoreCase(discriminator)) {
                int rowNumber = i + 1;
                try {
                    if ("Trades".equalsIgnoreCase(section)) {
                        ParsedTransactionRow row = parseStatementTrade(rowNumber, cols, symbolIdx, dateIdx, qtyIdx, priceIdx, proceedsIdx, commIdx, currencyIdx, line);
                        if (row != null) rows.add(row);
                    } else if (section.contains("Deposits") || section.contains("Withdrawals") || section.contains("Cash Report")) {
                        ParsedTransactionRow row = parseStatementCash(rowNumber, cols, line);
                        if (row != null) rows.add(row);
                    } else if (section.contains("Dividends")) {
                        ParsedTransactionRow row = parseStatementDividend(rowNumber, cols, line);
                        if (row != null) rows.add(row);
                    }
                } catch (Exception e) {
                    rows.add(new ParsedTransactionRow(
                            rowNumber, null, section, null, null, null, null,
                            "USD", null, null, null, null, null, "USD", null, null, null, null,
                            true, "Error parsing IBKR statement row: " + e.getMessage(), line
                    ));
                }
            }
        }
    }

    private ParsedTransactionRow parseStatementTrade(
            int rowNumber, List<String> cols, int symbolIdx, int dateIdx,
            int qtyIdx, int priceIdx, int proceedsIdx, int commIdx, int currencyIdx, String rawLine) {

        String symbol = col(cols, symbolIdx);
        String dateStr = col(cols, dateIdx);
        BigDecimal qty = parseDecimal(col(cols, qtyIdx));
        BigDecimal price = parseDecimal(col(cols, priceIdx));
        BigDecimal proceeds = parseDecimal(col(cols, proceedsIdx));
        BigDecimal comm = parseDecimal(col(cols, commIdx));
        String currency = col(cols, currencyIdx);
        if (currency.isEmpty()) currency = "USD";

        Instant timestamp = parseDateTime(dateStr);
        TransactionType type = (qty != null && qty.compareTo(BigDecimal.ZERO) < 0) ? TransactionType.SELL : TransactionType.BUY;
        BigDecimal fee = comm != null ? comm.abs() : BigDecimal.ZERO;
        BigDecimal gross = proceeds != null ? proceeds.abs() : (qty != null && price != null ? qty.abs().multiply(price) : BigDecimal.ZERO);

        return new ParsedTransactionRow(
                rowNumber,
                timestamp,
                type.name(),
                type,
                symbol,
                symbol,
                null,
                currency,
                qty != null ? qty.abs() : null,
                price != null ? price.abs() : null,
                gross,
                fee,
                BigDecimal.ZERO,
                currency,
                null,
                null,
                null,
                null,
                false,
                null,
                rawLine
        );
    }

    private ParsedTransactionRow parseStatementCash(int rowNumber, List<String> cols, String rawLine) {
        String currency = "USD";
        String dateStr = null;
        BigDecimal amount = null;

        for (int i = 2; i < cols.size(); i++) {
            String val = cols.get(i).trim();
            if (val.isEmpty()) continue;
            if (currency.equals("USD") && val.matches("^[A-Za-z]{3}$")) {
                currency = val.toUpperCase();
            } else if (dateStr == null && (val.contains("-") || val.contains("/"))) {
                dateStr = val;
            } else if (amount == null) {
                BigDecimal d = parseDecimal(val);
                if (d != null) amount = d;
            }
        }

        if (amount == null || dateStr == null) return null;

        Instant timestamp = parseDateTime(dateStr);
        TransactionType type = amount.compareTo(BigDecimal.ZERO) >= 0 ? TransactionType.DEPOSIT : TransactionType.WITHDRAWAL;

        return new ParsedTransactionRow(
                rowNumber,
                timestamp,
                type.name(),
                type,
                "Cash " + type.name(),
                null,
                null,
                currency,
                null,
                null,
                amount.abs(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                currency,
                null,
                null,
                null,
                null,
                false,
                null,
                rawLine
        );
    }

    private ParsedTransactionRow parseStatementDividend(int rowNumber, List<String> cols, String rawLine) {
        String currency = "USD";
        String symbol = null;
        String dateStr = null;
        BigDecimal amount = null;

        for (int i = 2; i < cols.size(); i++) {
            String val = cols.get(i).trim();
            if (val.isEmpty()) continue;
            if (currency.equals("USD") && val.matches("^[A-Za-z]{3}$") && !val.equalsIgnoreCase("DIV")) {
                currency = val.toUpperCase();
            } else if (symbol == null && !val.equalsIgnoreCase("DIV") && val.matches("^[A-Za-z0-9.]{1,10}$")) {
                symbol = val.toUpperCase();
            } else if (dateStr == null && (val.contains("-") || val.contains("/"))) {
                dateStr = val;
            } else if (amount == null) {
                BigDecimal d = parseDecimal(val);
                if (d != null) amount = d;
            }
        }

        if (amount == null || dateStr == null) return null;

        Instant timestamp = parseDateTime(dateStr);
        return new ParsedTransactionRow(
                rowNumber,
                timestamp,
                "DIVIDEND",
                TransactionType.DIVIDEND,
                symbol,
                symbol,
                null,
                currency,
                null,
                null,
                amount.abs(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                currency,
                null,
                null,
                null,
                null,
                false,
                null,
                rawLine
        );
    }

    private void parseStandardFormat(String[] lines, List<ParsedTransactionRow> rows) {
        int headerIndex = 0;
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            List<String> cols = FreetradeCsvParser.parseCsvLine(lines[i]);
            if (supports(cols)) {
                headerIndex = i;
                break;
            }
        }

        List<String> headers = FreetradeCsvParser.parseCsvLine(lines[headerIndex]);
        int symbolIdx = Math.max(0, findCol(headers, "Symbol", "Ticker"));
        int dateIdx = findCol(headers, "Date/Time");
        if (dateIdx < 0) dateIdx = findCol(headers, "Date");
        dateIdx = Math.max(1, dateIdx);
        int typeIdx = Math.max(2, findCol(headers, "Type", "Action", "Description"));
        int qtyIdx = Math.max(3, findCol(headers, "Quantity", "Shares"));
        int priceIdx = Math.max(4, findCol(headers, "Price", "T. Price"));
        int amountIdx = findCol(headers, "Amount");
        if (amountIdx < 0) amountIdx = findCol(headers, "Proceeds");
        amountIdx = Math.max(5, amountIdx);
        int commIdx = findCol(headers, "Commission");
        if (commIdx < 0) commIdx = findCol(headers, "Fee", "Comm/Fee");
        commIdx = Math.max(6, commIdx);
        int currIdx = Math.max(7, findCol(headers, "Currency", "Ccy"));

        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            List<String> cols = FreetradeCsvParser.parseCsvLine(line);
            if (cols.size() < 3) continue;

            int rowNumber = i + 1;
            try {
                String symbol = col(cols, symbolIdx);
                String dateStr = col(cols, dateIdx);
                String rawType = col(cols, typeIdx);
                BigDecimal qty = parseDecimal(col(cols, qtyIdx));
                BigDecimal price = parseDecimal(col(cols, priceIdx));
                BigDecimal amount = parseDecimal(col(cols, amountIdx));
                BigDecimal comm = parseDecimal(col(cols, commIdx));
                String currency = col(cols, currIdx);
                if (currency.isEmpty()) currency = "USD";

                Instant timestamp = parseDateTime(dateStr);
                TransactionType type = mapStandardType(rawType, qty);
                BigDecimal fee = comm != null ? comm.abs() : BigDecimal.ZERO;
                BigDecimal gross = amount != null ? amount.abs() : (qty != null && price != null ? qty.abs().multiply(price) : BigDecimal.ZERO);

                rows.add(new ParsedTransactionRow(
                        rowNumber,
                        timestamp,
                        rawType,
                        type,
                        symbol,
                        symbol,
                        null,
                        currency,
                        qty != null ? qty.abs() : null,
                        price != null ? price.abs() : null,
                        gross,
                        fee,
                        BigDecimal.ZERO,
                        currency,
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
                        rowNumber, null, col(cols, 0), null, null, null, null,
                        "USD", null, null, null, null, null, "USD", null, null, null, null,
                        true, "Error parsing IBKR row: " + e.getMessage(), line
                ));
            }
        }
    }

    private TransactionType mapStandardType(String rawType, BigDecimal qty) {
        String t = rawType.toLowerCase();
        if (t.contains("buy") || t.contains("bot")) return TransactionType.BUY;
        if (t.contains("sell") || t.contains("sld")) return TransactionType.SELL;
        if (t.contains("dividend")) return TransactionType.DIVIDEND;
        if (t.contains("deposit")) return TransactionType.DEPOSIT;
        if (t.contains("withdrawal")) return TransactionType.WITHDRAWAL;
        if (qty != null && qty.compareTo(BigDecimal.ZERO) < 0) return TransactionType.SELL;
        return TransactionType.BUY;
    }

    private Instant parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        for (DateTimeFormatter fmt : DATE_TIME_FORMATTERS) {
            try {
                if (dateStr.contains(":") || dateStr.contains(";")) {
                    LocalDateTime ldt = LocalDateTime.parse(dateStr.trim(), fmt);
                    return ldt.toInstant(ZoneOffset.UTC);
                } else {
                    LocalDate ld = LocalDate.parse(dateStr.trim(), fmt);
                    return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
                }
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("Cannot parse IBKR date: " + dateStr);
    }

    private int findCol(List<String> cols, String... targets) {
        for (String target : targets) {
            String t = target.toLowerCase();
            for (int i = 0; i < cols.size(); i++) {
                if (cols.get(i).trim().toLowerCase().contains(t)) {
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
            return new BigDecimal(val.replace(",", "").replace("$", "").replace("£", "").trim());
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

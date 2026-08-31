package com.takakim.investtracker.service.csv;

import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Trading212CsvParser implements BrokerCsvParser {

    private static final DateTimeFormatter T212_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[XXX][X]");

    @Override
    public String getBrokerName() {
        return "Trading 212";
    }

    @Override
    public boolean supports(List<String> headerColumns) {
        if (headerColumns == null || headerColumns.size() < 8) {
            return false;
        }
        String h0 = headerColumns.get(0).trim().toLowerCase();
        String h1 = headerColumns.get(1).trim().toLowerCase();
        String h2 = headerColumns.get(2).trim().toLowerCase();
        String h3 = headerColumns.get(3).trim().toLowerCase();
        return h0.contains("action") && h1.contains("time") && h2.contains("isin") && h3.contains("ticker");
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

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            List<String> cols = FreetradeCsvParser.parseCsvLine(line);
            if (cols.size() < 2) {
                continue;
            }

            int rowNumber = i + 1;
            try {
                ParsedTransactionRow parsed = parseRow(rowNumber, cols, line);
                rows.add(parsed);
            } catch (Exception e) {
                rows.add(new ParsedTransactionRow(
                        rowNumber,
                        null,
                        col(cols, 0),
                        null,
                        col(cols, 4),
                        col(cols, 3),
                        col(cols, 2),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        col(cols, 14),
                        null,
                        null,
                        col(cols, 6),
                        null,
                        true,
                        "Error parsing Trading 212 row: " + e.getMessage(),
                        line
                ));
            }
        }
        return rows;
    }

    private ParsedTransactionRow parseRow(int rowNumber, List<String> cols, String rawLine) {
        String action = col(cols, 0).trim();
        String timeStr = col(cols, 1).trim();
        String isin = col(cols, 2).trim();
        String ticker = col(cols, 3).trim();
        String name = col(cols, 4).trim();
        String notes = col(cols, 5).trim();
        String orderId = col(cols, 6).trim();
        BigDecimal noOfShares = parseDecimal(col(cols, 7));
        BigDecimal pricePerShare = parseDecimal(col(cols, 8));
        String priceCurrency = col(cols, 9).trim();
        BigDecimal exchangeRate = parseDecimal(col(cols, 10));
        BigDecimal total = parseDecimal(col(cols, 13));
        String totalCurrency = col(cols, 14).trim();
        BigDecimal withholdingTax = parseDecimal(col(cols, 15));
        BigDecimal stampDuty = parseDecimal(col(cols, 17));
        BigDecimal conversionFee = parseDecimal(col(cols, 19));

        Instant timestamp = parseTimestamp(timeStr);

        // Normalize GBX (pence sterling) to GBP
        String instrumentCurrency = priceCurrency;
        BigDecimal adjustedPrice = pricePerShare;
        if ("GBX".equalsIgnoreCase(priceCurrency)) {
            instrumentCurrency = "GBP";
            if (adjustedPrice != null) {
                adjustedPrice = adjustedPrice.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            }
        }

        BigDecimal fee = (stampDuty != null ? stampDuty : BigDecimal.ZERO)
                .add(conversionFee != null ? conversionFee : BigDecimal.ZERO);

        String accountCurrency = !totalCurrency.isEmpty() ? totalCurrency : "GBP";

        // 1. DEPOSIT / TOP UP
        if ("Deposit".equalsIgnoreCase(action) || "Top up".equalsIgnoreCase(action)) {
            return new ParsedTransactionRow(
                    rowNumber, timestamp, action, TransactionType.DEPOSIT, "Deposit", null, null,
                    null, null, null, total != null ? total : BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, accountCurrency, null, null,
                    orderId, notes.isEmpty() ? "Trading 212 Deposit" : notes, false, null, rawLine
            );
        }

        // 2. WITHDRAWAL
        if ("Withdrawal".equalsIgnoreCase(action)) {
            return new ParsedTransactionRow(
                    rowNumber, timestamp, action, TransactionType.WITHDRAWAL, "Withdrawal", null, null,
                    null, null, null, total != null ? total : BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, accountCurrency, null, null,
                    orderId, notes.isEmpty() ? "Trading 212 Withdrawal" : notes, false, null, rawLine
            );
        }

        // 3. INTEREST ON CASH
        if (action.startsWith("Interest")) {
            return new ParsedTransactionRow(
                    rowNumber, timestamp, action, TransactionType.INTEREST, "Cash Interest", null, null,
                    null, null, null, total != null ? total : BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, accountCurrency, null, null,
                    orderId, "Trading 212 Cash Interest", false, null, rawLine
            );
        }

        // 4. DIVIDEND
        if (action.startsWith("Dividend")) {
            BigDecimal netDiv = total != null ? total : BigDecimal.ZERO;
            BigDecimal tax = withholdingTax != null ? withholdingTax : BigDecimal.ZERO;
            BigDecimal grossDiv = netDiv.add(tax);
            return new ParsedTransactionRow(
                    rowNumber, timestamp, action, TransactionType.DIVIDEND, name.isEmpty() ? ticker : name, ticker, isin,
                    instrumentCurrency, noOfShares, null, grossDiv,
                    fee, tax,
                    accountCurrency, exchangeRate, instrumentCurrency,
                    orderId, "Trading 212 Dividend", false, null, rawLine
            );
        }

        // 5. BUY ORDERS
        if (action.toLowerCase().contains("buy")) {
            BigDecimal netOutlay = total != null ? total : BigDecimal.ZERO;
            BigDecimal grossTrade = netOutlay.subtract(fee).max(BigDecimal.ZERO);
            return new ParsedTransactionRow(
                    rowNumber, timestamp, action, TransactionType.BUY, name.isEmpty() ? ticker : name, ticker, isin,
                    instrumentCurrency, noOfShares, adjustedPrice, grossTrade,
                    fee, BigDecimal.ZERO, accountCurrency, exchangeRate, instrumentCurrency,
                    orderId, "Trading 212 Order " + orderId, false, null, rawLine
            );
        }

        // 6. SELL ORDERS
        if (action.toLowerCase().contains("sell")) {
            BigDecimal netProceeds = total != null ? total : BigDecimal.ZERO;
            BigDecimal grossTrade = netProceeds.add(fee);
            return new ParsedTransactionRow(
                    rowNumber, timestamp, action, TransactionType.SELL, name.isEmpty() ? ticker : name, ticker, isin,
                    instrumentCurrency, noOfShares, adjustedPrice, grossTrade,
                    fee, BigDecimal.ZERO, accountCurrency, exchangeRate, instrumentCurrency,
                    orderId, "Trading 212 Order " + orderId, false, null, rawLine
            );
        }

        // 7. SPIN OFF
        if (action.startsWith("Spin off")) {
            return new ParsedTransactionRow(
                    rowNumber, timestamp, action, TransactionType.STOCK_SPLIT, name.isEmpty() ? ticker : name, ticker, isin,
                    instrumentCurrency, noOfShares, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, accountCurrency, null, instrumentCurrency,
                    orderId, "Corporate Action: Spin Off Allotment", false, null, rawLine
            );
        }

        // 8. STOCK SPLIT OPEN / CLOSE
        if (action.startsWith("Stock split")) {
            return new ParsedTransactionRow(
                    rowNumber, timestamp, action, null, name, ticker, isin,
                    instrumentCurrency, noOfShares, adjustedPrice, total != null ? total : BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, accountCurrency, exchangeRate, instrumentCurrency,
                    orderId, "Corporate Stock Split Marker (Ignored)", true, "Stock split internal marker", rawLine
            );
        }

        // Default unsupported action
        return new ParsedTransactionRow(
                rowNumber, timestamp, action, null, name, ticker, isin,
                instrumentCurrency, noOfShares, adjustedPrice, total != null ? total : BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, accountCurrency, exchangeRate, instrumentCurrency,
                orderId, null, true, "Unsupported Trading 212 action: " + action, rawLine
        );
    }

    private Instant parseTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return Instant.now();
        }
        try {
            if (timeStr.contains("+") || timeStr.endsWith("Z")) {
                return Instant.parse(timeStr.replace(" ", "T"));
            }
            return LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .toInstant(ZoneOffset.UTC);
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
        try {
            return new BigDecimal(str.replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }
}

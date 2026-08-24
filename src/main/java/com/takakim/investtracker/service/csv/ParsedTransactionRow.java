package com.takakim.investtracker.service.csv;

import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record ParsedTransactionRow(
        int rowNumber,
        Instant timestamp,
        String rawType,
        TransactionType mappedType,
        String instrumentTitle,
        String ticker,
        String isin,
        String instrumentCurrency,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        String currency,
        BigDecimal fxRate,
        String counterCurrency,
        String externalReference,
        String notes,
        boolean isIgnored,
        String ignoreReason,
        String rawLine
) {}

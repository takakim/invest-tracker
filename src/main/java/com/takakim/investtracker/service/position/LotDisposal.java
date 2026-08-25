package com.takakim.investtracker.service.position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Records the consumption and realized profit/loss of an individual lot during a disposal (e.g. SELL).
 */
public record LotDisposal(
        UUID disposalTransactionId,
        UUID lotId,
        Instant disposalDate,
        BigDecimal disposedQuantity,
        BigDecimal disposedCostBasis,
        BigDecimal proceeds,
        BigDecimal realizedGainLoss,
        String currency
) {
    public LotDisposal {
        Objects.requireNonNull(disposalTransactionId, "Disposal transaction ID must not be null");
        Objects.requireNonNull(lotId, "Lot ID must not be null");
        Objects.requireNonNull(disposalDate, "Disposal date must not be null");
        Objects.requireNonNull(disposedQuantity, "Disposed quantity must not be null");
        Objects.requireNonNull(disposedCostBasis, "Disposed cost basis must not be null");
        Objects.requireNonNull(proceeds, "Proceeds must not be null");
        Objects.requireNonNull(realizedGainLoss, "Realized gain/loss must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
    }
}

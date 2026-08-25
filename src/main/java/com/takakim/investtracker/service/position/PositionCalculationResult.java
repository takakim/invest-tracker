package com.takakim.investtracker.service.position;

import com.takakim.investtracker.domain.CostBasisMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of deriving position holding, open tax lots, and realized performance from transaction history.
 */
public record PositionCalculationResult(
        UUID accountId,
        UUID instrumentId,
        CostBasisMethod costBasisMethod,
        BigDecimal quantity,
        BigDecimal costBasisAmount,
        String costBasisCurrency,
        BigDecimal averageUnitCost,
        BigDecimal realizedGainLossAmount,
        List<PositionLot> openLots,
        List<LotDisposal> disposals
) {
    public PositionCalculationResult {
        Objects.requireNonNull(accountId, "Account ID must not be null");
        Objects.requireNonNull(instrumentId, "Instrument ID must not be null");
        Objects.requireNonNull(costBasisMethod, "Cost basis method must not be null");
        Objects.requireNonNull(quantity, "Quantity must not be null");
        openLots = openLots != null ? List.copyOf(openLots) : List.of();
        disposals = disposals != null ? List.copyOf(disposals) : List.of();
    }
}

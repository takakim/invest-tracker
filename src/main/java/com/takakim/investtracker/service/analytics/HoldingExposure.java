package com.takakim.investtracker.service.analytics;

import com.takakim.investtracker.domain.AssetClass;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record HoldingExposure(
        UUID instrumentId,
        String instrumentName,
        String ticker,
        AssetClass assetClass,
        BigDecimal quantity,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedGainLoss,
        BigDecimal weightPercentage,
        String currency
) {
    public HoldingExposure {
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(instrumentName, "instrumentName must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(currentPrice, "currentPrice must not be null");
        Objects.requireNonNull(marketValue, "marketValue must not be null");
        Objects.requireNonNull(costBasis, "costBasis must not be null");
        Objects.requireNonNull(unrealizedGainLoss, "unrealizedGainLoss must not be null");
        Objects.requireNonNull(weightPercentage, "weightPercentage must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }
}

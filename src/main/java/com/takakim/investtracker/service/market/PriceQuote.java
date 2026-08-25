package com.takakim.investtracker.service.market;

import com.takakim.investtracker.domain.ObservationSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PriceQuote(
        UUID instrumentId,
        BigDecimal price,
        String currency,
        Instant asOf,
        ObservationSourceType sourceType,
        String sourceReference,
        boolean isStale,
        String warning
) {
    public PriceQuote {
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
    }
}

package com.takakim.investtracker.service.currency;

import com.takakim.investtracker.domain.ObservationSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record FxRateQuote(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        Instant asOf,
        ObservationSourceType sourceType,
        String sourceReference,
        boolean isDerived,
        String warning
) {
    public FxRateQuote {
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("FX rate must be strictly positive");
        }
        Objects.requireNonNull(asOf, "asOf must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
    }
}

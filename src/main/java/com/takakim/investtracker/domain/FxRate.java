package com.takakim.investtracker.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record FxRate(Currency source, Currency target, BigDecimal rate) {
    public FxRate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rate, "rate");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("FX rate must be positive");
        }
        if (source.equals(target) && rate.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Same-currency FX rate must be 1");
        }
    }
}

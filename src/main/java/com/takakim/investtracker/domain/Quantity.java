package com.takakim.investtracker.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Quantity(BigDecimal value) {
    public Quantity {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
    }
}

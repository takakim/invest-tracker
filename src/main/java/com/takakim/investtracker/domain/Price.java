package com.takakim.investtracker.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Price(BigDecimal amount, Currency currency) {
    public Price {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
    }
}

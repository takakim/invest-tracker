package com.takakim.investtracker.domain;

import java.util.Locale;

public record Currency(String code) {
    public Currency {
        if (code == null || !code.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter ISO-4217 code");
        }
        code = code.toUpperCase(Locale.ROOT);
    }
}

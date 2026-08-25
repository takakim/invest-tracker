package com.takakim.investtracker.service.performance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A single time-weighted return sub-period used for chain-linking.
 * A new period starts at each external cash flow (DEPOSIT or WITHDRAWAL).
 */
public record PerformancePeriod(
        Instant start,
        Instant end,
        BigDecimal startValue,
        BigDecimal endValue,
        BigDecimal cashFlow
) {
    public PerformancePeriod {
        Objects.requireNonNull(start, "Period start must not be null");
        Objects.requireNonNull(end, "Period end must not be null");
        Objects.requireNonNull(startValue, "Start value must not be null");
        Objects.requireNonNull(endValue, "End value must not be null");
        Objects.requireNonNull(cashFlow, "Cash flow must not be null");
    }

    /**
     * Sub-period return: (endValue - cashFlow) / startValue - 1.
     * Returns zero when startValue is zero (no capital deployed).
     */
    public BigDecimal subPeriodReturn() {
        if (startValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // HPR = (EndValue - ExternalCashFlow) / StartValue - 1
        return endValue.subtract(cashFlow).divide(startValue, 10, java.math.RoundingMode.HALF_EVEN)
                .subtract(BigDecimal.ONE);
    }
}

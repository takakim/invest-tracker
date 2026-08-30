package com.takakim.investtracker.service.market.fmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

public final class FmpDtos {
    private FmpDtos() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteResponse(
            String symbol,
            String name,
            BigDecimal price,
            BigDecimal changesPercentage,
            BigDecimal change,
            BigDecimal dayLow,
            BigDecimal dayHigh,
            BigDecimal yearHigh,
            BigDecimal yearLow,
            BigDecimal open,
            BigDecimal previousClose,
            String exchange,
            Long timestamp
    ) { }
}

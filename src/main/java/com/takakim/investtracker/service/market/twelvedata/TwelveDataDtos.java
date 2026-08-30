package com.takakim.investtracker.service.market.twelvedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public final class TwelveDataDtos {

    private TwelveDataDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuoteResponse(
            String symbol,
            String name,
            String exchange,
            String currency,
            String datetime,
            Long timestamp,
            String close,
            @JsonProperty("previous_close") String previousClose,
            String status,
            Integer code,
            String message
    ) {
        public boolean isSuccess() {
            return (status == null || "ok".equalsIgnoreCase(status)) && close != null && code == null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeSeriesResponse(
            List<TimeSeriesValue> values,
            String status,
            Integer code,
            String message
    ) {
        public boolean isSuccess() {
            return (status == null || "ok".equalsIgnoreCase(status)) && values != null && !values.isEmpty() && code == null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeSeriesValue(
            String datetime,
            String close
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExchangeRateResponse(
            String symbol,
            BigDecimal rate,
            Long timestamp,
            String status,
            Integer code,
            String message
    ) {
        public boolean isSuccess() {
            return (status == null || "ok".equalsIgnoreCase(status)) && rate != null && code == null;
        }
    }
}

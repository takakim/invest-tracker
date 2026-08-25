package com.takakim.investtracker.service.currency;

import com.takakim.investtracker.domain.ObservationSourceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DefaultFxRateProvider implements FxRateProvider {

    // Direct rates expressed as base:quote -> rate (e.g. GBP:USD -> 1.2800)
    private final Map<String, BigDecimal> rates = new ConcurrentHashMap<>();

    public DefaultFxRateProvider() {
        rates.put("GBP:USD", new BigDecimal("1.28000000"));
        rates.put("EUR:USD", new BigDecimal("1.08500000"));
        rates.put("USD:JPY", new BigDecimal("155.00000000"));
        rates.put("GBP:EUR", new BigDecimal("1.18000000"));
        rates.put("USD:CAD", new BigDecimal("1.36000000"));
        rates.put("USD:CHF", new BigDecimal("0.90500000"));
    }

    public void setRate(String base, String quote, BigDecimal rate) {
        if (base != null && quote != null && rate != null) {
            rates.put(base.toUpperCase() + ":" + quote.toUpperCase(), rate);
        }
    }

    @Override
    public Optional<FxRateQuote> fetchRate(String baseCurrency, String quoteCurrency, Instant asOf) {
        if (baseCurrency == null || quoteCurrency == null) {
            return Optional.empty();
        }
        String base = baseCurrency.toUpperCase();
        String quote = quoteCurrency.toUpperCase();

        if (base.equals(quote)) {
            return Optional.of(new FxRateQuote(
                    base, quote, BigDecimal.ONE.setScale(8, RoundingMode.HALF_EVEN),
                    asOf != null ? asOf : Instant.now(),
                    ObservationSourceType.PROVIDER, "DEFAULT_FX_PROVIDER", false, null
            ));
        }

        String directKey = base + ":" + quote;
        if (rates.containsKey(directKey)) {
            return Optional.of(new FxRateQuote(
                    base, quote, rates.get(directKey),
                    asOf != null ? asOf : Instant.now(),
                    ObservationSourceType.PROVIDER, "DEFAULT_FX_PROVIDER", false, null
            ));
        }

        String inverseKey = quote + ":" + base;
        if (rates.containsKey(inverseKey)) {
            BigDecimal inv = rates.get(inverseKey);
            BigDecimal rate = BigDecimal.ONE.divide(inv, 8, RoundingMode.HALF_EVEN);
            return Optional.of(new FxRateQuote(
                    base, quote, rate,
                    asOf != null ? asOf : Instant.now(),
                    ObservationSourceType.PROVIDER, "DEFAULT_FX_PROVIDER", true, null
            ));
        }

        return Optional.empty();
    }

    @Override
    public List<FxRateQuote> fetchHistoricalRates(String baseCurrency, String quoteCurrency, Instant from, Instant to) {
        Optional<FxRateQuote> quote = fetchRate(baseCurrency, quoteCurrency, to);
        return quote.map(List::of).orElseGet(List::of);
    }

    @Override
    public String getProviderId() {
        return "DEFAULT_FX_PROVIDER";
    }
}

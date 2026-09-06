package com.takakim.investtracker.service.currency;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FxRateProvider {

    Optional<FxRateQuote> fetchRate(String baseCurrency, String quoteCurrency, Instant asOf);

    List<FxRateQuote> fetchHistoricalRates(String baseCurrency, String quoteCurrency, Instant from, Instant to);

    String getProviderId();

    default void invalidateCache(String baseCurrency, String quoteCurrency) {}
}

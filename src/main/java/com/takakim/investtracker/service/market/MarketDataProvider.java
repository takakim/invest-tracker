package com.takakim.investtracker.service.market;

import com.takakim.investtracker.domain.Instrument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarketDataProvider {

    Optional<PriceQuote> fetchQuote(Instrument instrument, Instant asOf);

    List<PriceQuote> fetchHistoricalQuotes(Instrument instrument, Instant from, Instant to);

    String getProviderId();
}

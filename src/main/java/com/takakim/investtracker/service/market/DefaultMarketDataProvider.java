package com.takakim.investtracker.service.market;

import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.ObservationSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DefaultMarketDataProvider implements MarketDataProvider {

    private final Map<String, BigDecimal> tickerPriceMap = new ConcurrentHashMap<>();

    public DefaultMarketDataProvider() {
        // Pre-populate realistic prices for testing
        tickerPriceMap.put("AAPL", new BigDecimal("185.5000"));
        tickerPriceMap.put("MSFT", new BigDecimal("420.2500"));
        tickerPriceMap.put("GOOGL", new BigDecimal("175.8000"));
        tickerPriceMap.put("AMZN", new BigDecimal("180.1000"));
        tickerPriceMap.put("VUAG", new BigDecimal("85.4000"));
        tickerPriceMap.put("VUSA", new BigDecimal("72.6000"));
    }

    public void setPrice(String ticker, BigDecimal price) {
        if (ticker != null && price != null) {
            tickerPriceMap.put(ticker.toUpperCase(), price);
        }
    }

    @Override
    public Optional<PriceQuote> fetchQuote(Instrument instrument, Instant asOf) {
        if (instrument == null) {
            return Optional.empty();
        }
        String key = instrument.getTicker() != null ? instrument.getTicker().toUpperCase() : null;
        BigDecimal price = key != null ? tickerPriceMap.get(key) : null;
        if (price == null) {
            return Optional.empty();
        }

        Instant time = asOf != null ? asOf : Instant.now();
        return Optional.of(new PriceQuote(
                instrument.getId(),
                price,
                instrument.getCurrency().code(),
                time,
                ObservationSourceType.PROVIDER,
                "DEFAULT_PROVIDER",
                false,
                null
        ));
    }

    @Override
    public List<PriceQuote> fetchHistoricalQuotes(Instrument instrument, Instant from, Instant to) {
        Optional<PriceQuote> quote = fetchQuote(instrument, to);
        return quote.map(List::of).orElseGet(List::of);
    }

    @Override
    public String getProviderId() {
        return "DEFAULT_PROVIDER";
    }
}

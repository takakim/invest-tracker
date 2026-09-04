package com.takakim.investtracker.service.market;

import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.service.market.fmp.FmpMarketDataProvider;
import com.takakim.investtracker.service.market.twelvedata.TwelveDataMarketDataProvider;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceMarketDataProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CompositeMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(CompositeMarketDataProvider.class);

    private final TwelveDataMarketDataProvider twelveDataProvider;
    private final FmpMarketDataProvider fmpProvider;
    private final YahooFinanceMarketDataProvider yahooProvider;
    private final DefaultMarketDataProvider defaultProvider;

    public CompositeMarketDataProvider(
            TwelveDataMarketDataProvider twelveDataProvider,
            FmpMarketDataProvider fmpProvider,
            YahooFinanceMarketDataProvider yahooProvider,
            DefaultMarketDataProvider defaultProvider) {
        this.twelveDataProvider = twelveDataProvider;
        this.fmpProvider = fmpProvider;
        this.yahooProvider = yahooProvider;
        this.defaultProvider = defaultProvider;
    }

    @Override
    public Optional<PriceQuote> fetchQuote(Instrument instrument, Instant asOf) {
        if (instrument == null) {
            return Optional.empty();
        }

        // 1. Attempt Twelve Data
        Optional<PriceQuote> tdQuoteOpt = Optional.empty();
        if (twelveDataProvider != null && twelveDataProvider.isConfigured()) {
            try {
                tdQuoteOpt = twelveDataProvider.fetchQuote(instrument, asOf);
                if (tdQuoteOpt.isPresent() && "TWELVE_DATA".equals(tdQuoteOpt.get().sourceReference())) {
                    return tdQuoteOpt;
                }
            } catch (Exception ex) {
                log.debug("Twelve Data provider attempt failed for '{}': {}", instrument.getName(), ex.getMessage());
            }
        }

        // 2. Attempt Option B: FMP (Financial Modeling Prep)
        if (fmpProvider != null && fmpProvider.isConfigured()) {
            try {
                Optional<PriceQuote> fmpQuoteOpt = fmpProvider.fetchQuote(instrument, asOf);
                if (fmpQuoteOpt.isPresent() && "FMP".equals(fmpQuoteOpt.get().sourceReference())) {
                    return fmpQuoteOpt;
                }
            } catch (Exception ex) {
                log.debug("FMP provider attempt failed for '{}': {}", instrument.getName(), ex.getMessage());
            }
        }

        // 3. Attempt Option C / Tertiary: Yahoo Finance
        if (yahooProvider != null && yahooProvider.isConfigured()) {
            try {
                Optional<PriceQuote> yahooQuoteOpt = yahooProvider.fetchQuote(instrument, asOf);
                if (yahooQuoteOpt.isPresent() && "YAHOO_FINANCE".equals(yahooQuoteOpt.get().sourceReference())) {
                    return yahooQuoteOpt;
                }
            } catch (Exception ex) {
                log.debug("Yahoo Finance provider attempt failed for '{}': {}", instrument.getName(), ex.getMessage());
            }
        }

        // 4. Fallback to cached Twelve Data quote if available before default mock
        if (tdQuoteOpt.isPresent() && "TWELVE_DATA_CACHED".equals(tdQuoteOpt.get().sourceReference())) {
            log.info("Alternative providers failed or unavailable; falling back to cached Twelve Data quote for symbol '{}'", instrument.getTicker());
            return tdQuoteOpt;
        }

        // 5. Fallback to DefaultMarketDataProvider
        return defaultProvider.fetchQuote(instrument, asOf);
    }

    @Override
    public List<PriceQuote> fetchHistoricalQuotes(Instrument instrument, Instant from, Instant to) {
        if (instrument == null) {
            return List.of();
        }
        if (twelveDataProvider != null && twelveDataProvider.isConfigured()) {
            try {
                List<PriceQuote> tdQuotes = twelveDataProvider.fetchHistoricalQuotes(instrument, from, to);
                if (tdQuotes != null && !tdQuotes.isEmpty() && tdQuotes.stream().anyMatch(q -> "TWELVE_DATA".equals(q.sourceReference()))) {
                    return tdQuotes;
                }
            } catch (Exception ex) {
                log.debug("Twelve Data historical fetch failed for '{}': {}", instrument.getName(), ex.getMessage());
            }
        }

        if (yahooProvider != null && yahooProvider.isConfigured()) {
            try {
                List<PriceQuote> yahooQuotes = yahooProvider.fetchHistoricalQuotes(instrument, from, to);
                if (yahooQuotes != null && !yahooQuotes.isEmpty() && yahooQuotes.stream().anyMatch(q -> "YAHOO_FINANCE".equals(q.sourceReference()))) {
                    return yahooQuotes;
                }
            } catch (Exception ex) {
                log.debug("Yahoo Finance historical fetch failed for '{}': {}", instrument.getName(), ex.getMessage());
            }
        }

        return defaultProvider.fetchHistoricalQuotes(instrument, from, to);
    }

    @Override
    public String getProviderId() {
        return "COMPOSITE_PROXY";
    }
}

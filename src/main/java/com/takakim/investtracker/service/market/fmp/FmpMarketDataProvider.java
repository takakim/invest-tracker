package com.takakim.investtracker.service.market.fmp;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.service.market.MarketDataProvider;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FmpMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(FmpMarketDataProvider.class);

    private final MarketDataProperties properties;
    private final FmpGateway gateway;

    public FmpMarketDataProvider(MarketDataProperties properties, FmpGateway gateway) {
        this.properties = properties;
        this.gateway = gateway;

        if (isConfigured()) {
            log.info("FMP market data provider is ENABLED.");
        } else {
            log.info("FMP market data provider is running in OFFLINE mode.");
        }
    }

    public boolean isConfigured() {
        MarketDataProperties.FmpProperties fmp = properties != null ? properties.getFmp() : null;
        return fmp != null && fmp.isEnabled() && fmp.getApiKey() != null && !fmp.getApiKey().isBlank();
    }

    @Override
    public Optional<PriceQuote> fetchQuote(Instrument instrument, Instant asOf) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String symbol = resolveSymbol(instrument);
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        log.debug("Requesting quote from FMP Gateway for symbol: '{}'", symbol);
        Optional<FmpDtos.QuoteResponse> responseOpt = gateway.fetchQuote(symbol);

        if (responseOpt.isEmpty() || responseOpt.get().price() == null) {
            return Optional.empty();
        }

        FmpDtos.QuoteResponse res = responseOpt.get();
        BigDecimal price = res.price();
        String instrumentCurrency = instrument.getCurrency().code();

        // Handle pence (GBX) to pound (GBP) conversion for UK assets if reported in pence
        if ("GBP".equalsIgnoreCase(instrumentCurrency) && isLseInstrument(instrument) && price.compareTo(new BigDecimal("100")) > 0 && !symbol.contains("BTC")) {
            // For UK equities where price is reported in pence, convert to pounds
            price = price.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        }

        Instant quoteTime = (res.timestamp() != null && res.timestamp() > 0)
                ? Instant.ofEpochSecond(res.timestamp())
                : (asOf != null ? asOf : Instant.now());

        log.info("Successfully fetched FMP quote for symbol '{}': {} {}", symbol, price, instrumentCurrency);

        return Optional.of(new PriceQuote(
                instrument.getId(),
                price,
                instrumentCurrency,
                quoteTime,
                ObservationSourceType.PROVIDER,
                "FMP",
                false,
                null
        ));
    }

    @Override
    public List<PriceQuote> fetchHistoricalQuotes(Instrument instrument, Instant from, Instant to) {
        return List.of();
    }

    @Override
    public String getProviderId() {
        return "FMP";
    }

    public String resolveSymbol(Instrument instrument) {
        if (instrument.getTicker() != null && !instrument.getTicker().isBlank()) {
            String ticker = instrument.getTicker().trim().toUpperCase();
            if (ticker.endsWith(".")) {
                ticker = ticker.substring(0, ticker.length() - 1);
            }
            if ("BRK.B".equals(ticker) || "BRK/B".equals(ticker)) {
                return "BRK-B";
            }
            if (isLseInstrument(instrument)) {
                if (!ticker.endsWith(".L")) {
                    return ticker + ".L";
                }
            }
            return !ticker.isBlank() ? ticker : null;
        }
        return null;
    }

    public boolean isLseInstrument(Instrument instrument) {
        if (instrument.getExchange() != null && !instrument.getExchange().isBlank()) {
            String ex = instrument.getExchange().trim().toUpperCase();
            if (ex.contains("LON") || ex.contains("LSE")) {
                return true;
            }
        }
        if (instrument.getIsin() != null && instrument.getIsin().startsWith("GB")) {
            return true;
        }
        if (instrument.getCurrency() != null && ("GBP".equalsIgnoreCase(instrument.getCurrency().code()) || "GBX".equalsIgnoreCase(instrument.getCurrency().code()))) {
            return true;
        }
        return false;
    }
}

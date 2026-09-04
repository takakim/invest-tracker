package com.takakim.investtracker.service.market.yahoo;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.ObservationSourceType;
import com.takakim.investtracker.service.market.MarketDataProvider;
import com.takakim.investtracker.service.market.PriceQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceMarketDataProvider.class);

    private final MarketDataProperties properties;
    private final YahooFinanceGateway gateway;

    public YahooFinanceMarketDataProvider(MarketDataProperties properties, YahooFinanceGateway gateway) {
        this.properties = properties;
        this.gateway = gateway;

        if (isConfigured()) {
            log.info("Yahoo Finance market data provider is ENABLED.");
        } else {
            log.info("Yahoo Finance market data provider is running in OFFLINE mode.");
        }
    }

    public boolean isConfigured() {
        MarketDataProperties.YahooProperties yahoo = properties != null ? properties.getYahoo() : null;
        return yahoo != null && yahoo.isEnabled() && yahoo.getBaseUrl() != null && !yahoo.getBaseUrl().isBlank();
    }

    @Override
    public Optional<PriceQuote> fetchQuote(Instrument instrument, Instant asOf) {
        if (!isConfigured() || instrument == null) {
            return Optional.empty();
        }

        String symbol = resolveSymbol(instrument);
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        log.debug("Requesting quote from Yahoo Finance Gateway for symbol: '{}'", symbol);
        Optional<YahooFinanceDtos.ChartEntry> entryOpt = gateway.fetchChart(symbol, "1d", "1d");

        if (entryOpt.isEmpty()) {
            return Optional.empty();
        }

        YahooFinanceDtos.ChartEntry entry = entryOpt.get();
        YahooFinanceDtos.ChartMeta meta = entry.meta();
        if (meta == null || meta.regularMarketPrice() == null) {
            return Optional.empty();
        }

        BigDecimal price = meta.regularMarketPrice();
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        String responseCurrency = meta.currency();
        String instrumentCurrency = (instrument.getCurrency() != null) ? instrument.getCurrency().code() : "USD";

        // Auto-convert pence (GBp/GBX) to pound (GBP) if Yahoo returned GBp/GBX
        if ("GBP".equalsIgnoreCase(instrumentCurrency) && isPenceCurrency(responseCurrency)) {
            price = price.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        } else if ("GBP".equalsIgnoreCase(instrumentCurrency) && isLseInstrument(instrument)
                && responseCurrency == null && price.compareTo(new BigDecimal("100")) > 0 && !symbol.contains("BTC")) {
            // Fallback pence heuristic if meta.currency was not provided
            price = price.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        }

        Instant quoteTime = (asOf != null)
                ? asOf
                : Instant.now();

        log.info("Successfully fetched Yahoo Finance quote for symbol '{}': {} {}", symbol, price, instrumentCurrency);

        return Optional.of(new PriceQuote(
                instrument.getId(),
                price,
                instrumentCurrency,
                quoteTime,
                ObservationSourceType.PROVIDER,
                "YAHOO_FINANCE",
                false,
                null
        ));
    }

    @Override
    public List<PriceQuote> fetchHistoricalQuotes(Instrument instrument, Instant from, Instant to) {
        if (!isConfigured() || instrument == null) {
            return List.of();
        }

        String symbol = resolveSymbol(instrument);
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        String range = resolveRange(from, to);
        Optional<YahooFinanceDtos.ChartEntry> entryOpt = gateway.fetchChart(symbol, "1d", range);
        if (entryOpt.isEmpty()) {
            return List.of();
        }

        YahooFinanceDtos.ChartEntry entry = entryOpt.get();
        if (entry.timestamp() == null || entry.indicators() == null || entry.indicators().quote() == null || entry.indicators().quote().isEmpty()) {
            return List.of();
        }

        List<Long> timestamps = entry.timestamp();
        List<BigDecimal> closes = entry.indicators().quote().get(0).close();
        if (closes == null) {
            return List.of();
        }

        String responseCurrency = entry.meta() != null ? entry.meta().currency() : null;
        String instrumentCurrency = (instrument.getCurrency() != null) ? instrument.getCurrency().code() : "USD";
        boolean isPence = "GBP".equalsIgnoreCase(instrumentCurrency) && isPenceCurrency(responseCurrency);

        List<PriceQuote> quotes = new ArrayList<>();
        int count = Math.min(timestamps.size(), closes.size());
        for (int i = 0; i < count; i++) {
            Long epochSec = timestamps.get(i);
            BigDecimal closePrice = closes.get(i);
            if (epochSec != null && closePrice != null) {
                if (isPence) {
                    closePrice = closePrice.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                }
                quotes.add(new PriceQuote(
                        instrument.getId(),
                        closePrice,
                        instrumentCurrency,
                        Instant.ofEpochSecond(epochSec),
                        ObservationSourceType.PROVIDER,
                        "YAHOO_FINANCE",
                        false,
                        null
                ));
            }
        }
        return quotes;
    }

    @Override
    public String getProviderId() {
        return "YAHOO_FINANCE";
    }

    public String resolveSymbol(Instrument instrument) {
        if (instrument == null || instrument.getTicker() == null || instrument.getTicker().isBlank()) {
            return null;
        }

        String ticker = instrument.getTicker().trim().toUpperCase();
        if (ticker.endsWith(".")) {
            ticker = ticker.substring(0, ticker.length() - 1);
        }
        if (ticker.isBlank()) {
            return null;
        }
        if ("BRK.B".equals(ticker) || "BRK/B".equals(ticker)) {
            return "BRK-B";
        }
        if (ticker.contains(".")) {
            return ticker;
        }

        String suffix = resolveExchangeSuffix(instrument);
        if (suffix != null) {
            return ticker + suffix;
        }
        return ticker;
    }

    public boolean isLseInstrument(Instrument instrument) {
        return ".L".equals(resolveExchangeSuffix(instrument));
    }

    private record SuffixRule(java.util.regex.Pattern pattern, String isinPrefix, String suffix) {}

    private static final java.util.regex.Pattern LSE_PATTERN = java.util.regex.Pattern.compile("LON|LSE");
    private static final List<SuffixRule> SUFFIX_RULES = List.of(
            new SuffixRule(java.util.regex.Pattern.compile("PARIS|EPA|XPAR"), "FR", ".PA"),
            new SuffixRule(java.util.regex.Pattern.compile("AMSTERDAM|AMS|XAMS"), "NL", ".AS"),
            new SuffixRule(java.util.regex.Pattern.compile("XETRA|FRANKFURT|FRA|XFRA"), "DE", ".DE"),
            new SuffixRule(java.util.regex.Pattern.compile("BRUSSELS|BRU|XBRU"), "BE", ".BR"),
            new SuffixRule(java.util.regex.Pattern.compile("LISBON|XLIS"), "PT", ".LS"),
            new SuffixRule(java.util.regex.Pattern.compile("MILAN|BORSA|XMIL"), "IT", ".MI"),
            new SuffixRule(java.util.regex.Pattern.compile("MADRID|BME|XMCE"), "ES", ".MC"),
            new SuffixRule(java.util.regex.Pattern.compile("TORONTO|TSX|XTSE"), "CA", ".TO")
    );

    public static String resolveExchangeSuffix(Instrument instrument) {
        if (instrument == null) {
            return null;
        }
        String ex = instrument.getExchange() != null ? instrument.getExchange().trim().toUpperCase() : "";
        String isin = instrument.getIsin() != null ? instrument.getIsin().trim().toUpperCase() : "";

        // 1. London Stock Exchange (.L)
        if (LSE_PATTERN.matcher(ex).find() || isin.startsWith("GB")
                || (instrument.getCurrency() != null && ("GBP".equalsIgnoreCase(instrument.getCurrency().code()) || "GBX".equalsIgnoreCase(instrument.getCurrency().code())))) {
            return ".L";
        }

        for (SuffixRule rule : SUFFIX_RULES) {
            if (rule.pattern().matcher(ex).find() || isin.startsWith(rule.isinPrefix())) {
                return rule.suffix();
            }
        }

        return null;
    }

    public static String resolveRange(Instant from, Instant to) {
        if (from == null) {
            return "1mo";
        }
        Instant end = to != null ? to : Instant.now();
        long days = java.time.Duration.between(from, end).abs().toDays();
        if (days > 5 * 365) {
            return "max";
        }
        if (days > 2 * 365) {
            return "5y";
        }
        if (days > 365) {
            return "2y";
        }
        if (days > 180) {
            return "1y";
        }
        if (days > 90) {
            return "6mo";
        }
        if (days > 30) {
            return "3mo";
        }
        return "1mo";
    }

    private boolean isPenceCurrency(String currency) {
        return currency != null && ("GBp".equals(currency) || "GBX".equalsIgnoreCase(currency));
    }
}

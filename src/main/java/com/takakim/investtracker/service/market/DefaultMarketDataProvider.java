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
        // Pre-populate realistic baseline prices
        tickerPriceMap.put("AAPL", new BigDecimal("185.5000"));
        tickerPriceMap.put("APPL", new BigDecimal("185.5000"));
        tickerPriceMap.put("MSFT", new BigDecimal("420.2500"));
        tickerPriceMap.put("GOOGL", new BigDecimal("175.8000"));
        tickerPriceMap.put("AMZN", new BigDecimal("180.1000"));
        tickerPriceMap.put("NVDA", new BigDecimal("125.0000"));
        tickerPriceMap.put("TSLA", new BigDecimal("210.5000"));
        tickerPriceMap.put("META", new BigDecimal("490.0000"));
        tickerPriceMap.put("VUAG", new BigDecimal("85.4000"));
        tickerPriceMap.put("VUSA", new BigDecimal("72.6000"));
        tickerPriceMap.put("VWRL", new BigDecimal("98.2000"));
        tickerPriceMap.put("VWRP", new BigDecimal("105.5000"));
        tickerPriceMap.put("XDPG", new BigDecimal("112.3000"));
        tickerPriceMap.put("IITU", new BigDecimal("24.5000"));
        tickerPriceMap.put("EQQQ", new BigDecimal("380.0000"));
        tickerPriceMap.put("GB00BSGJV473", new BigDecimal("99.8500"));
        tickerPriceMap.put("RGL", new BigDecimal("0.5500"));
        tickerPriceMap.put("LLOY", new BigDecimal("1.1145"));
        tickerPriceMap.put("GB0008706128", new BigDecimal("1.1145"));
        tickerPriceMap.put("HSBA", new BigDecimal("6.8500"));
        tickerPriceMap.put("RR.", new BigDecimal("5.2500"));
        tickerPriceMap.put("RR", new BigDecimal("5.2500"));
        tickerPriceMap.put("FTC", new BigDecimal("0.7200"));
        tickerPriceMap.put("SPCX", new BigDecimal("95.0000"));
        tickerPriceMap.put("MU", new BigDecimal("112.5000"));
        tickerPriceMap.put("SFTBY", new BigDecimal("28.4000"));
        tickerPriceMap.put("RTX", new BigDecimal("121.2000"));
        tickerPriceMap.put("RIVN", new BigDecimal("13.8000"));
        tickerPriceMap.put("BBAI", new BigDecimal("2.1500"));
        tickerPriceMap.put("STX", new BigDecimal("104.5000"));
        tickerPriceMap.put("VALE", new BigDecimal("15.1800"));
        tickerPriceMap.put("SGLD", new BigDecimal("42.1000"));
        tickerPriceMap.put("ALAB", new BigDecimal("65.3000"));
        tickerPriceMap.put("NVO", new BigDecimal("136.2000"));
        tickerPriceMap.put("HON", new BigDecimal("212.0000"));
        tickerPriceMap.put("QS", new BigDecimal("5.8500"));
        tickerPriceMap.put("RPI", new BigDecimal("5.7986"));
        tickerPriceMap.put("GB00BS3DYQ52", new BigDecimal("5.7986"));
        tickerPriceMap.put("IQE", new BigDecimal("0.4797"));
        tickerPriceMap.put("GB0009619924", new BigDecimal("0.4797"));
        tickerPriceMap.put("BKCN", new BigDecimal("46.2750"));
        tickerPriceMap.put("CHTE", new BigDecimal("6.0230"));
        tickerPriceMap.put("CYSE", new BigDecimal("19.9780"));
        tickerPriceMap.put("INTL", new BigDecimal("63.8200"));
        tickerPriceMap.put("KLWD", new BigDecimal("20.8150"));
        tickerPriceMap.put("QWTM", new BigDecimal("24.7850"));
        tickerPriceMap.put("WBIO", new BigDecimal("13.9380"));
        tickerPriceMap.put("WTNR", new BigDecimal("22.2300"));
        tickerPriceMap.put("HNSS", new BigDecimal("35.5050"));
        tickerPriceMap.put("SMGB", new BigDecimal("43.2800"));
        tickerPriceMap.put("BRK.B", new BigDecimal("327.3600"));
        tickerPriceMap.put("BRK/B", new BigDecimal("327.3600"));
        tickerPriceMap.put("BNPP", new BigDecimal("54.7367"));
        tickerPriceMap.put("BNP", new BigDecimal("54.7367"));
        tickerPriceMap.put("AMD", new BigDecimal("128.6150"));
        tickerPriceMap.put("COIN", new BigDecimal("209.9200"));
        tickerPriceMap.put("CRWD", new BigDecimal("253.1250"));
        tickerPriceMap.put("COST", new BigDecimal("549.5749"));
        tickerPriceMap.put("NET", new BigDecimal("41.8400"));
        tickerPriceMap.put("PLTR", new BigDecimal("17.0313"));
        tickerPriceMap.put("BBD", new BigDecimal("2.2600"));
        tickerPriceMap.put("BIRD", new BigDecimal("2.4314"));
        tickerPriceMap.put("SMCI", new BigDecimal("588.5600"));
        tickerPriceMap.put("SMLR", new BigDecimal("25.1600"));
        tickerPriceMap.put("SOUN", new BigDecimal("3.8996"));
        tickerPriceMap.put("SPOT", new BigDecimal("220.7000"));
        tickerPriceMap.put("STLA", new BigDecimal("21.8564"));
        tickerPriceMap.put("TM", new BigDecimal("190.3900"));
        tickerPriceMap.put("LUNR", new BigDecimal("8.0050"));
        tickerPriceMap.put("MRNA", new BigDecimal("109.3150"));
        tickerPriceMap.put("NU", new BigDecimal("8.0988"));
        tickerPriceMap.put("QBTS", new BigDecimal("24.7000"));
        tickerPriceMap.put("QCOM", new BigDecimal("96.7280"));
        tickerPriceMap.put("QUBT", new BigDecimal("10.0500"));
        tickerPriceMap.put("RDDT", new BigDecimal("40.4050"));
        tickerPriceMap.put("RGTI", new BigDecimal("21.2700"));
        tickerPriceMap.put("BWXT", new BigDecimal("188.0000"));
        tickerPriceMap.put("CEG", new BigDecimal("253.4900"));
        tickerPriceMap.put("PSIX", new BigDecimal("40.7400"));
        tickerPriceMap.put("IONQ", new BigDecimal("59.5000"));
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
        BigDecimal basePrice = resolveBasePrice(instrument);
        if (basePrice == null) {
            return Optional.empty();
        }
        Instant time = asOf != null ? asOf : Instant.now();
        BigDecimal priceAtTime = computeHistoricalPrice(basePrice, time);

        return Optional.of(new PriceQuote(
                instrument.getId(),
                priceAtTime,
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
        if (instrument == null) {
            return List.of();
        }
        Instant start = from != null ? from : Instant.now().minus(365, java.time.temporal.ChronoUnit.DAYS);
        Instant end = to != null ? to : Instant.now();
        if (start.isAfter(end)) {
            start = end.minus(30, java.time.temporal.ChronoUnit.DAYS);
        }

        List<PriceQuote> quotes = new ArrayList<>();
        Instant cursor = start;
        while (!cursor.isAfter(end)) {
            fetchQuote(instrument, cursor).ifPresent(quotes::add);
            cursor = cursor.plus(7, java.time.temporal.ChronoUnit.DAYS);
        }
        // Ensure end point is included
        if (quotes.isEmpty() || quotes.get(quotes.size() - 1).asOf().isBefore(end)) {
            fetchQuote(instrument, end).ifPresent(quotes::add);
        }
        return quotes;
    }

    private BigDecimal resolveBasePrice(Instrument instrument) {
        String ticker = instrument.getTicker() != null ? instrument.getTicker().toUpperCase() : null;
        if (ticker != null) {
            String clean = ticker.endsWith(".") ? ticker.substring(0, ticker.length() - 1) : ticker;
            BigDecimal price = tickerPriceMap.get(clean);
            if (price != null) {
                return price;
            }
        }
        String isin = instrument.getIsin() != null ? instrument.getIsin().toUpperCase() : null;
        if (isin != null) {
            BigDecimal isinPrice = tickerPriceMap.get(isin);
            if (isinPrice != null) {
                return isinPrice;
            }
        }
        // Treasury bills pattern matching (UK T-Bills price near par ~99.85) - only for bonds
        if (instrument.getAssetClass() == com.takakim.investtracker.domain.AssetClass.BOND) {
            if (instrument.getName() != null && instrument.getName().toUpperCase().contains("T-BILL")) {
                return new BigDecimal("99.8500");
            }
            if (isin != null && isin.startsWith("GB00") && (isin.contains("BS") || isin.contains("BP") || isin.contains("BX"))) {
                return new BigDecimal("99.8500");
            }
        }
        return null;
    }

    private BigDecimal computeHistoricalPrice(BigDecimal basePrice, Instant asOf) {
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(asOf, Instant.now());
        if (daysDiff <= 0) {
            return basePrice;
        }
        // Realistic continuous historical drift (approx 10-12% annual return back in time)
        double base = basePrice.doubleValue();
        double driftFactor = Math.exp(-0.0003 * daysDiff);
        double cyclic = 1.0 + (0.015 * Math.sin(daysDiff / 14.0));
        double adjusted = base * driftFactor * cyclic;
        if (adjusted <= 0.01) {
            adjusted = 0.01;
        }
        return BigDecimal.valueOf(adjusted).setScale(4, java.math.RoundingMode.HALF_EVEN);
    }

    @Override
    public String getProviderId() {
        return "DEFAULT_PROVIDER";
    }
}

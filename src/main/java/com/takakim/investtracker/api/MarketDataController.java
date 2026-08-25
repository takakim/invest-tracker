package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.MarketObservationResponse;
import com.takakim.investtracker.api.ApiDtos.MarketPriceOverrideRequest;
import com.takakim.investtracker.api.ApiDtos.PriceQuoteResponse;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/api/v1/instruments/{instrumentId}/quotes/latest")
    public PriceQuoteResponse getLatestPrice(
            @PathVariable UUID instrumentId,
            @RequestParam(required = false) Instant asOf) {
        PriceQuote quote = marketDataService.getLatestPrice(instrumentId, asOf);
        return toQuoteResponse(quote);
    }

    @PostMapping("/api/v1/instruments/{instrumentId}/quotes/override")
    public ResponseEntity<MarketObservationResponse> recordPriceOverride(
            @PathVariable UUID instrumentId,
            @Valid @RequestBody MarketPriceOverrideRequest request) {
        MarketObservation obs = marketDataService.recordManualOverride(
                instrumentId,
                request.price(),
                request.currency(),
                request.observedAt(),
                request.reason()
        );
        URI location = URI.create(String.format("/api/v1/instruments/%s/quotes/%s", instrumentId, obs.getId()));
        return ResponseEntity.created(location).body(toObservationResponse(obs));
    }

    @GetMapping("/api/v1/instruments/{instrumentId}/quotes/history")
    public List<MarketObservationResponse> getPriceHistory(
            @PathVariable UUID instrumentId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        List<MarketObservation> history = marketDataService.getHistoricalPrices(instrumentId, from, to);
        return history.stream().map(this::toObservationResponse).toList();
    }

    private PriceQuoteResponse toQuoteResponse(PriceQuote q) {
        return new PriceQuoteResponse(
                q.instrumentId(),
                q.price(),
                q.currency(),
                q.asOf(),
                q.sourceType().name(),
                q.sourceReference(),
                q.isStale(),
                q.warning()
        );
    }

    private MarketObservationResponse toObservationResponse(MarketObservation obs) {
        return new MarketObservationResponse(
                obs.getId(),
                obs.getInstrument().getId(),
                obs.getPrice(),
                obs.getCurrency(),
                obs.getObservedAt(),
                obs.getSourceType().name(),
                obs.getSourceReference(),
                obs.getCreatedAt()
        );
    }
}

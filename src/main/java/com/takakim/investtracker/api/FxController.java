package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.FxObservationResponse;
import com.takakim.investtracker.api.ApiDtos.FxRateOverrideRequest;
import com.takakim.investtracker.api.ApiDtos.FxRateQuoteResponse;
import com.takakim.investtracker.domain.FxObservation;
import com.takakim.investtracker.service.currency.FxRateQuote;
import com.takakim.investtracker.service.currency.FxRateService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FxController {

    private final FxRateService fxRateService;

    public FxController(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    @GetMapping("/api/v1/currencies/rates")
    public FxRateQuoteResponse getRate(
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam(required = false) Instant asOf) {
        FxRateQuote fxQuote = fxRateService.getRate(base, quote, asOf);
        return toQuoteResponse(fxQuote);
    }

    @PostMapping("/api/v1/currencies/rates/override")
    public ResponseEntity<FxObservationResponse> recordRateOverride(
            @Valid @RequestBody FxRateOverrideRequest request) {
        FxObservation obs = fxRateService.recordManualOverride(
                request.baseCurrency(),
                request.quoteCurrency(),
                request.rate(),
                request.observedAt(),
                request.reason()
        );
        URI location = URI.create(String.format("/api/v1/currencies/rates/%s", obs.getId()));
        return ResponseEntity.created(location).body(toObservationResponse(obs));
    }

    @GetMapping("/api/v1/currencies/rates/history")
    public List<FxObservationResponse> getRateHistory(
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        List<FxObservation> history = fxRateService.getHistoricalRates(base, quote, from, to);
        return history.stream().map(this::toObservationResponse).toList();
    }

    private FxRateQuoteResponse toQuoteResponse(FxRateQuote q) {
        return new FxRateQuoteResponse(
                q.baseCurrency(),
                q.quoteCurrency(),
                q.rate(),
                q.asOf(),
                q.sourceType().name(),
                q.sourceReference(),
                q.isDerived(),
                q.warning()
        );
    }

    private FxObservationResponse toObservationResponse(FxObservation obs) {
        return new FxObservationResponse(
                obs.getId(),
                obs.getBaseCurrency(),
                obs.getQuoteCurrency(),
                obs.getRate(),
                obs.getObservedAt(),
                obs.getSourceType().name(),
                obs.getSourceReference(),
                obs.getCreatedAt()
        );
    }
}

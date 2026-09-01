package com.takakim.investtracker.service;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketObservation;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.service.market.MarketDataService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InstrumentService {

    private final InstrumentRepository repository;
    private final MarketObservationRepository marketObservationRepository;
    private final MarketDataService marketDataService;

    public InstrumentService(
            InstrumentRepository repository,
            MarketObservationRepository marketObservationRepository,
            MarketDataService marketDataService) {
        this.repository = repository;
        this.marketObservationRepository = marketObservationRepository;
        this.marketDataService = marketDataService;
    }

    public ApiDtos.InstrumentResponse create(ApiDtos.InstrumentRequest request) {
        if (request.isin() != null && !request.isin().isBlank() && repository.existsByIsinIgnoreCase(request.isin())) {
            throw new ConflictException("Instrument ISIN already exists");
        }
        boolean manualOnly = Boolean.TRUE.equals(request.manualPriceOnly());
        return toResponse(repository.save(new Instrument(request.name(), request.assetClass(), request.ticker(), request.isin(), request.exchange(), new Currency(request.currency()), manualOnly)));
    }

    public ApiDtos.InstrumentResponse update(UUID id, ApiDtos.InstrumentRequest request) {
        Instrument instrument = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + id));
        if (request.isin() != null && !request.isin().isBlank()) {
            repository.findByIsin(request.isin())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new ConflictException("Instrument ISIN already exists on another instrument: " + request.isin());
                        }
                    });
        }
        boolean manualOnly = Boolean.TRUE.equals(request.manualPriceOnly());
        instrument.update(
                request.name(),
                request.assetClass(),
                request.ticker(),
                request.isin(),
                request.exchange(),
                new Currency(request.currency()),
                manualOnly
        );
        Instrument saved = repository.save(instrument);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ApiDtos.InstrumentResponse get(UUID id) {
        return toResponse(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.InstrumentResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public List<ApiDtos.InstrumentResponse> refreshAllPrices() {
        List<Instrument> instruments = repository.findAllByOrderByNameAsc();
        if (marketDataService != null) {
            // Prioritize stalest or unobserved instruments first
            java.util.List<Instrument> prioritized = new java.util.ArrayList<>(instruments);
            prioritized.sort(java.util.Comparator.comparing((Instrument inst) -> {
                if (marketObservationRepository == null) {
                    return Instant.MIN;
                }
                return marketObservationRepository.findFirstByInstrumentIdOrderByObservedAtDesc(inst.getId())
                        .map(MarketObservation::getObservedAt)
                        .orElse(Instant.MIN);
            }));

            for (Instrument inst : prioritized) {
                try {
                    marketDataService.refreshPrice(inst.getId());
                } catch (Exception ignored) {
                }
            }
        }
        return list();
    }

    public ApiDtos.InstrumentResponse refreshPrice(UUID id) {
        Instrument instrument = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + id));
        if (marketDataService != null) {
            try {
                marketDataService.refreshPrice(instrument.getId());
            } catch (Exception ignored) {
            }
        }
        return toResponse(instrument);
    }

    private ApiDtos.InstrumentResponse toResponse(Instrument i) {
        BigDecimal price = null;
        String priceCurrency = null;
        Instant priceAsOf = null;
        Boolean isStale = null;

        if (marketObservationRepository != null) {
            Optional<MarketObservation> latest = marketObservationRepository
                    .findFirstByInstrumentIdOrderByObservedAtDesc(i.getId());
            if (latest.isPresent()) {
                MarketObservation obs = latest.get();
                if (obs.getPrice() != null && obs.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    price = obs.getPrice();
                    priceCurrency = obs.getCurrency();
                    priceAsOf = obs.getObservedAt();
                    isStale = MarketDataService.isObservationStale(obs.getObservedAt(), Instant.now(), i.getAssetClass());
                }
            }
        }

        return new ApiDtos.InstrumentResponse(
                i.getId(), i.getName(), i.getAssetClass(), i.getTicker(), i.getIsin(),
                i.getExchange(), i.getCurrency().code(), i.isManualPriceOnly(), i.getCreatedAt(), i.getUpdatedAt(),
                price, priceCurrency, priceAsOf, isStale
        );
    }
}

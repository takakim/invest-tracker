package com.takakim.investtracker.service;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.repository.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class InstrumentService {
    private final InstrumentRepository repository;

    public InstrumentService(InstrumentRepository repository) { this.repository = repository; }

    public ApiDtos.InstrumentResponse create(ApiDtos.InstrumentRequest request) {
        if (request.isin() != null && !request.isin().isBlank() && repository.existsByIsinIgnoreCase(request.isin())) throw new ConflictException("Instrument ISIN already exists");
        return toResponse(repository.save(new Instrument(request.name(), request.assetClass(), request.ticker(), request.isin(), request.exchange(), new Currency(request.currency()))));
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
        instrument.update(
                request.name(),
                request.assetClass(),
                request.ticker(),
                request.isin(),
                request.exchange(),
                new Currency(request.currency())
        );
        return toResponse(repository.save(instrument));
    }

    @Transactional(readOnly = true)
    public ApiDtos.InstrumentResponse get(UUID id) { return toResponse(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + id))); }

    @Transactional(readOnly = true)
    public List<ApiDtos.InstrumentResponse> list() { return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList(); }

    private ApiDtos.InstrumentResponse toResponse(Instrument i) {
        return new ApiDtos.InstrumentResponse(i.getId(), i.getName(), i.getAssetClass(), i.getTicker(), i.getIsin(), i.getExchange(), i.getCurrency().code(), i.getCreatedAt(), i.getUpdatedAt());
    }
}

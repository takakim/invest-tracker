package com.takakim.investtracker.service;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.PortfolioStatus;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PortfolioService {
    private final PortfolioRepository repository;

    public PortfolioService(PortfolioRepository repository) { this.repository = repository; }

    public ApiDtos.PortfolioResponse create(ApiDtos.PortfolioRequest request) {
        return toResponse(repository.save(new Portfolio(request.name(), new Currency(request.baseCurrency()), request.costBasisMethod(), request.returnMethod())));
    }

    @Transactional(readOnly = true)
    public ApiDtos.PortfolioResponse get(UUID id) { return toResponse(find(id)); }

    @Transactional(readOnly = true)
    public List<ApiDtos.PortfolioResponse> list() { return repository.findAllByStatusOrderByNameAsc(PortfolioStatus.ACTIVE).stream().map(this::toResponse).toList(); }

    public ApiDtos.PortfolioResponse update(UUID id, ApiDtos.PortfolioRequest request) {
        Portfolio portfolio = find(id);
        portfolio.update(request.name(), new Currency(request.baseCurrency()), request.costBasisMethod(), request.returnMethod());
        return toResponse(repository.save(portfolio));
    }

    public void archive(UUID id) { Portfolio portfolio = find(id); portfolio.archive(); repository.save(portfolio); }

    private Portfolio find(UUID id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + id)); }

    private ApiDtos.PortfolioResponse toResponse(Portfolio p) {
        return new ApiDtos.PortfolioResponse(p.getId(), p.getName(), p.getBaseCurrency().code(), p.getCostBasisMethod(), p.getReturnMethod(), p.getStatus().name(), p.getCreatedAt(), p.getUpdatedAt());
    }
}

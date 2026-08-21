package com.takakim.investtracker.api;

import com.takakim.investtracker.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {
    private final PortfolioService service;
    public PortfolioController(PortfolioService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.PortfolioResponse create(@Valid @RequestBody ApiDtos.PortfolioRequest request) { return service.create(request); }

    @GetMapping
    public List<ApiDtos.PortfolioResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public ApiDtos.PortfolioResponse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}")
    public ApiDtos.PortfolioResponse update(@PathVariable UUID id, @Valid @RequestBody ApiDtos.PortfolioRequest request) { return service.update(id, request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) { service.archive(id); }
}

package com.takakim.investtracker.api;

import com.takakim.investtracker.service.AccountService;
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
@RequestMapping("/api/v1/portfolios/{portfolioId}/accounts")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.AccountResponse create(@PathVariable UUID portfolioId, @Valid @RequestBody ApiDtos.AccountRequest request) { return service.create(portfolioId, request); }

    @GetMapping
    public List<ApiDtos.AccountResponse> list(@PathVariable UUID portfolioId) { return service.list(portfolioId); }

    @GetMapping("/{id}")
    public ApiDtos.AccountResponse get(@PathVariable UUID portfolioId, @PathVariable UUID id) { return service.get(portfolioId, id); }

    @PutMapping("/{id}")
    public ApiDtos.AccountResponse update(@PathVariable UUID portfolioId, @PathVariable UUID id, @Valid @RequestBody ApiDtos.AccountRequest request) { return service.update(portfolioId, id, request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID portfolioId, @PathVariable UUID id) { service.archive(portfolioId, id); }
}

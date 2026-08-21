package com.takakim.investtracker.service;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AccountService {
    private final AccountRepository accounts;
    private final PortfolioRepository portfolios;

    public AccountService(AccountRepository accounts, PortfolioRepository portfolios) {
        this.accounts = accounts;
        this.portfolios = portfolios;
    }

    public ApiDtos.AccountResponse create(UUID portfolioId, ApiDtos.AccountRequest request) {
        Portfolio portfolio = portfolios.findById(portfolioId).orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        if (portfolio.getStatus() != com.takakim.investtracker.domain.PortfolioStatus.ACTIVE) throw new IllegalStateException("Archived portfolio cannot contain new accounts");
        return toResponse(accounts.save(new Account(portfolio, request.name(), request.brokerName(), new Currency(request.accountCurrency()))));
    }

    @Transactional(readOnly = true)
    public ApiDtos.AccountResponse get(UUID id) { return toResponse(find(id)); }

    @Transactional(readOnly = true)
    public List<ApiDtos.AccountResponse> list(UUID portfolioId) {
        if (!portfolios.existsById(portfolioId)) throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        return accounts.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE).stream().map(this::toResponse).toList();
    }

    public ApiDtos.AccountResponse update(UUID id, ApiDtos.AccountRequest request) {
        Account account = find(id);
        account.update(request.name(), request.brokerName(), new Currency(request.accountCurrency()));
        return toResponse(accounts.save(account));
    }

    public void archive(UUID id) { Account account = find(id); account.archive(); accounts.save(account); }

    private Account find(UUID id) { return accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id)); }

    private ApiDtos.AccountResponse toResponse(Account a) {
        return new ApiDtos.AccountResponse(a.getId(), a.getPortfolio().getId(), a.getName(), a.getBrokerName(), a.getAccountCurrency().code(), a.getStatus().name(), a.getCreatedAt(), a.getUpdatedAt());
    }
}

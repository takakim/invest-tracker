package com.takakim.investtracker.repository;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findAllByPortfolioIdAndStatusOrderByNameAsc(UUID portfolioId, AccountStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT a.accountCurrency FROM Account a WHERE a.status = 'ACTIVE' AND a.accountCurrency IS NOT NULL")
    List<String> findDistinctAccountCurrencies();
}

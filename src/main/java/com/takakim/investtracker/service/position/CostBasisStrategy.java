package com.takakim.investtracker.service.position;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Transaction;
import java.util.List;

/**
 * Strategy interface for calculating position lots, cost basis, and disposals according to a specific method.
 */
public interface CostBasisStrategy {

    CostBasisMethod getMethod();

    PositionCalculationResult calculate(Account account, Instrument instrument, List<Transaction> transactions);
}

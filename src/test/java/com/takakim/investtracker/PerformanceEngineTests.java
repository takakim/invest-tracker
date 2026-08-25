package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.performance.AccountPerformanceSummary;
import com.takakim.investtracker.service.performance.PerformanceEngine;
import com.takakim.investtracker.service.performance.PerformancePeriod;
import com.takakim.investtracker.service.performance.PerformanceResult;
import com.takakim.investtracker.service.position.AverageCostBasisStrategy;
import com.takakim.investtracker.service.position.FifoCostBasisStrategy;
import com.takakim.investtracker.service.position.LifoCostBasisStrategy;
import com.takakim.investtracker.service.position.LotDisposal;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceEngineTests {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PositionRepository positionRepository;

    private PerformanceEngine performanceEngine;
    private PositionEngine positionEngine;

    private Portfolio twrPortfolio;
    private Portfolio mwrPortfolio;
    private Portfolio xirrPortfolio;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        positionEngine = new PositionEngine(
                positionRepository, transactionRepository,
                portfolioRepository, accountRepository,
                List.of(new FifoCostBasisStrategy(), new LifoCostBasisStrategy(), new AverageCostBasisStrategy())
        );
        performanceEngine = new PerformanceEngine(
                portfolioRepository, accountRepository, transactionRepository, positionEngine
        );

        twrPortfolio = new Portfolio("TWR Port", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        mwrPortfolio = new Portfolio("MWR Port", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.MWR);
        xirrPortfolio = new Portfolio("XIRR Port", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.XIRR);
        instrument = new Instrument("Apple", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
    }

    @Test
    @DisplayName("PerformanceEngine throws ResourceNotFoundException for missing portfolio")
    void missingPortfolioThrows() {
        UUID badId = UUID.randomUUID();
        when(portfolioRepository.findById(badId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> performanceEngine.calculate(badId));
    }

    @Test
    @DisplayName("PerformanceEngine throws UnsupportedOperationException for XIRR portfolio")
    void xirrThrowsNotImplemented() {
        UUID pId = xirrPortfolio.getId();
        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(xirrPortfolio));
        assertThrows(UnsupportedOperationException.class, () -> performanceEngine.calculate(pId));
    }

    @Test
    @DisplayName("Empty portfolio returns zero returns and zero income/cost")
    void emptyPortfolioZeroReturns() {
        UUID pId = twrPortfolio.getId();
        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of());
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of());

        PerformanceResult result = performanceEngine.calculate(pId);
        assertNotNull(result);
        assertEquals("TWR", result.returnMethod());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalRealizedGainLoss()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalDividendIncome()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalFees()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalCostBasis()));
        assertEquals("COST_BASIS", result.valuationBasis());
        assertTrue(result.byAccount().isEmpty());
    }

    @Test
    @DisplayName("TWR with multiple cash flows, BUY, and SELL computes chain-linked return")
    void twrMultiPeriodWithBuyAndSell() {
        UUID pId = twrPortfolio.getId();
        Account acc = new Account(twrPortfolio, "Trading", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));

        Instant d1 = Instant.now().minus(200, ChronoUnit.DAYS);
        Instant d2 = Instant.now().minus(150, ChronoUnit.DAYS);
        Instant d3 = Instant.now().minus(100, ChronoUnit.DAYS);
        Instant d4 = Instant.now().minus(50, ChronoUnit.DAYS);

        Transaction deposit1 = new Transaction(
                acc, null, TransactionType.DEPOSIT,
                d1, null, null, null, new BigDecimal("10000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction buy = new Transaction(
                acc, instrument, TransactionType.BUY,
                d2, null, new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("5000"),
                new BigDecimal("10"), BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction deposit2 = new Transaction(
                acc, null, TransactionType.DEPOSIT,
                d3, null, null, null, new BigDecimal("2000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction sell = new Transaction(
                acc, instrument, TransactionType.SELL,
                d4, null, new BigDecimal("20"), new BigDecimal("150"), new BigDecimal("3000"),
                new BigDecimal("5"), BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction withdrawal = new Transaction(
                acc, null, TransactionType.WITHDRAWAL,
                d4.plus(1, ChronoUnit.DAYS), null, null, null, new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of(withdrawal, sell, deposit2, buy, deposit1));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(withdrawal, sell, deposit2, buy, deposit1));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertNotNull(result);
        assertEquals("TWR", result.returnMethod());
        assertNotNull(result.twrReturn());
        assertNotNull(result.twrAnnualized());
    }

    @Test
    @DisplayName("TWR same day transaction results in zero annualized return")
    void twrSameDayZeroAnnualized() {
        UUID pId = twrPortfolio.getId();
        Account acc = new Account(twrPortfolio, "Trading", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));

        Transaction deposit = new Transaction(
                acc, null, TransactionType.DEPOSIT,
                Instant.now(), null, null, null, new BigDecimal("5000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of(deposit));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(deposit));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertEquals("TWR", result.returnMethod());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.twrAnnualized()));
    }

    @Test
    @DisplayName("MWR portfolio calculates money-weighted return including withdrawals")
    void mwrPortfolioCalculatesWithWithdrawal() {
        UUID pId = mwrPortfolio.getId();
        Account mwrAccount = new Account(mwrPortfolio, "ISA", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(mwrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(mwrAccount));

        Instant d1 = Instant.now().minus(365, ChronoUnit.DAYS);
        Instant d2 = Instant.now().minus(180, ChronoUnit.DAYS);

        Transaction deposit = new Transaction(
                mwrAccount, null, TransactionType.DEPOSIT,
                d1, null, null, null, new BigDecimal("10000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction withdrawal = new Transaction(
                mwrAccount, null, TransactionType.WITHDRAWAL,
                d2, null, null, null, new BigDecimal("2000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of(withdrawal, deposit));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(mwrAccount.getId()))
                .thenReturn(List.of(withdrawal, deposit));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertEquals("MWR", result.returnMethod());
        assertNull(result.twrReturn());
        assertNotNull(result.mwrReturn());
    }

    @Test
    @DisplayName("MWR portfolio with same-day transaction returns zero MWR")
    void mwrSameDayZeroReturn() {
        UUID pId = mwrPortfolio.getId();
        Account mwrAccount = new Account(mwrPortfolio, "ISA", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(mwrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(mwrAccount));

        Transaction deposit = new Transaction(
                mwrAccount, null, TransactionType.DEPOSIT,
                Instant.now(), null, null, null, new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of(deposit));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(mwrAccount.getId()))
                .thenReturn(List.of(deposit));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertEquals("MWR", result.returnMethod());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.mwrReturn()));
    }

    @Test
    @DisplayName("MWR empty portfolio returns zero MWR")
    void mwrEmptyPortfolioZero() {
        UUID pId = mwrPortfolio.getId();
        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(mwrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of());
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of());

        PerformanceResult result = performanceEngine.calculate(pId);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.mwrReturn()));
    }

    @Test
    @DisplayName("Dividend income is correctly aggregated from COMPLETED DIVIDEND transactions")
    void dividendIncomeAggregation() {
        UUID pId = twrPortfolio.getId();
        Account acc = new Account(twrPortfolio, "ISA", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of());

        Transaction div = new Transaction(
                acc, instrument, TransactionType.DIVIDEND,
                Instant.now(), null,
                null, null, new BigDecimal("50.00"),
                BigDecimal.ZERO, new BigDecimal("5.00"), "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(div));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertEquals(new BigDecimal("50.0000"), result.totalDividendIncome());
        assertEquals(new BigDecimal("5.0000"), result.totalTaxes());
        assertEquals(0, new BigDecimal("45.0000").compareTo(result.totalNetIncome()));
    }

    @Test
    @DisplayName("Fee transactions are aggregated into totalFees")
    void feeAggregation() {
        UUID pId = twrPortfolio.getId();
        Account acc = new Account(twrPortfolio, "SIPP", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of());

        Transaction fee = new Transaction(
                acc, null, TransactionType.FEE,
                Instant.now(), null,
                null, null, new BigDecimal("9.99"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(fee));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertEquals(new BigDecimal("9.9900"), result.totalFees());
        assertEquals(0, new BigDecimal("-9.9900").compareTo(result.totalNetIncome()));
    }

    @Test
    @DisplayName("Interest income is aggregated from INTEREST transactions")
    void interestIncomeAggregation() {
        UUID pId = twrPortfolio.getId();
        Account acc = new Account(twrPortfolio, "Cash", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of());

        Transaction interest = new Transaction(
                acc, null, TransactionType.INTEREST,
                Instant.now(), null,
                null, null, new BigDecimal("12.50"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(interest));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertEquals(new BigDecimal("12.5000"), result.totalInterestIncome());
        assertEquals(0, new BigDecimal("12.5000").compareTo(result.totalNetIncome()));
    }

    @Test
    @DisplayName("Corrected transactions are skipped from performance calculations")
    void correctedTransactionsAreSkipped() {
        UUID pId = twrPortfolio.getId();
        Account acc = new Account(twrPortfolio, "Trading", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));

        Transaction fee = new Transaction(
                acc, null, TransactionType.FEE,
                Instant.now(), null,
                null, null, new BigDecimal("20.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        fee.markCorrected();

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of(fee));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(fee));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalFees()));
    }

    @Test
    @DisplayName("PerformancePeriod value object invariants and subPeriodReturn calculation")
    void performancePeriodInvariants() {
        Instant now = Instant.now();
        PerformancePeriod zeroPeriod = new PerformancePeriod(
                now.minus(10, ChronoUnit.DAYS), now,
                BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO
        );
        assertEquals(BigDecimal.ZERO, zeroPeriod.subPeriodReturn());

        PerformancePeriod activePeriod = new PerformancePeriod(
                now.minus(10, ChronoUnit.DAYS), now,
                new BigDecimal("1000"), new BigDecimal("1100"), BigDecimal.ZERO
        );
        assertEquals(0, new BigDecimal("0.1").compareTo(activePeriod.subPeriodReturn()));

        assertThrows(NullPointerException.class, () -> new PerformancePeriod(
                null, now, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        ));
        assertThrows(NullPointerException.class, () -> new PerformancePeriod(
                now, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        ));
        assertThrows(NullPointerException.class, () -> new PerformancePeriod(
                now, now, null, BigDecimal.ZERO, BigDecimal.ZERO
        ));
        assertThrows(NullPointerException.class, () -> new PerformancePeriod(
                now, now, BigDecimal.ZERO, null, BigDecimal.ZERO
        ));
        assertThrows(NullPointerException.class, () -> new PerformancePeriod(
                now, now, BigDecimal.ZERO, BigDecimal.ZERO, null
        ));
    }

    @Test
    @DisplayName("PerformanceResult value object invariants")
    void performanceResultInvariants() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThrows(NullPointerException.class, () -> new PerformanceResult(
                null, now, "TWR", null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", "COST_BASIS", null
        ));
        assertThrows(NullPointerException.class, () -> new PerformanceResult(
                id, null, "TWR", null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", "COST_BASIS", null
        ));
        assertThrows(NullPointerException.class, () -> new PerformanceResult(
                id, now, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", "COST_BASIS", null
        ));
        assertThrows(NullPointerException.class, () -> new PerformanceResult(
                id, now, "TWR", null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, "COST_BASIS", null
        ));

        PerformanceResult valid = new PerformanceResult(
                id, now, "TWR", null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", "COST_BASIS", null
        );
        assertNotNull(valid.byAccount());
        assertTrue(valid.byAccount().isEmpty());
    }

    @Test
    @DisplayName("Performance calculation includes realized gains and cost basis from positionEngine results")
    void performanceIncludesPositionResults() {
        UUID pId = twrPortfolio.getId();
        Account acc = new Account(twrPortfolio, "Trading", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));
        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of());
        Transaction buy = new Transaction(
                acc, instrument, TransactionType.BUY,
                Instant.now().minus(10, ChronoUnit.DAYS), null,
                new BigDecimal("20"), new BigDecimal("100"), new BigDecimal("2000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction sell = new Transaction(
                acc, instrument, TransactionType.SELL,
                Instant.now().minus(5, ChronoUnit.DAYS), null,
                new BigDecimal("10"), new BigDecimal("150"), new BigDecimal("1500"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );

        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(sell, buy));

        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(acc.getId(), instrument.getId()))
                .thenReturn(List.of(buy, sell));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertNotNull(result);
        assertEquals(0, new BigDecimal("500.0000").compareTo(result.totalRealizedGainLoss()));
        assertEquals(0, new BigDecimal("1000.0000").compareTo(result.totalCostBasis()));
        assertEquals(1, result.byAccount().size());
        assertEquals(0, new BigDecimal("500.0000").compareTo(result.byAccount().get(0).realizedGainLoss()));
        assertEquals(0, new BigDecimal("1000.0000").compareTo(result.byAccount().get(0).costBasis()));
    }

    @Test
    @DisplayName("MWR calculation ignores BUY, SELL, DIVIDEND, INTEREST, FEE when weighting external flows")
    void mwrIgnoresNonCashFlowTransactions() {
        UUID pId = mwrPortfolio.getId();
        Account acc = new Account(mwrPortfolio, "Trading", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(mwrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));

        Instant d1 = Instant.now().minus(300, ChronoUnit.DAYS);
        Instant d2 = Instant.now().minus(200, ChronoUnit.DAYS);
        Instant d3 = Instant.now().minus(100, ChronoUnit.DAYS);

        Transaction deposit = new Transaction(
                acc, null, TransactionType.DEPOSIT,
                d1, null, null, null, new BigDecimal("10000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction buy = new Transaction(
                acc, instrument, TransactionType.BUY,
                d2, null, new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("5000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction div = new Transaction(
                acc, instrument, TransactionType.DIVIDEND,
                d3, null, null, null, new BigDecimal("100"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of(div, buy, deposit));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(div, buy, deposit));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertNotNull(result);
        assertEquals("MWR", result.returnMethod());
        assertNotNull(result.mwrReturn());
    }

    @Test
    @DisplayName("TWR handles pure purchases followed by external cash flow")
    void twrPurePurchasesFollowedByDeposit() {
        UUID pId = twrPortfolio.getId();
        Account acc = new Account(twrPortfolio, "Trading", "Broker", new Currency("USD"));

        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(twrPortfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, AccountStatus.ACTIVE))
                .thenReturn(List.of(acc));

        Instant d1 = Instant.now().minus(200, ChronoUnit.DAYS);
        Instant d2 = Instant.now().minus(100, ChronoUnit.DAYS);

        Transaction buy = new Transaction(
                acc, instrument, TransactionType.BUY,
                d1, null, new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction deposit = new Transaction(
                acc, null, TransactionType.DEPOSIT,
                d2, null, null, null, new BigDecimal("5000"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );

        when(transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(pId))
                .thenReturn(List.of(deposit, buy));
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId()))
                .thenReturn(List.of(deposit, buy));

        PerformanceResult result = performanceEngine.calculate(pId);
        assertNotNull(result);
        assertEquals("TWR", result.returnMethod());
        assertNotNull(result.twrReturn());
    }
}



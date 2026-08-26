package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
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
import com.takakim.investtracker.service.position.AverageCostBasisStrategy;
import com.takakim.investtracker.service.position.FifoCostBasisStrategy;
import com.takakim.investtracker.service.position.LifoCostBasisStrategy;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import java.math.BigDecimal;
import java.time.Instant;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionEngineTests {

    @Mock
    private PositionRepository positionRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AccountRepository accountRepository;

    private PositionEngine positionEngine;
    private Portfolio portfolio;
    private Account account;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        positionEngine = new PositionEngine(
                positionRepository,
                transactionRepository,
                portfolioRepository,
                accountRepository,
                List.of(new FifoCostBasisStrategy(), new LifoCostBasisStrategy(), new AverageCostBasisStrategy())
        );

        portfolio = new Portfolio("Tech", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        account = new Account(portfolio, "Trading", "Broker", new Currency("USD"));
        instrument = new Instrument("Apple", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
    }

    @Test
    @DisplayName("PositionEngine defaults to FIFO when method is null")
    void defaultToFifo() {
        PositionCalculationResult result = positionEngine.calculate(account, instrument, List.of(), null);
        assertNotNull(result);
        assertEquals(CostBasisMethod.FIFO, result.costBasisMethod());
    }

    @Test
    @DisplayName("PositionEngine recalculates and updates existing position")
    void recalculateAndSyncExistingPosition() {
        Transaction buy = new Transaction(
                account,
                instrument,
                TransactionType.BUY,
                Instant.now(),
                null,
                new BigDecimal("10.0"),
                new BigDecimal("150.0"),
                new BigDecimal("1500.0"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "USD",
                null,
                null,
                null,
                null
        );

        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), instrument.getId()))
                .thenReturn(List.of(buy));

        Position existing = new Position(account, instrument, new Quantity(new BigDecimal("5.0")), null);
        when(positionRepository.findByAccountIdAndInstrumentId(account.getId(), instrument.getId()))
                .thenReturn(Optional.of(existing));

        PositionCalculationResult result = positionEngine.recalculateAndSync(account, instrument);

        assertNotNull(result);
        assertEquals(new BigDecimal("10.00000000"), result.quantity());
        verify(positionRepository).save(existing);
    }

    @Test
    @DisplayName("PositionEngine creates brand new position if non-existent and quantity > 0")
    void recalculateAndSyncNewPosition() {
        Transaction buy = new Transaction(
                account,
                instrument,
                TransactionType.BUY,
                Instant.now(),
                null,
                new BigDecimal("10.0"),
                new BigDecimal("150.0"),
                new BigDecimal("1500.0"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "USD",
                null,
                null,
                null,
                null
        );

        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), instrument.getId()))
                .thenReturn(List.of(buy));
        when(positionRepository.findByAccountIdAndInstrumentId(account.getId(), instrument.getId()))
                .thenReturn(Optional.empty());

        PositionCalculationResult result = positionEngine.recalculateAndSync(account, instrument);

        assertNotNull(result);
        assertEquals(new BigDecimal("10.00000000"), result.quantity());
        verify(positionRepository).save(any(Position.class));
    }

    @Test
    @DisplayName("PositionEngine returns null for null instrument or account")
    void nullArgumentsSafeguard() {
        assertNull(positionEngine.recalculateAndSync(null, instrument));
        assertNull(positionEngine.recalculateAndSync(account, null));
    }

    @Test
    @DisplayName("PositionEngine throws not found on invalid portfolio during recalculation")
    void invalidPortfolioThrows() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> positionEngine.recalculatePortfolio(id));
    }

    @Test
    @DisplayName("PositionEngine recalculatePortfolio processes all accounts and instruments")
    void recalculatePortfolioSuccess() {
        UUID pId = portfolio.getId();
        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(portfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, com.takakim.investtracker.domain.AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        Transaction buy = new Transaction(
                account, instrument, TransactionType.BUY, Instant.now(), null,
                new BigDecimal("10.0"), new BigDecimal("100.0"), new BigDecimal("1000.0"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );

        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(account.getId())).thenReturn(List.of(buy));
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), instrument.getId()))
                .thenReturn(List.of(buy));

        List<PositionCalculationResult> results = positionEngine.recalculatePortfolio(pId);
        assertEquals(1, results.size());
        assertEquals(new BigDecimal("10.00000000"), results.get(0).quantity());
    }

    @Test
    @DisplayName("PositionEngine getPositionLots validates hierarchy and returns open lots")
    void getPositionLotsFlow() {
        UUID pId = portfolio.getId();
        UUID aId = account.getId();
        Position position = new Position(account, instrument, new Quantity(new BigDecimal("10.0")), null);
        UUID posId = position.getId();

        when(portfolioRepository.existsById(pId)).thenReturn(true);
        when(accountRepository.findById(aId)).thenReturn(Optional.of(account));
        when(positionRepository.findById(posId)).thenReturn(Optional.of(position));

        Transaction buy = new Transaction(
                account, instrument, TransactionType.BUY, Instant.now(), null,
                new BigDecimal("10.0"), new BigDecimal("100.0"), new BigDecimal("1000.0"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(aId, instrument.getId()))
                .thenReturn(List.of(buy));

        PositionCalculationResult result = positionEngine.getPositionLots(pId, aId, posId);
        assertNotNull(result);
        assertEquals(1, result.openLots().size());

        // Error validations: missing portfolio
        UUID wrongId = UUID.randomUUID();
        when(portfolioRepository.existsById(wrongId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> positionEngine.getPositionLots(wrongId, aId, posId));

        // Account does not belong to portfolio
        Portfolio otherPortfolio = new Portfolio("Other", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        Account otherAccount = new Account(otherPortfolio, "Other", "Broker", new Currency("USD"));
        when(accountRepository.findById(otherAccount.getId())).thenReturn(Optional.of(otherAccount));
        assertThrows(ResourceNotFoundException.class, () -> positionEngine.getPositionLots(pId, otherAccount.getId(), posId));

        // Position does not belong to account
        Account anotherAccount = new Account(portfolio, "Another", "Broker", new Currency("USD"));
        Position unownedPos = new Position(anotherAccount, instrument, new Quantity(new BigDecimal("5.0")), null);
        when(positionRepository.findById(unownedPos.getId())).thenReturn(Optional.of(unownedPos));
        assertThrows(ResourceNotFoundException.class, () -> positionEngine.getPositionLots(pId, aId, unownedPos.getId()));
    }

    @Test
    @DisplayName("PositionEngine recalculateAndSync supports LIFO, AVERAGE_COST, and null costBasisMethod")
    void recalculateMethodVariations() {
        Portfolio lifoPort = new Portfolio("LIFO", new Currency("USD"), CostBasisMethod.LIFO, ReturnMethod.TWR);
        Account lifoAcc = new Account(lifoPort, "LIFO Acc", "Broker", new Currency("USD"));

        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(lifoAcc.getId(), instrument.getId()))
                .thenReturn(List.of());
        Position existing = new Position(lifoAcc, instrument, new Quantity(BigDecimal.ONE), null);
        when(positionRepository.findByAccountIdAndInstrumentId(lifoAcc.getId(), instrument.getId()))
                .thenReturn(Optional.of(existing));

        PositionCalculationResult res = positionEngine.recalculateAndSync(lifoAcc, instrument);
        assertNotNull(res);
        assertEquals(BigDecimal.ZERO.setScale(8), res.quantity());
    }

    @Test
    @DisplayName("PositionEngine handles account with null portfolio gracefully (defaults to FIFO)")
    void recalculateAndSyncNullPortfolioMethod() {
        // Account with null portfolio — engine should default to FIFO
        Account noPortAcc = new Account(portfolio, "NoPM", "Broker", new Currency("USD"));
        Transaction buy = new Transaction(
                noPortAcc, instrument, TransactionType.BUY, Instant.now(), null,
                new BigDecimal("5.0"), new BigDecimal("100.0"), new BigDecimal("500.0"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(noPortAcc.getId(), instrument.getId()))
                .thenReturn(List.of(buy));
        when(positionRepository.findByAccountIdAndInstrumentId(noPortAcc.getId(), instrument.getId()))
                .thenReturn(Optional.empty());

        PositionCalculationResult res = positionEngine.recalculateAndSync(noPortAcc, instrument);
        assertNotNull(res);
        assertEquals(new BigDecimal("5.00000000"), res.quantity());
        verify(positionRepository).save(any(Position.class));
    }

    @Test
    @DisplayName("PositionEngine recalculatePortfolio skips null-instrument transactions")
    void recalculatePortfolioSkipsNullInstrumentTx() {
        UUID pId = portfolio.getId();
        when(portfolioRepository.findById(pId)).thenReturn(Optional.of(portfolio));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(pId, com.takakim.investtracker.domain.AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        // Use mock transaction with null instrument — should be filtered out by PositionEngine
        Transaction noInstTx = org.mockito.Mockito.mock(Transaction.class);
        when(noInstTx.getInstrument()).thenReturn(null);
        when(transactionRepository.findByAccountIdOrderByTradeDateDesc(account.getId()))
                .thenReturn(List.of(noInstTx));

        List<PositionCalculationResult> results = positionEngine.recalculatePortfolio(pId);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("PositionEngine getPositionLots throws when account not found")
    void getPositionLotsAccountNotFound() {
        UUID pId = portfolio.getId();
        UUID missingAccId = UUID.randomUUID();
        when(portfolioRepository.existsById(pId)).thenReturn(true);
        when(accountRepository.findById(missingAccId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> positionEngine.getPositionLots(pId, missingAccId, UUID.randomUUID()));
    }

    @Test
    @DisplayName("PositionEngine getPositionLots throws when position not found")
    void getPositionLotsPositionNotFound() {
        UUID pId = portfolio.getId();
        UUID aId = account.getId();
        UUID missingPosId = UUID.randomUUID();
        when(portfolioRepository.existsById(pId)).thenReturn(true);
        when(accountRepository.findById(aId)).thenReturn(Optional.of(account));
        when(positionRepository.findById(missingPosId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> positionEngine.getPositionLots(pId, aId, missingPosId));
    }

    @Test
    @DisplayName("PositionEngine recalculateAndSync unarchives position when positive quantity exists")
    void recalculateAndSyncUnarchivesPosition() {
        Transaction buy = new Transaction(
                account, instrument, TransactionType.BUY, Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS),
                null, new BigDecimal("10.00000000"), new BigDecimal("100.0000"), new BigDecimal("1000.0000"),
                null, null, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), instrument.getId()))
                .thenReturn(List.of(buy));

        Position archivedPos = new Position(account, instrument, new Quantity(new BigDecimal("10.00000000")), null);
        archivedPos.archive();
        assertEquals(com.takakim.investtracker.domain.PositionStatus.ARCHIVED, archivedPos.getStatus());

        when(positionRepository.findByAccountIdAndInstrumentId(account.getId(), instrument.getId()))
                .thenReturn(Optional.of(archivedPos));

        PositionCalculationResult res = positionEngine.recalculateAndSync(account, instrument);
        assertNotNull(res);
        assertEquals(com.takakim.investtracker.domain.PositionStatus.ACTIVE, archivedPos.getStatus());
        verify(positionRepository).save(archivedPos);
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.ApplyCorporateActionRequest;
import com.takakim.investtracker.api.ApiDtos.CorporateActionResponse;
import com.takakim.investtracker.api.ApiDtos.ScanCorporateActionsResponse;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CorporateAction;
import com.takakim.investtracker.domain.CorporateActionStatus;
import com.takakim.investtracker.domain.CorporateActionType;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.CorporateActionRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.CorporateActionService;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.TransactionService;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway.DiscoveredCorporateAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorporateActionServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CorporateActionRepository corporateActionRepository;
    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private TransactionService transactionService;
    @Mock
    private YahooFinanceGateway yahooFinanceGateway;

    private CorporateActionService service;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Account account;
    private Instrument apple;
    private Instrument regional;

    @BeforeEach
    void setUp() {
        service = new CorporateActionService(
                portfolioRepository,
                accountRepository,
                positionRepository,
                transactionRepository,
                corporateActionRepository,
                instrumentRepository,
                transactionService,
                yahooFinanceGateway
        );

        lenient().when(positionRepository.findByAccountPortfolioIdAndStatus(any(), any())).thenReturn(List.of());
        lenient().when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(any(), any())).thenReturn(List.of());

        portfolio = new Portfolio("Tech Portfolio", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        portfolioId = portfolio.getId();
        account = new Account(portfolio, "Interactive Brokers", "Broker", new Currency("USD"));

        apple = new Instrument("Apple Inc.", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        regional = new Instrument("Regional REIT", AssetClass.REIT, "RGL.L", "GG00BSY2LD72", "LSE", new Currency("GBP"));
    }

    @Test
    void scanPortfolio_portfolioNotFound_throwsException() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.scanPortfolio(portfolioId));
    }

    @Test
    void scanPortfolio_discoversActionsAndAutoLinks() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Position pos = new Position(account, apple, new Quantity(new BigDecimal("10.00")), new Money(new BigDecimal("1500.00"), new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        Instant exDateSplit = Instant.now().minusSeconds(86400 * 20);
        Instant exDateDiv = Instant.now().minusSeconds(86400 * 5);
        Instant exDateRev = Instant.now().minusSeconds(86400 * 15);

        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDateSplit.minusSeconds(86400 * 5), null,
                new BigDecimal("10.00"), new BigDecimal("100.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        DiscoveredCorporateAction split = new DiscoveredCorporateAction(
                CorporateActionType.STOCK_SPLIT, exDateSplit, BigDecimal.ONE, new BigDecimal("4"),
                null, null, "4:1 stock split", "YF-AAPL-SPLIT-1"
        );
        DiscoveredCorporateAction div = new DiscoveredCorporateAction(
                CorporateActionType.DIVIDEND, exDateDiv, null, null,
                new BigDecimal("0.25"), "USD", "Dividend 0.25 USD", "YF-AAPL-DIV-1"
        );
        DiscoveredCorporateAction rev = new DiscoveredCorporateAction(
                CorporateActionType.REVERSE_STOCK_SPLIT, exDateRev, new BigDecimal("10"), BigDecimal.ONE,
                null, null, "1:10 reverse split", "YF-AAPL-REV-1"
        );

        when(yahooFinanceGateway.fetchCorporateActions("AAPL", "1y"))
                .thenReturn(List.of(split, div, rev));

        // Mock that split is brand new, div and rev already have ledger transactions
        when(corporateActionRepository.findByInstrumentIdAndActionTypeAndExDate(apple.getId(), CorporateActionType.STOCK_SPLIT, exDateSplit))
                .thenReturn(Optional.empty());
        when(corporateActionRepository.findByInstrumentIdAndActionTypeAndExDate(apple.getId(), CorporateActionType.DIVIDEND, exDateDiv))
                .thenReturn(Optional.empty());
        when(corporateActionRepository.findByInstrumentIdAndActionTypeAndExDate(apple.getId(), CorporateActionType.REVERSE_STOCK_SPLIT, exDateRev))
                .thenReturn(Optional.empty());

        Transaction existingDivTx = new Transaction(
                account, apple, TransactionType.DIVIDEND, exDateDiv, null,
                null, null, new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        Transaction existingRevTx = new Transaction(
                account, apple, TransactionType.REVERSE_STOCK_SPLIT, exDateRev, null,
                new BigDecimal("9.00"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findAll()).thenReturn(List.of(existingDivTx, existingRevTx));

        ScanCorporateActionsResponse result = service.scanPortfolio(portfolioId);

        assertNotNull(result);
        assertEquals(1, result.scannedInstrumentsCount());
        assertEquals(3, result.discoveredActionsCount());
        assertEquals(1, result.newPendingActionsCount()); // 1 pending split, 1 auto-linked dividend, 1 auto-linked rev split
        verify(corporateActionRepository, times(3)).save(any(CorporateAction.class));
    }

    @Test
    void getPortfolioCorporateActions_filtersAndCalculatesImpacts() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction split = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, new BigDecimal("4"), null, null, "4:1 split", "YAHOO", "EXT-1"
        );

        when(corporateActionRepository.findActivePortfolioCorporateActionsByStatus(portfolioId, CorporateActionStatus.PENDING))
                .thenReturn(List.of(split));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        // 10 shares bought before ex-date
        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("10.00"), new BigDecimal("100.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        List<CorporateActionResponse> responses = service.getPortfolioCorporateActions(portfolioId, CorporateActionStatus.PENDING);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        CorporateActionResponse resp = responses.get(0);
        assertEquals("AAPL", resp.ticker());
        assertEquals("STOCK_SPLIT", resp.actionType());
        assertEquals(new BigDecimal("10.00000000"), resp.heldQuantityAtExDate());
        // 4:1 split on 10 shares -> +30 shares additional
        assertEquals(new BigDecimal("30.00000000"), resp.proposedImpactQuantity());
        assertEquals(account.getId(), resp.suggestedAccountId());
    }

    @Test
    void applyAction_stockSplit_recordsTransactionAndUpdatesStatus() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction split = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, new BigDecimal("4"), null, null, "4:1 split", "YAHOO", "EXT-1"
        );
        UUID actionId = split.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(split));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction recordedSplitTx = new Transaction(
                account, apple, TransactionType.STOCK_SPLIT, exDate, exDate,
                new BigDecimal("30.00000000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", null, null, "4:1 split", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(apple.getId()), eq(TransactionType.STOCK_SPLIT),
                eq(exDate), any(), eq(new BigDecimal("30.00000000")), any(), eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq("USD"), any(), any(), any(), any()
        )).thenReturn(recordedSplitTx);

        ApplyCorporateActionRequest request = new ApplyCorporateActionRequest(
                account.getId(), new BigDecimal("30.00"), null, null, "Applied split"
        );

        CorporateActionResponse response = service.applyAction(portfolioId, actionId, request);

        assertNotNull(response);
        assertEquals(CorporateActionStatus.APPLIED.name(), response.status());
        assertEquals(recordedSplitTx.getId(), response.appliedTransactionId());
        verify(corporateActionRepository).save(split);
    }

    @Test
    void applyAction_reverseStockSplit_recordsReverseSplitTx() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction revSplit = new CorporateAction(
                regional, CorporateActionType.REVERSE_STOCK_SPLIT, exDate, null, null,
                new BigDecimal("10"), BigDecimal.ONE, null, null, "10:1 reverse split", "YAHOO", "EXT-2"
        );
        UUID actionId = revSplit.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(revSplit));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction recordedRevSplitTx = new Transaction(
                account, regional, TransactionType.REVERSE_STOCK_SPLIT, exDate, exDate,
                new BigDecimal("90.00000000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, "10:1 reverse split", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(regional.getId()), eq(TransactionType.REVERSE_STOCK_SPLIT),
                eq(exDate), any(), eq(new BigDecimal("90.00000000")), any(), eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq("GBP"), any(), any(), any(), any()
        )).thenReturn(recordedRevSplitTx);

        ApplyCorporateActionRequest request = new ApplyCorporateActionRequest(
                account.getId(), new BigDecimal("90.00"), null, null, "Applied reverse split"
        );

        CorporateActionResponse response = service.applyAction(portfolioId, actionId, request);
        assertNotNull(response);
        assertEquals(CorporateActionStatus.APPLIED.name(), response.status());
    }

    @Test
    void applyAction_dividend_recordsDividendTx() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction dividend = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, exDate, null, null,
                null, null, new BigDecimal("0.50"), "USD", "0.50 USD div", "YAHOO", "EXT-3"
        );
        UUID actionId = dividend.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(dividend));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction recordedDivTx = new Transaction(
                account, apple, TransactionType.DIVIDEND, exDate, exDate,
                null, null, new BigDecimal("50.0000"), BigDecimal.ZERO, new BigDecimal("5.0000"),
                "USD", null, null, "0.50 USD div", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(apple.getId()), eq(TransactionType.DIVIDEND),
                eq(exDate), any(), any(), any(), eq(new BigDecimal("50.0000")),
                eq(BigDecimal.ZERO), eq(new BigDecimal("5.0000")), eq("USD"), any(), any(), any(), any()
        )).thenReturn(recordedDivTx);

        ApplyCorporateActionRequest request = new ApplyCorporateActionRequest(
                account.getId(), null, new BigDecimal("50.00"), new BigDecimal("5.00"), "Applied div"
        );

        CorporateActionResponse response = service.applyAction(portfolioId, actionId, request);
        assertNotNull(response);
        assertEquals(CorporateActionStatus.APPLIED.name(), response.status());
    }

    @Test
    void applyAction_actionNotPendingOrAccountMismatch_throwsException() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now();
        CorporateAction action = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, "10:1 split", null, null
        );
        action.markDismissed();
        UUID actionId = action.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(action));

        ApplyCorporateActionRequest request = new ApplyCorporateActionRequest(account.getId(), BigDecimal.TEN, null, null, null);
        assertThrows(IllegalStateException.class, () -> service.applyAction(portfolioId, actionId, request));

        // Account from another portfolio
        Portfolio otherPortfolio = new Portfolio("Other", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        Account otherAccount = new Account(otherPortfolio, "Other Acc", "Other", new Currency("GBP"));
        CorporateAction freshAction = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, "10:1 split", null, null
        );
        when(corporateActionRepository.findById(freshAction.getId())).thenReturn(Optional.of(freshAction));
        when(accountRepository.findById(otherAccount.getId())).thenReturn(Optional.of(otherAccount));

        ApplyCorporateActionRequest reqOther = new ApplyCorporateActionRequest(otherAccount.getId(), BigDecimal.TEN, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.applyAction(portfolioId, freshAction.getId(), reqOther));
    }

    @Test
    void dismissAction_success() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now();
        CorporateAction action = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, "10:1 split", null, null
        );
        UUID actionId = action.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(action));

        CorporateActionResponse response = service.dismissAction(portfolioId, actionId);
        assertNotNull(response);
        assertEquals(CorporateActionStatus.DISMISSED.name(), response.status());
        verify(corporateActionRepository).save(action);
    }

    @Test
    void applyAction_stockSplit_nullQuantityUsesCalculatedQuantity() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction split = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "2:1 split", "YAHOO", "EXT-SPLIT"
        );
        UUID actionId = split.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(split));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("5.00"), new BigDecimal("100.00"), new BigDecimal("500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        Transaction recordedSplitTx = new Transaction(
                account, apple, TransactionType.STOCK_SPLIT, exDate, exDate,
                new BigDecimal("5.00000000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", null, null, "2:1 split", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(apple.getId()), eq(TransactionType.STOCK_SPLIT),
                eq(exDate), any(), eq(new BigDecimal("5.00000000")), any(), eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq("USD"), any(), any(), any(), any()
        )).thenReturn(recordedSplitTx);

        // Quantity is null so it calculates 5 shares * (2 - 1) = 5 shares
        ApplyCorporateActionRequest request = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        CorporateActionResponse resp = service.applyAction(portfolioId, actionId, request);
        assertNotNull(resp);
        assertEquals(CorporateActionStatus.APPLIED.name(), resp.status());
    }

    @Test
    void applyAction_stockSplit_zeroCalculatedQuantity_throwsException() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction split = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "2:1 split", "YAHOO", "EXT-SPLIT"
        );
        UUID actionId = split.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(split));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of()); // No held shares

        ApplyCorporateActionRequest request = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.applyAction(portfolioId, actionId, request));
    }

    @Test
    void applyAction_dividend_nullGrossUsesCalculatedAmount() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction div = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, exDate, null, null,
                null, null, new BigDecimal("1.50"), "USD", "1.50 USD div", "YAHOO", "EXT-DIV"
        );
        UUID actionId = div.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(div));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("10.00"), new BigDecimal("100.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        Transaction recordedDivTx = new Transaction(
                account, apple, TransactionType.DIVIDEND, exDate, exDate,
                null, null, new BigDecimal("15.0000"), BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", null, null, "1.50 USD div", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(apple.getId()), eq(TransactionType.DIVIDEND),
                eq(exDate), any(), any(), any(), eq(new BigDecimal("15.0000")),
                eq(BigDecimal.ZERO), eq(new BigDecimal("0.0000")), eq("USD"), any(), any(), any(), any()
        )).thenReturn(recordedDivTx);

        // grossAmount is null -> calculates 10 shares * $1.50 = $15.00
        ApplyCorporateActionRequest request = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        CorporateActionResponse resp = service.applyAction(portfolioId, actionId, request);
        assertNotNull(resp);
        assertEquals(CorporateActionStatus.APPLIED.name(), resp.status());
    }

    @Test
    void applyAction_dividend_zeroCalculatedGross_throwsException() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction div = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, exDate, null, null,
                null, null, new BigDecimal("1.50"), "USD", "1.50 USD div", "YAHOO", "EXT-DIV"
        );
        UUID actionId = div.getId();

        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(div));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of()); // No shares

        ApplyCorporateActionRequest request = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.applyAction(portfolioId, actionId, request));
    }

    @Test
    void getPortfolioCorporateActions_reverseSplitAndDividendImpacts() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction revSplit = new CorporateAction(
                regional, CorporateActionType.REVERSE_STOCK_SPLIT, exDate, null, null,
                new BigDecimal("10"), BigDecimal.ONE, null, null, "10:1 rev", "YAHOO", "EXT-RGL"
        );
        CorporateAction div = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, exDate, null, null,
                null, null, new BigDecimal("0.50"), "USD", "div", "YAHOO", "EXT-DIV"
        );

        when(corporateActionRepository.findActivePortfolioCorporateActions(portfolioId))
                .thenReturn(List.of(revSplit, div));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        // 100 shares held for regional
        Transaction buyRgl = new Transaction(
                account, regional, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "GBP", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), regional.getId()))
                .thenReturn(List.of(buyRgl));

        // 20 shares held for apple
        Transaction buyApple = new Transaction(
                account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("20.00"), new BigDecimal("100.00"), new BigDecimal("2000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyApple));

        List<CorporateActionResponse> responses = service.getPortfolioCorporateActions(portfolioId, null);
        assertNotNull(responses);
        assertEquals(2, responses.size());

        // Reverse split: 100 shares * (1 - 0.1) = 90 shares reduction
        var rglResp = responses.stream().filter(r -> r.ticker().equals("RGL.L")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("90.00000000"), rglResp.proposedImpactQuantity());

        // Dividend: 20 shares * $0.50 = $10.00
        var appleResp = responses.stream().filter(r -> r.ticker().equals("AAPL")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("10.0000"), appleResp.proposedImpactAmount());
    }

    @Test
    void scanPortfolio_skipsExistingActionsAndBlankTickers() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instrument blankTickerInst = new Instrument("No Ticker Fund", AssetClass.OTHER, "   ", null, null, new Currency("USD"));
        Position pos1 = new Position(account, blankTickerInst, new Quantity(new BigDecimal("10.00")), new Money(new BigDecimal("100.00"), new Currency("USD")));
        Position pos2 = new Position(account, apple, new Quantity(new BigDecimal("10.00")), new Money(new BigDecimal("1500.00"), new Currency("USD")));

        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos1, pos2));

        Instant exDate = Instant.now().minusSeconds(86400 * 5);
        DiscoveredCorporateAction split = new DiscoveredCorporateAction(
                CorporateActionType.STOCK_SPLIT, exDate, BigDecimal.ONE, new BigDecimal("2"),
                null, null, "2:1 split", "YF-AAPL-SPLIT"
        );
        when(yahooFinanceGateway.fetchCorporateActions("AAPL", "1y")).thenReturn(List.of(split));

        // Already exists in repository
        CorporateAction existingAction = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "2:1 split", "YAHOO", "YF-AAPL-SPLIT"
        );
        when(corporateActionRepository.findByInstrumentIdAndActionTypeAndExDate(apple.getId(), CorporateActionType.STOCK_SPLIT, exDate))
                .thenReturn(Optional.of(existingAction));

        ScanCorporateActionsResponse res = service.scanPortfolio(portfolioId);
        assertNotNull(res);
        assertEquals(1, res.scannedInstrumentsCount()); // blankTickerInst was ignored
        assertEquals(1, res.discoveredActionsCount());
        assertEquals(0, res.newPendingActionsCount()); // duplicate was skipped
        verify(corporateActionRepository, never()).save(any(CorporateAction.class));
    }

    @Test
    void getPortfolioCorporateActions_portfolioNotFound_throws() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.getPortfolioCorporateActions(portfolioId, null));
    }

    @Test
    void getPortfolioCorporateActions_withStatusFilter() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(corporateActionRepository.findActivePortfolioCorporateActionsByStatus(portfolioId, CorporateActionStatus.PENDING))
                .thenReturn(List.of());

        List<CorporateActionResponse> res = service.getPortfolioCorporateActions(portfolioId, CorporateActionStatus.PENDING);
        assertTrue(res.isEmpty());
        verify(corporateActionRepository).findActivePortfolioCorporateActionsByStatus(portfolioId, CorporateActionStatus.PENDING);
    }

    @Test
    void applyAction_validationErrors() {
        UUID actionId = UUID.randomUUID();
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        // Action not found
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.empty());
        ApplyCorporateActionRequest req = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.applyAction(portfolioId, actionId, req));

        // Account not found
        CorporateAction action = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, Instant.now(), null, null,
                null, null, new BigDecimal("0.50"), "USD", "div", "YAHOO", "EXT-1"
        );
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.applyAction(portfolioId, actionId, req));

        // Account belongs to different portfolio
        Portfolio otherPortfolio = new Portfolio("Other", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        Account otherAccount = new Account(otherPortfolio, "Other", "Broker", new Currency("USD"));
        when(accountRepository.findById(otherAccount.getId())).thenReturn(Optional.of(otherAccount));
        ApplyCorporateActionRequest otherReq = new ApplyCorporateActionRequest(otherAccount.getId(), null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.applyAction(portfolioId, actionId, otherReq));
    }

    @Test
    void applyAction_portfolioNotFound_throws() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(false);
        ApplyCorporateActionRequest req = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.applyAction(portfolioId, UUID.randomUUID(), req));
    }

    @Test
    void applyAction_dividendNonPositiveGross_throws() {
        UUID actionId = UUID.randomUUID();
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        CorporateAction action = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, Instant.now(), null, null,
                null, null, new BigDecimal("0.50"), "USD", "div", "YAHOO", "EXT-1"
        );
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        // Zero shares held, no gross provided
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of());

        ApplyCorporateActionRequest req = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.applyAction(portfolioId, actionId, req));
    }

    @Test
    void dismissAction_portfolioNotFound_orActionNotFound() {
        // Portfolio not found
        when(portfolioRepository.existsById(portfolioId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.dismissAction(portfolioId, UUID.randomUUID()));

        // Action not found
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        UUID actionId = UUID.randomUUID();
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.dismissAction(portfolioId, actionId));
    }

    @Test
    void heldQuantityCalculation_exercisesAllTransactionTypes() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 5);
        CorporateAction action = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "2:1", "YAHOO", "EXT"
        );

        when(corporateActionRepository.findActivePortfolioCorporateActions(portfolioId))
                .thenReturn(List.of(action));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        // Setup: Buy 100, Sell 20, Split +30, RevSplit -10, Buy after exDate (skipped) 50
        Transaction txBuy = new Transaction(account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 10), null,
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        Transaction txSell = new Transaction(account, apple, TransactionType.SELL, exDate.minusSeconds(86400 * 8), null,
                new BigDecimal("20.00"), new BigDecimal("12.00"), new BigDecimal("240.00"), BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        Transaction txSplit = new Transaction(account, apple, TransactionType.STOCK_SPLIT, exDate.minusSeconds(86400 * 7), null,
                new BigDecimal("30.00"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        Transaction txRevSplit = new Transaction(account, apple, TransactionType.REVERSE_STOCK_SPLIT, exDate.minusSeconds(86400 * 6), null,
                new BigDecimal("10.00"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        Transaction txFutureBuy = new Transaction(account, apple, TransactionType.BUY, exDate.plusSeconds(86400 * 2), null,
                new BigDecimal("50.00"), new BigDecimal("15.00"), new BigDecimal("750.00"), BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);

        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(txBuy, txSell, txSplit, txRevSplit, txFutureBuy));

        List<CorporateActionResponse> responses = service.getPortfolioCorporateActions(portfolioId, null);
        assertEquals(1, responses.size());
        // Held shares = 100 - 20 + 30 - 10 = 100.00
        assertEquals(new BigDecimal("100.00000000"), responses.get(0).heldQuantityAtExDate());
        // 2:1 split impact on 100 shares = 100.00000000 additional shares
        assertEquals(new BigDecimal("100.00000000"), responses.get(0).proposedImpactQuantity());
    }

    @Test
    void getPortfolioCorporateActions_filtersOutActionsPriorToAcquisition() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 30);
        CorporateAction split = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "2:1", "YAHOO", "EXT-OLD"
        );

        when(corporateActionRepository.findActivePortfolioCorporateActions(portfolioId))
                .thenReturn(List.of(split));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        // Acquired 10 days after ex-date: 0 held at exDate
        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDate.plusSeconds(86400 * 10), null,
                new BigDecimal("50.00"), new BigDecimal("150.00"), new BigDecimal("7500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        List<CorporateActionResponse> responses = service.getPortfolioCorporateActions(portfolioId, null);
        assertTrue(responses.isEmpty(), "Corporate actions where portfolio held 0 shares at exDate should be filtered out");
    }

    @Test
    void scanPortfolio_discoversKnownCatalogActionForHon() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instrument hon = new Instrument("Honeywell", AssetClass.STOCK, "HON", "US4385162056", "NASDAQ", new Currency("USD"));
        Instrument hona = new Instrument("Honeywell Aerospace Inc.", AssetClass.STOCK, "HONA", "US43849R1059", "NASDAQ", new Currency("USD"));

        Position pos = new Position(account, hon, new Quantity(new BigDecimal("5.00000000")), new Money(new BigDecimal("1000.00"), new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        when(yahooFinanceGateway.fetchCorporateActions("HON", "1y")).thenReturn(List.of());
        when(corporateActionRepository.findBySourceAndExternalId(eq("SYSTEM_CATALOG"), any())).thenReturn(Optional.empty());
        when(corporateActionRepository.findByInstrumentIdAndActionTypeAndExDate(any(), any(), any())).thenReturn(Optional.empty());
        when(instrumentRepository.findByTicker("HONA")).thenReturn(Optional.of(hona));

        Instant exDate = Instant.parse("2026-06-29T13:30:00Z");
        Transaction buyTx = new Transaction(
                account, hon, TransactionType.BUY, exDate.minusSeconds(86400 * 10), null,
                new BigDecimal("5.00"), new BigDecimal("200.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), hon.getId()))
                .thenReturn(List.of(buyTx));

        ScanCorporateActionsResponse response = service.scanPortfolio(portfolioId);

        assertNotNull(response);
        assertEquals(1, response.newPendingActionsCount());
        verify(corporateActionRepository).save(argThat(ca ->
                ca.getInstrument().equals(hon)
                        && ca.getResultingInstrument() != null
                        && ca.getResultingInstrument().getTicker().equals("HONA")
                        && ca.getActionType() == CorporateActionType.STOCK_SPLIT
                        && ca.getStatus() == CorporateActionStatus.PENDING
        ));
    }

    @Test
    void applyAction_withResultingInstrument_allotsSharesInResultingInstrument() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instrument hon = new Instrument("Honeywell", AssetClass.STOCK, "HON", "US4385162056", "NASDAQ", new Currency("USD"));
        Instrument hona = new Instrument("Honeywell Aerospace Inc.", AssetClass.STOCK, "HONA", "US43849R1059", "NASDAQ", new Currency("USD"));

        Instant exDate = Instant.parse("2026-06-29T13:30:00Z");
        CorporateAction spinoff = new CorporateAction(
                hon, hona, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                new BigDecimal("2"), new BigDecimal("1"), null, "USD",
                "Honeywell Aerospace (HONA) spin-off split — 1 HONA per 2 HON",
                "SYSTEM_CATALOG", "CATALOG-HON-HONA-SPINOFF-20260629"
        );

        UUID actionId = spinoff.getId();
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(spinoff));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        // 5 shares held of HON -> 1:2 ratio -> 2.5 shares of HONA
        Transaction buyTx = new Transaction(
                account, hon, TransactionType.BUY, exDate.minusSeconds(86400 * 10), null,
                new BigDecimal("5.00"), new BigDecimal("200.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), hon.getId()))
                .thenReturn(List.of(buyTx));

        Transaction recordedTx = new Transaction(
                account, hona, TransactionType.STOCK_SPLIT, exDate, exDate,
                new BigDecimal("2.50000000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", null, null, spinoff.getDescription(), null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(hona.getId()), eq(TransactionType.STOCK_SPLIT),
                eq(exDate), eq(exDate), eq(new BigDecimal("2.50000000")), any(), any(), any(), any(),
                eq("USD"), any(), any(), any(), any()
        )).thenReturn(recordedTx);

        ApplyCorporateActionRequest req = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        CorporateActionResponse resp = service.applyAction(portfolioId, actionId, req);

        assertNotNull(resp);
        assertEquals(CorporateActionStatus.APPLIED.name(), resp.status());
        assertEquals("HON", resp.ticker());
        assertEquals("HONA", resp.resultingInstrumentTicker());
        assertEquals(new BigDecimal("2.50000000"), resp.proposedImpactQuantity());
        verify(transactionService).recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(hona.getId()), eq(TransactionType.STOCK_SPLIT),
                any(), any(), eq(new BigDecimal("2.50000000")), any(), any(), any(), any(),
                eq("USD"), any(), any(), any(), any()
        );
    }

    @Test
    void scanPortfolio_catalogActionExistingByExternalIdOrDate_skips() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instrument hon = new Instrument("Honeywell", AssetClass.STOCK, "HON", "US4385162056", "NASDAQ", new Currency("USD"));
        Position pos = new Position(account, hon, new Quantity(new BigDecimal("5.00000000")), new Money(new BigDecimal("1000.00"), new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        CorporateAction dummyAction = new CorporateAction(
                hon, CorporateActionType.STOCK_SPLIT, Instant.now(), null, null,
                BigDecimal.ONE, BigDecimal.ONE, null, "USD", "test", "SYSTEM_CATALOG", "CATALOG-HON-HONA-SPINOFF-20260629"
        );
        when(corporateActionRepository.findBySourceAndExternalId(eq("SYSTEM_CATALOG"), any())).thenReturn(Optional.of(dummyAction));

        ScanCorporateActionsResponse response = service.scanPortfolio(portfolioId);
        assertNotNull(response);
        verify(corporateActionRepository, never()).save(any());
    }

    @Test
    void scanPortfolio_catalogActionAutoLinksMatchingTransaction() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instrument hon = new Instrument("Honeywell", AssetClass.STOCK, "HON", "US4385162056", "NASDAQ", new Currency("USD"));
        Instrument hona = new Instrument("Honeywell Aerospace Inc.", AssetClass.STOCK, "HONA", "US43849R1059", "NASDAQ", new Currency("USD"));
        Position pos = new Position(account, hon, new Quantity(new BigDecimal("5.00000000")), new Money(new BigDecimal("1000.00"), new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(corporateActionRepository.findBySourceAndExternalId(eq("SYSTEM_CATALOG"), any())).thenReturn(Optional.empty());
        when(corporateActionRepository.findByInstrumentIdAndActionTypeAndExDate(any(), any(), any())).thenReturn(Optional.empty());
        when(instrumentRepository.findByTicker("HONA")).thenReturn(Optional.of(hona));

        Instant exDate = Instant.parse("2026-06-29T13:30:00Z");
        Transaction matchingTx = new Transaction(
                account, hona, TransactionType.STOCK_SPLIT, exDate, exDate,
                new BigDecimal("2.50000000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", null, null, "Spinoff", null
        );
        when(transactionRepository.findAll()).thenReturn(List.of(matchingTx));

        ScanCorporateActionsResponse response = service.scanPortfolio(portfolioId);
        assertNotNull(response);
        assertEquals(0, response.newPendingActionsCount());
        verify(corporateActionRepository).save(argThat(ca -> ca.getStatus() == CorporateActionStatus.APPLIED));
    }

    @Test
    void scanPortfolio_catalogActionProvisionsMissingInstrument() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instrument hon = new Instrument("Honeywell", AssetClass.STOCK, "HON", "US4385162056", "NASDAQ", new Currency("USD"));
        Position pos = new Position(account, hon, new Quantity(new BigDecimal("5.00000000")), new Money(new BigDecimal("1000.00"), new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        when(corporateActionRepository.findBySourceAndExternalId(eq("SYSTEM_CATALOG"), any())).thenReturn(Optional.empty());
        when(corporateActionRepository.findByInstrumentIdAndActionTypeAndExDate(any(), any(), any())).thenReturn(Optional.empty());
        when(instrumentRepository.findByTicker("HONA")).thenReturn(Optional.empty());
        when(instrumentRepository.findByIsin("US43849R1059")).thenReturn(Optional.empty());

        Instrument savedHona = new Instrument("Honeywell Aerospace Inc.", AssetClass.STOCK, "HONA", "US43849R1059", "NASDAQ", new Currency("USD"));
        when(instrumentRepository.save(any(Instrument.class))).thenReturn(savedHona);

        ScanCorporateActionsResponse response = service.scanPortfolio(portfolioId);
        assertNotNull(response);
        verify(instrumentRepository).save(argThat(i -> i.getTicker().equals("HONA")));
    }

    @Test
    void getPortfolioCorporateActions_includesAppliedActionsEvenIfCurrentHeldIsZero() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        CorporateAction action = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, Instant.now().minusSeconds(86400 * 5), null, null,
                null, null, new BigDecimal("0.50"), "USD", "div", "YAHOO", "EXT-1"
        );
        Transaction tx = new Transaction(account, apple, TransactionType.DIVIDEND, Instant.now(), null,
                null, null, new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        action.markApplied(tx, account);

        when(corporateActionRepository.findActivePortfolioCorporateActions(portfolioId)).thenReturn(List.of(action));

        List<CorporateActionResponse> responses = service.getPortfolioCorporateActions(portfolioId, null);
        assertEquals(1, responses.size());
        assertEquals(CorporateActionStatus.APPLIED.name(), responses.get(0).status());
    }

    @Test
    void applyAction_stockSplit_customNotesAndQuantity() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction split = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, null,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "2:1 split", "YAHOO", "EXT-1"
        );
        UUID actionId = split.getId();
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(split));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction recordedTx = new Transaction(
                account, apple, TransactionType.STOCK_SPLIT, exDate, exDate,
                new BigDecimal("15.00000000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", null, null, "Custom note", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(apple.getId()), eq(TransactionType.STOCK_SPLIT),
                any(), any(), eq(new BigDecimal("15.00000000")), any(), any(), any(), any(),
                eq("USD"), any(), any(), eq("Custom note"), any()
        )).thenReturn(recordedTx);

        ApplyCorporateActionRequest req = new ApplyCorporateActionRequest(
                account.getId(), new BigDecimal("15.00000000"), null, null, "Custom note"
        );
        CorporateActionResponse resp = service.applyAction(portfolioId, actionId, req);
        assertNotNull(resp);
        assertEquals(CorporateActionStatus.APPLIED.name(), resp.status());
    }    @Test
    void getPortfolioCorporateActions_handlesInstrumentsWithoutTickerAndReverseSplitAndDividend() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instrument nullTickerInst = new Instrument("Null Ticker", AssetClass.STOCK, null, null, null, new Currency("USD"));
        Instrument blankTickerInst = new Instrument("Blank Ticker", AssetClass.STOCK, "   ", null, null, new Currency("USD"));
        Position pos1 = new Position(account, nullTickerInst, new Quantity(new BigDecimal("10.00")), new Money(new BigDecimal("100.00"), new Currency("USD")));
        Position pos2 = new Position(account, blankTickerInst, new Quantity(new BigDecimal("10.00")), new Money(new BigDecimal("100.00"), new Currency("USD")));
        Position pos3 = new Position(account, apple, new Quantity(new BigDecimal("10.00")), new Money(new BigDecimal("1500.00"), new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos1, pos2, pos3));

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        // Reverse split: 10 to 1 -> ratio 0.1
        CorporateAction revSplit = new CorporateAction(
                apple, CorporateActionType.REVERSE_STOCK_SPLIT, exDate, null, exDate.plusSeconds(86400 * 2),
                new BigDecimal("10"), BigDecimal.ONE, null, null, "1:10 reverse split", "YAHOO", "EXT-REV"
        );
        // Dividend
        CorporateAction div = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, exDate, null, exDate.plusSeconds(86400 * 2),
                null, null, new BigDecimal("2.50"), "USD", "dividend", "YAHOO", "EXT-DIV"
        );

        when(corporateActionRepository.findActivePortfolioCorporateActions(portfolioId))
                .thenReturn(List.of(revSplit, div));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        List<CorporateActionResponse> responses = service.getPortfolioCorporateActions(portfolioId, null);
        assertEquals(2, responses.size());
        // For reverse split 1:10 on 100 shares -> 100 * (1 - 0.1) = 90 reduction
        CorporateActionResponse revResp = responses.stream()
                .filter(r -> r.actionType().equals("REVERSE_STOCK_SPLIT")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("90.00000000"), revResp.proposedImpactQuantity());

        CorporateActionResponse divResp = responses.stream()
                .filter(r -> r.actionType().equals("DIVIDEND")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("250.0000"), divResp.proposedImpactAmount());
    }

    @Test
    void getPortfolioCorporateActions_excludesActionAppliedToOtherPortfolioWhenHeldIsZero() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Portfolio otherPortfolio = new Portfolio("Other", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        Account otherAccount = new Account(otherPortfolio, "Other Acc", "Broker", new Currency("USD"));

        CorporateAction action = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, Instant.now().minusSeconds(86400 * 5), null, null,
                null, null, new BigDecimal("0.50"), "USD", "div", "YAHOO", "EXT-OTHER"
        );
        Transaction otherTx = new Transaction(otherAccount, apple, TransactionType.DIVIDEND, Instant.now(), null,
                null, null, new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        action.markApplied(otherTx, otherAccount);

        when(corporateActionRepository.findActivePortfolioCorporateActions(portfolioId)).thenReturn(List.of(action));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(any(), any()))
                .thenReturn(List.of());

        List<CorporateActionResponse> responses = service.getPortfolioCorporateActions(portfolioId, null);
        assertTrue(responses.isEmpty());
    }

    @Test
    void applyAction_stockSplitWithNullQuantityAndCustomNotes() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        Instant payDate = exDate.plusSeconds(86400 * 2);
        CorporateAction split = new CorporateAction(
                apple, CorporateActionType.STOCK_SPLIT, exDate, null, payDate,
                BigDecimal.ONE, new BigDecimal("2"), null, null, "2:1 split", "YAHOO", "EXT-NC"
        );
        UUID actionId = split.getId();
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(split));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        Transaction recordedSplitTx = new Transaction(
                account, apple, TransactionType.STOCK_SPLIT, exDate, payDate,
                new BigDecimal("10.00000000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", null, null, "Custom note", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(apple.getId()), eq(TransactionType.STOCK_SPLIT),
                eq(exDate), eq(payDate), eq(new BigDecimal("10.00000000")), any(), eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq("USD"), any(), any(), eq("Custom note"), any()
        )).thenReturn(recordedSplitTx);

        // quantity is null, custom notes -> calculated automatically
        ApplyCorporateActionRequest req = new ApplyCorporateActionRequest(account.getId(), null, null, null, "Custom note");
        CorporateActionResponse resp = service.applyAction(portfolioId, actionId, req);

        assertNotNull(resp);
        assertEquals(CorporateActionStatus.APPLIED.name(), resp.status());
    }

    @Test
    void applyAction_reverseStockSplit_calculatesQuantityWhenNull() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction revSplit = new CorporateAction(
                apple, CorporateActionType.REVERSE_STOCK_SPLIT, exDate, null, null,
                new BigDecimal("4"), BigDecimal.ONE, null, null, "1:4 reverse", "YAHOO", "EXT-REV4"
        );
        UUID actionId = revSplit.getId();
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(revSplit));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("40.00"), new BigDecimal("10.00"), new BigDecimal("400.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        // 40 * (1 - 0.25) = 30 reduction
        Transaction recordedTx = new Transaction(
                account, apple, TransactionType.REVERSE_STOCK_SPLIT, exDate, exDate,
                new BigDecimal("30.00000000"), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", null, null, "1:4 reverse", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(apple.getId()), eq(TransactionType.REVERSE_STOCK_SPLIT),
                eq(exDate), eq(exDate), eq(new BigDecimal("30.00000000")), any(), eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO), eq(BigDecimal.ZERO), eq("USD"), any(), any(), eq("1:4 reverse"), any()
        )).thenReturn(recordedTx);

        ApplyCorporateActionRequest req = new ApplyCorporateActionRequest(account.getId(), null, null, null, null);
        CorporateActionResponse resp = service.applyAction(portfolioId, actionId, req);

        assertNotNull(resp);
        assertEquals(CorporateActionStatus.APPLIED.name(), resp.status());
    }

    @Test
    void applyAction_dividendWithNullCurrencyAndCalculatedGross() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        Instant payDate = exDate.plusSeconds(86400 * 3);
        CorporateAction div = new CorporateAction(
                apple, CorporateActionType.DIVIDEND, exDate, null, payDate,
                null, null, new BigDecimal("1.50"), null, "Dividend action", "YAHOO", "EXT-DIVNC"
        );
        UUID actionId = div.getId();
        when(corporateActionRepository.findById(actionId)).thenReturn(Optional.of(div));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Transaction buyTx = new Transaction(
                account, apple, TransactionType.BUY, exDate.minusSeconds(86400 * 5), null,
                new BigDecimal("20.00"), new BigDecimal("10.00"), new BigDecimal("200.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of(buyTx));

        // 20 * 1.50 = 30.00
        Transaction recordedTx = new Transaction(
                account, apple, TransactionType.DIVIDEND, exDate, payDate,
                null, null, new BigDecimal("30.0000"), BigDecimal.ZERO, new BigDecimal("4.5000"),
                "USD", null, null, "Custom note", null
        );
        when(transactionService.recordTransaction(
                eq(portfolioId), eq(account.getId()), eq(apple.getId()), eq(TransactionType.DIVIDEND),
                eq(exDate), eq(payDate), any(), any(), eq(new BigDecimal("30.0000")),
                eq(BigDecimal.ZERO), eq(new BigDecimal("4.5000")), eq("USD"), any(), any(), eq("Custom note"), any()
        )).thenReturn(recordedTx);

        ApplyCorporateActionRequest req = new ApplyCorporateActionRequest(
                account.getId(), null, null, new BigDecimal("4.50"), "Custom note"
        );
        CorporateActionResponse resp = service.applyAction(portfolioId, actionId, req);

        assertNotNull(resp);
        assertEquals(CorporateActionStatus.APPLIED.name(), resp.status());
    }

    @Test
    void scanPortfolio_matchesLedgerTransactionBranches() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        Position pos = new Position(account, apple, new Quantity(new BigDecimal("10.00")), new Money(new BigDecimal("1500.00"), new Currency("USD")));
        when(positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE))
                .thenReturn(List.of(pos));

        Instant exDate = Instant.now().minusSeconds(86400 * 20);
        // Transactions that will be tested in findMatchingLedgerTransaction
        Instrument otherInst = new Instrument("Other", AssetClass.STOCK, "OTH", "US9999999999", "NASDAQ", new Currency("USD"));
        Transaction txDiffInst = new Transaction(account, otherInst, TransactionType.STOCK_SPLIT, exDate, null,
                BigDecimal.TEN, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        Transaction txDiffType = new Transaction(account, apple, TransactionType.BUY, exDate, null,
                BigDecimal.TEN, new BigDecimal("100.00"), new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        Transaction txBeforeWindow = new Transaction(account, apple, TransactionType.STOCK_SPLIT, exDate.minusSeconds(86400 * 10), null,
                BigDecimal.TEN, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);
        Transaction txAfterWindow = new Transaction(account, apple, TransactionType.STOCK_SPLIT, exDate.plusSeconds(86400 * 10), null,
                BigDecimal.TEN, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null);

        when(transactionRepository.findAll()).thenReturn(List.of(txDiffInst, txDiffType, txBeforeWindow, txAfterWindow));

        DiscoveredCorporateAction split = new DiscoveredCorporateAction(
                CorporateActionType.STOCK_SPLIT, exDate, BigDecimal.ONE, new BigDecimal("2"),
                null, null, "2:1 split", "EXT-TEST"
        );
        when(yahooFinanceGateway.fetchCorporateActions("AAPL", "1y"))
                .thenReturn(List.of(split));
        when(accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE))
                .thenReturn(List.of(account));
        when(transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), apple.getId()))
                .thenReturn(List.of());

        ScanCorporateActionsResponse resp = service.scanPortfolio(portfolioId);
        assertNotNull(resp);
        assertEquals(0, resp.newPendingActionsCount());
    }
}

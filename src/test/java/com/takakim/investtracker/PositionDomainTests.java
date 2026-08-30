package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionDomainTests {

    @Test
    @DisplayName("Position validates required fields and sets initial active state")
    void positionValidatesInputsAndSetsDefaults() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        assertThrows(NullPointerException.class, () -> new Position(null, instrument, new Quantity(BigDecimal.TEN), null));
        assertThrows(NullPointerException.class, () -> new Position(account, null, new Quantity(BigDecimal.TEN), null));
        assertThrows(IllegalArgumentException.class, () -> new Position(account, instrument, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Position(account, instrument, new Quantity(new BigDecimal("-1.0")), null));

        Money costBasis = new Money(new BigDecimal("150.5000"), new Currency("USD"));
        Position position = new Position(account, instrument, new Quantity(new BigDecimal("25.50000000")), costBasis);

        assertEquals(account, position.getAccount());
        assertEquals(instrument, position.getInstrument());
        assertEquals(new BigDecimal("25.50000000"), position.getQuantity());
        assertEquals(new BigDecimal("25.50000000"), position.getQuantityValueObject().value());
        assertEquals(new BigDecimal("150.5000"), position.getCostBasisAmount());
        assertEquals("USD", position.getCostBasisCurrency());
        assertEquals(costBasis, position.getCostBasisMoney());
        assertEquals(PositionStatus.ACTIVE, position.getStatus());
        assertNotNull(position.getId());
        assertNotNull(position.getCreatedAt());
        assertNotNull(position.getUpdatedAt());
    }

    @Test
    @DisplayName("Position updates quantity and cost basis correctly")
    void positionUpdatesCorrectly() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Position position = new Position(account, instrument, new Quantity(BigDecimal.TEN), null);
        assertNull(position.getCostBasisMoney());

        Money newCostBasis = new Money(new BigDecimal("2000.00"), gbp);
        position.update(new Quantity(new BigDecimal("50.00")), newCostBasis);

        position.update(new Quantity(new BigDecimal("50.00")), null);
        assertNull(position.getCostBasisAmount());
        assertNull(position.getCostBasisCurrency());
        assertNull(position.getCostBasisMoney());

        assertThrows(IllegalArgumentException.class, () -> position.update(null, null));
        assertThrows(IllegalArgumentException.class, () -> position.update(new Quantity(new BigDecimal("-0.1")), null));
    }

    @Test
    @DisplayName("Position handles partial cost basis null combinations correctly")
    void positionCostBasisNullVariations() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Position pos1 = new Position(account, instrument, new Quantity(BigDecimal.TEN), null);
        assertNull(pos1.getCostBasisAmount());
        assertNull(pos1.getCostBasisCurrency());
        assertNull(pos1.getCostBasisMoney());

        Money costBasis = new Money(new BigDecimal("100.00"), gbp);
        pos1.update(new Quantity(BigDecimal.TEN), costBasis);
        assertNotNull(pos1.getCostBasisMoney());
    }

    @Test
    @DisplayName("Position validates null quantity and negative quantity on update")
    void positionValidatesQuantityOnUpdate() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Position position = new Position(account, instrument, new Quantity(BigDecimal.TEN), null);
        assertThrows(IllegalArgumentException.class, () -> position.update(null, null));
        assertThrows(IllegalArgumentException.class, () -> position.update(new Quantity(new BigDecimal("-1.0")), null));
    }

    @Test
    @DisplayName("Archived position prevents updates")
    void positionArchivingEnforcesInvariants() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Position position = new Position(account, instrument, new Quantity(BigDecimal.ONE), null);
        position.archive();

        assertEquals(PositionStatus.ARCHIVED, position.getStatus());
        assertThrows(IllegalStateException.class, () -> position.update(new Quantity(BigDecimal.TEN), null));

        position.unarchive();
        assertEquals(PositionStatus.ACTIVE, position.getStatus());
        position.update(new Quantity(BigDecimal.TEN), null);
        assertEquals(new BigDecimal("10"), position.getQuantity());
    }

    @Test
    @DisplayName("PositionService calculates position performance with buys, sells, dividends, fees, and closed state")
    void positionServicePerformanceCalculations() {
        var portRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.PortfolioRepository.class);
        var accRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.AccountRepository.class);
        var instRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.InstrumentRepository.class);
        var posRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.PositionRepository.class);
        var posEngine = org.mockito.Mockito.mock(com.takakim.investtracker.service.position.PositionEngine.class);
        var txRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.TransactionRepository.class);
        var marketService = org.mockito.Mockito.mock(com.takakim.investtracker.service.market.MarketDataService.class);
        var fxService = org.mockito.Mockito.mock(com.takakim.investtracker.service.currency.FxRateService.class);

        var service = new com.takakim.investtracker.service.PositionService(
                portRepo, accRepo, instRepo, posRepo, posEngine, txRepo, marketService, fxService
        );

        Currency gbp = new Currency("GBP");
        Currency usd = new Currency("USD");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.TWR);
        Account account = new Account(portfolio, "ISA", "Broker", gbp);
        Instrument instrument = new Instrument("Vale SA", AssetClass.STOCK, "VALE", "US91912E1055", "NYSE", usd);
        Position position = new Position(account, instrument, new Quantity(new BigDecimal("10.0")), new Money(new BigDecimal("100.0"), gbp));

        UUID portId = portfolio.getId();
        UUID accId = account.getId();
        UUID posId = position.getId();
        UUID instId = instrument.getId();

        org.mockito.Mockito.when(portRepo.existsById(portId)).thenReturn(true);
        org.mockito.Mockito.when(accRepo.findById(accId)).thenReturn(java.util.Optional.of(account));
        org.mockito.Mockito.when(posRepo.findById(posId)).thenReturn(java.util.Optional.of(position));
        org.mockito.Mockito.when(posRepo.findByAccountPortfolioId(portId)).thenReturn(List.of(position));
        org.mockito.Mockito.when(posRepo.findByAccountPortfolioIdAndStatus(portId, PositionStatus.ACTIVE)).thenReturn(List.of(position));
        org.mockito.Mockito.when(posRepo.findByAccountId(accId)).thenReturn(List.of(position));
        org.mockito.Mockito.when(posRepo.findByAccountIdAndStatus(accId, PositionStatus.ACTIVE)).thenReturn(List.of(position));

        Transaction txBuy = new Transaction(
                account, instrument, TransactionType.BUY,
                Instant.now(), Instant.now(), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("200"),
                new BigDecimal("2"), new BigDecimal("1"),
                "GBP", null, null, "notes", null
        );
        Transaction txSell = new Transaction(
                account, instrument, TransactionType.SELL,
                Instant.now(), Instant.now(), new BigDecimal("10"),
                new BigDecimal("15"), new BigDecimal("150"),
                new BigDecimal("1"), null,
                "GBP", null, null, "notes", null
        );
        Transaction txDiv = new Transaction(
                account, instrument, TransactionType.DIVIDEND,
                Instant.now(), Instant.now(), null,
                null, new BigDecimal("10"),
                null, new BigDecimal("1.50"),
                "GBP", null, null, "dividend", null
        );
        Transaction txCancelled = new Transaction(
                account, instrument, TransactionType.BUY,
                Instant.now(), Instant.now(), new BigDecimal("5"),
                new BigDecimal("10"), new BigDecimal("50"),
                null, null,
                "GBP", null, null, "cancelled", null
        );
        txCancelled.markCorrected();

        org.mockito.Mockito.when(txRepo.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(accId, instId))
                .thenReturn(List.of(txBuy, txSell, txDiv, txCancelled));

        var calcOpen = new com.takakim.investtracker.service.position.PositionCalculationResult(
                accId, instId, CostBasisMethod.FIFO, new BigDecimal("10.0"),
                new BigDecimal("100.00"), "GBP", new BigDecimal("10.00"),
                new BigDecimal("50.00"), List.of(), List.of()
        );
        org.mockito.Mockito.when(posEngine.calculate(org.mockito.Mockito.eq(account), org.mockito.Mockito.eq(instrument), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(calcOpen);

        org.mockito.Mockito.when(marketService.getLatestPrice(org.mockito.Mockito.eq(instId), org.mockito.Mockito.any()))
                .thenReturn(new com.takakim.investtracker.service.market.PriceQuote(
                        instId, new BigDecimal("15.00"), "USD", Instant.now(),
                        com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "REF", false, null
                ));

        org.mockito.Mockito.when(fxService.convert(org.mockito.Mockito.any(), org.mockito.Mockito.eq(gbp), org.mockito.Mockito.any()))
                .thenReturn(new Money(new BigDecimal("12.00"), gbp));
        org.mockito.Mockito.when(fxService.convert(org.mockito.Mockito.any(), org.mockito.Mockito.eq(usd), org.mockito.Mockito.any()))
                .thenReturn(new Money(new BigDecimal("80.00"), usd));

        // 1. Get position performance for open position
        ApiDtos.PositionPerformanceResponse perf = service.getPositionPerformance(portId, accId, posId);
        assertNotNull(perf);
        assertEquals("ACTIVE", perf.status());
        assertEquals(new BigDecimal("10.0"), perf.currentQuantity());
        assertEquals(new BigDecimal("20"), perf.totalBoughtQuantity());
        assertEquals(new BigDecimal("10"), perf.totalSoldQuantity());

        // 2. List portfolio performance (active and all)
        List<ApiDtos.PositionPerformanceResponse> list1 = service.listPortfolioPositionsPerformance(portId, false);
        assertEquals(1, list1.size());
        List<ApiDtos.PositionPerformanceResponse> list2 = service.listPortfolioPositionsPerformance(portId, true);
        assertEquals(1, list2.size());

        // 3. List account performance
        List<ApiDtos.PositionPerformanceResponse> accList1 = service.listAccountPositionsPerformance(portId, accId, false);
        assertEquals(1, accList1.size());
        List<ApiDtos.PositionPerformanceResponse> accList2 = service.listAccountPositionsPerformance(portId, accId, true);
        assertEquals(1, accList2.size());

        // 4. Closed position calculation (0 remaining shares)
        var calcClosed = new com.takakim.investtracker.service.position.PositionCalculationResult(
                accId, instId, CostBasisMethod.FIFO, BigDecimal.ZERO,
                BigDecimal.ZERO, "GBP", BigDecimal.ZERO,
                new BigDecimal("50.00"), List.of(), List.of()
        );
        org.mockito.Mockito.when(posEngine.calculate(org.mockito.Mockito.eq(account), org.mockito.Mockito.eq(instrument), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(calcClosed);

        ApiDtos.PositionPerformanceResponse perfClosed = service.getPositionPerformance(portId, accId, posId);
        assertEquals("CLOSED", perfClosed.status());
        assertEquals(BigDecimal.ZERO, perfClosed.currentQuantity());

        // 5. Position in same currency (GBP == GBP) with fees and lots
        Instrument instGbp = new Instrument("BP Plc", AssetClass.STOCK, "BP", "GB0007980591", "LSE", gbp);
        UUID instGbpId = instGbp.getId();
        Position posGbp = new Position(account, instGbp, new Quantity(new BigDecimal("100")), new Money(new BigDecimal("500"), gbp));
        UUID posGbpId = posGbp.getId();

        org.mockito.Mockito.when(posRepo.findById(posGbpId)).thenReturn(java.util.Optional.of(posGbp));
        org.mockito.Mockito.when(marketService.getLatestPrice(org.mockito.Mockito.eq(instGbpId), org.mockito.Mockito.any()))
                .thenReturn(new com.takakim.investtracker.service.market.PriceQuote(
                        instGbpId, new BigDecimal("5.50"), "GBP", Instant.now(),
                        com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "REF", false, null
                ));

        var lot = new com.takakim.investtracker.service.position.PositionLot(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), new BigDecimal("100"),
                new BigDecimal("100"), new BigDecimal("5.00"), new BigDecimal("500.00"), "GBP"
        );
        var disposal = new com.takakim.investtracker.service.position.LotDisposal(
                UUID.randomUUID(), lot.getId(), Instant.now(), new BigDecimal("50"),
                new BigDecimal("250.00"), new BigDecimal("300.00"), new BigDecimal("50.00"), "GBP"
        );
        var calcGbp = new com.takakim.investtracker.service.position.PositionCalculationResult(
                accId, instGbpId, CostBasisMethod.FIFO, new BigDecimal("100.0"),
                new BigDecimal("500.00"), "GBP", new BigDecimal("5.00"),
                new BigDecimal("50.00"), List.of(lot), List.of(disposal)
        );
        org.mockito.Mockito.when(posEngine.calculate(org.mockito.Mockito.eq(account), org.mockito.Mockito.eq(instGbp), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(calcGbp);
        org.mockito.Mockito.when(txRepo.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(accId, instGbpId))
                .thenReturn(List.of());

        var perfGbp = service.getPositionPerformance(portId, accId, posGbpId);
        assertEquals("GBP", perfGbp.currency());
        assertEquals(1, perfGbp.openLots().size());
        assertEquals(1, perfGbp.disposals().size());

        // 6. Invalid portfolioId errors
        UUID randomPort = UUID.randomUUID();
        org.mockito.Mockito.when(portRepo.existsById(randomPort)).thenReturn(false);
        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class,
                () -> service.listPortfolioPositionsPerformance(randomPort, false));

        // 7. Null services handling (marketDataService = null, fxRateService = null)
        var bareService = new com.takakim.investtracker.service.PositionService(
                portRepo, accRepo, instRepo, posRepo, posEngine, txRepo, null, null
        );
        var barePerf = bareService.getPositionPerformance(portId, accId, posId);
        assertNotNull(barePerf);

        // 8. Test transaction gross fallback and non-equity transaction types (e.g. FEE)
        Transaction txGrossOnlyBuy = new Transaction(
                account, instrument, TransactionType.BUY,
                Instant.now(), Instant.now(), new BigDecimal("10"),
                new BigDecimal("10"), new BigDecimal("100"),
                null, null,
                "USD", null, null, null, null
        );
        Transaction txGrossOnlySell = new Transaction(
                account, instrument, TransactionType.SELL,
                Instant.now(), Instant.now(), new BigDecimal("10"),
                new BigDecimal("12"), new BigDecimal("120"),
                null, null,
                "USD", null, null, null, null
        );
        Transaction txFee = new Transaction(
                account, instrument, TransactionType.FEE,
                Instant.now(), Instant.now(), null,
                null, new BigDecimal("5"),
                new BigDecimal("5"), null,
                "USD", null, null, null, null
        );
        org.mockito.Mockito.when(txRepo.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(accId, instId))
                .thenReturn(List.of(txGrossOnlyBuy, txGrossOnlySell, txFee));
        var calcNulls = new com.takakim.investtracker.service.position.PositionCalculationResult(
                accId, instId, CostBasisMethod.FIFO, BigDecimal.ZERO,
                null, "USD", null,
                null, List.of(), List.of()
        );
        org.mockito.Mockito.when(posEngine.calculate(org.mockito.Mockito.eq(account), org.mockito.Mockito.eq(instrument), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(calcNulls);
        var perfNulls = service.getPositionPerformance(portId, accId, posId);
        assertNotNull(perfNulls);

        // 9. Exception in market data service and fx service
        org.mockito.Mockito.when(marketService.getLatestPrice(org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenThrow(new RuntimeException("Market data down"));
        org.mockito.Mockito.when(fxService.convert(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenThrow(new RuntimeException("FX rate down"));

        var perfExceptions = service.getPositionPerformance(portId, accId, posId);
        assertNotNull(perfExceptions);

        // 10. PositionService CRUD branches
        org.mockito.Mockito.when(instRepo.findById(instId)).thenReturn(java.util.Optional.of(instrument));
        org.mockito.Mockito.when(posRepo.findByAccountIdAndInstrumentId(accId, instId)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(posRepo.save(org.mockito.Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        var createdPos = service.createPosition(portId, accId, instId, new Quantity(BigDecimal.TEN), null);
        assertNotNull(createdPos);

        // Conflict on create
        org.mockito.Mockito.when(posRepo.findByAccountIdAndInstrumentId(accId, instId)).thenReturn(java.util.Optional.of(position));
        assertThrows(com.takakim.investtracker.service.ConflictException.class,
                () -> service.createPosition(portId, accId, instId, new Quantity(BigDecimal.TEN), null));

        // Update position
        var updatedPos = service.updatePosition(portId, accId, posId, new Quantity(new BigDecimal("15")), null);
        assertNotNull(updatedPos);

        // Archive position
        service.archivePosition(portId, accId, posId);
        position.unarchive();

        // Recalculate portfolio
        service.recalculatePortfolio(portId);

        // Get lots
        service.getPositionLots(portId, accId, posId);

        // Controller unit test
        var controller = new com.takakim.investtracker.api.PositionController(service);
        controller.listPositions(portId, accId);
        controller.listPortfolioPositions(portId);
        controller.getPosition(portId, accId, posId);
        controller.listPortfolioPositionsPerformance(portId, false);
        controller.listAccountPositionsPerformance(portId, accId, false);
        controller.getPositionPerformance(portId, accId, posId);
        controller.recalculatePositions(portId);
        controller.archivePosition(portId, accId, posId);
        position.unarchive();

        // Partial cost basis update in controller
        controller.updatePosition(portId, accId, posId, new ApiDtos.PositionUpdateRequest(new BigDecimal("12"), new BigDecimal("100"), null));
        controller.updatePosition(portId, accId, posId, new ApiDtos.PositionUpdateRequest(new BigDecimal("12"), null, "GBP"));

        org.mockito.Mockito.when(posRepo.findByAccountIdAndInstrumentId(accId, instId)).thenReturn(java.util.Optional.empty());
        controller.createPosition(portId, accId, new ApiDtos.PositionRequest(instId, new BigDecimal("12"), null, "GBP"));
        controller.createPosition(portId, accId, new ApiDtos.PositionRequest(instId, new BigDecimal("12"), new BigDecimal("100"), null));

        // 11. TransactionService branches
        var txService = new com.takakim.investtracker.service.TransactionService(
                portRepo, accRepo, instRepo, txRepo, posEngine
        );
        txService.listTransactions(portId, accId, TransactionType.BUY);
        txService.listTransactions(portId, accId, null);
        txService.listPortfolioTransactions(portId);

        UUID badTxId = UUID.randomUUID();
        org.mockito.Mockito.when(txRepo.findById(badTxId)).thenReturn(java.util.Optional.empty());
        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class,
                () -> txService.getTransaction(portId, accId, badTxId));

        Transaction otherAccountTx = new Transaction(
                new Account(portfolio, "Other", "Broker", gbp), instrument, TransactionType.BUY,
                Instant.now(), Instant.now(), new BigDecimal("1"), new BigDecimal("1"),
                new BigDecimal("1"), null, null, "GBP", null, null, null, null
        );
        UUID otherTxId = otherAccountTx.getId();
        org.mockito.Mockito.when(txRepo.findById(otherTxId)).thenReturn(java.util.Optional.of(otherAccountTx));
        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class,
                () -> txService.getTransaction(portId, accId, otherTxId));

        // 12. Account mismatch & Position not found branches in PositionService
        UUID badPosId = UUID.randomUUID();
        org.mockito.Mockito.when(posRepo.findById(badPosId)).thenReturn(java.util.Optional.empty());
        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class,
                () -> service.getPosition(portId, accId, badPosId));

        Account otherAcc = new Account(portfolio, "Other ISA", "Broker", gbp);
        UUID otherAccId = otherAcc.getId();
        Position otherPos = new Position(otherAcc, instrument, new Quantity(BigDecimal.ONE), null);
        UUID otherPosId = otherPos.getId();
        org.mockito.Mockito.when(posRepo.findById(otherPosId)).thenReturn(java.util.Optional.of(otherPos));
        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class,
                () -> service.getPosition(portId, accId, otherPosId));

        // Portfolio not found in listPortfolioPositions
        UUID badPortId = UUID.randomUUID();
        org.mockito.Mockito.when(portRepo.existsById(badPortId)).thenReturn(false);
        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class,
                () -> service.listPortfolioPositions(badPortId));

        // Account does not belong to portfolio
        Portfolio otherPort = new Portfolio("Other Port", gbp, CostBasisMethod.FIFO, ReturnMethod.TWR);
        Account orphanAcc = new Account(otherPort, "Orphan", "Broker", gbp);
        UUID orphanAccId = orphanAcc.getId();
        org.mockito.Mockito.when(accRepo.findById(orphanAccId)).thenReturn(java.util.Optional.of(orphanAcc));
        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class,
                () -> service.listPositions(portId, orphanAccId));

        // Cash transaction with null instrument in computePositionPerformance
        Transaction cashTx = new Transaction(
                account, null, TransactionType.DEPOSIT,
                Instant.now(), Instant.now(), null,
                null, new BigDecimal("1000"),
                null, null, "GBP", null, null, "deposit", null
        );
        org.mockito.Mockito.when(txRepo.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(accId, instId))
                .thenReturn(List.of(cashTx));
        var perfCashTx = service.getPositionPerformance(portId, accId, posId);
        assertNotNull(perfCashTx);

        // 13. Create position with unknown instrument
        UUID badInstId = UUID.randomUUID();
        org.mockito.Mockito.when(instRepo.findById(badInstId)).thenReturn(java.util.Optional.empty());
        assertThrows(com.takakim.investtracker.service.ResourceNotFoundException.class,
                () -> service.createPosition(portId, accId, badInstId, new Quantity(BigDecimal.ONE), null));

        // Update position with non-null cost basis
        service.updatePosition(portId, accId, posId, new Quantity(new BigDecimal("20")), new Money(new BigDecimal("200"), gbp));

        // 14. Performance of unlisted instrument (null ticker and null isin)
        Instrument unlisted = new Instrument("Unlisted Fund", AssetClass.MUTUAL_FUND, null, null, null, gbp);
        UUID unlistedId = unlisted.getId();
        Position unlistedPos = new Position(account, unlisted, new Quantity(BigDecimal.TEN), null);
        UUID unlistedPosId = unlistedPos.getId();
        org.mockito.Mockito.when(posRepo.findById(unlistedPosId)).thenReturn(java.util.Optional.of(unlistedPos));
        org.mockito.Mockito.when(txRepo.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(accId, unlistedId)).thenReturn(List.of());
        var calcUnlisted = new com.takakim.investtracker.service.position.PositionCalculationResult(
                accId, unlistedId, CostBasisMethod.FIFO, BigDecimal.TEN,
                BigDecimal.ZERO, "GBP", BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of()
        );
        org.mockito.Mockito.when(posEngine.calculate(org.mockito.Mockito.eq(account), org.mockito.Mockito.eq(unlisted), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(calcUnlisted);
        var perfUnlisted = service.getPositionPerformance(portId, accId, unlistedPosId);
        assertNull(perfUnlisted.ticker());
        assertNull(perfUnlisted.isin());

        // 15. MarketObservation coverage
        var observation = new com.takakim.investtracker.domain.MarketObservation(
                instrument, new BigDecimal("100"), "USD", Instant.now(),
                com.takakim.investtracker.domain.ObservationSourceType.MANUAL, "Manual Ref"
        );
        assertNotNull(observation.getId());
        assertEquals("MANUAL", observation.getSourceType().name());
        assertEquals("Manual Ref", observation.getSourceReference());

        // 16. Portfolio with null costBasisMethod in PositionService
        Portfolio portNullMethod = new Portfolio("Null Method", gbp, null, ReturnMethod.TWR);
        Account accNullMethod = new Account(portNullMethod, "Acc Null Method", "Broker", gbp);
        UUID accNullMethodId = accNullMethod.getId();
        Position posNullMethod = new Position(accNullMethod, instrument, new Quantity(BigDecimal.TEN), null);
        UUID posNullMethodId = posNullMethod.getId();

        org.mockito.Mockito.when(portRepo.existsById(portNullMethod.getId())).thenReturn(true);
        org.mockito.Mockito.when(accRepo.findById(accNullMethodId)).thenReturn(java.util.Optional.of(accNullMethod));
        org.mockito.Mockito.when(posRepo.findById(posNullMethodId)).thenReturn(java.util.Optional.of(posNullMethod));
        org.mockito.Mockito.when(txRepo.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(accNullMethodId, instId)).thenReturn(List.of());
        org.mockito.Mockito.when(posEngine.calculate(org.mockito.Mockito.eq(accNullMethod), org.mockito.Mockito.eq(instrument), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(calcOpen);
        var perfNullMethod = service.getPositionPerformance(portNullMethod.getId(), accNullMethodId, posNullMethodId);
        assertNotNull(perfNullMethod);

        // 17. Null portfolio on account
        Account accNoPort = org.mockito.Mockito.mock(Account.class);
        org.mockito.Mockito.when(accNoPort.getPortfolio()).thenReturn(null);
        org.mockito.Mockito.when(accNoPort.getId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.when(accNoPort.getAccountCurrency()).thenReturn(gbp);
        org.mockito.Mockito.when(posEngine.calculate(org.mockito.Mockito.eq(accNoPort), org.mockito.Mockito.eq(instrument), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(calcOpen);
        var perfNoPort = service.computePositionPerformance(accNoPort, instrument, UUID.randomUUID());
        assertNotNull(perfNoPort);

        // 18. MarketObservation negative price check
        assertThrows(IllegalArgumentException.class, () -> new com.takakim.investtracker.domain.MarketObservation(
                instrument, new BigDecimal("-5.00"), "USD", Instant.now(),
                com.takakim.investtracker.domain.ObservationSourceType.MANUAL, null
        ));

        // 19. Position partial null reflection check for getCostBasisMoney
        try {
            var fieldAmount = Position.class.getDeclaredField("costBasisAmount");
            fieldAmount.setAccessible(true);
            var fieldCurr = Position.class.getDeclaredField("costBasisCurrency");
            fieldCurr.setAccessible(true);

            Position posPartial = new Position(account, instrument, new Quantity(BigDecimal.ONE), null);
            fieldAmount.set(posPartial, new BigDecimal("100.00"));
            fieldCurr.set(posPartial, null);
            assertNull(posPartial.getCostBasisMoney());

            fieldAmount.set(posPartial, null);
            fieldCurr.set(posPartial, "GBP");
            assertNull(posPartial.getCostBasisMoney());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 20. Bare InstrumentService without repos
        var bareInstService = new com.takakim.investtracker.service.InstrumentService(instRepo, null, null);
        var bareInstResp = bareInstService.refreshAllPrices();
        assertNotNull(bareInstResp);
        var bareSingleResp = bareInstService.refreshPrice(instId);
        assertNotNull(bareSingleResp);
    }

    @Test
    @DisplayName("InstrumentService refreshPrice and refreshAllPrices branches")
    void instrumentServiceRefreshBranches() {
        var instRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.InstrumentRepository.class);
        var obsRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.MarketObservationRepository.class);
        var marketService = org.mockito.Mockito.mock(com.takakim.investtracker.service.market.MarketDataService.class);

        var service = new com.takakim.investtracker.service.InstrumentService(instRepo, obsRepo, marketService);

        UUID instId = UUID.randomUUID();
        Instrument inst = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        org.mockito.Mockito.when(instRepo.findById(instId)).thenReturn(java.util.Optional.of(inst));
        org.mockito.Mockito.when(instRepo.findAllByOrderByNameAsc()).thenReturn(List.of(inst));

        var obs = new com.takakim.investtracker.domain.MarketObservation(
                inst, new BigDecimal("225.00"), "USD", Instant.now().minus(java.time.Duration.ofHours(30)),
                com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "REF"
        );
        org.mockito.Mockito.when(obsRepo.findFirstByInstrumentIdOrderByObservedAtDesc(org.mockito.Mockito.any()))
                .thenReturn(java.util.Optional.of(obs));

        // 1. Get with stale price
        ApiDtos.InstrumentResponse resp = service.get(instId);
        assertEquals(new BigDecimal("225.00"), resp.latestPrice());
        assertTrue(resp.isStale());

        // 2. Refresh price
        ApiDtos.InstrumentResponse refResp = service.refreshPrice(instId);
        assertNotNull(refResp);

        // 3. Refresh all prices
        List<ApiDtos.InstrumentResponse> allResp = service.refreshAllPrices();
        assertEquals(1, allResp.size());
    }

    @Test
    @DisplayName("PositionService computePositionPerformance handles all transaction null branches and native return conversion")
    void computePositionPerformanceEdgeBranches() {
        var portRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.PortfolioRepository.class);
        var accRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.AccountRepository.class);
        var instRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.InstrumentRepository.class);
        var posRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.PositionRepository.class);
        var posEngine = org.mockito.Mockito.mock(com.takakim.investtracker.service.position.PositionEngine.class);
        var txRepo = org.mockito.Mockito.mock(com.takakim.investtracker.repository.TransactionRepository.class);
        var marketService = org.mockito.Mockito.mock(com.takakim.investtracker.service.market.MarketDataService.class);
        var fxService = org.mockito.Mockito.mock(com.takakim.investtracker.service.currency.FxRateService.class);

        var service = new com.takakim.investtracker.service.PositionService(
                portRepo, accRepo, instRepo, posRepo, posEngine, txRepo, marketService, fxService
        );

        Currency gbp = new Currency("GBP");
        Currency usd = new Currency("USD");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.AVERAGE_COST, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Nvidia", AssetClass.STOCK, "NVDA", "US67066G1040", "NASDAQ", usd);

        // 1. Transaction with valid quantity for BUY, null fee/tax
        Transaction txBuy = new Transaction(
                account, instrument, TransactionType.BUY,
                Instant.now(), Instant.now(), new BigDecimal("5.0"),
                new BigDecimal("20.00"), new BigDecimal("100.00"),
                null, null,
                "GBP", null, null, null, null
        );
        // 2. Transaction with valid quantity for SELL, null fee/tax
        Transaction txSell = new Transaction(
                account, instrument, TransactionType.SELL,
                Instant.now(), Instant.now(), new BigDecimal("2.0"),
                new BigDecimal("25.00"), new BigDecimal("50.00"),
                null, null,
                "GBP", null, null, null, null
        );
        // 3. Transaction for DIVIDEND with null fee/tax
        Transaction txDiv = new Transaction(
                account, instrument, TransactionType.DIVIDEND,
                Instant.now(), Instant.now(), null,
                null, new BigDecimal("10.00"),
                null, null,
                "GBP", null, null, null, null
        );
        // 4. Transaction with non-null netAmount, non-null fee, non-null tax
        Transaction txFull = new Transaction(
                account, instrument, TransactionType.BUY,
                Instant.now(), Instant.now(), new BigDecimal("10"),
                new BigDecimal("10.00"), new BigDecimal("100.00"),
                new BigDecimal("2.00"), new BigDecimal("1.00"),
                "GBP", null, null, null, null
        );

        org.mockito.Mockito.when(txRepo.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(account.getId(), instrument.getId()))
                .thenReturn(List.of(txBuy, txSell, txDiv, txFull));

        var calc = new com.takakim.investtracker.service.position.PositionCalculationResult(
                account.getId(), instrument.getId(), CostBasisMethod.AVERAGE_COST,
                new BigDecimal("10.0"), new BigDecimal("200.00"), "GBP",
                new BigDecimal("20.00"), new BigDecimal("15.00"), List.of(), List.of()
        );
        org.mockito.Mockito.when(posEngine.calculate(org.mockito.Mockito.eq(account), org.mockito.Mockito.eq(instrument), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(calc);

        org.mockito.Mockito.when(marketService.getLatestPrice(org.mockito.Mockito.eq(instrument.getId()), org.mockito.Mockito.any()))
                .thenReturn(new com.takakim.investtracker.service.market.PriceQuote(
                        instrument.getId(), new BigDecimal("25.00"), "USD", Instant.now(),
                        com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "REF", false, null
                ));

        // FX mock for price and return conversion
        org.mockito.Mockito.when(fxService.convert(org.mockito.Mockito.any(), org.mockito.Mockito.eq(gbp), org.mockito.Mockito.any()))
                .thenReturn(new Money(new BigDecimal("20.00"), gbp));
        org.mockito.Mockito.when(fxService.convert(org.mockito.Mockito.any(), org.mockito.Mockito.eq(usd), org.mockito.Mockito.any()))
                .thenReturn(new Money(new BigDecimal("15.00"), usd));

        ApiDtos.PositionPerformanceResponse perf = service.computePositionPerformance(account, instrument, UUID.randomUUID());
        assertNotNull(perf);
        assertEquals("ACTIVE", perf.status());
        assertEquals(new BigDecimal("15.0"), perf.totalBoughtQuantity());
        assertEquals(new BigDecimal("2.0"), perf.totalSoldQuantity());

        // Also test same currency quote
        org.mockito.Mockito.when(marketService.getLatestPrice(org.mockito.Mockito.eq(instrument.getId()), org.mockito.Mockito.any()))
                .thenReturn(new com.takakim.investtracker.service.market.PriceQuote(
                        instrument.getId(), new BigDecimal("25.00"), "GBP", Instant.now(),
                        com.takakim.investtracker.domain.ObservationSourceType.PROVIDER, "REF", false, null
                ));
        ApiDtos.PositionPerformanceResponse perfSameCurr = service.computePositionPerformance(account, instrument, UUID.randomUUID());
        assertNotNull(perfSameCurr);
    }
}

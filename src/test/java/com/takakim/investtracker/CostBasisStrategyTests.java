package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.position.AverageCostBasisStrategy;
import com.takakim.investtracker.service.position.FifoCostBasisStrategy;
import com.takakim.investtracker.service.position.LifoCostBasisStrategy;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostBasisStrategyTests {

    private final FifoCostBasisStrategy fifo = new FifoCostBasisStrategy();
    private final LifoCostBasisStrategy lifo = new LifoCostBasisStrategy();
    private final AverageCostBasisStrategy avgCost = new AverageCostBasisStrategy();

    private Account account;
    private Instrument instrument;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        Portfolio portfolio = new Portfolio("Tech Growth", new Currency("USD"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        account = new Account(portfolio, "Trading Account", "Freetrade", new Currency("USD"));
        instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
        baseTime = Instant.parse("2026-01-01T10:00:00Z");
    }

    private Transaction createBuy(int daysOffset, String qty, String grossAmount, String fee) {
        return new Transaction(
                account,
                instrument,
                TransactionType.BUY,
                baseTime.plus(daysOffset, ChronoUnit.DAYS),
                null,
                new BigDecimal(qty),
                new BigDecimal(grossAmount).divide(new BigDecimal(qty), 4, java.math.RoundingMode.HALF_EVEN),
                new BigDecimal(grossAmount),
                new BigDecimal(fee),
                BigDecimal.ZERO,
                "USD",
                null,
                null,
                null,
                null
        );
    }

    private Transaction createSell(int daysOffset, String qty, String grossAmount, String fee) {
        return new Transaction(
                account,
                instrument,
                TransactionType.SELL,
                baseTime.plus(daysOffset, ChronoUnit.DAYS),
                null,
                new BigDecimal(qty),
                new BigDecimal(grossAmount).divide(new BigDecimal(qty), 4, java.math.RoundingMode.HALF_EVEN),
                new BigDecimal(grossAmount),
                new BigDecimal(fee),
                BigDecimal.ZERO,
                "USD",
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("FIFO consumes oldest acquisition lots first")
    void fifoCostBasisCalculation() {
        // Lot 1: 10 shares @ $100 + $5 fee = $1050 total ($105.00/share)
        Transaction buy1 = createBuy(1, "10.0", "1000.00", "50.00");
        // Lot 2: 10 shares @ $150 + $50 fee = $1550 total ($155.00/share)
        Transaction buy2 = createBuy(5, "10.0", "1500.00", "50.00");
        // Sell: 5 shares for $1000 gross - $10 fee = $990 net ($198/share)
        Transaction sell = createSell(10, "5.0", "1000.00", "10.00");

        PositionCalculationResult result = fifo.calculate(account, instrument, List.of(buy1, buy2, sell));

        assertEquals(new BigDecimal("15.00000000"), result.quantity());
        // Remaining in Lot 1: 5 shares * $105 = $525. Lot 2: 10 shares * $155 = $1550. Total = $2075
        assertEquals(new BigDecimal("2075.0000"), result.costBasisAmount());
        assertEquals(2, result.openLots().size());
        assertEquals(1, result.disposals().size());

        // Realized gain on 5 shares: $990 proceeds - (5 * $105 = $525 cost) = $465.00
        assertEquals(new BigDecimal("465.0000"), result.realizedGainLossAmount());
        assertEquals(CostBasisMethod.FIFO, fifo.getMethod());
    }

    @Test
    @DisplayName("LIFO consumes newest acquisition lots first")
    void lifoCostBasisCalculation() {
        // Lot 1: 10 shares @ $100 + $50 fee = $1050 total ($105.00/share)
        Transaction buy1 = createBuy(1, "10.0", "1000.00", "50.00");
        // Lot 2: 10 shares @ $150 + $50 fee = $1550 total ($155.00/share)
        Transaction buy2 = createBuy(5, "10.0", "1500.00", "50.00");
        // Sell: 5 shares for $1000 gross - $10 fee = $990 net
        Transaction sell = createSell(10, "5.0", "1000.00", "10.00");

        PositionCalculationResult result = lifo.calculate(account, instrument, List.of(buy1, buy2, sell));

        assertEquals(new BigDecimal("15.00000000"), result.quantity());
        // Lot 2 is consumed first: remaining in Lot 2: 5 shares * $155 = $775. Lot 1: 10 shares * $105 = $1050. Total = $1825
        assertEquals(new BigDecimal("1825.0000"), result.costBasisAmount());
        assertEquals(2, result.openLots().size());
        assertEquals(1, result.disposals().size());

        // Realized gain on 5 shares from Lot 2: $990 proceeds - (5 * $155 = $775 cost) = $215.00
        assertEquals(new BigDecimal("215.0000"), result.realizedGainLossAmount());
        assertEquals(CostBasisMethod.LIFO, lifo.getMethod());
    }

    @Test
    @DisplayName("Average Cost calculates weighted average unit cost on buy and proportional disposal on sell")
    void averageCostBasisCalculation() {
        // Buy 1: 10 shares @ $100 = $1000 total
        Transaction buy1 = createBuy(1, "10.0", "1000.00", "0.00");
        // Buy 2: 10 shares @ $200 = $2000 total
        // Combined: 20 shares, $3000 total -> $150.00/share avg
        Transaction buy2 = createBuy(5, "10.0", "2000.00", "0.00");
        // Sell: 5 shares @ $250 = $1250 total
        Transaction sell = createSell(10, "5.0", "1250.00", "0.00");

        PositionCalculationResult result = avgCost.calculate(account, instrument, List.of(buy1, buy2, sell));

        assertEquals(new BigDecimal("15.00000000"), result.quantity());
        // 15 remaining shares * $150.00 = $2250.00
        assertEquals(new BigDecimal("2250.0000"), result.costBasisAmount());
        assertEquals(new BigDecimal("150.0000"), result.averageUnitCost());

        // Realized gain: $1250 proceeds - (5 * $150 = $750 cost) = $500.00
        assertEquals(new BigDecimal("500.0000"), result.realizedGainLossAmount());
        assertEquals(CostBasisMethod.AVERAGE_COST, avgCost.getMethod());
    }

    @Test
    @DisplayName("Stock split adjusts lot quantities upwards and unit costs downwards while preserving total cost")
    void stockSplitHandling() {
        // Buy 10 shares for $1000 total ($100/share)
        Transaction buy = createBuy(1, "10.0", "1000.00", "0.00");

        // 2-for-1 split adds 10 additional shares
        Transaction split = new Transaction(
                account,
                instrument,
                TransactionType.STOCK_SPLIT,
                baseTime.plus(5, ChronoUnit.DAYS),
                null,
                new BigDecimal("10.0"),
                null,
                BigDecimal.ZERO,
                null,
                null,
                "USD",
                null,
                null,
                "2-for-1 stock split",
                null
        );

        PositionCalculationResult result = fifo.calculate(account, instrument, List.of(buy, split));

        assertEquals(new BigDecimal("20.00000000"), result.quantity());
        assertEquals(new BigDecimal("1000.0000"), result.costBasisAmount());
        assertEquals(new BigDecimal("50.0000"), result.averageUnitCost());
    }

    @Test
    @DisplayName("Reverse stock split adjusts lot quantities downwards and unit costs upwards")
    void reverseStockSplitHandling() {
        // Buy 20 shares for $1000 total ($50/share)
        Transaction buy = createBuy(1, "20.0", "1000.00", "0.00");

        // 1-for-2 reverse split removes 10 shares
        Transaction revSplit = new Transaction(
                account,
                instrument,
                TransactionType.REVERSE_STOCK_SPLIT,
                baseTime.plus(5, ChronoUnit.DAYS),
                null,
                new BigDecimal("10.0"),
                null,
                BigDecimal.ZERO,
                null,
                null,
                "USD",
                null,
                null,
                "1-for-2 reverse split",
                null
        );

        PositionCalculationResult result = fifo.calculate(account, instrument, List.of(buy, revSplit));

        assertEquals(new BigDecimal("10.00000000"), result.quantity());
        assertEquals(new BigDecimal("1000.0000"), result.costBasisAmount());
        assertEquals(new BigDecimal("100.0000"), result.averageUnitCost());
    }

    @Test
    @DisplayName("Empty transactions list returns zero position safely across all strategies")
    void emptyTransactionsHandling() {
        for (var strategy : List.of(fifo, lifo, avgCost)) {
            PositionCalculationResult result = strategy.calculate(account, instrument, List.of());
            assertEquals(BigDecimal.ZERO.setScale(8), result.quantity());
            assertEquals(BigDecimal.ZERO.setScale(4), result.costBasisAmount());
            assertTrue(result.openLots().isEmpty());

            PositionCalculationResult nullResult = strategy.calculate(account, instrument, null);
            assertEquals(BigDecimal.ZERO.setScale(8), nullResult.quantity());
            assertEquals(BigDecimal.ZERO.setScale(4), nullResult.costBasisAmount());
            assertTrue(nullResult.openLots().isEmpty());
        }
    }

    @Test
    @DisplayName("LIFO and Average Cost handle stock split and reverse stock split correctly")
    void lifoAndAvgCostStockSplits() {
        Transaction buy = createBuy(1, "20.0", "1000.00", "0.00");
        Transaction split = new Transaction(
                account, instrument, TransactionType.STOCK_SPLIT,
                baseTime.plus(5, ChronoUnit.DAYS), null,
                new BigDecimal("20.0"), null, BigDecimal.ZERO, null, null,
                "USD", null, null, "2-for-1 split", null
        );
        Transaction revSplit = new Transaction(
                account, instrument, TransactionType.REVERSE_STOCK_SPLIT,
                baseTime.plus(10, ChronoUnit.DAYS), null,
                new BigDecimal("20.0"), null, BigDecimal.ZERO, null, null,
                "USD", null, null, "1-for-2 reverse split", null
        );

        // LIFO Split
        PositionCalculationResult lifoSplit = lifo.calculate(account, instrument, List.of(buy, split));
        assertEquals(new BigDecimal("40.00000000"), lifoSplit.quantity());
        assertEquals(new BigDecimal("1000.0000"), lifoSplit.costBasisAmount());
        assertEquals(new BigDecimal("25.0000"), lifoSplit.averageUnitCost());

        // LIFO Reverse Split
        PositionCalculationResult lifoRevSplit = lifo.calculate(account, instrument, List.of(buy, split, revSplit));
        assertEquals(new BigDecimal("20.00000000"), lifoRevSplit.quantity());
        assertEquals(new BigDecimal("1000.0000"), lifoRevSplit.costBasisAmount());
        assertEquals(new BigDecimal("50.0000"), lifoRevSplit.averageUnitCost());

        // Avg Cost Split
        PositionCalculationResult avgSplit = avgCost.calculate(account, instrument, List.of(buy, split));
        assertEquals(new BigDecimal("40.00000000"), avgSplit.quantity());
        assertEquals(new BigDecimal("1000.0000"), avgSplit.costBasisAmount());
        assertEquals(new BigDecimal("25.0000"), avgSplit.averageUnitCost());

        // Avg Cost Reverse Split
        PositionCalculationResult avgRevSplit = avgCost.calculate(account, instrument, List.of(buy, split, revSplit));
        assertEquals(new BigDecimal("20.00000000"), avgRevSplit.quantity());
        assertEquals(new BigDecimal("1000.0000"), avgRevSplit.costBasisAmount());
        assertEquals(new BigDecimal("50.0000"), avgRevSplit.averageUnitCost());
    }

    @Test
    @DisplayName("Handling of excess sells exceeding available open lots")
    void excessSellHandling() {
        Transaction buy = createBuy(1, "10.0", "1000.00", "0.00");
        // Sell 15 shares (5 more than owned)
        Transaction sellExcess = createSell(5, "15.0", "1500.00", "0.00");

        for (var strategy : List.of(fifo, lifo, avgCost)) {
            PositionCalculationResult res = strategy.calculate(account, instrument, List.of(buy, sellExcess));
            assertEquals(BigDecimal.ZERO.setScale(8), res.quantity());
            assertEquals(BigDecimal.ZERO.setScale(4), res.costBasisAmount());
            assertTrue(res.openLots().isEmpty());
        }
    }

    @Test
    @DisplayName("Non-trade transactions do not alter position state")
    void nonTradeTransactionsIgnored() {
        Transaction buy = createBuy(1, "10.0", "1000.00", "0.00");
        Transaction div = new Transaction(
                account, instrument, TransactionType.DIVIDEND,
                baseTime.plus(3, ChronoUnit.DAYS), null,
                null, null, new BigDecimal("50.00"), null, null,
                "USD", null, null, "Dividend", null
        );
        Transaction fee = new Transaction(
                account, instrument, TransactionType.FEE,
                baseTime.plus(4, ChronoUnit.DAYS), null,
                null, null, new BigDecimal("5.00"), null, null,
                "USD", null, null, "Custody fee", null
        );
        Transaction interest = new Transaction(
                account, instrument, TransactionType.INTEREST,
                baseTime.plus(5, ChronoUnit.DAYS), null,
                null, null, new BigDecimal("10.00"), null, null,
                "USD", null, null, "Cash interest", null
        );

        for (var strategy : List.of(fifo, lifo, avgCost)) {
            PositionCalculationResult res = strategy.calculate(account, instrument, List.of(buy, div, fee, interest));
            assertEquals(new BigDecimal("10.00000000"), res.quantity());
            assertEquals(new BigDecimal("1000.0000"), res.costBasisAmount());
            assertEquals(1, res.openLots().size());
        }
    }

    @Test
    @DisplayName("Selling when holdings are zero is safely skipped")
    void sellWhenZeroHoldings() {
        Transaction sell = createSell(1, "10.0", "1000.00", "0.00");
        for (var strategy : List.of(fifo, lifo, avgCost)) {
            PositionCalculationResult res = strategy.calculate(account, instrument, List.of(sell));
            assertEquals(BigDecimal.ZERO.setScale(8), res.quantity());
            assertEquals(BigDecimal.ZERO.setScale(4), res.costBasisAmount());
            assertTrue(res.openLots().isEmpty());
        }
    }

    @Test
    @DisplayName("Stock split and reverse split when holdings are zero is safely skipped")
    void splitWhenZeroHoldings() {
        Transaction split = new Transaction(
                account, instrument, TransactionType.STOCK_SPLIT,
                baseTime.plus(5, ChronoUnit.DAYS), null,
                new BigDecimal("10.0"), null, BigDecimal.ZERO, null, null,
                "USD", null, null, "2-for-1 split", null
        );
        Transaction revSplit = new Transaction(
                account, instrument, TransactionType.REVERSE_STOCK_SPLIT,
                baseTime.plus(6, ChronoUnit.DAYS), null,
                new BigDecimal("5.0"), null, BigDecimal.ZERO, null, null,
                "USD", null, null, "1-for-2 split", null
        );

        for (var strategy : List.of(fifo, lifo, avgCost)) {
            PositionCalculationResult res = strategy.calculate(account, instrument, List.of(split, revSplit));
            assertEquals(BigDecimal.ZERO.setScale(8), res.quantity());
            assertEquals(BigDecimal.ZERO.setScale(4), res.costBasisAmount());
            assertTrue(res.openLots().isEmpty());
        }
    }

    @Test
    @DisplayName("Full disposal closes all open lots")
    void fullDisposalClosesLots() {
        Transaction buy = createBuy(1, "10.0", "1000.00", "0.00");
        Transaction sell = createSell(5, "10.0", "1500.00", "0.00");

        for (var strategy : List.of(fifo, lifo, avgCost)) {
            PositionCalculationResult res = strategy.calculate(account, instrument, List.of(buy, sell));
            assertEquals(BigDecimal.ZERO.setScale(8), res.quantity());
            assertEquals(BigDecimal.ZERO.setScale(4), res.costBasisAmount());
            assertEquals(BigDecimal.ZERO.setScale(4), res.averageUnitCost());
            assertTrue(res.openLots().isEmpty());
            assertEquals(1, res.disposals().size());
            assertEquals(new BigDecimal("500.0000"), res.realizedGainLossAmount());
        }
    }

    @Test
    @DisplayName("PositionCalculationResult handles null openLots and disposals gracefully")
    void positionCalculationResultNullLists() {
        PositionCalculationResult res = new PositionCalculationResult(
                account.getId(),
                instrument.getId(),
                CostBasisMethod.FIFO,
                BigDecimal.TEN,
                new BigDecimal("100.0"),
                "USD",
                BigDecimal.TEN,
                BigDecimal.ZERO,
                null,
                null
        );
        assertNotNull(res.openLots());
        assertNotNull(res.disposals());
        assertTrue(res.openLots().isEmpty());
        assertTrue(res.disposals().isEmpty());
    }

    @Test
    @DisplayName("Non-completed transactions are skipped in calculation")
    void nonCompletedTransactionsSkipped() {
        Transaction buy = createBuy(1, "10.0", "1000.00", "0.00");
        Transaction pendingBuy = new Transaction(
                account, instrument, TransactionType.BUY, baseTime.plus(2, ChronoUnit.DAYS), null,
                new BigDecimal("5.0"), new BigDecimal("100.0"), new BigDecimal("500.0"),
                BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null, null, null
        );
        pendingBuy.markCorrected(); // TransactionStatus.CORRECTED — not COMPLETED, so filtered out

        for (var strategy : List.of(fifo, lifo, avgCost)) {
            PositionCalculationResult res = strategy.calculate(account, instrument, List.of(buy, pendingBuy));
            assertEquals(new BigDecimal("10.00000000"), res.quantity());
            assertEquals(new BigDecimal("1000.0000"), res.costBasisAmount());
        }
    }

    @Test
    @DisplayName("Fallback currency resolution when instrument and account are null")
    void fallbackCurrencyResolution() {
        Account eurAcc = new Account(account.getPortfolio(), "EurAcc", "Broker", new Currency("EUR"));
        // null instrument falls back to account currency for FIFO
        PositionCalculationResult res = fifo.calculate(eurAcc, null, List.of());
        assertEquals("EUR", res.costBasisCurrency());

        // null instrument falls back to account currency for LIFO
        PositionCalculationResult lifoRes = lifo.calculate(eurAcc, null, List.of());
        assertEquals("EUR", lifoRes.costBasisCurrency());

        // null instrument falls back to account currency for AVERAGE_COST
        PositionCalculationResult avgRes = avgCost.calculate(eurAcc, null, List.of());
        assertEquals("EUR", avgRes.costBasisCurrency());

        // null account and null instrument falls back to USD default
        PositionCalculationResult nullRes = fifo.calculate(null, null, List.of());
        assertEquals("USD", nullRes.costBasisCurrency());

        PositionCalculationResult lifoNullRes = lifo.calculate(null, null, List.of());
        assertEquals("USD", lifoNullRes.costBasisCurrency());

        PositionCalculationResult avgNullRes = avgCost.calculate(null, null, List.of());
        assertEquals("USD", avgNullRes.costBasisCurrency());
    }

    @Test
    @DisplayName("Multi-lot SELL skips exhausted lots (remainingQuantity == 0)")
    void sellSkipsExhaustedLots() {
        // Buy twice to create two lots, then sell all first lot completely, then buy again
        Transaction buy1 = createBuy(1, "5.0", "500.00", "0.00");    // lot1: 5 shares @ $100
        Transaction sell1 = createSell(2, "5.0", "550.00", "0.00");  // fully exhausts lot1
        Transaction buy2 = createBuy(3, "10.0", "900.00", "0.00");   // lot2: 10 shares @ $90
        Transaction sell2 = createSell(4, "5.0", "500.00", "0.00");  // partial sell from lot2

        // FIFO: lot1 already exhausted, sell2 picks from lot2
        PositionCalculationResult fifoRes = fifo.calculate(account, instrument, List.of(buy1, sell1, buy2, sell2));
        assertEquals(new BigDecimal("5.00000000"), fifoRes.quantity());
        assertEquals(2, fifoRes.disposals().size());

        // LIFO: same scenario
        PositionCalculationResult lifoRes = lifo.calculate(account, instrument, List.of(buy1, sell1, buy2, sell2));
        assertEquals(new BigDecimal("5.00000000"), lifoRes.quantity());
        // AVERAGE_COST
        PositionCalculationResult avgRes = avgCost.calculate(account, instrument, List.of(buy1, sell1, buy2, sell2));
        assertEquals(new BigDecimal("5.00000000"), avgRes.quantity());
    }

    @Test
    @DisplayName("costBasisCurrency is derived from BUY transaction currency, not instrument native currency (Freetrade GBP scenario)")
    void costBasisCurrencyDerivedFromTransactionCurrency() {
        Portfolio gbpPortfolio = new Portfolio("ISA", new Currency("GBP"), CostBasisMethod.AVERAGE_COST, ReturnMethod.XIRR);
        Account gbpAccount = new Account(gbpPortfolio, "Freetrade ISA", "Freetrade", new Currency("GBP"));
        Instrument usdStock = new Instrument("Nvidia", AssetClass.STOCK, "NVDA", "US67066G1040", "NASDAQ", new Currency("USD"));
        Instant t = Instant.parse("2026-01-01T10:00:00Z");

        Transaction gbpBuy1 = new Transaction(
                gbpAccount, usdStock, TransactionType.BUY,
                t, null, new BigDecimal("10.0"),
                new BigDecimal("112.70"), new BigDecimal("1127.00"),
                new BigDecimal("4.50"), BigDecimal.ZERO,
                "GBP", new BigDecimal("1.28"), "USD", "Order ABC", null
        );
        Transaction gbpBuy2 = new Transaction(
                gbpAccount, usdStock, TransactionType.BUY,
                t.plus(5, ChronoUnit.DAYS), null, new BigDecimal("5.0"),
                new BigDecimal("125.00"), new BigDecimal("625.00"),
                new BigDecimal("2.50"), BigDecimal.ZERO,
                "GBP", new BigDecimal("1.28"), "USD", "Order DEF", null
        );

        PositionCalculationResult avgResult = avgCost.calculate(gbpAccount, usdStock, List.of(gbpBuy1, gbpBuy2));
        assertEquals("GBP", avgResult.costBasisCurrency(),
                "AVERAGE_COST: costBasisCurrency must be GBP (tx), not USD (instrument)");
        assertEquals(new BigDecimal("1759.0000"), avgResult.costBasisAmount());

        PositionCalculationResult fifoResult = fifo.calculate(gbpAccount, usdStock, List.of(gbpBuy1, gbpBuy2));
        assertEquals("GBP", fifoResult.costBasisCurrency(),
                "FIFO: costBasisCurrency must be GBP (tx), not USD (instrument)");

        PositionCalculationResult lifoResult = lifo.calculate(gbpAccount, usdStock, List.of(gbpBuy1, gbpBuy2));
        assertEquals("GBP", lifoResult.costBasisCurrency(),
                "LIFO: costBasisCurrency must be GBP (tx), not USD (instrument)");
    }

    @Test
    @DisplayName("costBasisCurrency falls back to account currency when no BUY transactions exist")
    void costBasisCurrencyFallsBackWhenNoBuys() {
        Portfolio gbpPortfolio = new Portfolio("ISA", new Currency("GBP"), CostBasisMethod.AVERAGE_COST, ReturnMethod.XIRR);
        Account gbpAccount = new Account(gbpPortfolio, "Freetrade ISA", "Freetrade", new Currency("GBP"));
        Instrument usdStock = new Instrument("Nvidia", AssetClass.STOCK, "NVDA", "US67066G1040", "NASDAQ", new Currency("USD"));
        Instant t = Instant.parse("2026-01-01T10:00:00Z");

        // Only a SELL transaction — no BUY in the list so currency loop exits without matching
        Transaction gbpSell = new Transaction(
                gbpAccount, usdStock, TransactionType.SELL,
                t.plus(3, ChronoUnit.DAYS), null, new BigDecimal("10.0"),
                new BigDecimal("120.00"), new BigDecimal("1200.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                "GBP", null, null, null, null
        );

        PositionCalculationResult avgSellOnly = avgCost.calculate(gbpAccount, usdStock, List.of(gbpSell));
        assertEquals("GBP", avgSellOnly.costBasisCurrency(),
                "AVERAGE_COST: no BUY found, falls back to account currency GBP");

        PositionCalculationResult fifoSellOnly = fifo.calculate(gbpAccount, usdStock, List.of(gbpSell));
        assertEquals("GBP", fifoSellOnly.costBasisCurrency(),
                "FIFO: no BUY found, falls back to account currency GBP");

        PositionCalculationResult lifoSellOnly = lifo.calculate(gbpAccount, usdStock, List.of(gbpSell));
        assertEquals("GBP", lifoSellOnly.costBasisCurrency(),
                "LIFO: no BUY found, falls back to account currency GBP");
    }
}

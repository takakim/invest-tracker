package com.takakim.investtracker.service.position;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionStatus;
import com.takakim.investtracker.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Weighted Average Cost basis strategy.
 * Every BUY recalculates the weighted average unit cost.
 * Sells consume cost basis proportionally based on the weighted average cost.
 */
@Component
public class AverageCostBasisStrategy implements CostBasisStrategy {

    private static final int QUANTITY_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    @Override
    public CostBasisMethod getMethod() {
        return CostBasisMethod.AVERAGE_COST;
    }

    @Override
    public PositionCalculationResult calculate(Account account, Instrument instrument, List<Transaction> transactions) {
        String currency = "USD";
        if (account != null && account.getAccountCurrency() != null) {
            currency = account.getAccountCurrency().code();
        } else if (instrument != null && instrument.getCurrency() != null) {
            currency = instrument.getCurrency().code();
        }

        UUID instId = instrument != null ? instrument.getId() : UUID.randomUUID();
        UUID accId = account != null ? account.getId() : UUID.randomUUID();

        if (transactions == null || transactions.isEmpty()) {
            return new PositionCalculationResult(
                    accId,
                    instId,
                    getMethod(),
                    BigDecimal.ZERO.setScale(QUANTITY_SCALE, ROUNDING),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING),
                    currency,
                    BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING),
                    List.of(),
                    List.of()
            );
        }

        List<Transaction> sortedTxs = transactions.stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.COMPLETED)
                .sorted(Comparator.comparing(Transaction::getTradeDate)
                        .thenComparing(Transaction::getCreatedAt)
                        .thenComparing(Transaction::getId))
                .toList();

        // Derive cost basis currency from the first BUY transaction's actual settled currency.
        // Freetrade and other brokers settle in account currency (GBP) even for USD-denominated stocks.
        // Using instrument.getCurrency() here would mislabel GBP amounts as USD, causing wrong FX conversion.
        // Note: BUY transactions always have a non-null currency enforced by the Transaction constructor.
        for (Transaction tx : sortedTxs) {
            if (tx.getType() == TransactionType.BUY) {
                currency = tx.getCurrency();
                break;
            }
        }

        List<PositionLot> lots = new ArrayList<>();
        List<LotDisposal> disposals = new ArrayList<>();
        BigDecimal cumulativeRealizedGainLoss = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        BigDecimal currentQty = BigDecimal.ZERO;
        BigDecimal currentCostBasis = BigDecimal.ZERO;

        for (Transaction tx : sortedTxs) {
            TransactionType type = tx.getType();

            if (type == TransactionType.BUY) {
                BigDecimal qty = tx.getQuantity();
                BigDecimal totalCost = tx.getNetAmount();
                currentQty = currentQty.add(qty);
                currentCostBasis = currentCostBasis.add(totalCost);

                BigDecimal unitCost = currentCostBasis.divide(currentQty, MONEY_SCALE, ROUNDING);

                lots.add(new PositionLot(
                        UUID.randomUUID(),
                        tx.getId(),
                        tx.getTradeDate(),
                        qty.setScale(QUANTITY_SCALE, ROUNDING),
                        qty.setScale(QUANTITY_SCALE, ROUNDING),
                        unitCost,
                        totalCost.setScale(MONEY_SCALE, ROUNDING),
                        tx.getCurrency()
                ));

                for (PositionLot lot : lots) {
                    if (lot.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        lot.setUnitCost(unitCost);
                        lot.setTotalCost(lot.getRemainingQuantity().multiply(unitCost).setScale(MONEY_SCALE, ROUNDING));
                    }
                }

            } else if (type == TransactionType.SELL) {
                BigDecimal qtyToSell = tx.getQuantity();
                if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal totalProceeds = tx.getNetAmount();

                BigDecimal avgUnitCost = currentCostBasis.divide(currentQty, MONEY_SCALE, ROUNDING);
                BigDecimal actualSellQty = qtyToSell.min(currentQty);
                BigDecimal disposedCostBasis = actualSellQty.multiply(avgUnitCost).setScale(MONEY_SCALE, ROUNDING);
                BigDecimal realizedPnl = totalProceeds.subtract(disposedCostBasis);
                cumulativeRealizedGainLoss = cumulativeRealizedGainLoss.add(realizedPnl);

                disposals.add(new LotDisposal(
                        tx.getId(),
                        lots.isEmpty() ? UUID.randomUUID() : lots.get(0).getId(),
                        tx.getTradeDate(),
                        actualSellQty.setScale(QUANTITY_SCALE, ROUNDING),
                        disposedCostBasis,
                        totalProceeds,
                        realizedPnl,
                        currency
                ));

                BigDecimal remainingRatio = currentQty.subtract(actualSellQty).divide(currentQty, 8, ROUNDING);
                currentQty = currentQty.subtract(actualSellQty);
                currentCostBasis = currentCostBasis.subtract(disposedCostBasis).max(BigDecimal.ZERO);

                for (PositionLot lot : lots) {
                    if (lot.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal newRemainingQty = lot.getRemainingQuantity().multiply(remainingRatio).setScale(QUANTITY_SCALE, ROUNDING);
                        lot.setRemainingQuantity(newRemainingQty);
                        lot.setTotalCost(newRemainingQty.multiply(avgUnitCost).setScale(MONEY_SCALE, ROUNDING));
                    }
                }

            } else if (type == TransactionType.STOCK_SPLIT) {
                if (currentQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal splitQty = tx.getQuantity();
                    BigDecimal newTotalQty = currentQty.add(splitQty);
                    BigDecimal ratio = newTotalQty.divide(currentQty, 8, ROUNDING);
                    currentQty = newTotalQty;

                    BigDecimal newAvgCost = currentCostBasis.divide(currentQty, MONEY_SCALE, ROUNDING);

                    for (PositionLot lot : lots) {
                        if (lot.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal newLotQty = lot.getRemainingQuantity().multiply(ratio).setScale(QUANTITY_SCALE, ROUNDING);
                            lot.setRemainingQuantity(newLotQty);
                            lot.setUnitCost(newAvgCost);
                            lot.setTotalCost(newLotQty.multiply(newAvgCost).setScale(MONEY_SCALE, ROUNDING));
                        }
                    }
                }

            } else if (type == TransactionType.REVERSE_STOCK_SPLIT) {
                if (currentQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal revQty = tx.getQuantity();
                    BigDecimal newTotalQty = currentQty.subtract(revQty);
                    BigDecimal ratio = newTotalQty.divide(currentQty, 8, ROUNDING);
                    currentQty = newTotalQty;

                    BigDecimal newAvgCost = currentCostBasis.divide(currentQty, MONEY_SCALE, ROUNDING);

                    for (PositionLot lot : lots) {
                        if (lot.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal newLotQty = lot.getRemainingQuantity().multiply(ratio).setScale(QUANTITY_SCALE, ROUNDING);
                            lot.setRemainingQuantity(newLotQty);
                            lot.setUnitCost(newAvgCost);
                            lot.setTotalCost(newLotQty.multiply(newAvgCost).setScale(MONEY_SCALE, ROUNDING));
                        }
                    }
                }
            }
        }

        List<PositionLot> openLots = lots.stream()
                .filter(l -> l.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal finalQuantity = currentQty.setScale(QUANTITY_SCALE, ROUNDING);
        BigDecimal finalCostBasis = currentCostBasis.setScale(MONEY_SCALE, ROUNDING);

        BigDecimal averageUnitCost = finalQuantity.compareTo(BigDecimal.ZERO) > 0
                ? finalCostBasis.divide(finalQuantity, MONEY_SCALE, ROUNDING)
                : BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        return new PositionCalculationResult(
                accId,
                instId,
                getMethod(),
                finalQuantity,
                finalCostBasis,
                currency,
                averageUnitCost,
                cumulativeRealizedGainLoss,
                openLots,
                disposals
        );
    }
}

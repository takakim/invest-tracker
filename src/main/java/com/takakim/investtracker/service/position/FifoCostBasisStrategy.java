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
 * First-In, First-Out (FIFO) cost-basis strategy.
 * Sells consume the oldest available acquisition lots first.
 */
@Component
public class FifoCostBasisStrategy implements CostBasisStrategy {

    private static final int QUANTITY_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    @Override
    public CostBasisMethod getMethod() {
        return CostBasisMethod.FIFO;
    }

    @Override
    public PositionCalculationResult calculate(Account account, Instrument instrument, List<Transaction> transactions) {
        String currency = (instrument != null && instrument.getCurrency() != null)
                ? instrument.getCurrency().code()
                : (account != null && account.getAccountCurrency() != null ? account.getAccountCurrency().code() : "USD");

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

        List<PositionLot> lots = new ArrayList<>();
        List<LotDisposal> disposals = new ArrayList<>();
        BigDecimal cumulativeRealizedGainLoss = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        for (Transaction tx : sortedTxs) {
            TransactionType type = tx.getType();

            if (type == TransactionType.BUY) {
                BigDecimal qty = tx.getQuantity();
                BigDecimal totalCost = tx.getNetAmount();
                BigDecimal unitCost = totalCost.divide(qty, MONEY_SCALE, ROUNDING);

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

            } else if (type == TransactionType.SELL) {
                BigDecimal qtyToSell = tx.getQuantity();
                BigDecimal totalProceeds = tx.getNetAmount();
                BigDecimal remainingToSell = qtyToSell;

                for (PositionLot lot : lots) {
                    if (remainingToSell.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    if (lot.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    BigDecimal matchQty = lot.getRemainingQuantity().min(remainingToSell);
                    BigDecimal lotCostBasis = matchQty.multiply(lot.getUnitCost()).setScale(MONEY_SCALE, ROUNDING);
                    BigDecimal lotProceeds = totalProceeds.multiply(matchQty).divide(qtyToSell, MONEY_SCALE, ROUNDING);
                    BigDecimal realizedPnl = lotProceeds.subtract(lotCostBasis);
                    cumulativeRealizedGainLoss = cumulativeRealizedGainLoss.add(realizedPnl);

                    disposals.add(new LotDisposal(
                            tx.getId(),
                            lot.getId(),
                            tx.getTradeDate(),
                            matchQty.setScale(QUANTITY_SCALE, ROUNDING),
                            lotCostBasis,
                            lotProceeds,
                            realizedPnl,
                            currency
                    ));

                    BigDecimal newRemaining = lot.getRemainingQuantity().subtract(matchQty);
                    lot.setRemainingQuantity(newRemaining.setScale(QUANTITY_SCALE, ROUNDING));
                    lot.setTotalCost(newRemaining.multiply(lot.getUnitCost()).setScale(MONEY_SCALE, ROUNDING));

                    remainingToSell = remainingToSell.subtract(matchQty);
                }

            } else if (type == TransactionType.STOCK_SPLIT) {
                BigDecimal splitQty = tx.getQuantity();
                BigDecimal totalOpenQty = lots.stream()
                        .map(PositionLot::getRemainingQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalOpenQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal newTotalQty = totalOpenQty.add(splitQty);
                    BigDecimal ratio = newTotalQty.divide(totalOpenQty, 8, ROUNDING);

                    for (PositionLot lot : lots) {
                        if (lot.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal newLotQty = lot.getRemainingQuantity().multiply(ratio).setScale(QUANTITY_SCALE, ROUNDING);
                            lot.setRemainingQuantity(newLotQty);
                            BigDecimal newUnitCost = lot.getTotalCost().divide(newLotQty, MONEY_SCALE, ROUNDING);
                            lot.setUnitCost(newUnitCost);
                        }
                    }
                }

            } else if (type == TransactionType.REVERSE_STOCK_SPLIT) {
                BigDecimal revQty = tx.getQuantity();
                BigDecimal totalOpenQty = lots.stream()
                        .map(PositionLot::getRemainingQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalOpenQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal newTotalQty = totalOpenQty.subtract(revQty);
                    BigDecimal ratio = newTotalQty.divide(totalOpenQty, 8, ROUNDING);

                    for (PositionLot lot : lots) {
                        if (lot.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal newLotQty = lot.getRemainingQuantity().multiply(ratio).setScale(QUANTITY_SCALE, ROUNDING);
                            lot.setRemainingQuantity(newLotQty);
                            BigDecimal newUnitCost = lot.getTotalCost().divide(newLotQty, MONEY_SCALE, ROUNDING);
                            lot.setUnitCost(newUnitCost);
                        }
                    }
                }
            }
        }

        List<PositionLot> openLots = lots.stream()
                .filter(l -> l.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal finalQuantity = openLots.stream()
                .map(PositionLot::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(QUANTITY_SCALE, ROUNDING);

        BigDecimal finalCostBasis = openLots.stream()
                .map(PositionLot::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, ROUNDING);

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

package com.takakim.investtracker.service.analytics;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.currency.FxRateQuote;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnalyticsEngine {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final MarketDataService marketDataService;
    private final FxRateService fxRateService;
    private final PositionEngine positionEngine;

    public AnalyticsEngine(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            PositionRepository positionRepository,
            TransactionRepository transactionRepository,
            MarketDataService marketDataService,
            FxRateService fxRateService,
            PositionEngine positionEngine) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.marketDataService = marketDataService;
        this.fxRateService = fxRateService;
        this.positionEngine = positionEngine;
    }

    public PortfolioAnalytics calculate(UUID portfolioId, Instant asOf) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        Instant targetTime = asOf != null ? asOf : Instant.now();
        Currency baseCurrency = portfolio.getBaseCurrency();
        List<String> warnings = new ArrayList<>();

        List<Account> activeAccounts = accountRepository
                .findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE);

        // 1. Calculate Cash per account
        BigDecimal totalCashInBase = BigDecimal.ZERO;
        Map<String, BigDecimal> cashByAccountMap = new LinkedHashMap<>();
        Map<String, BigDecimal> cashByCurrencyMap = new LinkedHashMap<>();

        for (Account account : activeAccounts) {
            BigDecimal accountCash = calculateAccountCash(account.getId());
            if (accountCash.compareTo(BigDecimal.ZERO) != 0) {
                Money cashMoney = new Money(accountCash, account.getAccountCurrency());
                Money cashInBase = fxRateService.convert(cashMoney, baseCurrency, targetTime);
                totalCashInBase = totalCashInBase.add(cashInBase.amount());

                cashByAccountMap.put(account.getName(), cashInBase.amount());
                cashByCurrencyMap.merge(account.getAccountCurrency().code(), cashInBase.amount(), BigDecimal::add);
            }
        }

        // 2. Fetch and value all active positions
        List<Position> positions = positionRepository
                .findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE);

        List<HoldingExposure> holdings = new ArrayList<>();
        BigDecimal totalPositionsMarketValue = BigDecimal.ZERO;
        BigDecimal totalPositionsCostBasis = BigDecimal.ZERO;

        for (Position pos : positions) {
            if (pos.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            var instrument = pos.getInstrument();
            BigDecimal qty = pos.getQuantity();

            // Fetch live price quote
            PriceQuote quote;
            try {
                quote = marketDataService.getLatestPrice(instrument.getId(), targetTime);
                if (quote.warning() != null) {
                    warnings.add(instrument.getName() + " (" + instrument.getTicker() + "): " + quote.warning());
                }
            } catch (ResourceNotFoundException e) {
                // If price unavailable, fallback to average unit cost if available
                BigDecimal fallbackPrice = pos.getCostBasisAmount() != null && qty.compareTo(BigDecimal.ZERO) > 0
                        ? pos.getCostBasisAmount().divide(qty, SCALE, ROUNDING)
                        : BigDecimal.ZERO;
                quote = new PriceQuote(
                        instrument.getId(), fallbackPrice, instrument.getCurrency().code(),
                        targetTime, com.takakim.investtracker.domain.ObservationSourceType.PROVIDER,
                        "COST_PROXY", true, "Price quote unavailable; using cost basis proxy"
                );
                warnings.add(quote.warning());
            }

            // Convert unit price and market value into base currency
            Money unitPriceMoney = new Money(quote.price(), instrument.getCurrency());
            Money unitPriceInBase = fxRateService.convert(unitPriceMoney, baseCurrency, targetTime);

            BigDecimal marketValue = qty.multiply(unitPriceInBase.amount()).setScale(SCALE, ROUNDING);

            // Convert cost basis into base currency
            BigDecimal posCostBasis = pos.getCostBasisAmount() != null ? pos.getCostBasisAmount() : BigDecimal.ZERO;
            String posCostCurr = pos.getCostBasisCurrency() != null ? pos.getCostBasisCurrency() : instrument.getCurrency().code();
            Money costBasisMoney = new Money(posCostBasis, new Currency(posCostCurr));
            Money costBasisInBase = fxRateService.convert(costBasisMoney, baseCurrency, targetTime);

            BigDecimal unrealizedGain = marketValue.subtract(costBasisInBase.amount()).setScale(SCALE, ROUNDING);

            totalPositionsMarketValue = totalPositionsMarketValue.add(marketValue);
            totalPositionsCostBasis = totalPositionsCostBasis.add(costBasisInBase.amount());

            holdings.add(new HoldingExposure(
                    instrument.getId(),
                    instrument.getName(),
                    instrument.getTicker() != null ? instrument.getTicker() : "—",
                    instrument.getAssetClass(),
                    qty,
                    quote.price(),
                    marketValue,
                    costBasisInBase.amount(),
                    unrealizedGain,
                    BigDecimal.ZERO, // will set weight after total
                    instrument.getCurrency().code()
            ));
        }

        // 3. Totals
        BigDecimal totalCurrentValue = totalPositionsMarketValue.add(totalCashInBase).setScale(SCALE, ROUNDING);
        BigDecimal totalCostBasis = totalPositionsCostBasis.add(totalCashInBase).setScale(SCALE, ROUNDING);
        BigDecimal totalUnrealizedGainLoss = totalPositionsMarketValue.subtract(totalPositionsCostBasis).setScale(SCALE, ROUNDING);

        BigDecimal totalUnrealizedReturnPct = totalPositionsCostBasis.compareTo(BigDecimal.ZERO) > 0
                ? totalUnrealizedGainLoss.divide(totalPositionsCostBasis, 6, ROUNDING).multiply(BigDecimal.valueOf(100)).setScale(4, ROUNDING)
                : BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        // Realized gain/loss from position engine
        BigDecimal totalRealizedGainLoss = BigDecimal.ZERO;
        try {
            List<PositionCalculationResult> res = positionEngine.recalculatePortfolio(portfolioId);
            for (PositionCalculationResult r : res) {
                if (r.realizedGainLossAmount() != null) {
                    String curr = r.costBasisCurrency() != null ? r.costBasisCurrency() : baseCurrency.code();
                    Money realMoney = new Money(r.realizedGainLossAmount(), new Currency(curr));
                    Money realInBase = fxRateService.convert(realMoney, baseCurrency, targetTime);
                    totalRealizedGainLoss = totalRealizedGainLoss.add(realInBase.amount());
                }
            }
        } catch (Exception ignored) {
            // Recalculation fallback
        }
        totalRealizedGainLoss = totalRealizedGainLoss.setScale(SCALE, ROUNDING);

        // 4. Compute Holding Weights
        List<HoldingExposure> weightedHoldings = new ArrayList<>();
        for (HoldingExposure h : holdings) {
            BigDecimal weight = totalCurrentValue.compareTo(BigDecimal.ZERO) > 0
                    ? h.marketValue().divide(totalCurrentValue, 4, ROUNDING)
                    : BigDecimal.ZERO;
            weightedHoldings.add(new HoldingExposure(
                    h.instrumentId(), h.instrumentName(), h.ticker(), h.assetClass(),
                    h.quantity(), h.currentPrice(), h.marketValue(), h.costBasis(),
                    h.unrealizedGainLoss(), weight, h.currency()
            ));
        }
        weightedHoldings.sort(Comparator.comparing(HoldingExposure::marketValue).reversed());

        // 5. Allocation by Asset Class
        Map<String, BigDecimal> assetClassMarketValue = new LinkedHashMap<>();
        Map<String, BigDecimal> assetClassCostBasis = new LinkedHashMap<>();
        Map<String, BigDecimal> assetClassUnrealized = new LinkedHashMap<>();

        for (HoldingExposure h : weightedHoldings) {
            String category = h.assetClass().name();
            assetClassMarketValue.merge(category, h.marketValue(), BigDecimal::add);
            assetClassCostBasis.merge(category, h.costBasis(), BigDecimal::add);
            assetClassUnrealized.merge(category, h.unrealizedGainLoss(), BigDecimal::add);
        }
        if (totalCashInBase.compareTo(BigDecimal.ZERO) > 0) {
            String cashCat = AssetClass.CASH.name();
            assetClassMarketValue.merge(cashCat, totalCashInBase, BigDecimal::add);
            assetClassCostBasis.merge(cashCat, totalCashInBase, BigDecimal::add);
            assetClassUnrealized.merge(cashCat, BigDecimal.ZERO, BigDecimal::add);
        }

        List<AllocationItem> byAssetClass = new ArrayList<>();
        for (String cat : assetClassMarketValue.keySet()) {
            BigDecimal mv = assetClassMarketValue.get(cat).setScale(SCALE, ROUNDING);
            BigDecimal cb = assetClassCostBasis.get(cat).setScale(SCALE, ROUNDING);
            BigDecimal un = assetClassUnrealized.get(cat).setScale(SCALE, ROUNDING);
            BigDecimal pct = totalCurrentValue.compareTo(BigDecimal.ZERO) > 0
                    ? mv.divide(totalCurrentValue, 4, ROUNDING)
                    : BigDecimal.ZERO;
            byAssetClass.add(new AllocationItem(cat, mv, pct, cb, un));
        }
        byAssetClass.sort(Comparator.comparing(AllocationItem::marketValue).reversed());

        // 6. Allocation by Currency
        Map<String, BigDecimal> currencyMarketValue = new LinkedHashMap<>();
        for (HoldingExposure h : weightedHoldings) {
            currencyMarketValue.merge(h.currency(), h.marketValue(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> entry : cashByCurrencyMap.entrySet()) {
            currencyMarketValue.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
        }

        List<AllocationItem> byCurrency = new ArrayList<>();
        for (String curr : currencyMarketValue.keySet()) {
            BigDecimal mv = currencyMarketValue.get(curr).setScale(SCALE, ROUNDING);
            BigDecimal pct = totalCurrentValue.compareTo(BigDecimal.ZERO) > 0
                    ? mv.divide(totalCurrentValue, 4, ROUNDING)
                    : BigDecimal.ZERO;
            byCurrency.add(new AllocationItem(curr, mv, pct, BigDecimal.ZERO.setScale(SCALE, ROUNDING), BigDecimal.ZERO.setScale(SCALE, ROUNDING)));
        }
        byCurrency.sort(Comparator.comparing(AllocationItem::marketValue).reversed());

        // 7. Allocation by Account
        Map<String, BigDecimal> accountMarketValue = new LinkedHashMap<>();
        for (Position pos : positions) {
            String accName = pos.getAccount().getName();
            // find holding for this position
            for (HoldingExposure h : weightedHoldings) {
                if (h.instrumentId().equals(pos.getInstrument().getId())) {
                    accountMarketValue.merge(accName, h.marketValue(), BigDecimal::add);
                    break;
                }
            }
        }
        for (Map.Entry<String, BigDecimal> entry : cashByAccountMap.entrySet()) {
            accountMarketValue.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
        }

        List<AllocationItem> byAccount = new ArrayList<>();
        for (String acc : accountMarketValue.keySet()) {
            BigDecimal mv = accountMarketValue.get(acc).setScale(SCALE, ROUNDING);
            BigDecimal pct = totalCurrentValue.compareTo(BigDecimal.ZERO) > 0
                    ? mv.divide(totalCurrentValue, 4, ROUNDING)
                    : BigDecimal.ZERO;
            byAccount.add(new AllocationItem(acc, mv, pct, BigDecimal.ZERO.setScale(SCALE, ROUNDING), BigDecimal.ZERO.setScale(SCALE, ROUNDING)));
        }
        byAccount.sort(Comparator.comparing(AllocationItem::marketValue).reversed());

        return new PortfolioAnalytics(
                portfolioId,
                targetTime,
                baseCurrency.code(),
                totalCurrentValue,
                totalCostBasis,
                totalUnrealizedGainLoss,
                totalUnrealizedReturnPct,
                totalRealizedGainLoss,
                totalCashInBase.setScale(SCALE, ROUNDING),
                byAssetClass,
                byCurrency,
                byAccount,
                weightedHoldings,
                warnings
        );
    }

    private BigDecimal calculateAccountCash(UUID accountId) {
        List<Transaction> txs = transactionRepository.findByAccountIdOrderByTradeDateDesc(accountId);
        BigDecimal cash = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            TransactionType type = tx.getType();
            if (type == TransactionType.DEPOSIT || type == TransactionType.DIVIDEND || type == TransactionType.INTEREST) {
                cash = cash.add(tx.getNetAmount());
            } else if (type == TransactionType.SELL) {
                cash = cash.add(tx.getNetAmount());
            } else if (type == TransactionType.WITHDRAWAL || type == TransactionType.FEE) {
                cash = cash.subtract(tx.getGrossAmount());
            } else if (type == TransactionType.BUY) {
                cash = cash.subtract(tx.getNetAmount());
            }
        }
        return cash.max(BigDecimal.ZERO);
    }
}

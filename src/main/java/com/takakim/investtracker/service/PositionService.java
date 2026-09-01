package com.takakim.investtracker.service;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PositionService {

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final PositionRepository positionRepository;
    private final PositionEngine positionEngine;
    private final TransactionRepository transactionRepository;
    private final MarketDataService marketDataService;
    private final FxRateService fxRateService;

    public PositionService(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            PositionRepository positionRepository,
            PositionEngine positionEngine,
            TransactionRepository transactionRepository,
            MarketDataService marketDataService,
            FxRateService fxRateService) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.positionRepository = positionRepository;
        this.positionEngine = positionEngine;
        this.transactionRepository = transactionRepository;
        this.marketDataService = marketDataService;
        this.fxRateService = fxRateService;
    }

    @Transactional(readOnly = true)
    public List<Position> listPositions(UUID portfolioId, UUID accountId) {
        getValidatedAccount(portfolioId, accountId);
        return positionRepository.findByAccountIdAndStatus(accountId, PositionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Position> listPortfolioPositions(UUID portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        return positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Position getPosition(UUID portfolioId, UUID accountId, UUID positionId) {
        getValidatedAccount(portfolioId, accountId);
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));
        if (!position.getAccount().getId().equals(accountId)) {
            throw new ResourceNotFoundException("Position " + positionId + " does not belong to account " + accountId);
        }
        return position;
    }

    @Transactional(readOnly = true)
    public PositionCalculationResult getPositionLots(UUID portfolioId, UUID accountId, UUID positionId) {
        return positionEngine.getPositionLots(portfolioId, accountId, positionId);
    }

    @Transactional(readOnly = true)
    public ApiDtos.PositionPerformanceResponse getPositionPerformance(UUID portfolioId, UUID accountId, UUID positionId) {
        Position position = getPosition(portfolioId, accountId, positionId);
        return computePositionPerformance(position.getAccount(), position.getInstrument(), position.getId());
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.PositionPerformanceResponse> listPortfolioPositionsPerformance(UUID portfolioId, boolean includeClosed) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        List<Position> positions = includeClosed
                ? positionRepository.findByAccountPortfolioId(portfolioId)
                : positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE);

        List<ApiDtos.PositionPerformanceResponse> results = new java.util.ArrayList<>(
                positions.stream()
                        .map(p -> computePositionPerformance(p.getAccount(), p.getInstrument(), p.getId()))
                        .toList()
        );

        if (includeClosed) {
            java.util.Set<String> presentKeys = positions.stream()
                    .map(p -> p.getAccount().getId() + "_" + p.getInstrument().getId())
                    .collect(java.util.stream.Collectors.toSet());

            List<Transaction> portfolioTxs = transactionRepository.findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId);
            java.util.Map<Account, List<Transaction>> txsByAccount = portfolioTxs.stream()
                    .filter(tx -> tx.getInstrument() != null)
                    .collect(java.util.stream.Collectors.groupingBy(Transaction::getAccount));

            for (java.util.Map.Entry<Account, List<Transaction>> entry : txsByAccount.entrySet()) {
                Account acc = entry.getKey();
                List<Instrument> missingInsts = entry.getValue().stream()
                        .map(Transaction::getInstrument)
                        .filter(inst -> !presentKeys.contains(acc.getId() + "_" + inst.getId()))
                        .distinct()
                        .toList();
                for (Instrument inst : missingInsts) {
                    results.add(computePositionPerformance(acc, inst, null));
                }
            }
        }

        return results;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.PositionPerformanceResponse> listAccountPositionsPerformance(UUID portfolioId, UUID accountId, boolean includeClosed) {
        Account account = getValidatedAccount(portfolioId, accountId);
        List<Position> positions = includeClosed
                ? positionRepository.findByAccountId(accountId)
                : positionRepository.findByAccountIdAndStatus(accountId, PositionStatus.ACTIVE);

        List<ApiDtos.PositionPerformanceResponse> results = new java.util.ArrayList<>(
                positions.stream()
                        .map(p -> computePositionPerformance(account, p.getInstrument(), p.getId()))
                        .toList()
        );

        if (includeClosed) {
            java.util.Set<UUID> presentInstrumentIds = positions.stream()
                    .map(p -> p.getInstrument().getId())
                    .collect(java.util.stream.Collectors.toSet());

            List<Transaction> accountTxs = transactionRepository.findByAccountIdOrderByTradeDateDesc(accountId);
            List<Instrument> missingInstruments = accountTxs.stream()
                    .map(Transaction::getInstrument)
                    .filter(java.util.Objects::nonNull)
                    .filter(inst -> !presentInstrumentIds.contains(inst.getId()))
                    .distinct()
                    .toList();

            for (Instrument inst : missingInstruments) {
                results.add(computePositionPerformance(account, inst, null));
            }
        }

        return results;
    }

    public List<PositionCalculationResult> recalculatePortfolio(UUID portfolioId) {
        return positionEngine.recalculatePortfolio(portfolioId);
    }

    public Position createPosition(
            UUID portfolioId,
            UUID accountId,
            UUID instrumentId,
            Quantity quantity,
            Money costBasis) {
        Account account = getValidatedAccount(portfolioId, accountId);
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));

        if (positionRepository.findByAccountIdAndInstrumentId(accountId, instrumentId).isPresent()) {
            throw new ConflictException("Position already exists for instrument " + instrumentId + " in account " + accountId);
        }

        Position position = new Position(account, instrument, quantity, costBasis);
        return positionRepository.save(position);
    }

    public Position updatePosition(
            UUID portfolioId,
            UUID accountId,
            UUID positionId,
            Quantity quantity,
            Money costBasis) {
        Position position = getPosition(portfolioId, accountId, positionId);
        position.update(quantity, costBasis);
        return positionRepository.save(position);
    }

    public void archivePosition(UUID portfolioId, UUID accountId, UUID positionId) {
        Position position = getPosition(portfolioId, accountId, positionId);
        position.archive();
        positionRepository.save(position);
    }

    private Account getValidatedAccount(UUID portfolioId, UUID accountId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        if (!account.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException("Account " + accountId + " does not belong to portfolio " + portfolioId);
        }
        return account;
    }

    public ApiDtos.PositionPerformanceResponse computePositionPerformance(Account account, Instrument instrument, UUID positionId) {
        List<Transaction> txList = transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(
                account.getId(), instrument.getId());

        CostBasisMethod method = account.getPortfolio() != null && account.getPortfolio().getCostBasisMethod() != null
                ? account.getPortfolio().getCostBasisMethod()
                : CostBasisMethod.FIFO;

        PositionCalculationResult calc = positionEngine.calculate(account, instrument, txList, method);

        BigDecimal totalBoughtQuantity = BigDecimal.ZERO;
        BigDecimal totalSoldQuantity = BigDecimal.ZERO;
        BigDecimal totalInvestedAmount = BigDecimal.ZERO;
        BigDecimal totalProceedsAmount = BigDecimal.ZERO;
        BigDecimal dividendIncome = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;

        for (Transaction tx : txList) {
            if (tx.getStatus() != com.takakim.investtracker.domain.TransactionStatus.COMPLETED) {
                continue;
            }
            if (tx.getType() == com.takakim.investtracker.domain.TransactionType.BUY) {
                totalBoughtQuantity = totalBoughtQuantity.add(tx.getQuantity());
                totalInvestedAmount = totalInvestedAmount.add(tx.getNetAmount().abs());
            } else if (tx.getType() == com.takakim.investtracker.domain.TransactionType.SELL) {
                totalSoldQuantity = totalSoldQuantity.add(tx.getQuantity());
                totalProceedsAmount = totalProceedsAmount.add(tx.getNetAmount().abs());
            } else if (tx.getType() == com.takakim.investtracker.domain.TransactionType.DIVIDEND) {
                dividendIncome = dividendIncome.add(tx.getNetAmount().abs());
            }

            if (tx.getFeeAmount() != null) {
                totalFees = totalFees.add(tx.getFeeAmount().abs());
            }
            if (tx.getTaxAmount() != null) {
                totalTaxes = totalTaxes.add(tx.getTaxAmount().abs());
            }
        }

        BigDecimal averageBuyPrice = (totalBoughtQuantity.compareTo(BigDecimal.ZERO) > 0)
                ? totalInvestedAmount.divide(totalBoughtQuantity, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal averageSellPrice = (totalSoldQuantity.compareTo(BigDecimal.ZERO) > 0)
                ? totalProceedsAmount.divide(totalSoldQuantity, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal currentQty = calc.quantity();
        BigDecimal currentCostBasis = calc.costBasisAmount() != null ? calc.costBasisAmount() : BigDecimal.ZERO;
        String accountCurrency = account.getAccountCurrency().code();

        BigDecimal nativePrice = BigDecimal.ZERO;
        String nativeCurrency = instrument.getCurrency().code();
        BigDecimal currentPriceInAccountCurrency = BigDecimal.ZERO;
        BigDecimal currentMarketValue = BigDecimal.ZERO;
        BigDecimal unrealizedGainLoss = BigDecimal.ZERO;

        if (currentQty.compareTo(BigDecimal.ZERO) > 0) {
            try {
                if (marketDataService != null) {
                    PriceQuote quote = marketDataService.getLatestPrice(instrument.getId(), Instant.now());
                    nativePrice = quote.price();
                    nativeCurrency = quote.currency();

                    if (fxRateService != null && !nativeCurrency.equalsIgnoreCase(accountCurrency)) {
                        Money converted = fxRateService.convert(
                                new Money(nativePrice, new Currency(nativeCurrency)),
                                new Currency(accountCurrency),
                                Instant.now()
                        );
                        currentPriceInAccountCurrency = converted.amount();
                    } else {
                        currentPriceInAccountCurrency = nativePrice;
                    }

                    currentMarketValue = currentQty.multiply(currentPriceInAccountCurrency).setScale(2, java.math.RoundingMode.HALF_UP);
                    unrealizedGainLoss = currentMarketValue.subtract(currentCostBasis).setScale(2, java.math.RoundingMode.HALF_UP);
                }
            } catch (Exception ignored) {
            }
        }

        BigDecimal realizedGainLoss = calc.realizedGainLossAmount() != null ? calc.realizedGainLossAmount() : BigDecimal.ZERO;
        BigDecimal netTotalReturnAmount = realizedGainLoss.add(unrealizedGainLoss).add(dividendIncome)
                .subtract(totalFees).subtract(totalTaxes).setScale(2, java.math.RoundingMode.HALF_UP);

        BigDecimal totalReturnPercentage = BigDecimal.ZERO;
        if (totalInvestedAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalReturnPercentage = netTotalReturnAmount.divide(totalInvestedAmount, 6, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(4, java.math.RoundingMode.HALF_UP);
        }

        BigDecimal nativeNetTotalReturn = netTotalReturnAmount;
        BigDecimal nativeReturnPercentage = totalReturnPercentage;
        if (fxRateService != null && !nativeCurrency.equalsIgnoreCase(accountCurrency)) {
            try {
                Money nativeConverted = fxRateService.convert(
                        new Money(netTotalReturnAmount, new Currency(accountCurrency)),
                        new Currency(nativeCurrency),
                        Instant.now()
                );
                nativeNetTotalReturn = nativeConverted.amount();
            } catch (Exception ignored) {
            }
        }

        String status = currentQty.compareTo(BigDecimal.ZERO) > 0 ? "ACTIVE" : "CLOSED";

        List<ApiDtos.PositionLotResponse> openLots = calc.openLots().stream()
                .map(lot -> new ApiDtos.PositionLotResponse(
                        lot.getId(),
                        lot.getTransactionId(),
                        lot.getAcquisitionDate(),
                        lot.getOriginalQuantity(),
                        lot.getRemainingQuantity(),
                        lot.getUnitCost(),
                        lot.getTotalCost(),
                        lot.getCurrency()
                ))
                .toList();

        List<ApiDtos.LotDisposalResponse> disposals = calc.disposals().stream()
                .map(d -> new ApiDtos.LotDisposalResponse(
                        d.disposalTransactionId(),
                        d.lotId(),
                        d.disposalDate(),
                        d.disposedQuantity(),
                        d.disposedCostBasis(),
                        d.proceeds(),
                        d.realizedGainLoss(),
                        d.currency()
                ))
                .toList();

        List<ApiDtos.TransactionResponse> transactions = txList.stream()
                .map(t -> new ApiDtos.TransactionResponse(
                        t.getId(),
                        t.getAccount().getId(),
                        t.getInstrument() != null ? t.getInstrument().getId() : null,
                        t.getInstrument() != null ? t.getInstrument().getName() : null,
                        t.getInstrument() != null ? t.getInstrument().getTicker() : null,
                        t.getType(),
                        t.getTradeDate(),
                        t.getSettlementDate(),
                        t.getQuantity(),
                        t.getPrice(),
                        t.getGrossAmount(),
                        t.getFeeAmount(),
                        t.getTaxAmount(),
                        t.getNetAmount(),
                        t.getCurrency(),
                        t.getFxRate(),
                        t.getCounterCurrency(),
                        t.getNotes(),
                        t.getStatus().name(),
                        t.getCorrectionOfTransactionId(),
                        t.getCreatedAt(),
                        t.getUpdatedAt()
                ))
                .toList();

        return new ApiDtos.PositionPerformanceResponse(
                positionId,
                account.getId(),
                account.getName(),
                instrument.getId(),
                instrument.getName(),
                instrument.getTicker(),
                instrument.getIsin(),
                instrument.getAssetClass(),
                status,
                currentQty,
                totalBoughtQuantity,
                totalSoldQuantity,
                averageBuyPrice,
                averageSellPrice,
                totalInvestedAmount,
                totalProceedsAmount,
                currentCostBasis,
                currentPriceInAccountCurrency,
                currentMarketValue,
                realizedGainLoss,
                unrealizedGainLoss,
                dividendIncome,
                totalFees,
                totalTaxes,
                netTotalReturnAmount,
                totalReturnPercentage,
                accountCurrency,
                nativePrice,
                nativeCurrency,
                nativeNetTotalReturn,
                nativeReturnPercentage,
                openLots,
                disposals,
                transactions
        );
    }
}



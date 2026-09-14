package com.takakim.investtracker.service;

import com.takakim.investtracker.api.ApiDtos.ApplyCorporateActionRequest;
import com.takakim.investtracker.api.ApiDtos.CorporateActionResponse;
import com.takakim.investtracker.api.ApiDtos.ScanCorporateActionsResponse;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.CorporateAction;
import com.takakim.investtracker.domain.CorporateActionStatus;
import com.takakim.investtracker.domain.CorporateActionType;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.CorporateActionRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway.DiscoveredCorporateAction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CorporateActionService {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionService.class);
    private static final int QUANTITY_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final TransactionService transactionService;
    private final YahooFinanceGateway yahooFinanceGateway;

    public CorporateActionService(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            PositionRepository positionRepository,
            TransactionRepository transactionRepository,
            CorporateActionRepository corporateActionRepository,
            TransactionService transactionService,
            YahooFinanceGateway yahooFinanceGateway) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.corporateActionRepository = corporateActionRepository;
        this.transactionService = transactionService;
        this.yahooFinanceGateway = yahooFinanceGateway;
    }

    public ScanCorporateActionsResponse scanPortfolio(UUID portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }

        List<Position> activePositions = positionRepository
                .findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE);

        List<Instrument> instruments = activePositions.stream()
                .map(Position::getInstrument)
                .distinct()
                .filter(i -> i.getTicker() != null && !i.getTicker().isBlank())
                .toList();

        int scanned = 0;
        int discovered = 0;
        int newPending = 0;
        List<String> messages = new ArrayList<>();

        for (Instrument inst : instruments) {
            scanned++;
            String ticker = inst.getTicker().trim();
            List<DiscoveredCorporateAction> actions = yahooFinanceGateway.fetchCorporateActions(ticker, "1y");
            if (actions.isEmpty()) {
                continue;
            }

            for (DiscoveredCorporateAction disc : actions) {
                discovered++;
                Optional<CorporateAction> existing = corporateActionRepository
                        .findByInstrumentIdAndActionTypeAndExDate(inst.getId(), disc.actionType(), disc.exDate());

                if (existing.isPresent()) {
                    continue;
                }

                CorporateAction action = new CorporateAction(
                        inst,
                        disc.actionType(),
                        disc.exDate(),
                        null,
                        null,
                        disc.ratioFrom(),
                        disc.ratioTo(),
                        disc.amountPerShare(),
                        disc.currency(),
                        disc.description(),
                        "YAHOO_FINANCE",
                        disc.externalId()
                );

                // Auto-detect if a corresponding transaction already exists in the ledger around the exDate
                TransactionType matchingTxType = switch (disc.actionType()) {
                    case STOCK_SPLIT -> TransactionType.STOCK_SPLIT;
                    case REVERSE_STOCK_SPLIT -> TransactionType.REVERSE_STOCK_SPLIT;
                    case DIVIDEND -> TransactionType.DIVIDEND;
                };

                Optional<Transaction> matchingTx = findMatchingLedgerTransaction(inst.getId(), matchingTxType, disc.exDate());
                if (matchingTx.isPresent()) {
                    action.markApplied(matchingTx.get(), matchingTx.get().getAccount());
                    messages.add("Auto-linked existing transaction for " + ticker + " (" + disc.actionType() + ")");
                } else {
                    newPending++;
                }

                corporateActionRepository.save(action);
            }
        }

        log.info("Portfolio {} corporate action scan complete: {} instruments scanned, {} actions discovered, {} new pending.",
                portfolioId, scanned, discovered, newPending);

        return new ScanCorporateActionsResponse(portfolioId, scanned, discovered, newPending, messages);
    }

    @Transactional(readOnly = true)
    public List<CorporateActionResponse> getPortfolioCorporateActions(UUID portfolioId, CorporateActionStatus statusFilter) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }

        List<CorporateAction> actions = statusFilter != null
                ? corporateActionRepository.findActivePortfolioCorporateActionsByStatus(portfolioId, statusFilter)
                : corporateActionRepository.findActivePortfolioCorporateActions(portfolioId);

        List<CorporateActionResponse> responses = new ArrayList<>();
        for (CorporateAction action : actions) {
            responses.add(mapToResponse(action, portfolioId));
        }

        responses.sort(Comparator.comparing(CorporateActionResponse::exDate).reversed());
        return responses;
    }

    public CorporateActionResponse applyAction(UUID portfolioId, UUID actionId, ApplyCorporateActionRequest request) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }

        CorporateAction action = corporateActionRepository.findById(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Corporate action not found: " + actionId));

        if (action.getStatus() != CorporateActionStatus.PENDING) {
            throw new IllegalStateException("Corporate action " + actionId + " is not PENDING (current: " + action.getStatus() + ")");
        }

        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.accountId()));

        if (!account.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException("Account " + request.accountId() + " does not belong to portfolio " + portfolioId);
        }

        Instrument inst = action.getInstrument();
        Transaction recordedTx;

        if (action.getActionType() == CorporateActionType.STOCK_SPLIT || action.getActionType() == CorporateActionType.REVERSE_STOCK_SPLIT) {
            TransactionType txType = action.getActionType() == CorporateActionType.STOCK_SPLIT
                    ? TransactionType.STOCK_SPLIT
                    : TransactionType.REVERSE_STOCK_SPLIT;

            BigDecimal splitQuantity = request.quantity();
            if (splitQuantity == null || splitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                splitQuantity = calculateProposedSplitQuantity(action, account.getId(), action.getExDate());
            }

            if (splitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Calculated or provided split adjustment quantity must be positive");
            }

            String notes = request.notes() != null ? request.notes() : action.getDescription();
            String currency = inst.getCurrency() != null ? inst.getCurrency().code() : account.getAccountCurrency().code();

            recordedTx = transactionService.recordTransaction(
                    portfolioId,
                    account.getId(),
                    inst.getId(),
                    txType,
                    action.getExDate(),
                    action.getPaymentDate() != null ? action.getPaymentDate() : action.getExDate(),
                    splitQuantity.setScale(QUANTITY_SCALE, ROUNDING),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    currency,
                    null,
                    null,
                    notes,
                    null
            );

        } else { // DIVIDEND
            BigDecimal gross = request.grossAmount();
            if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0) {
                gross = calculateProposedDividendAmount(action, account.getId(), action.getExDate());
            }

            if (gross.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Calculated or provided dividend gross amount must be positive");
            }

            BigDecimal tax = request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO;
            String currency = action.getCurrency() != null ? action.getCurrency() : account.getAccountCurrency().code();
            String notes = request.notes() != null ? request.notes() : action.getDescription();

            recordedTx = transactionService.recordTransaction(
                    portfolioId,
                    account.getId(),
                    inst.getId(),
                    TransactionType.DIVIDEND,
                    action.getExDate(),
                    action.getPaymentDate() != null ? action.getPaymentDate() : action.getExDate(),
                    null,
                    null,
                    gross.setScale(MONEY_SCALE, ROUNDING),
                    BigDecimal.ZERO,
                    tax.setScale(MONEY_SCALE, ROUNDING),
                    currency,
                    null,
                    null,
                    notes,
                    null
            );
        }

        action.markApplied(recordedTx, account);
        corporateActionRepository.save(action);

        log.info("Applied corporate action {} to account {}. Created transaction {}.",
                actionId, account.getId(), recordedTx.getId());

        return mapToResponse(action, portfolioId);
    }

    public CorporateActionResponse dismissAction(UUID portfolioId, UUID actionId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }

        CorporateAction action = corporateActionRepository.findById(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Corporate action not found: " + actionId));

        action.markDismissed();
        corporateActionRepository.save(action);

        log.info("Dismissed corporate action {} for portfolio {}.", actionId, portfolioId);
        return mapToResponse(action, portfolioId);
    }

    private Optional<Transaction> findMatchingLedgerTransaction(UUID instrumentId, TransactionType type, Instant exDate) {
        Instant windowStart = exDate.minus(7, ChronoUnit.DAYS);
        Instant windowEnd = exDate.plus(7, ChronoUnit.DAYS);

        return transactionRepository.findAll().stream()
                .filter(t -> t.getInstrument() != null && t.getInstrument().getId().equals(instrumentId))
                .filter(t -> t.getType() == type)
                .filter(t -> !t.getTradeDate().isBefore(windowStart) && !t.getTradeDate().isAfter(windowEnd))
                .findFirst();
    }

    private CorporateActionResponse mapToResponse(CorporateAction action, UUID portfolioId) {
        Instrument inst = action.getInstrument();
        Account targetAccount = action.getAccount();
        BigDecimal heldQty = BigDecimal.ZERO;

        if (targetAccount == null) {
            // Find active account in portfolio holding the instrument
            List<Account> accounts = accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(
                    portfolioId, com.takakim.investtracker.domain.AccountStatus.ACTIVE);

            for (Account acc : accounts) {
                BigDecimal qty = calculateHeldQuantityUpTo(inst.getId(), acc.getId(), action.getExDate());
                if (qty.compareTo(BigDecimal.ZERO) > 0) {
                    targetAccount = acc;
                    heldQty = qty;
                    break;
                }
            }
        } else {
            heldQty = calculateHeldQuantityUpTo(inst.getId(), targetAccount.getId(), action.getExDate());
        }

        BigDecimal proposedQty = null;
        BigDecimal proposedAmount = null;

        if (heldQty.compareTo(BigDecimal.ZERO) > 0) {
            if (action.getActionType() == CorporateActionType.STOCK_SPLIT && action.getRatioFrom() != null && action.getRatioTo() != null) {
                BigDecimal ratio = action.getRatioTo().divide(action.getRatioFrom(), 8, ROUNDING);
                proposedQty = heldQty.multiply(ratio.subtract(BigDecimal.ONE)).setScale(QUANTITY_SCALE, ROUNDING);
            } else if (action.getActionType() == CorporateActionType.REVERSE_STOCK_SPLIT && action.getRatioFrom() != null && action.getRatioTo() != null) {
                BigDecimal ratio = action.getRatioTo().divide(action.getRatioFrom(), 8, ROUNDING);
                proposedQty = heldQty.multiply(BigDecimal.ONE.subtract(ratio)).setScale(QUANTITY_SCALE, ROUNDING);
            } else if (action.getActionType() == CorporateActionType.DIVIDEND && action.getAmountPerShare() != null) {
                proposedAmount = heldQty.multiply(action.getAmountPerShare()).setScale(MONEY_SCALE, ROUNDING);
            }
        }

        return new CorporateActionResponse(
                action.getId(),
                inst.getId(),
                inst.getName(),
                inst.getTicker(),
                inst.getIsin(),
                inst.getAssetClass().name(),
                action.getActionType().name(),
                action.getStatus().name(),
                action.getExDate(),
                action.getRecordDate(),
                action.getPaymentDate(),
                action.getRatioFrom(),
                action.getRatioTo(),
                action.getAmountPerShare(),
                action.getCurrency() != null ? action.getCurrency() : (inst.getCurrency() != null ? inst.getCurrency().code() : "USD"),
                action.getDescription(),
                action.getSource(),
                heldQty.setScale(QUANTITY_SCALE, ROUNDING),
                proposedQty,
                proposedAmount,
                targetAccount != null ? targetAccount.getId() : null,
                targetAccount != null ? targetAccount.getName() : null,
                action.getAppliedTransaction() != null ? action.getAppliedTransaction().getId() : null,
                action.getCreatedAt(),
                action.getUpdatedAt()
        );
    }

    private BigDecimal calculateHeldQuantityUpTo(UUID instrumentId, UUID accountId, Instant upTo) {
        List<Transaction> txs = transactionRepository.findByAccountIdAndInstrumentIdOrderByTradeDateAsc(accountId, instrumentId);
        BigDecimal qty = BigDecimal.ZERO;

        for (Transaction tx : txs) {
            if (tx.getTradeDate().isAfter(upTo)) {
                continue;
            }
            if (tx.getType() == TransactionType.BUY) {
                qty = qty.add(tx.getQuantity());
            } else if (tx.getType() == TransactionType.SELL) {
                qty = qty.subtract(tx.getQuantity());
            } else if (tx.getType() == TransactionType.STOCK_SPLIT) {
                qty = qty.add(tx.getQuantity());
            } else if (tx.getType() == TransactionType.REVERSE_STOCK_SPLIT) {
                qty = qty.subtract(tx.getQuantity());
            }
        }

        return qty.max(BigDecimal.ZERO);
    }

    private BigDecimal calculateProposedSplitQuantity(CorporateAction action, UUID accountId, Instant exDate) {
        BigDecimal held = calculateHeldQuantityUpTo(action.getInstrument().getId(), accountId, exDate);
        if (held.compareTo(BigDecimal.ZERO) <= 0 || action.getRatioFrom() == null || action.getRatioTo() == null) {
            return BigDecimal.ZERO;
        }

        if (action.getActionType() == CorporateActionType.STOCK_SPLIT) {
            BigDecimal ratio = action.getRatioTo().divide(action.getRatioFrom(), 8, ROUNDING);
            return held.multiply(ratio.subtract(BigDecimal.ONE)).setScale(QUANTITY_SCALE, ROUNDING);
        } else {
            BigDecimal ratio = action.getRatioTo().divide(action.getRatioFrom(), 8, ROUNDING);
            return held.multiply(BigDecimal.ONE.subtract(ratio)).setScale(QUANTITY_SCALE, ROUNDING);
        }
    }

    private BigDecimal calculateProposedDividendAmount(CorporateAction action, UUID accountId, Instant exDate) {
        BigDecimal held = calculateHeldQuantityUpTo(action.getInstrument().getId(), accountId, exDate);
        if (held.compareTo(BigDecimal.ZERO) <= 0 || action.getAmountPerShare() == null) {
            return BigDecimal.ZERO;
        }
        return held.multiply(action.getAmountPerShare()).setScale(MONEY_SCALE, ROUNDING);
    }
}

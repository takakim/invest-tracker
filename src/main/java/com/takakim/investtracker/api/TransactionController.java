package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.TransactionCorrectionRequest;
import com.takakim.investtracker.api.ApiDtos.TransactionRequest;
import com.takakim.investtracker.api.ApiDtos.TransactionResponse;
import com.takakim.investtracker.api.ApiDtos.TransactionUpdateRequest;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.TransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions")
    public List<TransactionResponse> listTransactions(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @RequestParam(required = false) TransactionType type) {
        return transactionService.listTransactions(portfolioId, accountId, type)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/transactions")
    public List<TransactionResponse> listPortfolioTransactions(@PathVariable UUID portfolioId) {
        return transactionService.listPortfolioTransactions(portfolioId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions/{transactionId}")
    public TransactionResponse getTransaction(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @PathVariable UUID transactionId) {
        return toResponse(transactionService.getTransaction(portfolioId, accountId, transactionId));
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> recordTransaction(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @Valid @RequestBody TransactionRequest request) {

        Transaction tx = transactionService.recordTransaction(
                portfolioId,
                accountId,
                request.instrumentId(),
                request.type(),
                request.tradeDate(),
                request.settlementDate(),
                request.quantity(),
                request.price(),
                request.grossAmount(),
                request.feeAmount(),
                request.taxAmount(),
                request.currency(),
                request.fxRate(),
                request.counterCurrency(),
                request.notes(),
                request.correctionOfTransactionId()
        );

        TransactionResponse response = toResponse(tx);
        URI location = URI.create(String.format("/api/v1/portfolios/%s/accounts/%s/transactions/%s",
                portfolioId, accountId, tx.getId()));
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions/{transactionId}/correct")
    public ResponseEntity<TransactionResponse> correctTransaction(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionCorrectionRequest request) {

        Transaction replacement = transactionService.correctTransaction(
                portfolioId,
                accountId,
                transactionId,
                request.replacementInstrumentId(),
                request.replacementType(),
                request.replacementTradeDate(),
                request.replacementSettlementDate(),
                request.replacementQuantity(),
                request.replacementPrice(),
                request.replacementGrossAmount(),
                request.replacementFeeAmount(),
                request.replacementTaxAmount(),
                request.replacementCurrency(),
                request.replacementFxRate(),
                request.replacementCounterCurrency(),
                request.replacementNotes()
        );

        TransactionResponse response = toResponse(replacement);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions/{transactionId}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionUpdateRequest request) {

        Transaction updated = transactionService.updateTransaction(
                portfolioId,
                accountId,
                transactionId,
                request.instrumentId(),
                request.type(),
                request.tradeDate(),
                request.settlementDate(),
                request.quantity(),
                request.price(),
                request.grossAmount(),
                request.feeAmount(),
                request.taxAmount(),
                request.currency(),
                request.fxRate(),
                request.counterCurrency(),
                request.notes()
        );

        TransactionResponse response = toResponse(updated);
        return ResponseEntity.ok(response);
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
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
        );
    }
}

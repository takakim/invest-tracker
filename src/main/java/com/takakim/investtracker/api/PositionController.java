package com.takakim.investtracker.api;

import com.takakim.investtracker.api.ApiDtos.PositionRequest;
import com.takakim.investtracker.api.ApiDtos.PositionResponse;
import com.takakim.investtracker.api.ApiDtos.PositionUpdateRequest;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.service.PositionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/positions")
    public List<PositionResponse> listPositions(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId) {
        return positionService.listPositions(portfolioId, accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/positions")
    public List<PositionResponse> listPortfolioPositions(@PathVariable UUID portfolioId) {
        return positionService.listPortfolioPositions(portfolioId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/positions/{positionId}")
    public PositionResponse getPosition(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @PathVariable UUID positionId) {
        return toResponse(positionService.getPosition(portfolioId, accountId, positionId));
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/positions/{positionId}/lots")
    public ApiDtos.PositionLotsDetailResponse getPositionLots(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @PathVariable UUID positionId) {
        var result = positionService.getPositionLots(portfolioId, accountId, positionId);
        List<ApiDtos.PositionLotResponse> lotResponses = result.openLots().stream()
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

        return new ApiDtos.PositionLotsDetailResponse(
                positionId,
                accountId,
                result.instrumentId(),
                result.costBasisMethod().name(),
                result.quantity(),
                result.costBasisAmount(),
                result.costBasisCurrency(),
                result.averageUnitCost(),
                result.realizedGainLossAmount(),
                lotResponses
        );
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/positions/recalculate")
    public ApiDtos.PositionRecalculateResponse recalculatePositions(@PathVariable UUID portfolioId) {
        var results = positionService.recalculatePortfolio(portfolioId);
        return new ApiDtos.PositionRecalculateResponse(
                portfolioId,
                results.size(),
                "Successfully recalculated " + results.size() + " position(s) across portfolio."
        );
    }

    @PostMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/positions")
    public ResponseEntity<PositionResponse> createPosition(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @Valid @RequestBody PositionRequest request) {
        Money costBasis = null;
        if (request.costBasisAmount() != null && request.costBasisCurrency() != null) {
            costBasis = new Money(request.costBasisAmount(), new Currency(request.costBasisCurrency()));
        }

        Position position = positionService.createPosition(
                portfolioId,
                accountId,
                request.instrumentId(),
                new Quantity(request.quantity()),
                costBasis);

        PositionResponse response = toResponse(position);
        URI location = URI.create(String.format("/api/v1/portfolios/%s/accounts/%s/positions/%s",
                portfolioId, accountId, position.getId()));
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/positions/{positionId}")
    public PositionResponse updatePosition(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @PathVariable UUID positionId,
            @Valid @RequestBody PositionUpdateRequest request) {
        Money costBasis = null;
        if (request.costBasisAmount() != null && request.costBasisCurrency() != null) {
            costBasis = new Money(request.costBasisAmount(), new Currency(request.costBasisCurrency()));
        }

        Position position = positionService.updatePosition(
                portfolioId,
                accountId,
                positionId,
                new Quantity(request.quantity()),
                costBasis);

        return toResponse(position);
    }

    @DeleteMapping("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/positions/{positionId}")
    public ResponseEntity<Void> archivePosition(
            @PathVariable UUID portfolioId,
            @PathVariable UUID accountId,
            @PathVariable UUID positionId) {
        positionService.archivePosition(portfolioId, accountId, positionId);
        return ResponseEntity.noContent().build();
    }

    private PositionResponse toResponse(Position p) {
        return new PositionResponse(
                p.getId(),
                p.getAccount().getId(),
                p.getInstrument().getId(),
                p.getInstrument().getName(),
                p.getInstrument().getTicker(),
                p.getInstrument().getIsin(),
                p.getInstrument().getAssetClass(),
                p.getQuantity(),
                p.getCostBasisAmount(),
                p.getCostBasisCurrency(),
                p.getStatus().name(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}

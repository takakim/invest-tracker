package com.takakim.investtracker.service.analytics;

import com.takakim.investtracker.api.ApiDtos.TargetAllocationItemRequest;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationItemResponse;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationPlanRequest;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationPlanResponse;
import com.takakim.investtracker.domain.AllocationType;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.TargetAllocationItem;
import com.takakim.investtracker.domain.TargetAllocationPlan;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TargetAllocationPlanRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TargetAllocationService {

    private final PortfolioRepository portfolioRepository;
    private final TargetAllocationPlanRepository planRepository;
    private final InstrumentRepository instrumentRepository;

    public TargetAllocationService(
            PortfolioRepository portfolioRepository,
            TargetAllocationPlanRepository planRepository,
            InstrumentRepository instrumentRepository) {
        this.portfolioRepository = portfolioRepository;
        this.planRepository = planRepository;
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TargetAllocationPlanResponse> getTargetPlan(UUID portfolioId) {
        Objects.requireNonNull(portfolioId, "Portfolio ID must not be null");
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found with ID: " + portfolioId);
        }
        return planRepository.findByPortfolioIdWithItems(portfolioId)
                .map(this::toResponse);
    }

    public TargetAllocationPlanResponse saveTargetPlan(UUID portfolioId, TargetAllocationPlanRequest request) {
        Objects.requireNonNull(portfolioId, "Portfolio ID must not be null");
        Objects.requireNonNull(request, "Target allocation request must not be null");

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with ID: " + portfolioId));

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Target allocation plan must have at least one allocation item");
        }

        // Validate sum of percentages
        BigDecimal sum = request.items().stream()
                .map(TargetAllocationItemRequest::targetPercentage)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(new BigDecimal("99.90")) < 0 || sum.compareTo(new BigDecimal("100.10")) > 0) {
            throw new IllegalArgumentException("Target allocation percentages must sum to 100.00% (currently " + sum.setScale(2, RoundingMode.HALF_UP) + "%)");
        }

        TargetAllocationPlan plan = planRepository.findByPortfolioIdWithItems(portfolioId)
                .orElseGet(() -> new TargetAllocationPlan(
                        portfolio,
                        request.name() != null ? request.name() : "Target Allocation Plan",
                        request.allocationType() != null ? request.allocationType() : AllocationType.ASSET_CLASS,
                        request.driftTolerancePercentage()
                ));

        plan.update(
                request.name() != null ? request.name() : "Target Allocation Plan",
                request.allocationType() != null ? request.allocationType() : AllocationType.ASSET_CLASS,
                request.driftTolerancePercentage()
        );

        plan.clearItems();

        for (TargetAllocationItemRequest itemReq : request.items()) {
            Instrument instrument = null;
            if (itemReq.instrumentId() != null) {
                instrument = instrumentRepository.findById(itemReq.instrumentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Instrument not found with ID: " + itemReq.instrumentId()));
            }

            String categoryKey = itemReq.categoryKey();
            if (plan.getAllocationType() == AllocationType.ASSET_CLASS) {
                // Validate AssetClass enum name if not CASH
                if (!"CASH".equalsIgnoreCase(categoryKey)) {
                    try {
                        AssetClass.valueOf(categoryKey.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid AssetClass category key: " + categoryKey);
                    }
                }
            }

            TargetAllocationItem item = new TargetAllocationItem(
                    plan,
                    categoryKey,
                    itemReq.categoryLabel() != null ? itemReq.categoryLabel() : categoryKey,
                    itemReq.targetPercentage(),
                    instrument
            );
            plan.addItem(item);
        }

        TargetAllocationPlan saved = planRepository.save(plan);
        return toResponse(saved);
    }

    public void deleteTargetPlan(UUID portfolioId) {
        Objects.requireNonNull(portfolioId, "Portfolio ID must not be null");
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found with ID: " + portfolioId);
        }
        planRepository.deleteByPortfolioId(portfolioId);
    }

    private TargetAllocationPlanResponse toResponse(TargetAllocationPlan plan) {
        List<TargetAllocationItemResponse> itemResponses = new ArrayList<>();
        for (TargetAllocationItem item : plan.getItems()) {
            itemResponses.add(new TargetAllocationItemResponse(
                    item.getId(),
                    item.getCategoryKey(),
                    item.getCategoryLabel(),
                    item.getTargetPercentage().setScale(2, RoundingMode.HALF_UP),
                    item.getInstrument() != null ? item.getInstrument().getId() : null,
                    item.getInstrument() != null ? item.getInstrument().getTicker() : null,
                    item.getInstrument() != null ? item.getInstrument().getName() : null
            ));
        }
        return new TargetAllocationPlanResponse(
                plan.getId(),
                plan.getPortfolio().getId(),
                plan.getName(),
                plan.getAllocationType(),
                plan.getDriftTolerancePct().setScale(2, RoundingMode.HALF_UP),
                itemResponses,
                plan.getUpdatedAt()
        );
    }
}

package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos.TargetAllocationItemRequest;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationPlanRequest;
import com.takakim.investtracker.api.ApiDtos.TargetAllocationPlanResponse;
import com.takakim.investtracker.domain.AllocationType;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.TargetAllocationItem;
import com.takakim.investtracker.domain.TargetAllocationPlan;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.TargetAllocationPlanRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.TargetAllocationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TargetAllocationServiceTests {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private TargetAllocationPlanRepository planRepository;
    @Mock
    private InstrumentRepository instrumentRepository;

    private TargetAllocationService service;

    private UUID portfolioId;
    private Portfolio portfolio;
    private Instrument vusa;
    private Instrument aapl;

    @BeforeEach
    void setUp() {
        service = new TargetAllocationService(portfolioRepository, planRepository, instrumentRepository);
        portfolio = new Portfolio("Main Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        portfolioId = portfolio.getId();
        vusa = new Instrument("Vanguard S&P 500 ETF", AssetClass.ETF, "VUSA", "IE00B3XXRP09", "LSE", new Currency("GBP"));
        aapl = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));
    }

    @Test
    void getTargetPlan_portfolioNotFound_throwsException() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.getTargetPlan(portfolioId));
    }

    @Test
    void getTargetPlan_planNotFound_returnsEmpty() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.empty());

        Optional<TargetAllocationPlanResponse> result = service.getTargetPlan(portfolioId);
        assertTrue(result.isEmpty());
    }

    @Test
    void getTargetPlan_planExists_returnsResponse() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);

        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "60/40 Equity/Bond", AllocationType.ASSET_CLASS, new BigDecimal("4.00"));
        TargetAllocationItem item1 = new TargetAllocationItem(plan, "STOCK", "Equities", new BigDecimal("60.00"), null);
        TargetAllocationItem item2 = new TargetAllocationItem(plan, "BOND", "Fixed Income", new BigDecimal("40.00"), null);
        plan.addItem(item1);
        plan.addItem(item2);

        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(plan));

        Optional<TargetAllocationPlanResponse> result = service.getTargetPlan(portfolioId);
        assertTrue(result.isPresent());
        assertEquals("60/40 Equity/Bond", result.get().name());
        assertEquals(AllocationType.ASSET_CLASS, result.get().allocationType());
        assertEquals(new BigDecimal("4.00"), result.get().driftTolerancePercentage());
        assertEquals(2, result.get().items().size());
        assertEquals("STOCK", result.get().items().get(0).categoryKey());
        assertEquals(new BigDecimal("60.00"), result.get().items().get(0).targetPercentage());
    }

    @Test
    void saveTargetPlan_portfolioNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.empty());

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                "My Plan", AllocationType.ASSET_CLASS, new BigDecimal("5.00"),
                List.of(new TargetAllocationItemRequest("STOCK", "Equities", new BigDecimal("100.00"), null))
        );

        assertThrows(ResourceNotFoundException.class, () -> service.saveTargetPlan(portfolioId, request));
    }

    @Test
    void saveTargetPlan_emptyItems_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                "My Plan", AllocationType.ASSET_CLASS, new BigDecimal("5.00"), List.of()
        );

        assertThrows(IllegalArgumentException.class, () -> service.saveTargetPlan(portfolioId, request));
    }

    @Test
    void saveTargetPlan_sumNot100Percent_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                "My Plan", AllocationType.ASSET_CLASS, new BigDecimal("5.00"),
                List.of(
                        new TargetAllocationItemRequest("STOCK", "Equities", new BigDecimal("60.00"), null),
                        new TargetAllocationItemRequest("BOND", "Fixed Income", new BigDecimal("30.00"), null)
                )
        );

        assertThrows(IllegalArgumentException.class, () -> service.saveTargetPlan(portfolioId, request));
    }

    @Test
    void saveTargetPlan_invalidAssetClass_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                "My Plan", AllocationType.ASSET_CLASS, new BigDecimal("5.00"),
                List.of(new TargetAllocationItemRequest("INVALID_CLASS", "Bad Category", new BigDecimal("100.00"), null))
        );

        assertThrows(IllegalArgumentException.class, () -> service.saveTargetPlan(portfolioId, request));
    }

    @Test
    void saveTargetPlan_instrumentNotFound_throwsException() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        UUID missingInstId = UUID.randomUUID();
        when(instrumentRepository.findById(missingInstId)).thenReturn(Optional.empty());

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                "Instrument Plan", AllocationType.INSTRUMENT, new BigDecimal("5.00"),
                List.of(new TargetAllocationItemRequest(missingInstId.toString(), "VUSA ETF", new BigDecimal("100.00"), missingInstId))
        );

        assertThrows(ResourceNotFoundException.class, () -> service.saveTargetPlan(portfolioId, request));
    }

    @Test
    void saveTargetPlan_validAssetClassAndCashPlan_createsAndSaves() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.empty());
        when(planRepository.save(any(TargetAllocationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                "Conservative Growth", AllocationType.ASSET_CLASS, new BigDecimal("3.00"),
                List.of(
                        new TargetAllocationItemRequest("STOCK", "Equities", new BigDecimal("60.00"), null),
                        new TargetAllocationItemRequest("ETF", "Index Funds", new BigDecimal("30.00"), null),
                        new TargetAllocationItemRequest("CASH", "Cash Buffer", new BigDecimal("10.00"), null)
                )
        );

        TargetAllocationPlanResponse response = service.saveTargetPlan(portfolioId, request);

        assertNotNull(response);
        assertEquals("Conservative Growth", response.name());
        assertEquals(AllocationType.ASSET_CLASS, response.allocationType());
        assertEquals(new BigDecimal("3.00"), response.driftTolerancePercentage());
        assertEquals(3, response.items().size());
        verify(planRepository).save(any(TargetAllocationPlan.class));
    }

    @Test
    void saveTargetPlan_validInstrumentPlan_createsAndSaves() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.empty());
        when(instrumentRepository.findById(vusa.getId())).thenReturn(Optional.of(vusa));
        when(instrumentRepository.findById(aapl.getId())).thenReturn(Optional.of(aapl));
        when(planRepository.save(any(TargetAllocationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                "Two Stock Core", AllocationType.INSTRUMENT, new BigDecimal("2.50"),
                List.of(
                        new TargetAllocationItemRequest(vusa.getId().toString(), "VUSA", new BigDecimal("70.00"), vusa.getId()),
                        new TargetAllocationItemRequest(aapl.getId().toString(), "AAPL", new BigDecimal("30.00"), aapl.getId())
                )
        );

        TargetAllocationPlanResponse response = service.saveTargetPlan(portfolioId, request);

        assertNotNull(response);
        assertEquals("Two Stock Core", response.name());
        assertEquals(AllocationType.INSTRUMENT, response.allocationType());
        assertEquals(2, response.items().size());
        assertEquals("VUSA", response.items().get(0).instrumentTicker());
        assertEquals("AAPL", response.items().get(1).instrumentTicker());
    }

    @Test
    void saveTargetPlan_updatesExistingPlan() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        TargetAllocationPlan existingPlan = new TargetAllocationPlan(portfolio, "Old Name", AllocationType.ASSET_CLASS, new BigDecimal("5.00"));
        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(existingPlan));
        when(planRepository.save(any(TargetAllocationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                "Updated Name", AllocationType.ASSET_CLASS, new BigDecimal("2.00"),
                List.of(new TargetAllocationItemRequest("STOCK", "Equities", new BigDecimal("100.00"), null))
        );

        TargetAllocationPlanResponse response = service.saveTargetPlan(portfolioId, request);

        assertEquals("Updated Name", response.name());
        assertEquals(new BigDecimal("2.00"), response.driftTolerancePercentage());
        assertEquals(1, response.items().size());
    }

    @Test
    void saveTargetPlan_nullNameAndDefaults_usesFallbackValues() {
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(planRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.empty());
        when(planRepository.save(any(TargetAllocationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TargetAllocationPlanRequest request = new TargetAllocationPlanRequest(
                null, null, null,
                List.of(new TargetAllocationItemRequest("STOCK", null, new BigDecimal("100.00"), null))
        );

        TargetAllocationPlanResponse response = service.saveTargetPlan(portfolioId, request);

        assertEquals("Target Allocation Plan", response.name());
        assertEquals(AllocationType.ASSET_CLASS, response.allocationType());
        assertEquals(new BigDecimal("5.00"), response.driftTolerancePercentage());
        assertEquals("STOCK", response.items().get(0).categoryLabel());
    }

    @Test
    void deleteTargetPlan_portfolioNotFound_throwsException() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteTargetPlan(portfolioId));
    }

    @Test
    void deleteTargetPlan_planDeleted() {
        when(portfolioRepository.existsById(portfolioId)).thenReturn(true);
        service.deleteTargetPlan(portfolioId);
        verify(planRepository).deleteByPortfolioId(portfolioId);
    }
}

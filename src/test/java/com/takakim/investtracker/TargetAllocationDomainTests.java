package com.takakim.investtracker;

import com.takakim.investtracker.domain.AllocationType;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.ReturnMethod;
import com.takakim.investtracker.domain.TargetAllocationItem;
import com.takakim.investtracker.domain.TargetAllocationPlan;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TargetAllocationDomainTests {

    private Portfolio portfolio;
    private Instrument vusa;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio("Main Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        vusa = new Instrument("Vanguard S&P 500 ETF", AssetClass.ETF, "VUSA", "IE00B3XXRP09", "LSE", new Currency("GBP"));
    }

    @Test
    void targetAllocationPlan_validCreationAndGetters() {
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Growth Plan", AllocationType.ASSET_CLASS, new BigDecimal("4.00"));
        assertNotNull(plan.getId());
        assertEquals(portfolio, plan.getPortfolio());
        assertEquals("Growth Plan", plan.getName());
        assertEquals(AllocationType.ASSET_CLASS, plan.getAllocationType());
        assertEquals(new BigDecimal("4.00"), plan.getDriftTolerancePct());
        assertNotNull(plan.getCreatedAt());
        assertNotNull(plan.getUpdatedAt());
        assertTrue(plan.getItems().isEmpty());

        // Default tolerance when null
        TargetAllocationPlan defaultPlan = new TargetAllocationPlan(portfolio, "Default Tolerance", AllocationType.INSTRUMENT, null);
        assertEquals(new BigDecimal("5.00"), defaultPlan.getDriftTolerancePct());
    }

    @Test
    void targetAllocationPlan_validationErrors() {
        assertThrows(NullPointerException.class, () -> new TargetAllocationPlan(null, "Plan", AllocationType.ASSET_CLASS, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new TargetAllocationPlan(portfolio, "", AllocationType.ASSET_CLASS, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new TargetAllocationPlan(portfolio, "   ", AllocationType.ASSET_CLASS, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new TargetAllocationPlan(portfolio, "Plan", null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new TargetAllocationPlan(portfolio, "Plan", AllocationType.ASSET_CLASS, new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> new TargetAllocationPlan(portfolio, "Plan", AllocationType.ASSET_CLASS, new BigDecimal("100.01")));
    }

    @Test
    void targetAllocationPlan_updateAndItems() {
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Plan", AllocationType.ASSET_CLASS, new BigDecimal("5.00"));
        plan.update("Updated Plan", AllocationType.INSTRUMENT, new BigDecimal("2.50"));
        assertEquals("Updated Plan", plan.getName());
        assertEquals(AllocationType.INSTRUMENT, plan.getAllocationType());
        assertEquals(new BigDecimal("2.50"), plan.getDriftTolerancePct());

        // Update with null tolerance uses default
        plan.update("Updated Again", AllocationType.ASSET_CLASS, null);
        assertEquals(new BigDecimal("5.00"), plan.getDriftTolerancePct());

        assertThrows(IllegalArgumentException.class, () -> plan.update("", AllocationType.ASSET_CLASS, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> plan.update("Plan", null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> plan.update("Plan", AllocationType.ASSET_CLASS, new BigDecimal("-1.00")));
        assertThrows(IllegalArgumentException.class, () -> plan.update("Plan", AllocationType.ASSET_CLASS, new BigDecimal("105.00")));

        TargetAllocationItem item = new TargetAllocationItem(plan, "STOCK", "Equities", new BigDecimal("60.00"), null);
        plan.addItem(item);
        assertEquals(1, plan.getItems().size());
        assertThrows(NullPointerException.class, () -> plan.addItem(null));

        plan.clearItems();
        assertTrue(plan.getItems().isEmpty());
    }

    @Test
    void targetAllocationItem_validCreationAndGetters() {
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Plan", AllocationType.INSTRUMENT, new BigDecimal("5.00"));
        TargetAllocationItem item = new TargetAllocationItem(plan, "VUSA", "VUSA ETF", new BigDecimal("70.00"), vusa);

        assertNotNull(item.getId());
        assertEquals(plan, item.getPlan());
        assertEquals("VUSA", item.getCategoryKey());
        assertEquals("VUSA ETF", item.getCategoryLabel());
        assertEquals(new BigDecimal("70.00"), item.getTargetPercentage());
        assertEquals(vusa, item.getInstrument());
        assertNotNull(item.getCreatedAt());
        assertNotNull(item.getUpdatedAt());

        item.update("Vanguard S&P 500", new BigDecimal("80.00"), vusa);
        assertEquals("Vanguard S&P 500", item.getCategoryLabel());
        assertEquals(new BigDecimal("80.00"), item.getTargetPercentage());
    }

    @Test
    void targetAllocationItem_validationErrors() {
        TargetAllocationPlan plan = new TargetAllocationPlan(portfolio, "Plan", AllocationType.INSTRUMENT, new BigDecimal("5.00"));

        assertThrows(IllegalArgumentException.class, () -> new TargetAllocationItem(plan, "", "Label", new BigDecimal("50.00"), null));
        assertThrows(IllegalArgumentException.class, () -> new TargetAllocationItem(plan, "Key", "", new BigDecimal("50.00"), null));
        assertThrows(NullPointerException.class, () -> new TargetAllocationItem(plan, "Key", "Label", null, null));
        assertThrows(IllegalArgumentException.class, () -> new TargetAllocationItem(plan, "Key", "Label", new BigDecimal("-1.00"), null));
        assertThrows(IllegalArgumentException.class, () -> new TargetAllocationItem(plan, "Key", "Label", new BigDecimal("100.01"), null));

        TargetAllocationItem validItem = new TargetAllocationItem(plan, "Key", "Label", new BigDecimal("50.00"), null);
        assertThrows(IllegalArgumentException.class, () -> validItem.update("", new BigDecimal("50.00"), null));
        assertThrows(NullPointerException.class, () -> validItem.update("Label", null, null));
        assertThrows(IllegalArgumentException.class, () -> validItem.update("Label", new BigDecimal("-0.01"), null));
        assertThrows(IllegalArgumentException.class, () -> validItem.update("Label", new BigDecimal("100.01"), null));
    }
}

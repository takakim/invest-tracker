package com.takakim.investtracker;

import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.service.analytics.AllocationItem;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioAnalyticsTests {

    @Test
    void portfolioAnalytics_handlesNullListsSafely() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PortfolioAnalytics analytics = new PortfolioAnalytics(
                id, now, "GBP", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null
        );

        assertEquals(id, analytics.portfolioId());
        assertEquals("GBP", analytics.baseCurrency());
        assertNotNull(analytics.byAssetClass());
        assertTrue(analytics.byAssetClass().isEmpty());
        assertNotNull(analytics.byCurrency());
        assertTrue(analytics.byCurrency().isEmpty());
        assertNotNull(analytics.byAccount());
        assertTrue(analytics.byAccount().isEmpty());
        assertNotNull(analytics.topHoldings());
        assertTrue(analytics.topHoldings().isEmpty());
        assertNotNull(analytics.warnings());
        assertTrue(analytics.warnings().isEmpty());
    }

    @Test
    void allocationItem_validatesNonNulls() {
        assertThrows(NullPointerException.class, () -> new AllocationItem(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new AllocationItem("STOCK", null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new AllocationItem("STOCK", BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new AllocationItem("STOCK", BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new AllocationItem("STOCK", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    void holdingExposure_validatesNonNulls() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new HoldingExposure(null, "Apple", "AAPL", AssetClass.STOCK, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, "USD"));
        assertThrows(NullPointerException.class, () -> new HoldingExposure(id, null, "AAPL", AssetClass.STOCK, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, "USD"));
        assertThrows(NullPointerException.class, () -> new HoldingExposure(id, "Apple", "AAPL", null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, "USD"));
    }
}

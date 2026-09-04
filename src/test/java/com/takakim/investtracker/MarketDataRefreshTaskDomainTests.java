package com.takakim.investtracker;

import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.MarketDataRefreshTask;
import com.takakim.investtracker.domain.RefreshTaskStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataRefreshTaskDomainTests {

    @Test
    @DisplayName("MarketDataRefreshTask constructor and getters/setters work correctly")
    void testEntityProperties() {
        Instrument inst = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", null, null, new Currency("USD"));
        Instant scheduledAt = Instant.now().plusSeconds(60);

        MarketDataRefreshTask task = new MarketDataRefreshTask(inst, scheduledAt, 3);
        assertNotNull(task.getId());
        assertEquals(inst, task.getInstrument());
        assertEquals(RefreshTaskStatus.PENDING, task.getStatus());
        assertEquals(scheduledAt, task.getScheduledAt());
        assertEquals(0, task.getAttemptCount());
        assertEquals(3, task.getMaxAttempts());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());

        // Default constructor for JPA
        MarketDataRefreshTask defaultTask = new MarketDataRefreshTask(inst, null);
        assertEquals(MarketDataRefreshTask.DEFAULT_MAX_ATTEMPTS, defaultTask.getMaxAttempts());
        assertNotNull(defaultTask.getScheduledAt());

        // Setters and mutations
        task.setStatus(RefreshTaskStatus.PROCESSING);
        assertEquals(RefreshTaskStatus.PROCESSING, task.getStatus());

        Instant newScheduled = Instant.now().plusSeconds(120);
        task.setScheduledAt(newScheduled);
        assertEquals(newScheduled, task.getScheduledAt());

        task.incrementAttemptCount();
        assertEquals(1, task.getAttemptCount());

        task.setAttemptCount(4);
        assertEquals(4, task.getAttemptCount());

        task.setMaxAttempts(10);
        assertEquals(10, task.getMaxAttempts());

        task.setLastError("Rate limit exceeded");
        assertEquals("Rate limit exceeded", task.getLastError());
    }

    @Test
    @DisplayName("Constructor throws when instrument is null")
    void testNullInstrumentThrows() {
        assertThrows(NullPointerException.class, () -> new MarketDataRefreshTask(null, Instant.now()));
    }

    @Test
    @DisplayName("Setters throw when arguments are null")
    void testNullSettersThrow() {
        Instrument inst = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", null, null, new Currency("USD"));
        MarketDataRefreshTask task = new MarketDataRefreshTask(inst, Instant.now());

        assertThrows(NullPointerException.class, () -> task.setStatus(null));
        assertThrows(NullPointerException.class, () -> task.setScheduledAt(null));
    }
}

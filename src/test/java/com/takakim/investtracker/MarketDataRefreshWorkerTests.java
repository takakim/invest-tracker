package com.takakim.investtracker;

import com.takakim.investtracker.service.market.queue.MarketDataRefreshQueueService;
import com.takakim.investtracker.service.market.queue.MarketDataRefreshWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class MarketDataRefreshWorkerTests {

    @Test
    @DisplayName("pollAndProcess invokes queueService processNextDueTask")
    void testPollAndProcessInvokesQueue() {
        MarketDataRefreshQueueService queueService = mock(MarketDataRefreshQueueService.class);
        MarketDataRefreshWorker worker = new MarketDataRefreshWorker(queueService);

        worker.pollAndProcess();

        verify(queueService, times(1)).processNextDueTask();
    }

    @Test
    @DisplayName("pollAndProcess swallows unexpected exceptions without throwing")
    void testPollAndProcessSwallowsExceptions() {
        MarketDataRefreshQueueService queueService = mock(MarketDataRefreshQueueService.class);
        when(queueService.processNextDueTask()).thenThrow(new RuntimeException("DB offline"));
        MarketDataRefreshWorker worker = new MarketDataRefreshWorker(queueService);

        // Should not throw
        worker.pollAndProcess();

        verify(queueService, times(1)).processNextDueTask();
    }
}

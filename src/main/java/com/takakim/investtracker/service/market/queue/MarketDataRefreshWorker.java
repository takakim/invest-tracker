package com.takakim.investtracker.service.market.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.market-data.refresh-queue.worker-enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataRefreshWorker {

    private static final Logger log = LoggerFactory.getLogger(MarketDataRefreshWorker.class);

    private final MarketDataRefreshQueueService queueService;

    public MarketDataRefreshWorker(MarketDataRefreshQueueService queueService) {
        this.queueService = queueService;
    }

    @Scheduled(fixedDelayString = "${app.market-data.refresh-queue.interval-ms:1500}")
    public void pollAndProcess() {
        try {
            queueService.processNextDueTask();
        } catch (Exception ex) {
            log.error("Unexpected error in market data refresh worker cycle: {}", ex.getMessage(), ex);
        }
    }
}

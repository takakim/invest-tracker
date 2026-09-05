-- V10: Support FX Rate Refresh Tasks in Asynchronous Market Data Queue

-- Allow tasks without an instrument (for FX currency pairs)
ALTER TABLE market_data_refresh_tasks
    ALTER COLUMN instrument_id DROP NOT NULL;

-- Add currency pair columns for FX tasks
ALTER TABLE market_data_refresh_tasks
    ADD COLUMN base_currency VARCHAR(10),
    ADD COLUMN quote_currency VARCHAR(10);

-- Ensure idempotency for FX tasks: only one active task per currency pair
CREATE UNIQUE INDEX idx_refresh_tasks_active_fx
    ON market_data_refresh_tasks (base_currency, quote_currency)
    WHERE status IN ('PENDING', 'PROCESSING') AND instrument_id IS NULL;

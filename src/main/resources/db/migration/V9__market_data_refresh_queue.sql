-- V9: Asynchronous Persistent Market Data Refresh Queue

CREATE TABLE market_data_refresh_tasks (
    id UUID PRIMARY KEY,
    instrument_id UUID NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Ensure idempotency: only one active (PENDING or PROCESSING) task per instrument
CREATE UNIQUE INDEX idx_refresh_tasks_active_instrument
    ON market_data_refresh_tasks (instrument_id)
    WHERE status IN ('PENDING', 'PROCESSING');

-- Index for polling scheduled tasks in due order
CREATE INDEX idx_refresh_tasks_scheduled
    ON market_data_refresh_tasks (status, scheduled_at ASC);

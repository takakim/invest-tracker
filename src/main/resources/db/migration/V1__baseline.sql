-- Phase 0 baseline migration.
-- Domain tables will be introduced in later phases.

CREATE TABLE schema_baseline (
    id BIGINT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT schema_baseline_single_row CHECK (id = 1)
);

INSERT INTO schema_baseline (id) VALUES (1);

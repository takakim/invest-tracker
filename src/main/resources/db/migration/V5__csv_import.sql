-- V5__csv_import.sql: CSV import batches and record audit history
CREATE TABLE import_batches (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    file_name VARCHAR(255) NOT NULL,
    broker_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    imported_rows INT NOT NULL DEFAULT 0,
    skipped_rows INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_import_batches_account ON import_batches (account_id);

CREATE TABLE import_records (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES import_batches(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    row_number INT NOT NULL,
    raw_data TEXT,
    fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message VARCHAR(512),
    transaction_id UUID REFERENCES transactions(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_import_records_account_fingerprint ON import_records (account_id, fingerprint);

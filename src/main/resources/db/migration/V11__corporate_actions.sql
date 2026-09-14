-- V11: Corporate Actions Feed & Ingestion (Splits, Reverse Splits, Dividends)

CREATE TABLE corporate_actions (
    id UUID PRIMARY KEY,
    instrument_id UUID NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    action_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    ex_date TIMESTAMPTZ NOT NULL,
    record_date TIMESTAMPTZ,
    payment_date TIMESTAMPTZ,
    ratio_from NUMERIC(19, 8),
    ratio_to NUMERIC(19, 8),
    amount_per_share NUMERIC(19, 4),
    currency VARCHAR(3),
    description TEXT,
    source VARCHAR(64) NOT NULL,
    external_id VARCHAR(128),
    applied_transaction_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_corp_action_type CHECK (action_type IN ('STOCK_SPLIT', 'REVERSE_STOCK_SPLIT', 'DIVIDEND')),
    CONSTRAINT ck_corp_action_status CHECK (status IN ('PENDING', 'APPLIED', 'DISMISSED'))
);

CREATE INDEX idx_corp_actions_inst_status ON corporate_actions(instrument_id, status);
CREATE INDEX idx_corp_actions_ex_date ON corporate_actions(ex_date);
CREATE UNIQUE INDEX idx_corp_actions_dedup ON corporate_actions(instrument_id, action_type, ex_date);

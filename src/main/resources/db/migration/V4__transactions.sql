CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    instrument_id UUID REFERENCES instruments(id),
    type VARCHAR(30) NOT NULL,
    trade_date TIMESTAMPTZ NOT NULL,
    settlement_date TIMESTAMPTZ,
    quantity NUMERIC(19, 8),
    price NUMERIC(19, 4),
    gross_amount NUMERIC(19, 4) NOT NULL,
    fee_amount NUMERIC(19, 4) DEFAULT 0,
    tax_amount NUMERIC(19, 4) DEFAULT 0,
    net_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    fx_rate NUMERIC(19, 6),
    counter_currency VARCHAR(3),
    notes VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    correction_of_transaction_id UUID REFERENCES transactions(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_transactions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_transactions_type CHECK (type IN ('BUY', 'SELL', 'DIVIDEND', 'FEE', 'DEPOSIT', 'WITHDRAWAL', 'INTEREST', 'STOCK_SPLIT', 'REVERSE_STOCK_SPLIT', 'TRANSFER')),
    CONSTRAINT ck_transactions_status CHECK (status IN ('COMPLETED', 'CORRECTED'))
);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_instrument_id ON transactions(instrument_id);
CREATE INDEX idx_transactions_trade_date ON transactions(trade_date);
CREATE INDEX idx_transactions_account_date ON transactions(account_id, trade_date);

CREATE TABLE positions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    quantity NUMERIC(19, 8) NOT NULL CHECK (quantity >= 0),
    cost_basis_amount NUMERIC(19, 4),
    cost_basis_currency VARCHAR(3),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_positions_account_instrument UNIQUE (account_id, instrument_id)
);

CREATE INDEX idx_positions_account_id ON positions(account_id);
CREATE INDEX idx_positions_instrument_id ON positions(instrument_id);
CREATE INDEX idx_positions_account_status ON positions(account_id, status);

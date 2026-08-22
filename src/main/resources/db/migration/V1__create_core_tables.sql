CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    cost_basis_method VARCHAR(20) NOT NULL,
    return_method VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    broker_name VARCHAR(120) NOT NULL,
    account_currency VARCHAR(3) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_accounts_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

CREATE INDEX idx_accounts_portfolio_id ON accounts(portfolio_id);

CREATE TABLE instruments (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    ticker VARCHAR(32),
    isin VARCHAR(12),
    exchange VARCHAR(80),
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_instruments_isin_lower
    ON instruments (LOWER(isin))
    WHERE isin IS NOT NULL;

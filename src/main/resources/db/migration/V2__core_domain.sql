CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    base_currency CHAR(3) NOT NULL,
    cost_basis_method VARCHAR(20) NOT NULL,
    return_method VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_portfolio_currency CHECK (base_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_portfolio_cost_basis CHECK (cost_basis_method IN ('FIFO', 'LIFO', 'AVERAGE_COST')),
    CONSTRAINT ck_portfolio_return_method CHECK (return_method IN ('XIRR', 'TWR', 'MWR')),
    CONSTRAINT ck_portfolio_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_portfolios_status_name ON portfolios(status, name);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolios(id),
    name VARCHAR(120) NOT NULL,
    broker_name VARCHAR(120) NOT NULL,
    account_currency CHAR(3) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_currency CHECK (account_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_accounts_portfolio_status_name ON accounts(portfolio_id, status, name);

CREATE TABLE instruments (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    ticker VARCHAR(32),
    isin VARCHAR(12),
    exchange VARCHAR(80),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_instrument_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_instrument_asset_class CHECK (asset_class IN ('STOCK', 'ETF', 'MUTUAL_FUND', 'BOND', 'REIT', 'CRYPTO', 'CASH', 'OTHER')),
    CONSTRAINT uq_instrument_isin UNIQUE (isin)
);

CREATE INDEX idx_instruments_name ON instruments(name);
CREATE INDEX idx_instruments_ticker ON instruments(ticker);

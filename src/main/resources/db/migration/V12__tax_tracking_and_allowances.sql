-- V12: Tax tracking and allowance management
-- Adds tax treatment to accounts (TAXABLE, TAX_EXEMPT, TAX_DEFERRED)
-- and portfolio tax settings table for custom allowances and loss carryforwards.

ALTER TABLE accounts
ADD COLUMN tax_treatment VARCHAR(20) NOT NULL DEFAULT 'TAXABLE';

CREATE INDEX idx_accounts_tax_treatment ON accounts (portfolio_id, tax_treatment);

CREATE TABLE portfolio_tax_settings (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    tax_year VARCHAR(20) NOT NULL,
    tax_regime VARCHAR(30) NOT NULL DEFAULT 'UK_HMRC',
    cgt_allowance NUMERIC(18, 4),
    dividend_allowance NUMERIC(18, 4),
    loss_carryforward NUMERIC(18, 4) NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_portfolio_tax_settings UNIQUE (portfolio_id, tax_year)
);

CREATE INDEX idx_portfolio_tax_settings_port_year ON portfolio_tax_settings (portfolio_id, tax_year);

-- V8: Target Asset Allocations & Portfolio Rebalancing Schema

CREATE TABLE target_allocation_plans (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    allocation_type VARCHAR(30) NOT NULL,
    drift_tolerance_pct NUMERIC(5, 2) NOT NULL DEFAULT 5.00,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_portfolio_target_plan UNIQUE (portfolio_id)
);

CREATE TABLE target_allocation_items (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES target_allocation_plans(id) ON DELETE CASCADE,
    category_key VARCHAR(100) NOT NULL,
    category_label VARCHAR(150) NOT NULL,
    target_percentage NUMERIC(5, 2) NOT NULL,
    instrument_id UUID REFERENCES instruments(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_plan_category UNIQUE (plan_id, category_key)
);

CREATE INDEX idx_target_allocation_plans_portfolio ON target_allocation_plans(portfolio_id);
CREATE INDEX idx_target_allocation_items_plan ON target_allocation_items(plan_id);

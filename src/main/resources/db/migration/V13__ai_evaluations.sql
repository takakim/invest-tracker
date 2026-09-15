-- V13: AI Evaluation Persistence
-- Stores the latest portfolio-level and holding-level AI evaluation results.
-- UNIQUE constraints implement "upsert latest" semantics — each new evaluation
-- replaces the prior one for the same scope key.

CREATE TABLE portfolio_ai_evaluations (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id        UUID        NOT NULL REFERENCES portfolios(id),
    provider            VARCHAR(50) NOT NULL,
    model_used          VARCHAR(200) NOT NULL,
    overall_risk_score  INT         NOT NULL CHECK (overall_risk_score BETWEEN 1 AND 10),
    overall_risk_level  VARCHAR(20) NOT NULL,
    executive_summary   TEXT,
    result_json         TEXT        NOT NULL,
    evaluated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_portfolio_ai_evaluation UNIQUE (portfolio_id)
);

CREATE TABLE holding_ai_evaluations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id    UUID        NOT NULL REFERENCES portfolios(id),
    instrument_id   UUID        NOT NULL REFERENCES instruments(id),
    provider        VARCHAR(50) NOT NULL,
    model_used      VARCHAR(200) NOT NULL,
    stance          VARCHAR(20) NOT NULL,
    risk_score      INT         NOT NULL CHECK (risk_score BETWEEN 1 AND 10),
    risk_level      VARCHAR(20) NOT NULL,
    executive_summary TEXT,
    result_json     TEXT        NOT NULL,
    evaluated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_holding_ai_evaluation UNIQUE (portfolio_id, instrument_id)
);

CREATE INDEX idx_holding_ai_portfolio ON holding_ai_evaluations (portfolio_id);
CREATE INDEX idx_holding_ai_instrument ON holding_ai_evaluations (instrument_id);

CREATE TABLE market_observations (
    id UUID PRIMARY KEY,
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    price NUMERIC(19, 8) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_reference VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_market_obs_instrument_date ON market_observations(instrument_id, observed_at DESC);
CREATE INDEX idx_market_obs_source ON market_observations(source_type);

CREATE TABLE fx_observations (
    id UUID PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(19, 8) NOT NULL CHECK (rate > 0),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_reference VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_fx_obs_pair_date ON fx_observations(base_currency, quote_currency, observed_at DESC);
CREATE INDEX idx_fx_obs_source ON fx_observations(source_type);

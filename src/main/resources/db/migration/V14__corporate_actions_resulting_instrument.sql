-- V14: Add resulting_instrument_id to corporate_actions for spin-offs and stock distributions
ALTER TABLE corporate_actions ADD COLUMN resulting_instrument_id UUID REFERENCES instruments(id) ON DELETE SET NULL;

CREATE INDEX idx_corp_actions_resulting_inst ON corporate_actions(resulting_instrument_id);

-- Provision Honeywell Aerospace (HONA) master instrument if not present
INSERT INTO instruments (id, name, asset_class, ticker, isin, exchange, currency, manual_price_only, created_at, updated_at)
VALUES (
    '5a12b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c',
    'Honeywell Aerospace Inc.',
    'STOCK',
    'HONA',
    'US43849R1059',
    'NASDAQ',
    'USD',
    false,
    NOW(),
    NOW()
)
ON CONFLICT (isin) DO NOTHING;

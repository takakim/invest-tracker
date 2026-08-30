-- Migration V7: Add manual_price_only flag to instruments table
ALTER TABLE instruments ADD COLUMN manual_price_only BOOLEAN NOT NULL DEFAULT FALSE;

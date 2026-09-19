---
name: history-backfill
description: Backfills historical daily market price observations for active and liquidated portfolio instruments from Yahoo Finance directly into PostgreSQL.
---

# Historical Market Price Backfill Skill

Use this skill when portfolio performance or historical valuation curves show artificial jumps, cliffs, or sparse data points.

## Capabilities

1. **Direct Database Backfill**: Backfills multi-year daily market observations from Yahoo Finance directly into PostgreSQL.
2. **On-Demand API Backfill**: Triggers history backfilling via backend REST API endpoint `POST /api/v1/portfolios/{id}/history/backfill`.

## Usage

### 1. Backfill All Active Portfolio Instruments (2 Years)
```bash
python3 scripts/backfill_history.py
```

### 2. Backfill Specific Tickers with Custom Range (e.g. 5 Years)
```bash
python3 scripts/backfill_history.py --range 5y --tickers NVDA AAPL MU VUAG
```

### 3. Trigger via Backend REST API
```bash
curl -X POST "http://localhost:8080/api/v1/portfolios/<PORTFOLIO_UUID>/history/backfill?range=1y"
```

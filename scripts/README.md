# Utility & Operational Scripts

This directory contains standalone Python 3 CLI utilities designed for development, testing, live portfolio auditing, and CSV data repair.

---

## Available Scripts

### 1. `freetrade_csv_tool.py`
Audits Freetrade activity feed CSV exports for position anomalies (e.g. negative balances, dividend quantity mismatches) and injects corporate action rows (`STOCK_SPLIT`, `REVERSE_STOCK_SPLIT`) formatted with the standard 44-column Freetrade schema.

```bash
# 1. Audit a CSV file for anomalies & missing splits
python3 scripts/freetrade_csv_tool.py audit path/to/activity-feed-export.csv

# 2. Inject missing forward/reverse splits and save to a new CSV
python3 scripts/freetrade_csv_tool.py inject input.csv patched_output.csv --tickers NVDA SMCI RGL

# 3. Print ready-to-paste raw CSV rows for NVDA and SMCI
python3 scripts/freetrade_csv_tool.py print-rows
```

---

### 2. `audit_holdings.py`
Connects to the running Invest-Tracker REST API (`/api/v1/portfolios`, `/api/v1/positions`, `/api/v1/accounts`) to verify holdings, calculate total returns, check cost basis currencies, and highlight any position anomalies.

```bash
# Audit all portfolios against local backend (default: http://localhost:8080)
python3 scripts/audit_holdings.py

# Audit a specific portfolio with custom API URL
python3 scripts/audit_holdings.py --url http://localhost:8080 --portfolio <PORTFOLIO_UUID>
```

---

### 3. `check_coverage.py`
Inspects JaCoCo XML reports (`target/site/jacoco/jacoco.xml`) against the mandatory 90% branch and line coverage gates, identifying exact classes and missed source lines.

```bash
# Check repository-wide branch & instruction coverage against 90% threshold
python3 scripts/check_coverage.py

# Inspect missed branches in a specific file
python3 scripts/check_coverage.py --file FreetradeCsvParser.java
```

---

### 4. `audit_csv_ledger.py`
Audits cash ledger balances, embedded fees, withholding taxes, and net outlays/proceeds across broker CSV exports, detecting potential double-deductions and reconciling directly against live app cash balances.

```bash
# Audit a CSV ledger breakdown
python3 scripts/audit_csv_ledger.py path/to/activity-feed-export.csv

# Reconcile against target live broker app cash balance
python3 scripts/audit_csv_ledger.py path/to/activity-feed-export.csv --target-cash 3637.84
```

---

### 5. `scan_instruments.py`
Scans CSV files under `docs/` or current workspace to discover unique financial instruments, ISINs, tickers, native currencies, and initial price references.

```bash
python3 scripts/scan_instruments.py
```

---

### 6. `enrich_investengine_csv.py`
Enriches raw InvestEngine CSV exports by cross-referencing ISINs to add standard ticker symbols, native currencies, and LSE exchange metadata.

```bash
# Enrich an InvestEngine statement and write to a new file
python3 scripts/enrich_investengine_csv.py path/to/SIPP.csv -o path/to/SIPP_enriched.csv
```

---

### 7. `backfill_history.py`
Backfills multi-year daily market price observations for active portfolio instruments from Yahoo Finance directly into the PostgreSQL database, ensuring smooth and accurate historical portfolio valuation without artificial cliff jumps.

```bash
# Backfill all active portfolio instruments with 2y daily price bars
python3 scripts/backfill_history.py

# Backfill specific tickers with a 5y range
python3 scripts/backfill_history.py --range 5y --tickers NVDA AAPL RR. VUAG
```




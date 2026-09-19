---
name: portfolio-audit
description: Audits live Invest-Tracker portfolio holdings, cost basis currencies, cash reconciliation, and broker CSV ledger anomalies against the backend REST API.
---

# Portfolio & Holdings Audit Skill

Use this skill when you need to inspect, reconcile, or audit live portfolio data, cash balances, or broker CSV activity feeds within the `invest-tracker` project.

## Capabilities

1. **Live API Portfolio & Holding Audit**: Queries `/api/v1/portfolios`, `/api/v1/positions`, and `/api/v1/accounts` to verify holdings, calculate returns, check cost-basis currencies, and highlight valuation anomalies.
2. **Broker CSV Ledger Reconciliation**: Audits cash ledger balances, embedded fees, withholding taxes, and net outlays/proceeds across broker exports, detecting potential double-deductions and reconciling against target live cash balances.

## Usage

### 1. Live Holdings & Valuation Audit
Run the underlying Python utility script to audit all portfolios or a specific portfolio against the backend:
```bash
python3 scripts/audit_holdings.py
python3 scripts/audit_holdings.py --url http://localhost:8080 --portfolio <PORTFOLIO_UUID>
```

### 2. Broker CSV Ledger & Cash Audit
Audit cash ledger breakdowns and reconcile against live app cash balances:
```bash
python3 scripts/audit_csv_ledger.py path/to/activity-feed-export.csv
python3 scripts/audit_csv_ledger.py path/to/activity-feed-export.csv --target-cash 3637.84
```

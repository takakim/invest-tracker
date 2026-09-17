# AGENTS.md — AI Agent Guidelines & Project Architecture

This document provides essential guidelines, architectural context, quality standards, and command references for AI coding agents working on the `invest-tracker` repository.

---

## 1. Project Overview & Architecture

`invest-tracker` is a multi-currency investment portfolio tracker designed with a domain-driven, security-first architecture.

### Tech Stack
- **Backend**: Java 25, Spring Boot 4.1.1, Spring Data JPA, Spring Security, Flyway, PostgreSQL.
- **Frontend**: React 19, TypeScript, Vite, Material UI (MUI v9), TanStack Query v5, React Hook Form + Zod, Vitest.
- **Testing & Security**: Testcontainers PostgreSQL, JUnit 6, JaCoCo, OWASP Dependency-Check, CycloneDX SBOM.
- **Contract & API**: OpenAPI 3.1.1 (`docs/api/openapi.yaml`), RFC 9457 Problem Details.

### Domain Model & Ownership Hierarchy
```text
Instrument (Asset Master Data)
    ^
    |
Position ---- Account ---- Portfolio
    ^            ^            ^
    |            |            |
Transaction ----+-------------+
```
- **Instrument**: Master data identity (internal UUID). Independent from Account/Portfolio ownership.
- **Account**: Broker or custodian boundary containing cash, positions, and transactions. Belongs to exactly one Portfolio.
- **Portfolio**: Reporting and cost-basis strategy boundary containing accounts.
- **Ledger & Precision**: The transaction ledger is the financial source of truth. All money, prices, FX rates, and quantities use `BigDecimal` domain value objects (`Currency`, `Money`, `Quantity`, `Price`, `FxRate`) — never binary floating-point primitives (`float`, `double`).

---

## 2. Directory Structure

```text
invest-tracker/
├── pom.xml                                 # Root Maven POM (Spring Boot, JaCoCo, OWASP, CycloneDX)
├── PROJECT_CONTEXT.md                      # Canonical LLM context & phase status
├── AGENTS.md                               # AI Agent instructions & repository guidelines
├── docs/                                   # Architectural documentation, ADRs, & openapi.yaml
│   ├── api/openapi.yaml
│   ├── architecture/
│   └── adr/
├── src/main/java/com/takakim/investtracker/
│   ├── api/                                # Thin REST Controllers & DTOs
│   ├── domain/                             # Core Entities, Enums, Value Objects
│   ├── repository/                         # Spring Data JPA Repositories
│   ├── service/                            # Business Logic Services & Invariants
│   └── config/                             # Security & Application Configurations
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/                       # Flyway SQL schema evolution (V1__baseline.sql, V2__core_domain.sql, V3__investments.sql, V4__transactions.sql, V5__csv_import.sql)
├── src/test/java/com/takakim/investtracker/ # Test suites (Testcontainers PostgreSQL, Unit Tests)
└── frontend/                               # React + TypeScript Web Frontend
    ├── src/
    │   ├── api/                            # Typed HTTP client & RFC 9457 problem details handler
    │   ├── components/                     # Reusable UI primitives (Layout, LoadingState, ErrorAlert, etc.)
    │   ├── features/                       # Domain feature modules (dashboard, portfolios, accounts, instruments, positions, transactions, imports)
    │   ├── forms/                          # Zod schemas & form validation
    │   ├── routing/                        # React Router definitions
    │   ├── test/                           # Vitest & React Testing Library suites
    │   ├── theme/                          # Material UI financial theme
    │   └── types/                          # TypeScript domain interfaces matching OpenAPI
    ├── package.json
    ├── vite.config.ts
    └── vitest.config.ts
```

---

## 3. Strict Development Rules for AI Agents

1. **Always Branch from Fresh `main`**: Before starting any new phase, feature, or task, always checkout `main` and pull the latest changes (`git checkout main && git pull origin main`) before creating a feature branch (`git checkout -b <branch-name>`). Never start work from a stale or unmerged feature branch to avoid branch divergence and merge conflicts.
2. **Check Canonical Context First**: Always inspect `PROJECT_CONTEXT.md` to understand active phase scope. Do not prematurely implement future phase functionality (e.g., transaction engine in Phase 1, CSV import in Phase 2).
3. **Immutability of Flyway Migrations**: Applied Flyway migration scripts in `src/main/resources/db/migration` must **NEVER** be modified. Always create a new versioned migration script (e.g. `V6__description.sql`) for schema additions.
4. **Database Schema Enforcement**: Hibernate is configured with `ddl-auto: validate`. Schema evolution is strictly owned by Flyway.
5. **API & Contract Synchronization**:
   - Endpoints must follow `/api/v1/...`.
   - Responses must use DTOs; never expose domain persistence entities directly.
   - Errors must return RFC 9457 `application/problem+json`.
   - Update `docs/api/openapi.yaml` whenever API contracts change.
6. **Precision & Financial Rules**:
   - Pass monetary amounts and fractional share quantities using `BigDecimal` and explicit domain value objects.
   - Use archive/deactivation status (`status = ARCHIVED`) instead of hard database deletion for financial entities.
7. **No Superficial Patches**:
   - Identify the root cause when tests fail.
   - Never suppress exceptions with empty fallbacks, comment out assertions, or delete failing tests.
8. **Always Maintain an Active Task Checklist**:
   - Always create a structured task list (`task.md` or equivalent checklist) before starting implementation.
   - Keep the task list continuously updated as work progresses, marking items as pending (`[ ]`), in-progress (`[/]`), or completed (`[x]`), ensuring complete transparency into execution state.

---

## 4. Quality & Coverage Gates

The repository enforces mandatory quality gates in CI:
- **Line Coverage**: `>= 90%` (JaCoCo)
- **Branch Coverage**: `>= 90%` (JaCoCo)
- **Security Scans**: OWASP Dependency-Check must pass with 0 high/critical vulnerabilities.
- **Frontend Security**: `npm audit --audit-level=high` must report 0 vulnerabilities.

---

## 5. Verification Commands

Run the following commands to verify backend and frontend correctness before concluding work:

### Full Repository Verification
```bash
mvn -B verify && npm --prefix frontend run build && npm --prefix frontend test && npm --prefix frontend audit --audit-level=high
```

### Backend-Only Verification
```bash
# Run unit & Testcontainers integration tests, JaCoCo coverage check, OWASP check, and SBOM
mvn clean verify

# Inspect JaCoCo branch coverage and identify any missed branches by class/source file
python3 scripts/check_coverage.py
```

### Frontend-Only Verification
```bash
# Typecheck & bundle build
npm --prefix frontend run build

# Run Vitest test suite
npm --prefix frontend test

# Run dependency vulnerability audit
npm --prefix frontend audit --audit-level=high
```

---

## 6. Available Python Development & Operational Scripts

The `scripts/` directory contains standalone Python 3 CLI utilities to accelerate AI agent and developer workflows. Whenever new scripts are created or modified, their purpose and CLI arguments must be documented here and in [`scripts/README.md`](file:///Users/massanoritakaki/code/invest-tracker/scripts/README.md).

### 1. `check_coverage.py`
Inspects JaCoCo XML reports (`target/site/jacoco/jacoco.xml`) against the mandatory 90% line and branch coverage gates, pinpointing classes and missed branch counts.
```bash
# Check overall repository branch & instruction coverage against 90% threshold
python3 scripts/check_coverage.py

# Inspect missed branches for a specific Java file
python3 scripts/check_coverage.py --file FreetradeCsvParser.java
```

### 2. `freetrade_csv_tool.py`
Audits Freetrade CSV exports for anomalies (e.g. negative balances, dividend quantity mismatches) and injects corporate action rows (`STOCK_SPLIT`, `REVERSE_STOCK_SPLIT`) adhering to the standard 44-column schema.
```bash
# Audit a CSV file for anomalies and missing splits
python3 scripts/freetrade_csv_tool.py audit path/to/activity-feed-export.csv

# Inject forward/reverse stock splits into a new CSV
python3 scripts/freetrade_csv_tool.py inject input.csv output.csv --tickers NVDA SMCI RGL

# Print ready-to-paste raw CSV rows for corporate actions
python3 scripts/freetrade_csv_tool.py print-rows
```

### 3. `audit_holdings.py`
Queries the live Invest-Tracker REST API (`http://localhost:8080`) to verify holdings, returns, cash balances, cost basis currencies, and highlight position anomalies across portfolios.
```bash
# Audit all portfolios against local backend
python3 scripts/audit_holdings.py

# Audit specific portfolio against custom API URL
python3 scripts/audit_holdings.py --url http://localhost:8080 --portfolio <PORTFOLIO_UUID>
```

### 4. `audit_csv_ledger.py`
Audits cash ledger balances, embedded fees, withholding taxes, and net outlays/proceeds across broker CSV exports, detecting potential double-deductions and reconciling directly against live app cash balances.
```bash
# Audit a CSV ledger breakdown
python3 scripts/audit_csv_ledger.py path/to/activity-feed-export.csv

# Reconcile against target live broker app cash balance
python3 scripts/audit_csv_ledger.py path/to/activity-feed-export.csv --target-cash 3637.84
```

### 5. `scan_instruments.py`
Scans CSV files under `docs/` or current workspace to discover unique financial instruments, ISINs, tickers, native currencies, and initial price references.
```bash
python3 scripts/scan_instruments.py
```

### 6. `enrich_investengine_csv.py`
Enriches raw InvestEngine CSV exports by cross-referencing ISINs to add standard ticker symbols, native currencies, and LSE exchange metadata.
```bash
# Enrich an InvestEngine statement and write to a new file
python3 scripts/enrich_investengine_csv.py path/to/SIPP.csv -o path/to/SIPP_enriched.csv
```

### 7. `backfill_history.py`
Backfills multi-year daily market price observations for active portfolio instruments from Yahoo Finance directly into the PostgreSQL database, ensuring smooth and accurate historical portfolio valuation without artificial cliff jumps.
```bash
# Backfill all active portfolio instruments with 2y daily price bars
python3 scripts/backfill_history.py

# Backfill specific tickers with a 5y range
python3 scripts/backfill_history.py --range 5y --tickers NVDA AAPL RR. VUAG
```



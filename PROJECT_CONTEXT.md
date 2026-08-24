# Invest Tracker — Project Context

> Canonical, LLM-readable context. Read this before making project changes. Update it whenever scope, architecture, security, CI/CD, testing, phase status, or a material decision changes. Preserve historical decisions unless explicitly superseded.

## 1. Project

**Repository:** `takakim/invest-tracker`  
**Purpose:** Personal investment portfolio tracking and performance management.

The system tracks investment transactions and calculates individual-investment and overall portfolio performance, including dividends, fees, taxes, gains/losses, cash flows, currencies, and market valuation.

Authentication/multi-user support is intentionally deferred.

## 2. Engineering principles

1. Security first.
2. Correctness and auditability over cleverness.
3. Maintainability over premature optimisation.
4. Check latest stable dependency versions and known vulnerabilities before adding/updating dependencies.
5. Ask the user when a material product/architecture decision is ambiguous; do not silently assume.
6. API-first design.
7. Every production change requires appropriate tests and must preserve >=90% line and branch coverage.
8. Use `BigDecimal`/decimal-safe types for financial amounts and quantities; never binary floating point for financial calculations.
9. Derived portfolio/performance state must be reproducible from transactions and market/FX inputs.
10. External providers must be replaceable abstractions.

## 3. Agreed product scope

### Portfolios/accounts

- Multiple portfolios.
- Multiple broker/custodian accounts per portfolio.
- Cash is managed per account.
- Multiple currencies.
- Portfolio has a configurable base/reporting currency.
- The same instrument may be held in multiple accounts and portfolios.

### Instruments

Initial asset classes:

- Stocks
- ETFs
- Mutual funds
- Bonds
- REITs
- Crypto
- Cash

Instruments support fractional quantities. Instrument master data is independent of ownership and may include ticker, ISIN, name, exchange, native currency, asset class, and provider identifiers.

### Transactions

Initial types:

- Buy
- Sell
- Dividend
- Fee
- Deposit
- Withdrawal
- Interest
- Stock split
- Reverse stock split
- Transfer

Transactions support quantities, prices, fees, taxes, currencies, notes, source/import metadata and corrections. Completed financial transactions are immutable; corrections use reversal/correction records.

CSV imports must be idempotent.

### Performance

Performance method is configurable per portfolio. Required strategies:

- FIFO
- LIFO
- Average Cost
- XIRR
- TWR
- MWR

Performance must expose/reconcile realized gains, unrealized gains, dividends, fees, taxes, total return, annualized return and cash-flow effects where applicable.

### Market/FX

- Free market-data APIs initially.
- Market data behind a replaceable provider abstraction.
- Historical prices where available.
- Historical FX where available.
- Corporate actions/dividend data where available.
- Missing historical price/FX data: calculate with available data, emit structured warnings, and allow explicit manual overrides.
- Never silently substitute today's price/FX for missing historical data.

### Benchmarking

Planned later:

- S&P 500
- FTSE 100
- MSCI World

## 4. Architecture

```text
React + TypeScript
       |
   REST/OpenAPI
       |
Spring Boot backend
       |
 Domain/services
       |
 Spring Data JPA
       |
 PostgreSQL
```

Use domain-oriented modules such as:

```text
portfolio
account
instrument
transaction
position
performance
market
currency
import
report
common
configuration
```

Controllers remain thin. Business rules live in domain/services. Persistence and provider infrastructure are kept outside the domain model.

## 5. Core domain decisions

### Ownership model

```text
Instrument
    ^
    |
Position ---- Account ---- Portfolio
    ^            ^            ^
    |            |            |
Transaction ----+-------------+
```

- **Instrument** = financial asset identity/master data.
- **Position** = account-specific ownership state derived from transactions.
- **Account** = broker/custodian boundary containing cash, transactions and positions.
- **Portfolio** = reporting/strategy boundary containing accounts.

An Instrument is not owned by a Portfolio directly.

### Transfers

Transfers between accounts preserve acquisition lots/cost basis and do not create artificial gains/losses. Linked outgoing/incoming transfer events share a transfer identifier.

### Ledger

The transaction ledger is the financial source of truth for ownership and cash movement. Completed transactions are immutable. Corrections/reversals reference the original record and preserve an audit trail.

### Derived state

Positions, lots and performance are derived/rebuildable. Caches may be introduced later but are never authoritative.

### Financial precision

Use decimal types for money, prices, FX rates and fractional quantities. Currency is explicit on monetary values.

## 6. Cost basis and performance architecture

Separate:

1. Transaction ledger.
2. Position/lot derivation.
3. Market valuation.
4. FX conversion.
5. Return calculation.

Cost-basis strategies use a common strategy interface and are selected per Portfolio:

```text
CostBasisStrategy
  calculateLotsAfter(transaction, currentLots)
  calculateDisposal(transaction, currentLots)
```

Initial strategies: FIFO, LIFO, Average Cost.

Performance calculations consume a normalized input model containing dated valuations, cash flows, income, costs, opening/closing values and data-quality warnings. Calculation results should record the method and algorithm version for reproducibility.

## 7. Transaction semantics

- **BUY:** increases quantity and consumes cash; acquisition costs participate in cost basis according to the selected rules.
- **SELL:** reduces quantity and generates proceeds; realized gain/loss follows the selected cost-basis strategy.
- **DIVIDEND:** records income; withholding tax is explicit when supplied.
- **FEE:** records a separately auditable cost, optionally linked to another transaction.
- **DEPOSIT/WITHDRAWAL:** cash movements, not investment performance by themselves.
- **INTEREST:** cash interest income.
- **STOCK_SPLIT/REVERSE_STOCK_SPLIT:** changes quantity/lots without economic gain/loss.
- **TRANSFER:** moves ownership between accounts while preserving lots/cost basis.

Deterministic transaction ordering uses effective timestamp, source sequence/reference when available, then stable transaction ID.

Imported transaction fingerprints provide idempotency. Ambiguous duplicates require user review rather than silent merging.

## 8. Market data and FX provider architecture

Domain-facing abstractions conceptually provide:

```text
MarketDataProvider
  getCurrentPrice(instrument, asOf)
  getHistoricalPrices(instrument, range)
  getCorporateActions(instrument, range)
  getDividends(instrument, range)

FxRateProvider
  getRate(baseCurrency, quoteCurrency, asOf)
  getHistoricalRates(baseCurrency, quoteCurrency, range)
```

Provider observations carry provenance, observed time/date and retrieval metadata. Manual overrides are separate observations and never overwrite provider history.

Provider calls require timeouts, bounded retries for transient failures, rate-limit handling, response validation and no secret logging.

Provider selection remains a Phase 4 implementation decision unless required earlier for tests.

## 9. Database/Flyway design

PostgreSQL is authoritative persistence. Flyway owns schema evolution; Hibernate uses `ddl-auto=validate` and never creates/updates the production schema.

Core conceptual tables:

- `portfolio`
- `account`
- `instrument`
- `instrument_identifier`
- `transaction`
- `transaction_metadata`
- `import_batch`
- `import_record`
- provider/manual observation tables

Use PostgreSQL `numeric` for money and quantities. Foreign keys and unique constraints enforce ownership/integrity. Index transaction queries by account/instrument/date and import fingerprints.

Migrations use `V<sequence>__<description>.sql`, are immutable once applied, and are exercised against PostgreSQL Testcontainers.

## 10. API architecture

Initial API boundary is `/api/v1`.

Core resources:

```text
/portfolios
/portfolios/{portfolioId}
/portfolios/{portfolioId}/accounts
/accounts/{accountId}
/accounts/{accountId}/transactions
/accounts/{accountId}/positions
/instruments
/portfolios/{portfolioId}/performance
/portfolios/{portfolioId}/warnings
```

Use DTOs, server-side validation, bounded pagination, explicit filtering/sorting and RFC 9457-style Problem Details errors. Do not expose stack traces, SQL, secrets or internal implementation details.

The design-level OpenAPI contract is `docs/api/openapi.yaml`.

## 11. Frontend architecture

Selected stack:

- React
- TypeScript
- Vite
- Material UI
- React Router
- TanStack Query
- TanStack Table
- React Hook Form
- Zod
- Recharts or Apache ECharts, selected after chart requirements are clearer

Organize by feature/domain. TanStack Query owns server state; local UI state remains local unless shared. API types should be derived from/kept aligned with OpenAPI.

Desktop-first but responsive. Accessibility is a requirement.

Phase 1 starts with portfolio/account shell; transaction, import and analytics flows arrive with their respective phases.

## 12. Security threat model

Key assets: financial ledger, holdings, broker imports, market/FX observations, manual overrides and future credentials.

Key controls:

- Strict API/file validation and upload limits.
- Immutable ledger.
- Database constraints and integration tests.
- Bounded external provider calls and response validation.
- No arbitrary user-supplied outbound URLs.
- No secrets in source control, logs or frontend bundles.
- XSS-safe rendering and safe handling of imported content.
- Authentication/authorization required before public exposure.
- Rate limiting and secure headers before public exposure.
- Dependabot, OWASP Dependency-Check, CycloneDX SBOM and verified action pins where practical.

Full Phase 0.5 threat model is `docs/architecture/SECURITY_THREAT_MODEL.md`.

## 13. CI/CD and quality gates

GitHub Actions CI runs Maven verification for pushes/PRs/manual dispatch.

Required pipeline stages:

```text
checkout -> Java 25 -> Maven verify
  -> tests/integration tests
  -> JaCoCo
  -> OWASP Dependency-Check
  -> CycloneDX SBOM
  -> JaCoCo report artifact
```

Minimum enforced coverage:

- 90% line coverage.
- 90% branch coverage.

CVSS >= 7 dependency findings fail the build.

GitHub Dependency Review is not enabled because the current private repository lacks the required Advanced Security capability.

Deployment/CD target remains intentionally undecided.

## 14. Technology baseline

- Java 25
- Spring Boot 4.x (Phase 0 used 4.1.1)
- Maven
- PostgreSQL 18.4 integration testing baseline
- Flyway 12.11.0
- JUnit 6.x via Spring Boot dependency management
- Testcontainers 2.0.5
- JaCoCo 0.8.15
- Spring Data JPA, Validation, Security, Actuator

Dependency versions must be re-verified for current stable releases and vulnerabilities before implementation phases add dependencies.

## 15. Phase roadmap/status

### Phase 0 — Foundation & Security

**Status: Completed and merged.**

Tracking: Issue #1, PR #2. Acceptance criteria were satisfied including PostgreSQL Testcontainers, Flyway, schema validation, security baseline, CI, 90% line/branch coverage, Dependency-Check and SBOM.

### Phase 0.5 — Architecture & Domain Design

**Status: Design complete; awaiting final review/merge.**

Tracking: Issue #19, branch `phase-0.5-architecture`.

Deliverables on this branch:

- `docs/architecture/DOMAIN_MODEL.md`
- `docs/architecture/TRANSACTION_LEDGER.md`
- `docs/architecture/PERFORMANCE_MODEL.md`
- `docs/architecture/PROVIDERS.md`
- `docs/architecture/DATABASE_MODEL.md`
- `docs/architecture/API.md`
- `docs/architecture/FRONTEND.md`
- `docs/architecture/SECURITY_THREAT_MODEL.md`
- `docs/architecture/WORKFLOWS.md`
- `docs/api/openapi.yaml`
- `docs/adr/0001-instrument-position-account-portfolio.md`
- `docs/adr/0002-immutable-financial-ledger.md`
- `docs/adr/0003-missing-market-data-policy.md`
- `docs/adr/0004-phase-0-5-design-boundary.md`

Phase 0.5 decisions explicitly agreed:

- Instrument → Position → Account → Portfolio ownership model.
- Same instrument can exist in multiple accounts/portfolios.
- Transfers preserve lots/cost basis.
- Completed financial transactions are immutable; corrections use reversals/corrections.
- Missing historical market/FX data results in calculation + structured warnings + manual override.
- Authentication is deferred but architecture remains ready for it.
- Paid market-data providers and deployment infrastructure are not assumed.

### Phase 1 — Portfolio & Account / Core Domain

**Status: Completed and merged.**

Tracking: Issue #21, PR #22. Implemented portfolio/account/instrument domain, value objects, PostgreSQL Flyway V2 migrations, RFC 9457 problem APIs, Spring Security rules, and Testcontainers integration tests.

### Phase 1.5 — React Frontend Foundation

**Status: Completed and tested.**

Tracking: Issue #23, branch `phase-1.5-frontend-foundation`. Deliverables:
- Modular feature architecture (`src/features/portfolios`, `src/features/accounts`, `src/features/instruments`, `src/features/dashboard`, `src/components`, `src/theme`, `src/api`, `src/forms`, `src/routing`)
- React Hook Form + Zod validation matching OpenAPI schemas
- Material UI responsive design system and financial dashboard theme
- Portfolio, account, and instrument management flows with RFC 9457 error alerts, skeleton loaders, empty states, and confirm dialogs
- Vitest and React Testing Library test suite (22 unit, client, and UI tests passing)

### Phase 2 — Investment Holdings & Position Engine

**Status: Completed and tested.**

Tracking: Issue #24, branch `phase-2-investments`. Deliverables:
- Position/holding domain model (`Position`, `PositionStatus`) enforcing fractional decimal quantity precision (`BigDecimal`), account/instrument ownership invariants, non-negative quantities, and cost basis value objects (`Money`).
- Database schema evolution managed via Flyway migration `V3__investments.sql` creating table `positions` with foreign key references to `accounts` and `instruments`, unique constraints, and indexes.
- JPA persistence with `PositionRepository` supporting eager fetch graphs (`@EntityGraph`) for account and instrument.
- `PositionService` business logic layer enforcing portfolio/account validation, duplicate prevention, and position lifecycle management.
- REST endpoints `/api/v1/portfolios/{portfolioId}/accounts/{accountId}/positions` and `/api/v1/portfolios/{portfolioId}/positions` with RFC 9457 Problem Details error handling.
- OpenAPI contract updated (`docs/api/openapi.yaml`).
- Frontend position holdings UI (`src/features/positions/`): `PositionTable`, `PositionFormModal`, `usePositions` query/mutation hooks, and Zod `positionSchema` validation.
- Unit and integration tests (22 Spring Boot tests with Testcontainers PostgreSQL and 24 Vitest frontend tests passing with 100% line coverage and >=90% branch coverage).

### Phase 3 — CSV Import & Position Engine

Implement generic/broker CSV import adapters, preview/validation/conflict resolution, idempotency, position engine, cost basis and holdings UI. Broker samples must be provided before format-specific assumptions.

### Phase 4 — Performance, Market Data & Currency

Implement FIFO/LIFO/Average Cost, XIRR/TWR/MWR, market/FX providers, historical valuation, warnings/overrides and performance UI.

### Phase 5 — Reporting, Dashboard & Benchmarking

Implement dashboard, allocation/dividend/fee/cash-flow reporting, exports and S&P 500/FTSE 100/MSCI World benchmarking.

### Phase 6 — Production Readiness & Deployment

Decide and implement deployment target, secrets/configuration, backups/recovery, observability, production migrations, CD gates, release strategy and disaster recovery.

## 16. Future LLM instructions

Before changing the repository:

1. Read this file completely.
2. Inspect current GitHub branch/issue/PR state.
3. Identify the active phase and avoid implementing later-phase functionality prematurely.
4. Ask the user when a material choice is ambiguous.
5. Verify current stable dependency versions and known vulnerabilities before adding/updating dependencies.
6. Preserve 90% line and branch coverage.
7. Add/update tests with production changes.
8. Keep Flyway migrations immutable after application.
9. Update OpenAPI and architecture documentation when contracts change.
10. Update this context whenever a material decision or phase status changes.
11. Do not silently introduce authentication, paid providers, cloud infrastructure or other major decisions.
12. Before closing a phase, verify its acceptance criteria and CI/security/coverage gates.

## 17. Change log

### 2026-08-21

- Repository created as `takakim/invest-tracker`; existing `investment-tracker` left untouched.
- Java 25, Spring Boot 4.x, Maven, PostgreSQL/Flyway and JUnit 6.x selected.
- React + TypeScript selected.
- Multiple portfolios/accounts, currencies, fractional shares and per-account cash agreed.
- Free market data initially; benchmarks postponed.
- Security-first dependency policy, GitHub Actions CI and 90% coverage gates established.
- Phase 0 completed and merged as PR #2.
- Phase 0.5 decisions agreed: ownership model, immutable ledger, transfer/cost-basis preservation, missing-data warnings/manual overrides.
- Phase 0.5 architecture documentation, ADRs, workflows and OpenAPI design added on `phase-0.5-architecture`.

### 2026-08-22 / 2026-08-24

- Phase 1 core domain merged in PR #22 (Flyway autoconfig, value objects, domain services, REST endpoints, and integration tests).
- Phase 1.5 React frontend foundation implemented and merged in PR #35 (Issue #23) with modular domain feature layout, Material UI theme, React Hook Form + Zod validation, TanStack Query integration, RFC 9457 error handling, and Vitest testing suite.
- Phase 2 Investment Holdings & Position Engine implemented (Issue #24) on branch `phase-2-investments` with Flyway migration `V3__investments.sql`, domain value objects, REST APIs, OpenAPI contract update, React position holdings UI, and 100% passing test suites.

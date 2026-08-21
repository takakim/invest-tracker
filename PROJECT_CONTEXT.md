# Invest Tracker — Project Context

> Canonical, LLM-readable context for the project. Keep this file updated whenever a phase, architecture decision, constraint, security requirement, CI/CD rule, test strategy, or acceptance criterion changes. Preserve historical decisions unless explicitly superseded.

## 1. Project

**Name:** Invest Tracker  
**Repository:** `takakim/invest-tracker`  
**Purpose:** Personal investment portfolio tracking and performance management platform.

The system tracks investment transactions and calculates individual-investment and overall portfolio performance, including dividends, fees, taxes, gains/losses, cash flows, currencies, and market valuation.

Authentication / multi-user support is intentionally deferred for now.

## 2. Core principles

1. **Security first.** Security is considered in every phase, architectural decision, dependency selection, and implementation.
2. **Correctness over cleverness.** Financial calculations must be deterministic, auditable, and reproducible.
3. **Maintainability over premature optimisation.** Prefer clear domain boundaries and replaceable external-data providers.
4. **Dependency security.** Before suggesting or adding a dependency, check the latest stable release and known vulnerabilities/advisories. Prefer actively maintained and current versions. Do not choose an older version merely because it is popular.
5. **No assumptions when a decision materially affects the product.** Ask the user when there are multiple reasonable options or insufficient information.
6. **API-first.** Define the REST API/OpenAPI contract before or alongside implementation so frontend and backend remain independently evolvable.
7. **Tests are mandatory.** New functionality must include appropriate unit/integration tests and must preserve the 90% coverage gates.
8. **Financial precision.** Use `BigDecimal`/appropriate decimal types for money and quantities; never use binary floating-point for financial calculations.
9. **Reproducibility.** Portfolio state and performance should be derivable from transaction history and relevant historical market/FX data whenever available.
10. **Auditability.** Calculations and portfolio state must be traceable back to source transactions and market/FX inputs.

## 3. Agreed product scope

### Portfolios and accounts

- Multiple portfolios.
- Multiple accounts/broker accounts per portfolio.
- Cash is managed **per account**.
- Multiple currencies.
- User can choose an overall/base portfolio currency or retain/display an investment's native currency where appropriate.

### Investments

Support at minimum:

- Stocks
- ETFs
- Mutual funds
- Bonds
- REITs
- Crypto
- Cash

Investments should support fractional quantities/shares.

Likely identifying/reference data includes ticker, ISIN, name, exchange, currency, sector, country, and asset class.

### Transactions

Transactions should be represented as financial events rather than separate dividend/fee domain objects where practical.

Initial transaction types:

- Buy
- Sell
- Dividend
- Fee
- Deposit
- Withdrawal
- Interest
- Stock split
- Reverse split
- Transfer

Future possibilities include rights issues, spin-offs, and tax payments.

Transactions must support:

- Fractional quantities
- Multiple currencies
- Prices
- Fees
- Taxes
- Notes
- Source/import metadata
- Editing and deletion with automatic recalculation

CSV import must be idempotent: re-importing the same source data must not silently create duplicates.

### Performance

Performance calculation method is configurable per portfolio.

Supported/required calculation choices:

- FIFO
- LIFO
- Average Cost
- XIRR
- Time-Weighted Return (TWR)
- Money-Weighted Return (MWR)

The design should use pluggable calculation strategies so new methods can be added without rewriting the core engine.

Performance should include, where applicable:

- Realized gains
- Unrealized gains
- Dividends
- Fees
- Taxes
- Total return
- Annualized return
- Cash flows

### Market data

Use free market-data APIs initially.

External market-data providers must be behind a replaceable abstraction/interface. Candidate providers may include Yahoo Finance-compatible/free sources, subject to current availability, terms, reliability, and security review before implementation.

Support:

- Current prices
- Historical prices where available
- Corporate actions/splits where available
- Dividend information where available

### Currency / FX

Multiple currencies are required.

Use a replaceable FX provider abstraction.

Historical FX rates should be used whenever available for historical portfolio valuation and performance.

### Benchmarking

Planned benchmarks:

- S&P 500
- FTSE 100
- MSCI World

Benchmarking is intentionally postponed to a later phase.

## 4. Architecture direction

High-level target architecture:

```text
React + TypeScript frontend
            |
        REST / OpenAPI
            |
Spring Boot Java backend
            |
      Domain / services
            |
     Spring Data JPA
            |
        PostgreSQL
```

Backend should use domain-oriented modules rather than one large technical package. Initial conceptual modules:

```text
portfolio
account
investment
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

Controllers must remain thin; business rules belong in services/domain components.

External data should use provider interfaces so the implementation can be changed without coupling the domain to a specific vendor.

## 5. Technology decisions

### Backend

- Java **25**
- Spring Boot **4.x**; Phase 0 uses **4.1.1**.
- Maven
- PostgreSQL **18.4** in integration testing
- Flyway **12.11.0**
- Spring Data JPA
- Spring Validation
- Spring Security
- Spring Actuator
- OpenAPI
- JUnit **6.x** via Spring Boot dependency management
- Mockito through the test stack
- Testcontainers **2.0.5**
- JaCoCo **0.8.15**

### Frontend

Selected direction:

- React
- TypeScript
- Vite
- Material UI
- TanStack Query
- TanStack Table
- React Hook Form
- Zod
- React Router
- Recharts or Apache ECharts depending on charting requirements

Frontend implementation is interleaved with backend phases rather than postponed until the end.

## 6. Database and Flyway policy

Flyway is the authoritative database schema migration mechanism.

- Schema changes are version-controlled SQL migrations.
- Do not manually modify environments as a normal development workflow.
- Do not modify already-applied migrations; create a new migration for changes.
- Hibernate must use schema validation (`ddl-auto=validate`) rather than generating/updating the production schema.
- Flyway history provides reproducible database state across environments.
- Testcontainers integration tests should execute Flyway migrations against real PostgreSQL.
- Structural migrations and reference-data management should be kept conceptually separate where useful.

## 7. Security baseline

Security is a cross-cutting requirement from Phase 0 onward.

Current baseline includes:

- Spring Security deny-by-default HTTP security baseline.
- Input validation at API boundaries.
- No secrets/credentials committed to Git.
- Secure local database configuration.
- Least-privilege database access where environments are deployed.
- Centralised exception handling without leaking internal implementation details.
- Sensitive information must not be logged.
- File upload limits and validation for CSV imports.
- CSV import must guard against malformed/malicious input and spreadsheet-formula injection where exported/imported data could be interpreted by spreadsheet software.
- No frontend secrets embedded in bundles.
- Avoid unsafe HTML rendering/XSS-prone APIs.
- Rate limiting and further perimeter controls to be evaluated before public exposure.

### Dependency/supply-chain security

Current tooling/policy includes:

- OWASP Dependency-Check with build failure for CVSS >= 7.
- CycloneDX SBOM generation.
- Dependabot for Maven dependencies and GitHub Actions.
- GitHub Actions should be pinned to verified commit SHAs where practical.
- Java setup uses signature verification.
- Dependency versions must be checked against current stable releases and vulnerability advisories before adoption.

GitHub Dependency Review is not currently enabled because the private-repository configuration does not provide the required Advanced Security capability; OWASP Dependency-Check remains the enforced dependency vulnerability gate.

## 8. CI/CD and quality gates

GitHub Actions is the CI platform.

Current CI expectations:

```text
checkout
  -> Java 25 setup
  -> Maven verify
       -> compile
       -> unit/integration tests
       -> JaCoCo coverage verification
       -> OWASP Dependency-Check
       -> CycloneDX SBOM
  -> publish JaCoCo report artifact
```

CI runs for pull requests and relevant pushes, with manual workflow dispatch available.

### Coverage policy

Coverage is **enforced**, not merely reported.

Minimum overall coverage:

- **90% line coverage**
- **90% branch coverage**

A build below either threshold must fail.

Every phase that adds production code must add enough tests to preserve these gates.

### CD

Deployment infrastructure has deliberately **not** been selected yet. Do not assume AWS/Azure/GCP/VPS/Kubernetes/etc. The deployment target and production release strategy will be decided before production-readiness work.

## 9. Phase roadmap

### Phase 0 — Foundation & Security

**Status:** ✅ Completed and merged on 2026-08-21.

Objective: Establish the secure, testable project foundation before domain implementation.

Completed scope:

- Java 25
- Spring Boot 4.1.1
- Maven
- PostgreSQL integration testing
- Flyway
- JUnit 6.x
- Testcontainers PostgreSQL integration test
- Spring Security baseline
- Flyway-managed schema + Hibernate validation
- JaCoCo 90% line/branch enforcement
- OWASP Dependency-Check
- CycloneDX SBOM
- Dependabot
- GitHub Actions CI
- Secure local PostgreSQL configuration
- Canonical `PROJECT_CONTEXT.md`

Acceptance criteria status:

- Application context starts against PostgreSQL in Testcontainers. ✅
- Flyway runs successfully. ✅
- Hibernate validates rather than manages the schema. ✅
- No credentials are committed. ✅
- CI runs tests, security checks, coverage checks, and SBOM generation. ✅
- CVSS >= 7 dependency findings fail the build. ✅
- Overall line coverage >= 90%. ✅
- Overall branch coverage >= 90%. ✅
- Phase branch reviewed and merged to `main`. ✅

Tracking:

- GitHub Issue #1 — Phase 0: Foundation & Security — completed.
- GitHub PR #2 — Phase 0: Foundation and security — merged as commit `41e5d0fddf722344f6b3fa4805a51d91b7aa2157`.

Important post-merge security hardening recorded during Phase 0:

- Dependency-Check NVD mirror configuration was corrected after initial XML/merge-ref issues.
- A pipeline run successfully parsed the POM, compiled, ran 3 tests, passed the 90% coverage gates, executed Dependency-Check, and uploaded the JaCoCo report.
- That scan identified CVE-2026-66299 (CVSS 7.5) in Tomcat 11.0.24; the project then updated Tomcat to 11.0.25 and Dependency-Check to 13.0.0 in the Phase 0 branch before merge. The merged repository should be verified against the final dependency graph before treating this hardening as fully complete.

### Phase 0.5 — Architecture & Domain Design

**Status:** Next phase.

Objective: Design the domain and contracts before substantial business implementation.

Planned scope:

- Domain model and aggregates/value objects.
- Portfolio/account/investment/transaction relationships.
- Position and performance concepts.
- Database schema design.
- Flyway migration plan.
- REST/OpenAPI contract.
- Package/module boundaries.
- Sequence diagrams for manual transaction, CSV import, and recalculation workflows.
- Frontend wireframes/design system.
- Test strategy.
- Security threat considerations.

Acceptance criteria:

- Core domain model reviewed and agreed.
- API contract sufficiently defined for Phase 1.
- Database model reviewed.
- Major calculation and recalculation workflows documented.
- Security and data-integrity risks identified.

### Phase 1 — Portfolio & Account / Core Domain

Objective: Implement the core portfolio/account domain and persistence.

Planned scope:

- Portfolio CRUD.
- Account CRUD and portfolio relationship.
- Portfolio base/overall currency.
- Per-account cash handling model.
- Investment catalogue foundations as needed by the core domain.
- Validation and persistence.
- REST API implementation.
- Unit/integration tests.
- Initial React application shell where appropriate.

Do not assume final transaction/performance rules until Phase 0.5 has been reviewed.

### Phase 2 — Investment & Transaction Engine

Objective: Implement the financial transaction ledger.

Planned scope:

- Investment catalogue.
- Buy/sell/dividend/fee/deposit/withdrawal/interest/split/reverse-split/transfer transactions.
- Fractional quantities.
- Multi-currency transactions.
- Fees/taxes.
- Transaction editing/deletion with automatic recalculation.
- Transaction validation.
- Manual transaction UI.
- Transaction list/filter/sort UI.
- Comprehensive financial correctness tests.

### Phase 3 — CSV Import & Position Engine

Objective: Import broker data reliably and derive current/historical positions.

Planned scope:

- Generic CSV import framework.
- Broker-specific importers based on user-provided CSV samples.
- Upload/preview/validation/conflict-resolution workflow.
- Idempotent imports.
- Position engine.
- Cost basis and average price.
- Historical positions where data permits.
- Holdings UI.

### Phase 4 — Performance, Market Data & Currency

Objective: Build the complete valuation/performance engine.

Planned scope:

- FIFO/LIFO/Average Cost strategies.
- XIRR/TWR/MWR.
- Realized/unrealized gains.
- Dividends/fees/taxes/total return.
- Free market-price provider integration.
- Historical prices where available.
- FX provider integration.
- Historical FX valuation where available.
- Portfolio and investment performance UI.

### Phase 5 — Reporting, Dashboard & Benchmarking

Objective: Deliver user-facing analytics and comparisons.

Planned scope:

- Portfolio dashboard.
- Holdings/allocation charts.
- Dividend reports.
- Fee reports.
- Cash-flow reports.
- Performance charts.
- Exports (CSV/Excel/PDF subject to later decision).
- S&P 500 benchmark.
- FTSE 100 benchmark.
- MSCI World benchmark.
- Relative performance and selected risk metrics.

### Phase 6 — Production Readiness & Deployment

Objective: Make the application safely deployable.

Scope to be decided after the product is functional:

- Deployment target.
- Infrastructure.
- Secrets/configuration management.
- Database backups and recovery.
- Observability.
- Production migrations.
- CI/CD deployment gates.
- Release strategy.
- Disaster recovery considerations.

### Future enhancements

Potential later features, subject to user validation:

- Scheduled market/FX synchronisation.
- Dividend forecasting.
- Portfolio goals.
- Rebalancing suggestions.
- Tax estimation/reporting.
- Alerts.
- Authentication and multi-user support.
- Mobile/PWA experience.
- Additional brokers/import formats.

Do not implement future enhancements simply because they are listed here; confirm requirements first.

## 10. Frontend delivery strategy

React is selected, but frontend work is intentionally interleaved with backend phases.

Initial frontend foundation should include:

- Application shell/layout.
- Routing.
- Theme/light-dark support as appropriate.
- API client.
- Error/loading states.
- Accessible reusable components.

Then add UI capabilities alongside the relevant backend domain phase rather than creating the entire frontend at the end.

## 11. CSV import strategy

The user will provide sample CSV files. Do not assume broker formats before seeing the samples.

Target architecture:

```text
CSV upload
   -> importer detection/selection
   -> parse
   -> validate
   -> preview
   -> conflict/duplicate resolution
   -> import
   -> recalculate
```

Broker-specific importer implementations should convert source rows into a common transaction model.

## 12. Important design questions already answered

| Question | Decision |
|---|---|
| Fractional shares? | Yes |
| Cash handling? | Per account |
| Historical valuation? | Use historical prices/FX when possible |
| Performance method? | Configurable per portfolio |
| Performance options? | FIFO, LIFO, Average Cost, XIRR, TWR, MWR |
| Currencies? | Multiple; portfolio/base currency configurable |
| Portfolios? | Multiple |
| Accounts? | Multiple per portfolio |
| CSV import? | Yes; samples will be provided by user |
| Market data? | Free API initially, behind provider abstraction |
| Benchmarks? | S&P 500, FTSE 100, MSCI World; later phase |
| Authentication? | Deferred |
| Frontend? | React + TypeScript |
| CI/CD? | GitHub Actions CI now; deployment target deferred |
| Coverage? | Enforced >=90% line and branch |
| Security? | First-class requirement throughout |

## 13. Current implementation status

**Repository is now on `main` after Phase 0 merge.**

Current known state:

- Phase 0 PR #2 is merged.
- Issue #1 is completed by PR #2.
- Issue #3 is this documentation maintenance issue and is now being completed for the Phase 0 handover.
- `PROJECT_CONTEXT.md` is the canonical LLM context document on `main`.
- GitHub Actions CI exists and runs Maven verification.
- JaCoCo coverage enforcement is configured for 90% line and branch coverage.
- OWASP Dependency-Check is configured as a security gate.
- CycloneDX SBOM generation is configured.
- Dependabot monitors Maven and GitHub Actions.
- Testcontainers PostgreSQL integration testing exists.
- Flyway owns database migrations; Hibernate validates schema.
- Spring Security baseline exists.

## 14. How future LLMs should work on this repository

Before making changes:

1. Read `PROJECT_CONTEXT.md` completely.
2. Inspect the current repository state and relevant phase issue/PR.
3. Identify whether the requested change belongs to the current phase or requires a design decision first.
4. If the request has material ambiguity, ask the user rather than assuming.
5. Check current stable versions and known vulnerabilities before adding/updating dependencies.
6. Preserve the 90% line and branch coverage gates.
7. Add/update tests with every production change.
8. Keep Flyway migrations immutable once applied.
9. Update OpenAPI/domain documentation when contracts change.
10. Update this file whenever architecture, scope, phase status, acceptance criteria, security rules, or important decisions change.
11. Do not silently introduce authentication, cloud infrastructure, paid market-data services, or other major product decisions that the user has not approved.
12. Before considering a phase complete, verify tests, coverage, security gates, and acceptance criteria and record the result here.

## 15. Change log

### 2026-08-21

- Project created as `takakim/invest-tracker`.
- Existing `takakim/investment-tracker` intentionally left untouched.
- Java 25 selected.
- Spring Boot 4.x selected; Phase 0 used 4.1.1.
- Maven selected.
- PostgreSQL + Flyway selected.
- JUnit 6.x selected.
- React + TypeScript selected for frontend.
- Multiple portfolios/accounts and currencies agreed.
- Fractional shares agreed.
- Per-account cash agreed.
- Historical prices/FX used when possible.
- Configurable performance methods agreed.
- Free market-data APIs agreed initially.
- CSV import agreed; user will provide broker samples.
- S&P 500 / FTSE 100 / MSCI World benchmarking agreed but postponed.
- Authentication deferred.
- Security-first dependency policy established.
- GitHub Actions CI established.
- 90% line and branch coverage enforcement established.
- PROJECT_CONTEXT.md established as the canonical LLM context document.
- Phase 0 completed and merged as PR #2.
- Issue #3 reviewed and completed as the documentation handover point between Phase 0 and Phase 0.5.

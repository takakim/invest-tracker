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
│   └── db/migration/                       # Flyway SQL schema evolution (V1__baseline.sql, V2__core_domain.sql, etc.)
├── src/test/java/com/takakim/investtracker/ # Test suites (Testcontainers PostgreSQL, Unit Tests)
└── frontend/                               # React + TypeScript Web Frontend
    ├── src/
    │   ├── api/                            # Typed HTTP client & RFC 9457 problem details handler
    │   ├── components/                     # Reusable UI primitives (Layout, LoadingState, ErrorAlert, etc.)
    │   ├── features/                       # Domain feature modules (dashboard, portfolios, accounts, instruments)
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

1. **Check Canonical Context First**: Always inspect `PROJECT_CONTEXT.md` to understand active phase scope. Do not prematurely implement future phase functionality (e.g., transaction engine in Phase 1, CSV import in Phase 2).
2. **Immutability of Flyway Migrations**: Applied Flyway migration scripts in `src/main/resources/db/migration` must **NEVER** be modified. Always create a new versioned migration script (e.g. `V3__description.sql`) for schema additions.
3. **Database Schema Enforcement**: Hibernate is configured with `ddl-auto: validate`. Schema evolution is strictly owned by Flyway.
4. **API & Contract Synchronization**:
   - Endpoints must follow `/api/v1/...`.
   - Responses must use DTOs; never expose domain persistence entities directly.
   - Errors must return RFC 9457 `application/problem+json`.
   - Update `docs/api/openapi.yaml` whenever API contracts change.
5. **Precision & Financial Rules**:
   - Pass monetary amounts and fractional share quantities using `BigDecimal` and explicit domain value objects.
   - Use archive/deactivation status (`status = ARCHIVED`) instead of hard database deletion for financial entities.
6. **No Superficial Patches**:
   - Identify the root cause when tests fail.
   - Never suppress exceptions with empty fallbacks, comment out assertions, or delete failing tests.

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

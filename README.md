# Invest Tracker

Investment portfolio tracking and performance management platform.

## Project principles

- Security first.
- Correctness over cleverness.
- Financial calculations use exact decimal arithmetic (`BigDecimal`), never binary floating point.
- Transactions are the source of truth; derived positions and performance are reproducible.
- External market and FX data are accessed through replaceable provider interfaces.
- Dependencies are reviewed for current stable releases and known vulnerabilities before adoption.

## Phase 0 foundation

### Backend

- Java 25
- Spring Boot 4.1.1
- Maven 3.9.16 recommended for local development
- PostgreSQL 18.4
- Flyway 12.11.0
- JUnit 6.x through the Spring Boot test dependency management
- Testcontainers for PostgreSQL integration tests

Spring Boot 4.1.1 is the current stable Spring Boot release selected for this project. Java 25 is the required language/runtime level.

### Security and supply-chain controls

- Spring Security with deny-by-default HTTP authorization until authentication is introduced.
- Hibernate schema generation disabled; Flyway owns schema changes and Hibernate validates the schema.
- OWASP Dependency-Check fails the build for CVSS 7+ findings.
- CycloneDX SBOM is generated during `verify`.
- GitHub Dependency Review runs on pull requests.
- Dependabot checks Maven and GitHub Actions dependencies weekly.
- Secrets are supplied through environment variables; no credentials are committed.
- Local PostgreSQL binds to `127.0.0.1` only.

## Local development

### Prerequisites

- **Java 25** (JDK 25)
- **Maven 3.9.x** (or use the configured Maven build tool)
- **Node.js >= 24** & **npm**
- **Docker** & **Docker Compose**

### Step 1: Environment Setup

1. Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
2. Export the environment variables in your shell (or let your IDE source `.env`):
```bash
export $(grep -v '^#' .env | xargs)
```

### Step 2: Start PostgreSQL Database

Start the local PostgreSQL 18 container (bound to `127.0.0.1:5432`):
```bash
docker compose -f compose.yaml up -d postgres
```

> **Note:** Flyway automatically creates and runs all schema migrations (`V1` through `V5`) when the backend application starts.

### Step 3: Run the Backend Application (Spring Boot)

Run the Spring Boot application locally on port `8080`:
```bash
mvn spring-boot:run
```

The REST API will be accessible at `http://localhost:8080/api/v1/...` and Actuator health check at `http://localhost:8080/actuator/health`.

### Step 4: Run the Frontend (React + Vite)

In a separate terminal, start the Vite development server on port `5173`:
```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` in your browser. The Vite dev server proxies `/api` requests to the backend at `http://localhost:8080`.

---

## Running Verification & Tests

### Full Repository Verification
Runs backend unit/integration tests (using Testcontainers PostgreSQL), JaCoCo coverage (>=90%), OWASP Dependency-Check, SBOM, frontend typecheck, and Vitest suite:
```bash
mvn -B verify && npm --prefix frontend run build && npm --prefix frontend test && npm --prefix frontend audit --audit-level=high
```

### Backend-Only Tests
```bash
mvn clean verify
```

### Frontend-Only Tests & Build
```bash
npm --prefix frontend run build
npm --prefix frontend test
```

## Roadmap

- Phase 0: Foundation & security
- Phase 0.5: Architecture and domain design
- Phase 0.75: OpenAPI contract
- Phase 1: Portfolio and accounts
- Phase 1.5: React frontend foundation
- Phase 2: Investments
- Phase 3: Transactions
- Phase 4: CSV import
- Phase 5: Position engine
- Phase 6: Performance engine
- Phase 7: Market data
- Phase 8: Currency engine
- Phase 9: Reporting
- Phase 10: Benchmarking
- Phase 11: Frontend completion
- Phase 12: Future enhancements

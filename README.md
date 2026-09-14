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

## Quickstart (One-Command Full Stack Docker)

The simplest and fastest way to launch the complete system (PostgreSQL 18, Spring Boot 4 Backend on Java 25, and React 19 Frontend on Nginx) is via the included startup script or Docker Compose:

### 1. Launch with `./start.sh`
```bash
./start.sh
```

Or using Docker Compose directly:
```bash
cp -n .env.example .env
docker compose up --build -d --wait
```

### 2. Access Services
- 🌐 **Web Application (Frontend)**: [http://localhost:3000](http://localhost:3000)
- 🔌 **REST API (Backend)**: [http://localhost:8080/api/v1](http://localhost:8080/api/v1)
- 🩺 **Health Check**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- 🗄️ **PostgreSQL Database**: `127.0.0.1:5432` (user/db: `invest_tracker`)

To view live container logs:
```bash
docker compose logs -f
```

To stop all containers and retain database volume:
```bash
docker compose down
```

---

## Native Local Development (Optional)

If developing locally without containerizing the backend/frontend processes:

### Prerequisites
- **Java 25** (JDK 25)
- **Maven 3.9.x**
- **Node.js >= 22** & **npm**
- **Docker** (for PostgreSQL)

### Step 1: Environment Setup
```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)
```

### Step 2: Start PostgreSQL Database
```bash
docker compose up -d postgres
```

> **Note:** Flyway automatically applies all schema migrations (`V1` through `V6`) when the backend starts.

### Step 3: Run the Backend Application
```bash
mvn spring-boot:run
```
Backend runs at `http://localhost:8080`.

### Step 4: Run the Frontend
```bash
cd frontend
npm install
npm run dev
```
Vite development server runs at `http://localhost:5173` and proxies `/api` requests to `http://localhost:8080`.

---

## AI Portfolio & Holding Intelligence Engine

Invest Tracker features an AI intelligence engine designed to connect to a local LLM running on **LM Studio** (optimized for **Gemma 4 12B**) or any OpenAI-compatible API.

### Key Capabilities
- **Portfolio-Level Intelligence**:
  - **Risk Meter (1–10 Gauge)**: Consolidated portfolio risk score and risk categorization (`LOW`, `MODERATE`, `HIGH`, `VERY_HIGH`).
  - **Diversification & Concentration Assessment**: Scans for single-asset concentration, regional exposure, and sector imbalances.
  - **UK Tax Wrapper Placement Optimization**: Evaluates asset placement across ISAs, SIPPs, and GIAs to minimize tax drag.
  - **Macro Stress Testing**: Evaluates resilience under interest rate shocks, stagflation, and market drawdowns.
  - **Prioritized Recommendations**: Concrete, actionable rebalancing steps to improve risk-adjusted returns.
- **Holding-Level Deep Evaluation**:
  - **Decisive Stance**: `STRONG_BUY`, `ACCUMULATE`, `HOLD`, `TRIM`, `SELL`.
  - **Executive Thesis**: Narrative catalyst and valuation overview.
  - **Hold vs. Sell Trade-Off Analysis**: Evaluates valuation multiple compression risks vs. dividend/buyback compounding.
  - **Fundamental Ratios**: P/E, Forward P/E, PEG, Debt-to-Equity, ROE, enriched with live 52-week price ranges from Yahoo Finance.
  - Accessible directly on each row of the positions table via the sparkle action icon.

### LM Studio Configuration

1. **Start the Local Server in LM Studio**:
   - Open LM Studio and go to the **Local Server** tab.
   - Load **Gemma 4 12B** (`google/gemma-4-12b`) or your chosen model.
   - Start the server on port `1234`.

2. **Docker Compose Networking**:
   When running via `./start.sh` or Docker Compose, the backend container communicates with your host machine via `http://host.docker.internal:1234`.

   Configurable via `.env` or environment variables:
   ```env
   AI_ENABLED=true
   AI_BASE_URL=http://host.docker.internal:1234
   AI_MODEL=google/gemma-4-12b
   AI_TIMEOUT_SECONDS=1200
   ```

3. **Inference Timeouts**:
   Local LLMs generating structured financial reports can take several minutes. The stack is preconfigured with a 20-minute timeout (`1200s`) across:
   - Backend `RestClient` socket read timeout (`AI_TIMEOUT_SECONDS=1200`)
   - Nginx reverse proxy `proxy_read_timeout 1200s`
   - Frontend HTTP client

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

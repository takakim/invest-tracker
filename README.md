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
- Detailed security architecture, supported versions, and vulnerability reporting procedures are documented in [docs/project/SECURITY.md](docs/project/SECURITY.md).

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

Invest Tracker features an AI intelligence engine that generates structured financial evaluations, risk ratings, and strategic action plans. It supports multiple LLM provider backends and automatically persists the latest intelligence for each portfolio and holding in PostgreSQL.

### Key Capabilities
- **Evaluation Persistence**:
  - Automatically saves the latest portfolio-level and holding-level AI evaluations to the database.
  - Previous analyses are immediately available without re-running expensive LLM inferences.
  - Displays the model/provider used and relative timestamp (e.g. `1h ago`).
  - Includes a one-click **Re-evaluate** button to generate fresh intelligence on demand.
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
- **Auto Model & Provider Detection**:
  - Automatically discovers whichever LLM chat model is currently loaded in memory (RAM/VRAM) in LM Studio.
  - In `AUTO` mode, prioritizes local inference if LM Studio is running, and seamlessly falls back to configured cloud providers if offline.
  - Automatically filters out non-chat models (such as embeddings) and extracts reasoning content from thinking/reasoning models.
  - Interactive AI Settings modal in the web UI allows switching active providers and selecting from discovered models at runtime.

### Supported AI Providers

Select your provider via the `AI_PROVIDER` environment variable (defaults to `AUTO`):

| Provider | `AI_PROVIDER` | Default Model | Required Credentials | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Auto** (default) | `AUTO` | `auto` | None (or cloud key for fallback) | Prioritizes local LM Studio if running; falls back to configured cloud providers if offline. |
| **LM Studio** (local) | `LM_STUDIO` | `auto` | None (runs locally) | Automatically detects whichever model is loaded in LM Studio. |
| **Google Gemini** | `GEMINI` | `auto` (`gemini-2.5-flash`) | `GEMINI_API_KEY` | Fast cloud inference via Google Gemini. |
| **OpenAI** | `OPENAI` | `auto` (`gpt-4o-mini`) | `OPENAI_API_KEY` | Cloud inference via OpenAI. |
| **Anthropic Claude** | `ANTHROPIC` | `auto` (`claude-3-5-haiku-latest`) | `ANTHROPIC_API_KEY` | Cloud inference via Anthropic. |

#### 1. LM Studio (Local Inference, Free & Private)
1. Open LM Studio and navigate to the **Local Server** tab.
2. Load any LLM chat model of your choice into memory (RAM/VRAM).
3. Start the server on port `1234`.
4. Launch Invest Tracker. With default settings (`AI_PROVIDER=AUTO` and `AI_MODEL=auto`), Invest Tracker automatically detects LM Studio, identifies your loaded model, and connects — no manual model configuration required!

Optional explicit `.env` configuration:
```env
AI_PROVIDER=AUTO
AI_ENABLED=true
AI_BASE_URL=http://host.docker.internal:1234
AI_MODEL=auto
AI_REASONING_EFFORT=none
AI_TIMEOUT_SECONDS=1200
```
> **Tip:** You can keep `AI_MODEL=auto` to dynamically use whatever model is loaded in LM Studio, or set `AI_MODEL` to a specific model ID if preferred. Setting `AI_REASONING_EFFORT=none` (default) disables redundant internal chain-of-thought tokens on reasoning models (e.g. Qwen 3.5), reducing inference latency from ~150s down to ~15s.

#### 2. Google Gemini
```env
AI_PROVIDER=GEMINI
AI_ENABLED=true
AI_MODEL=auto
GEMINI_API_KEY=AIzaSy...
```

#### 3. OpenAI
```env
AI_PROVIDER=OPENAI
AI_ENABLED=true
AI_MODEL=auto
OPENAI_API_KEY=sk-proj-...
```

#### 4. Anthropic Claude
```env
AI_PROVIDER=ANTHROPIC
AI_ENABLED=true
AI_MODEL=auto
ANTHROPIC_API_KEY=sk-ant-...
```

### Runtime Provider & Model Selection
You can inspect the currently connected provider and model, test connectivity, and switch active providers or models at runtime directly in the web application by clicking the AI status badge in the top navigation bar.

### Inference Timeouts
Complex prompts evaluated by local LLMs can take several minutes. The stack is preconfigured with a 20-minute timeout (`1200s`) across:
- Backend `RestClient` socket read timeout (`AI_TIMEOUT_SECONDS=1200`)
- Nginx reverse proxy `proxy_read_timeout 1200s`
- Frontend HTTP client

---

## Historical Valuation & Native Price Backfill Engine

Invest Tracker reconstructs exact daily portfolio valuations and equity trajectories without artificial cliff jumps:
- **Dense Historical Daily Bars**: Prioritizes multi-year chart observations from Yahoo Finance for all instruments ever traded (both currently held and liquidated/sold positions) from the earliest acquisition date up to today.
- **Dual Automatic Triggers**:
  - Automatically triggers backfilling on portfolio valuation loads when observation point density is insufficient (`hasSufficientHistoricalCoverage`).
  - Automatically backfills price observations for new instruments immediately after completing broker CSV imports.
- **On-Demand Synchronization**: Includes a **Sync History** button on the Historical Valuation & Benchmark Performance card with live progress and automatic cache refresh.
- **Dedicated Backfill API**: `POST /api/v1/portfolios/{id}/history/backfill?range={range}`.

---

## Operational & Diagnostic CLI Utilities (`scripts/`)

Standalone Python 3 CLI utilities are provided in `scripts/` to accelerate diagnostics, audits, and operational tasks:

| Script | Purpose | Key Arguments |
| :--- | :--- | :--- |
| `audit_ai_evaluations.py` | Audits persisted AI evaluations for reasoning leaks; triggers live re-evaluations. | `--ticker MU`, `--re-evaluate`, `--url` |
| `test_llm_completion.py` | Benchmarks LLM endpoints, measures token latency, and analyzes reasoning content. | `--model`, `--max-tokens`, `--reasoning-param` |
| `audit_holdings.py` | Verifies live holding calculations, cost-basis currencies, and cash reconciliation. | `--portfolio <UUID>`, `--url` |
| `audit_csv_ledger.py` | Audits broker CSV cash balances, withholding taxes, and detects double deductions. | `<file.csv>`, `--target-cash <amount>` |
| `backfill_history.py` | Backfills multi-year daily market price bars from Yahoo Finance directly to Postgres. | `--range 5y`, `--tickers AAPL NVDA` |
| `freetrade_csv_tool.py` | Injects corporate actions (forward/reverse splits) and audits Freetrade statements. | `audit <file>`, `inject <in> <out>` |
| `enrich_investengine_csv.py` | Cross-references ISINs to add ticker symbols and native currencies to InvestEngine CSVs. | `<file.csv> -o <out.csv>` |
| `check_coverage.py` | Inspects JaCoCo reports against mandatory 90% line & branch coverage gates. | `--file <SourceFile.java>` |

For detailed CLI usage, see [scripts/README.md](scripts/README.md).

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

---

## Project Governance & Documentation

Detailed architectural documentation, security guidelines, and task records are organized in `docs/project/`:

- 📋 **[PROJECT_CONTEXT.md](docs/project/PROJECT_CONTEXT.md)**: Canonical LLM and developer context, architectural decisions, and phase progression.
- 🛡️ **[SECURITY.md](docs/project/SECURITY.md)**: Security policy, supported versions, and vulnerability disclosure SLA.
- 🤖 **[AGENTS.md](AGENTS.md)**: AI agent rules, financial precision standards, and verification commands.
- 📦 **[Features Archive](docs/project/features/)**: Detailed specifications and task history for completed features.
- 📝 **[Active Task Tracker](docs/project/tasks/active_task.md)**: Current in-progress development task checklist.
- 📐 **[Architecture Specifications](docs/architecture/)**: Domain models, ledger invariants, and API designs.
- 📑 **[OpenAPI Contract](docs/api/openapi.yaml)**: OpenAPI 3.1.1 specification.

# Security Policy — Invest Tracker

`invest-tracker` adheres to a security-first engineering methodology. Because this application processes financial portfolios, asset valuations, and personal transaction ledgers, security, data integrity, and dependency supply-chain controls are strictly enforced.

---

## 1. Supported Versions

Security updates, dependency patches, and vulnerability remediations are actively maintained on the default development branch.

| Version / Branch | Supported          | Runtime Baseline            | Notes                                                |
| :--------------- | :----------------- | :-------------------------- | :--------------------------------------------------- |
| `main`           | :white_check_mark: | Java 25 / Node 22 / PG 18   | Actively maintained; all security gates enforced.    |
| `< 0.1.0`        | :x:                | —                           | Historical development checkpoints; upgrade to main. |

---

## 2. Core Security Controls & Architecture

### Application & Network Security
- **Deny-by-Default HTTP Authorization**: Spring Security is configured with strict request matchers, denying unauthorized access to non-whitelisted endpoints.
- **Local Loopback Isolation**: Database services (`invest-tracker-postgres`) explicitly bind to `127.0.0.1` only, preventing external exposure across LANs or public interfaces.
- **Non-Root Container Execution**: Backend and frontend Docker images execute as dedicated unprivileged users (`appuser:appgroup` / UID 10001).
- **Sensitive Configuration Isolation**: Credentials, API tokens (Twelve Data, FMP, OpenAI, Gemini, Anthropic), and database passwords are injected exclusively via environment variables (`.env`) and never checked into source control.

### Financial Precision & Ledger Invariants
- **Zero Binary Floating-Point Types**: All balances, transaction values, prices, FX rates, and quantities use domain value objects backed by exact-precision `BigDecimal` to eliminate floating-point calculation errors.
- **Immutable Schema Migrations**: Hibernate schema evolution is locked (`ddl-auto: validate`). Schema mutations are strictly managed by forward-only Flyway SQL scripts (`db/migration/V*`).

### Automated CI/CD & Dependency Supply-Chain Gates
- **OWASP Dependency-Check**: Evaluates all third-party Java libraries on every build. Any finding with CVSS >= 7.0 (High/Critical) halts the build pipeline immediately.
- **CycloneDX Software Bill of Materials (SBOM)**: Generates a cryptographically verifiable component inventory (`application.cdx.json`) on every package cycle.
- **Frontend Vulnerability Gate**: Continuous auditing via `npm audit --audit-level=high` with 0 tolerated vulnerabilities.
- **Automated Dependency Monitoring**: Dependabot scans root Maven dependencies, GitHub Actions workflows, and frontend npm packages on a weekly schedule.

---

## 3. Reporting a Vulnerability

If you discover a security vulnerability or security flaw in `invest-tracker`:

1. **Do NOT open a public issue.**
2. Report the vulnerability privately via **GitHub Private Vulnerability Reporting** under the repository's **Security** tab (`https://github.com/takakim/invest-tracker/security/advisories/new`).
3. Alternatively, contact the repository maintainer directly via GitHub profile.

### What to Include in Your Report
- Description of the vulnerability and its potential impact.
- Step-by-step reproduction instructions or a proof-of-concept (PoC).
- Affected components (backend service, frontend UI, Docker configuration, or third-party dependency).

### Response SLA
- **Initial Acknowledgement**: Within 48 hours of receipt.
- **Triage & Status Assessment**: Within 5 business days.
- **Fix & Disclosure**: Coordinated patch release following resolution and verification.

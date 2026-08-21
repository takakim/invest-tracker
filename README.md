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

1. Install Java 25 and Maven 3.9.16 or newer within the supported Maven 3.x line.
2. Copy `.env.example` to `.env` and replace the example password with a strong local-only password.
3. Export the variables from `.env` into your shell, or configure them in your IDE.
4. Start PostgreSQL:

```bash
docker compose up -d postgres
```

5. Run the test suite:

```bash
mvn verify
```

The application will use Flyway migrations and will refuse to start if the JPA model and database schema are inconsistent.

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

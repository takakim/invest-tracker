# Security Threat Model

## Scope

Phase 0.5 covers the architecture before authentication and external deployment. The model identifies threats that must shape the implementation.

## Trust boundaries

```text
Browser
  |
  | untrusted HTTP input
  v
REST API
  |
  +--> Domain/services
  |
  +--> PostgreSQL
  |
  +--> External market/FX providers
  |
  +--> File import pipeline
```

## Assets

- Financial transaction history.
- Portfolio/account balances and holdings.
- Market/FX observations and manual overrides.
- Broker import files.
- Future credentials/API keys.
- Derived performance results.

## Key threats and controls

### Malicious or malformed CSV

Threats:

- Oversized uploads.
- Malformed parser input.
- Formula injection in exported/imported content.
- Duplicate/replay attacks.
- Unexpected encodings.

Controls:

- Upload size/type limits.
- Streaming/bounded parsing where appropriate.
- Strict schema validation.
- Normalization and safe output encoding.
- Import fingerprints/idempotency.
- Preview before commit.
- No execution of imported content.

### Financial tampering

Threat: editing/deleting historical transactions changes performance silently.

Control: immutable completed ledger plus correction/reversal records.

### Data integrity

Threat: orphaned positions, duplicate instruments, inconsistent transfers.

Controls:

- Database foreign keys/unique constraints.
- Domain invariants.
- Integration tests using real PostgreSQL.
- Deterministic recalculation.

### External provider compromise/failure

Threats:

- Malformed responses.
- Data poisoning.
- Rate limits.
- Provider outage.
- SSRF-like behaviour if URLs are configurable.

Controls:

- Fixed provider endpoints/configuration.
- Response validation.
- Timeouts and bounded retries.
- Rate-limit handling.
- Provenance on observations.
- No arbitrary user-supplied outbound URLs.

### Secrets exposure

Controls:

- No secrets in source control.
- Runtime secret configuration.
- No secrets in logs or frontend bundles.
- Least privilege.

### API abuse

Controls planned before public exposure:

- Authentication/authorization.
- Request size limits.
- Rate limiting.
- Strict validation.
- Secure headers.
- Audit logging without sensitive data.

Authentication is deferred by product decision, but the architecture must not prevent its introduction.

### Dependency/supply-chain compromise

Controls:

- Dependabot.
- OWASP Dependency-Check.
- SBOM via CycloneDX.
- Latest stable dependency policy.
- Vulnerability review before upgrades.
- Pinned GitHub Actions where practical.

## Data quality vs security

Market/FX missing data is primarily a correctness issue rather than a security issue. It must nevertheless be treated as untrusted external input. Provider data cannot overwrite financial transactions.

## Security acceptance gate

Before public deployment:

- Authentication and authorization must be implemented.
- Threat model must be revisited against the deployed architecture.
- Secrets management must be selected.
- Rate limiting/perimeter controls must be implemented.
- Security headers/CSP must be configured.
- Dependency and container/image scanning must be defined if containers are used.
- Backup/recovery controls must be tested.

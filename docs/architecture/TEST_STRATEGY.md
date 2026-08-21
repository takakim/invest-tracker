# Test Strategy

## Quality gates

- Minimum 90% line coverage.
- Minimum 90% branch coverage.
- Security scan must pass with CVSS >= 7 treated as a build failure.
- Integration tests use real PostgreSQL through Testcontainers.

## Test layers

### Domain unit tests

Test financial rules without Spring or database dependencies:

- Transaction invariants.
- Cost-basis strategies.
- Transfer preservation.
- Split/reverse-split behaviour.
- Money/currency arithmetic and rounding.
- XIRR/TWR/MWR calculation cases.
- Missing-data warning behaviour.

### Application/service tests

Test orchestration, transaction boundaries, validation, correction workflows and provider error handling.

### Repository/integration tests

Use PostgreSQL Testcontainers to verify:

- Flyway migrations.
- Constraints/index assumptions where meaningful.
- Persistence mappings.
- Immutable transaction persistence.
- Import idempotency.

### API tests

Verify request validation, response contracts, pagination, problem details and security behaviour.

### Frontend tests

Use component and integration tests for forms, tables, routing, error states and data-quality warnings. Keep business calculations in the backend rather than duplicating them in UI tests.

## Financial test philosophy

Prefer deterministic, table-driven test cases with explicit expected values. Include edge cases:

- Fractional quantities.
- Multiple currencies.
- Fees and taxes.
- Same-day transactions.
- Transfers.
- Splits.
- Missing market/FX data.
- Manual overrides.
- Zero/near-zero values.
- Negative/invalid inputs.
- Long holding periods.

For return algorithms, include independently calculated known-answer scenarios rather than testing only against the implementation itself.

## Test data

Synthetic data is preferred. Never commit real broker statements, account numbers, credentials or other personal financial data.

## Coverage exclusions

Only generated/bootstrapping code that cannot reasonably be unit-tested may be excluded, and exclusions must be documented. Business/domain code must not be excluded simply to satisfy the coverage gate.

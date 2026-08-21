# ADR 0001: Instrument, Position, Account and Portfolio boundaries

- Status: Accepted
- Date: 2026-08-21

## Context

The same security can be held in multiple broker accounts and portfolios. An instrument's identity must not depend on a user's ownership.

## Decision

Use:

```text
Instrument -> referenced by Position -> owned by Account -> grouped by Portfolio
```

Instrument is reference/master data. Position is account-specific derived ownership state.

## Consequences

- The same Instrument can be shared across accounts and portfolios.
- Cost basis remains account-specific.
- Portfolio reporting can aggregate account positions without duplicating instrument definitions.
- Instrument master data can be updated independently from ownership.

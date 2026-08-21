# ADR 0002: Immutable financial transaction ledger

- Status: Accepted
- Date: 2026-08-21

## Context

Financial history must remain auditable and recalculable. Editing a historical transaction in place can silently change previously reported performance.

## Decision

Completed financial transactions are immutable. Corrections are represented by explicit reversal/correction records referencing the original transaction.

Transfers preserve acquisition lots/cost basis and do not create gains/losses.

## Consequences

- Full historical auditability.
- Deterministic recalculation.
- More explicit correction workflows in the UI.
- Derived positions can always be rebuilt from the ledger.

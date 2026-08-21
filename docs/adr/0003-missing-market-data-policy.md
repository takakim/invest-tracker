# ADR 0003: Missing market and FX data policy

- Status: Accepted
- Date: 2026-08-21

## Context

Free market/FX providers may not contain every historical observation required for a portfolio calculation.

## Decision

Calculations proceed with available valid data and emit structured warnings for missing observations. Users may supply explicit manual price/FX overrides. The original provider observation, if any, remains preserved.

The system must never silently substitute today's price or FX rate for a missing historical observation.

## Consequences

- Reporting remains useful when data is incomplete.
- Results can be clearly marked as affected by missing data.
- Manual overrides require provenance and auditability.
- The UI must expose data-quality warnings.

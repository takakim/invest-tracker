# ADR 0004: Phase 0.5 design boundary

- Status: Accepted
- Date: 2026-08-21

## Decision

Phase 0.5 establishes domain semantics, architecture boundaries, contracts and security requirements but does not implement business functionality.

Phase 1 may implement only after material Phase 0.5 decisions are reviewed and accepted.

External provider selection, authentication, deployment infrastructure and benchmark implementation remain deferred unless explicitly brought forward.

## Consequences

- Avoids premature infrastructure/vendor coupling.
- Keeps design work independently reviewable.
- Allows Phase 1 to begin with a stable domain/API/database direction.

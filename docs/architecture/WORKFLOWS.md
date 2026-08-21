# Core Workflows

## Manual transaction

```mermaid
sequenceDiagram
  actor User
  participant UI as React UI
  participant API as REST API
  participant Domain as Transaction Service
  participant DB as PostgreSQL
  User->>UI: Enter transaction
  UI->>API: POST /api/v1/accounts/{id}/transactions
  API->>Domain: Validate command
  Domain->>DB: Persist immutable transaction
  DB-->>Domain: Transaction ID
  Domain-->>API: Created transaction
  API-->>UI: 201 Created
  UI->>API: Request updated position/performance
```

## CSV import

```mermaid
sequenceDiagram
  actor User
  participant UI as React UI
  participant API as Import API
  participant Importer as Importer Adapter
  participant Domain as Transaction Domain
  participant DB as PostgreSQL
  User->>UI: Upload CSV
  UI->>API: POST multipart upload
  API->>Importer: Parse + normalize
  Importer->>Domain: Validate normalized rows
  Domain->>DB: Check fingerprints
  DB-->>Domain: Duplicate/conflict information
  Domain-->>API: Preview + warnings
  API-->>UI: Import preview
  User->>UI: Confirm import
  UI->>API: Commit selected rows
  API->>Domain: Create immutable transactions
  Domain->>DB: Persist batch + transactions
  API-->>UI: Import result
```

## Recalculation

```text
Transactions + corporate actions
              |
              v
      Position/Lot Engine
              |
              +--> Position snapshots
              |
              v
       Valuation Engine
          /         \
      Prices        FX
          \         /
           v       v
       Performance Input
              |
              v
        Return Strategies
              |
              v
        Results + warnings
```

Recalculation is deterministic for the same ledger, observations, calculation method/version and configuration.

## Account transfer

```text
Source account
  outgoing transfer event
          |
          | common transferId
          v
Destination account
  incoming transfer event

Acquisition lots/cost basis are transferred, not re-created as a new acquisition at market price.
```
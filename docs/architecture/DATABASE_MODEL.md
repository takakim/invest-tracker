# Database Model

## Design goals

- PostgreSQL is authoritative persistence.
- Flyway owns schema evolution.
- Hibernate/JPA validates schema but does not create or update it.
- Financial facts are immutable after completion.
- Database constraints enforce key integrity and important invariants.

## Core tables (conceptual)

```text
portfolio
  id PK
  name
  base_currency
  performance_method
  created_at
  updated_at

account
  id PK
  portfolio_id FK -> portfolio
  name
  broker_name
  created_at
  updated_at

instrument
  id PK
  asset_class
  name
  native_currency
  ticker
  isin
  exchange
  created_at
  updated_at

instrument_identifier
  id PK
  instrument_id FK -> instrument
  provider
  identifier_type
  identifier_value

transaction
  id PK
  account_id FK -> account
  instrument_id FK nullable -> instrument
  type
  effective_at
  quantity nullable
  price_amount nullable
  price_currency nullable
  gross_amount
  fee_amount
  tax_amount
  net_amount
  currency
  source_type
  source_reference
  import_fingerprint
  correction_of_id nullable FK -> transaction
  transfer_id nullable
  created_at

transaction_metadata
  transaction_id FK -> transaction
  key
  value

manual_observation
  id PK
  instrument_id / currency pair
  observation_type
  observed_at
  value
  currency
  source_reference
  created_at

provider_observation
  id PK
  provider
  instrument_id / currency pair
  observation_type
  observed_at
  value
  currency
  retrieved_at
  provider_reference

import_batch
  id PK
  account_id FK -> account
  source_type
  source_filename
  source_hash
  imported_at
  status

import_record
  id PK
  import_batch_id FK -> import_batch
  source_row_reference
  normalized_fingerprint
  transaction_id nullable FK -> transaction
  status
```

The exact normalized schema may differ after implementation review.

## Constraints

- Foreign keys are mandatory for ownership relationships.
- Portfolio base currency is required.
- Transaction currency is required for monetary values.
- Quantity is required only for transaction types where applicable.
- `import_fingerprint` should be unique within the appropriate source/account scope where the source guarantees stable identity.
- Transfer records share a stable transfer identifier.
- Correction references must point to an existing immutable transaction.
- Negative monetary/quantity values are controlled by transaction semantics rather than unrestricted database acceptance.

## Indexing

At minimum, plan indexes for:

- `account.portfolio_id`
- `transaction.account_id, effective_at`
- `transaction.instrument_id, effective_at`
- `transaction.import_fingerprint`
- `transaction.transfer_id`
- provider observation lookup by instrument/currency and observation date
- import batch/source lookups

Indexes should be validated against actual query plans after Phase 1/2 implementation.

## Money storage

Store monetary amounts as PostgreSQL `numeric` with explicit application precision/scale rules. Do not rely on floating-point types.

Quantity also uses `numeric` because fractional securities are required.

The application domain remains responsible for currency-specific rounding rules.

## Derived state

Positions, cost-basis lots and performance results are derived from transactions and observations. If persisted for performance, they must be rebuildable and invalidatable.

## Migration policy

- Migrations are immutable once applied.
- Naming convention: `V<sequence>__<description>.sql`.
- One logical schema change per migration where practical.
- Backward-compatible migrations are preferred for future deployment safety.
- Reference/master-data migrations are distinguishable from structural migrations.
- Integration tests execute migrations against PostgreSQL Testcontainers.

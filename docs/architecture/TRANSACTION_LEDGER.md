# Transaction Ledger

## Principle

The transaction ledger is the financial source of truth for ownership and cash movement.

Completed financial transactions are immutable. Corrections are represented by new transactions/reversal records rather than editing historical facts.

## Transaction structure

Conceptual fields:

- `id`
- `accountId`
- `instrumentId` when applicable
- `type`
- `tradeDate` / `effectiveDate`
- `quantity` when applicable
- `price` when applicable
- `grossAmount`
- `feeAmount`
- `taxAmount`
- `netAmount`
- `currency`
- `fxRate` when explicitly known/overridden
- `counterCurrency` when applicable
- `source`
- `sourceReference`
- `importFingerprint` where imported
- `correctionOfTransactionId` where applicable
- `createdAt`
- `metadata`

The exact persistence shape is subject to the database design.

## Transaction semantics

### BUY

Creates/increases instrument quantity and consumes cash. Cost basis includes acquisition costs according to the configured accounting rules.

### SELL

Reduces quantity and generates proceeds. Realized gain/loss is calculated by the selected cost-basis strategy.

### DIVIDEND

Records income associated with an instrument. Withholding tax, when supplied, is represented explicitly rather than silently netted away.

### FEE

Records a broker/custodian or transaction fee. A fee may be linked to a related transaction but remains separately auditable.

### DEPOSIT / WITHDRAWAL

Move cash into/out of an account without representing investment performance by themselves.

### INTEREST

Records account cash interest income.

### STOCK_SPLIT / REVERSE_STOCK_SPLIT

Changes quantity/lots without creating economic gain/loss. The resulting position must preserve economic value and cost basis semantics.

### TRANSFER

Represents movement between accounts. A transfer must preserve acquisition lots/cost basis and must not create a realized gain/loss.

Transfers should be modelled as linked outgoing/incoming events with a common transfer identifier.

## Corrections and reversals

A completed transaction must not be edited in place.

To correct a transaction:

```text
Original transaction
        |
        v
Reversal/correction transaction
        |
        v
Corrected replacement transaction
```

The original remains queryable and auditable. The correction references the original and records a reason.

Whether a correction is implemented as a fully reversed transaction plus replacement, or a typed adjustment event, is an implementation detail provided the ledger remains immutable and the net economic effect is reproducible.

## Ordering

Ledger calculations require deterministic ordering. Use:

1. Effective/trade date and time when available.
2. Explicit broker sequence/reference when available.
3. Stable transaction identifier as a final tie-breaker.

The system must not rely on database insertion order.

## Idempotency

Imported transactions must have a deterministic fingerprint based on source identity and broker-provided stable identifiers where available.

If no stable source identifier exists, the importer must construct a conservative fingerprint from normalized transaction attributes and mark ambiguous duplicates for user review rather than silently merging them.

## Missing data

A transaction may be imported without a historical market/FX observation if the source does not provide one. The calculation layer must emit a data-quality warning and permit a user-approved manual override.

Manual overrides must be explicitly distinguishable from provider observations.

## Ledger-to-position flow

```text
Immutable transactions
        |
        +--> cash movements
        |
        +--> acquisition/disposal lots
        |
        +--> corporate-action adjustments
        |
        v
Position state at time T
        |
        +--> valuation using market data
        +--> conversion using FX data
        v
Performance inputs
```
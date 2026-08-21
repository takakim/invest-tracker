# Domain Model

## Core ownership model

```text
Instrument
    ^
    | referenced by
Position ---- Account ---- Portfolio
    ^             ^            ^
    |             |            |
Transaction ----+-------------+
```

- **Instrument** is the canonical financial asset definition and has no ownership state.
- **Portfolio** is the user's reporting/strategy boundary and owns accounts.
- **Account** represents a broker/custodian account and owns cash and positions.
- **Position** represents ownership of an instrument in one account. It is derived from the immutable transaction ledger rather than independently edited.
- **Transaction** is an immutable financial event recorded against an account and, where applicable, an instrument.

The same Instrument may have Positions in many Accounts and Portfolios.

## Main aggregates

### Portfolio

Responsibilities:

- Portfolio identity and name.
- Base/reporting currency.
- Configured **cost-basis method**: FIFO, LIFO or Average Cost.
- Configured **return method**: XIRR, TWR or MWR.
- Ownership of Accounts.

Cost-basis selection determines acquisition/disposal accounting. Return-method selection determines how portfolio performance is measured. These are intentionally separate concepts.

### Account

Responsibilities:

- Broker/custodian identity and account metadata.
- Account currency/currencies.
- Cash balances by currency.
- Positions.
- Transactions.

### Instrument

Reference/master-data aggregate containing:

- Stable internal identifier.
- Asset class.
- Name.
- Ticker(s).
- ISIN where available.
- Exchange/venue where relevant.
- Native currency.
- Market-data identifiers/provider mappings.

Provider-specific metadata must not become the domain identity.

### Position

A derived read/model representation:

- Account + Instrument identity.
- Quantity.
- Lots/cost basis according to the Portfolio cost-basis method.
- Current valuation when market data is available.
- Data-quality warnings.

A Position is not independently mutated by users; transactions are the source of ownership state.

## Value objects

Use decimal-safe domain types backed by `BigDecimal` where monetary/quantity precision is required:

- `Money(amount, currency)`
- `Quantity(value)`
- `Price(amount, currency)`
- `FxRate(baseCurrency, quoteCurrency, rate, asOf)`
- `InstrumentId`
- `PortfolioId`
- `AccountId`
- `TransactionId`
- `PositionId`

No financial calculation may use `double`/`float`.

## Invariants

- A transaction belongs to exactly one account.
- Instrument references must resolve to a known Instrument for instrument-related transactions.
- Quantities and prices must satisfy transaction-type-specific validation.
- A sell cannot create an invalid negative position unless the account explicitly supports short selling; short selling is **out of initial scope** and must not be silently introduced.
- Currency must be explicit for monetary amounts.
- Completed transactions are immutable.
- Corrections/reversals reference the transaction they correct.
- Transfers preserve acquisition lots/cost basis and do not create gains/losses.
- Position state is reproducible from the transaction ledger plus corporate-action events.
- Missing market/FX data never silently becomes today's data.

## Transaction types

Initial supported types:

- BUY
- SELL
- DIVIDEND
- FEE
- DEPOSIT
- WITHDRAWAL
- INTEREST
- STOCK_SPLIT
- REVERSE_STOCK_SPLIT
- TRANSFER

The model should remain extensible for rights issues, spin-offs, mergers, symbol/ISIN changes and tax events.

## Performance boundaries

Performance is calculated at multiple scopes:

- Instrument within Account.
- Account within Portfolio.
- Portfolio overall.

Return methods are XIRR, TWR and MWR. Cost-basis methods are FIFO, LIFO and Average Cost. The same calculation engine must operate on a well-defined cash-flow/valuation input model so results can be compared consistently.

## Auditability

Every derived position/performance result should be traceable to:

1. Transactions.
2. Relevant corporate actions.
3. Market-price observations.
4. FX observations or explicit manual overrides.
5. Cost-basis method.
6. Return method and algorithm version.

This is a design requirement for later implementation and does not require a user-facing audit UI in Phase 0.5.
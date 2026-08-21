# Position, Cost Basis and Performance Architecture

## Separation of concerns

The system separates:

1. Transaction ledger.
2. Position/lots derivation.
3. Market valuation.
4. FX conversion.
5. Return calculation.

This prevents a calculation method from owning persistence or external market-data concerns.

## Cost-basis strategies

Define a common strategy interface conceptually equivalent to:

```text
CostBasisStrategy
  calculateLotsAfter(transaction, currentLots)
  calculateDisposal(transaction, currentLots)
```

Initial strategies:

- FIFO
- LIFO
- Average Cost

The selected strategy is configurable per Portfolio.

### FIFO / LIFO

Maintain acquisition lots and consume them according to ordering policy for disposals.

### Average Cost

Maintain an aggregate quantity and eligible cost basis. Disposal removes the proportional cost basis according to the strategy's defined rules.

The implementation must document treatment of fees, splits and transfers for each strategy before Phase 2 is implemented.

## Return calculations

Performance calculations consume a common normalized input model rather than raw database entities.

Conceptual input:

```text
PerformanceInput
  valuationCurrency
  openingValue
  closingValue
  valuationObservations[]
  cashFlows[]
  income[]
  costs[]
  dates[]
  dataQualityWarnings[]
```

### XIRR

Use dated external/internal cash flows and terminal value. The implementation must define sign conventions and behaviour for multiple/no valid roots.

### TWR

Split the measurement period at external cash-flow boundaries, calculate linked sub-period returns, and document treatment of dividends/fees.

### MWR

Use the investor's actual dated cash flows and portfolio terminal value; conceptually equivalent to solving the appropriate money-weighted return equation.

## Performance dimensions

Expose separate components so users can reconcile results:

- Market price gain/loss.
- Realized gain/loss.
- Unrealized gain/loss.
- Dividend/income.
- Fees.
- Taxes.
- FX impact where applicable.
- Total return.
- Annualized return.

Do not collapse these into one opaque number.

## Multi-currency

Portfolio performance is calculated in the portfolio base/reporting currency when requested.

Native-currency performance remains available for an instrument/account where meaningful.

Historical conversion uses the FX observation closest to the required valuation timestamp according to an explicit provider policy. Missing historical FX produces a warning and can be resolved with a manual override.

## Missing data policy

Calculation proceeds with available observations.

Every missing required observation is represented in a structured warning containing:

- data type (price/FX/corporate action/etc.)
- instrument/currency
- affected date/range
- severity
- calculation impact
- resolution status

A user-approved manual observation can satisfy the missing input while retaining provenance.

The engine must never silently substitute current data for missing historical data.

## Recalculation

Because transactions are immutable, derived positions/performance can be recalculated deterministically.

A future implementation may cache derived state, but cached values are never the source of truth and must be invalidatable/rebuildable.

## Calculation versioning

Performance results should record the calculation-method identifier and algorithm version. Changes to financial algorithms must be traceable so historical results can be explained and, when required, reproduced with the previous version.
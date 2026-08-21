# Initial UI Wireframes

These are workflow wireframes, not visual design specifications. Material UI is the selected component foundation.

## Application shell

```text
+-------------------------------------------------------------------+
| Invest Tracker                         Portfolio: [Main v]        |
+----------------------+--------------------------------------------+
| Dashboard            |                                            |
| Portfolios           | Page content                               |
| Accounts             |                                            |
| Holdings             |                                            |
| Transactions         |                                            |
| Imports              |                                            |
| Performance          |                                            |
| Settings             |                                            |
+----------------------+--------------------------------------------+
```

## Portfolio dashboard

```text
+-------------------------------------------------------------------+
| Main Portfolio                                                  |
| Base currency: GBP       Performance method: XIRR               |
+----------------+----------------+----------------+---------------+
| Total Value     | Total Return   | Gain/Loss      | Dividends     |
| £123,456        | +12.4%         | +£13,600       | £2,100        |
+----------------+----------------+----------------+---------------+
| Value / Return chart                                             |
|                                                                   |
+-------------------------------------------------------------------+
| Accounts                         | Allocation                    |
| IBKR            £80,000         | Stocks        65%             |
| Trading 212     £43,456         | ETFs          25%             |
|                                 | Cash          10%             |
+-------------------------------------------------------------------+
| Data-quality warnings: 2                                         |
+-------------------------------------------------------------------+
```

## Account / holdings

```text
Account: Interactive Brokers

Cash by currency: GBP £5,000 | USD $2,000

+---------+----------+----------+-----------+-----------+
| Symbol  | Quantity | Avg Cost | Value     | Gain/Loss |
+---------+----------+----------+-----------+-----------+
| AAPL    | 25.5     | £...     | £...      | +...      |
| VWCE    | 100.0    | £...     | £...      | +...      |
+---------+----------+----------+-----------+-----------+
```

## Transaction list

```text
+-------------------------------------------------------------------+
| Transactions                                  [Add transaction]   |
+-------------------------------------------------------------------+
| Date | Type | Instrument | Quantity | Amount | Fee | Source     |
+-------------------------------------------------------------------+
| ...                                                               |
+-------------------------------------------------------------------+
| Filters: account / date / type / instrument / source              |
+-------------------------------------------------------------------+
```

## Manual transaction

Use a type-specific form. Required fields change according to transaction type. Server-side validation remains authoritative.

## CSV import

```text
Upload -> Detect/Select importer -> Parse -> Validate -> Preview
                                              |
                                      duplicate/conflict flags
                                              |
                                           Confirm
                                              |
                                           Import
                                              |
                                         Results
```

The preview must show warnings/errors before any transaction is committed.

## Data-quality warning

Warnings should be visible without blocking normal navigation. A warning should identify the affected instrument/currency/date, impact, and available resolution (for example manual price/FX override).

## Design principles

- Financial values are visually distinguishable from labels but no colour is the only indicator of positive/negative state.
- Tables support keyboard navigation, sorting and filtering.
- Destructive/corrective operations require explicit confirmation.
- The UI never presents a calculated figure without indicating when required source data is missing or overridden.

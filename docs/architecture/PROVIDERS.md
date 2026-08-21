# Market Data and FX Provider Architecture

## Provider boundary

External data providers are infrastructure adapters behind domain-facing interfaces.

The domain must not depend directly on Yahoo Finance, an FX vendor, HTTP clients, SDKs, or provider-specific DTOs.

## Market data capabilities

Conceptual interface:

```text
MarketDataProvider
  getCurrentPrice(instrument, asOf)
  getHistoricalPrices(instrument, range)
  getCorporateActions(instrument, range)
  getDividends(instrument, range)
```

Capabilities may be optional. A provider declares which operations it supports.

Provider observations should carry:

- provider identifier
- instrument mapping used
- observed timestamp/date
- value and currency
- source timestamp where available
- retrieval timestamp
- provider-specific reference

## FX capabilities

Conceptual interface:

```text
FxRateProvider
  getRate(baseCurrency, quoteCurrency, asOf)
  getHistoricalRates(baseCurrency, quoteCurrency, range)
```

Rates are directional and must explicitly identify base and quote currencies.

## Provider selection

Initial production provider must be free to use, subject to:

- current availability
- API limits
- licensing/terms
- data quality
- security posture
- stability

Provider selection is a Phase 4 implementation decision unless a Phase 1/2 test fixture needs a concrete adapter.

## Caching

Provider responses should be cached according to data type and freshness requirements. Cache entries are derived data and must be invalidatable.

Do not persist provider responses as if they were authoritative transactions.

## Manual overrides

Manual market/FX observations are separate from provider observations and require provenance:

```text
Observation
  sourceType = PROVIDER | MANUAL
  sourceReference
  observedAt
  enteredAt
  enteredBy (future authenticated user)
```

A manual override must not overwrite the original provider observation.

## Missing-data behaviour

Provider failure or missing historical data does not automatically fail portfolio reporting.

Instead:

1. Use valid observations available within the defined policy.
2. Produce structured data-quality warnings.
3. Allow manual resolution where the calculation requires it.
4. Mark affected calculations as having incomplete data when unresolved.

## Security

External provider calls must have:

- Timeouts.
- Bounded retries only for transient failures.
- Rate-limit handling.
- Response validation.
- No logging of credentials/API keys.
- Strict outbound URL/provider configuration.
- Resilience against malformed or unexpectedly large responses.

Credentials, if ever required, must come from runtime secret configuration and never from source control.

# Archived Task Record: Native Historical Price Backfill & Corporate Actions Multi-Instrument Enrichment

- **Linked Feature Specification:** [01-historical-price-backfill-and-corporate-actions.md](../features/01-historical-price-backfill-and-corporate-actions.md)
- **Feature ID:** `01-historical-price-backfill-and-corporate-actions`
- **Completed Date:** 2026-09-18
- **Pull Request:** [#74](https://github.com/takakim/invest-tracker/pull/74)
- **Status:** Completed & Merged to `main`

---

## Tasks & Execution Checklist

- [x] Add observation density query in `MarketObservationRepository`
- [x] Prioritize Yahoo Finance in `CompositeMarketDataProvider.fetchHistoricalQuotes`
- [x] Implement `hasSufficientHistoricalCoverage` and `backfillPortfolioInstrumentsHistory` in `MarketDataService`
- [x] Auto-trigger backfilling in `PortfolioHistoryService.generateHistory`
- [x] Auto-trigger backfill on broker CSV imports in `CsvImportService`
- [x] Expose `POST /api/v1/portfolios/{portfolioId}/history/backfill` in `AnalyticsController` and update `openapi.yaml`
- [x] Add frontend API client `backfillPortfolioHistory` and types
- [x] Add "Sync History" button and toast feedback in `PortfolioHistoryCard.tsx`
- [x] Support multiple resulting instruments in corporate actions
- [x] Create standalone CLI script `scripts/backfill_history.py`
- [x] Verify backend tests, JaCoCo coverage (>= 90%), and frontend tests

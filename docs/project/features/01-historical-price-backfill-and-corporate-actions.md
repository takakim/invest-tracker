# Feature 01: Native Historical Price Backfill & Corporate Actions Multi-Instrument Enrichment

- **Status**: Completed & Merged (PR #74)
- **Primary Focus**: Data continuity, valuation precision, corporate reorganizations

---

## 1. Problem Statement

1. **Valuation Cliff Jumps**:
   - The historical portfolio valuation graph displayed sudden jumps or flat plateaus (e.g. late July / early August, September 2026) due to sparse daily market observations for active and liquidated holdings.
   - When instruments lacked historical daily price observations in the database, the portfolio valuation engine fell back to stale prices or acquisition cost, creating artificial cliffs.
2. **Corporate Action Resulting Instruments**:
   - In corporate reorganizations or complex splits (such as RGL or multi-ticker spinoffs), corporate action processing assumed a 1-to-1 resulting instrument mapping, preventing multiple resulting instruments from being ingested properly.

---

## 2. Architecture & Design Decisions

### High-Throughput Backfill via Yahoo Finance
- Prioritized Yahoo Finance multi-year chart data (`YahooFinanceMarketDataProvider`) over Twelve Data in `CompositeMarketDataProvider.fetchHistoricalQuotes`. This prevents hitting Twelve Data's 8 calls/minute limit when backfilling dozens of instruments.

### Automatic Coverage Detection
- Added `MarketObservationRepository.countByInstrumentIdAndObservedAtBetween` to check observation point density over any queried time window.
- Added `MarketDataService.hasSufficientHistoricalCoverage(UUID instrumentId, Instant from, Instant to, int minPoints)`.

### Dual Trigger Mechanisms
- **Query Time**: When `PortfolioHistoryService.generateHistory` runs, it scans all instruments ever traded in the portfolio (held or sold). If any instrument has `< 10` observations across the window, backfill executes automatically.
- **Ingestion Time**: In `CsvImportService`, upon completing any broker CSV import, all touched instruments are automatically backfilled from their earliest transaction date to today.

### API & Frontend Integration
- Exposed `POST /api/v1/portfolios/{portfolioId}/history/backfill?range={range}`.
- Added an interactive **"Sync History"** button with a loading spinner and toast notification on `PortfolioHistoryCard.tsx`.

---

## 3. Tasks & Implementation Checklist

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

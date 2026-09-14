import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { PortfolioListPage } from '../features/portfolios/PortfolioListPage';
import { PortfolioDetailPage } from '../features/portfolios/PortfolioDetailPage';
import { InstrumentListPage } from '../features/instruments/InstrumentListPage';
import { MarketRatesPage } from '../features/market/MarketRatesPage';
import { DividendDetailPage } from '../features/analytics/DividendDetailPage';
import { AccountDetailPage } from '../features/accounts/AccountDetailPage';
import { PerformanceDetailPage } from '../features/performance/PerformanceDetailPage';
import { HistoryDetailPage } from '../features/analytics/HistoryDetailPage';
import { AllocationDetailPage } from '../features/analytics/AllocationDetailPage';
import { RebalancingDetailPage } from '../features/rebalancing/RebalancingDetailPage';
import { CashFlowDetailPage } from '../features/analytics/CashFlowDetailPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<DashboardPage />} />
      <Route path="/portfolios" element={<PortfolioListPage />} />
      <Route path="/portfolios/:id" element={<PortfolioDetailPage />} />
      <Route path="/portfolios/:id/performance" element={<PerformanceDetailPage />} />
      <Route path="/portfolios/:id/allocation" element={<AllocationDetailPage />} />
      <Route path="/portfolios/:id/rebalancing" element={<RebalancingDetailPage />} />
      <Route path="/portfolios/:id/history" element={<HistoryDetailPage />} />
      <Route path="/portfolios/:id/dividends" element={<DividendDetailPage />} />
      <Route path="/portfolios/:id/cash-flows" element={<CashFlowDetailPage />} />
      <Route path="/portfolios/:id/accounts/:accountId" element={<AccountDetailPage />} />
      <Route path="/instruments" element={<InstrumentListPage />} />
      <Route path="/market" element={<MarketRatesPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

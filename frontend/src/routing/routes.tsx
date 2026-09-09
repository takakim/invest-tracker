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

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<DashboardPage />} />
      <Route path="/portfolios" element={<PortfolioListPage />} />
      <Route path="/portfolios/:id" element={<PortfolioDetailPage />} />
      <Route path="/portfolios/:id/performance" element={<PerformanceDetailPage />} />
      <Route path="/portfolios/:id/dividends" element={<DividendDetailPage />} />
      <Route path="/portfolios/:id/accounts/:accountId" element={<AccountDetailPage />} />
      <Route path="/instruments" element={<InstrumentListPage />} />
      <Route path="/market" element={<MarketRatesPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeProvider } from '@mui/material';

import { theme } from '../theme';
import { StickyHeroBar } from '../features/portfolios/StickyHeroBar';

describe('StickyHeroBar Capstone UX Suite', () => {
  it('renders StickyHeroBar with portfolio identity, metrics, and jump buttons', () => {
    const jumpSpy = vi.fn();
    const addAccountSpy = vi.fn();
    const exportSpy = vi.fn();

    render(
      <ThemeProvider theme={theme}>
        <StickyHeroBar
          portfolioName="Retirement Growth"
          currency="GBP"
          totalMarketValue={125000}
          unrealizedReturnPercentage={15.4}
          unrealizedGainLoss={16500}
          visible={true}
          activeSectionKey="performance"
          onJumpToSection={jumpSpy}
          onAddAccount={addAccountSpy}
          onExportReport={exportSpy}
        />
      </ThemeProvider>
    );

    const bar = screen.getByTestId('sticky-hero-bar');
    expect(bar).toBeInTheDocument();

    // Verify portfolio name & metrics
    expect(screen.getByText('Retirement Growth')).toBeInTheDocument();
    expect(screen.getByText(/125,000/)).toBeInTheDocument();
    expect(screen.getByText(/\+15.4%/)).toBeInTheDocument();

    // Verify jump pills
    expect(screen.getByRole('button', { name: 'Valuation' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Allocation' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Performance' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'History' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Dividends' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rebalancing' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Accounts' })).toBeInTheDocument();

    // Click jump pill
    fireEvent.click(screen.getByRole('button', { name: 'History' }));
    expect(jumpSpy).toHaveBeenCalledWith('history');

    // Click quick actions
    fireEvent.click(screen.getByRole('button', { name: /Add Account/i }));
    expect(addAccountSpy).toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /Export/i }));
    expect(exportSpy).toHaveBeenCalled();
  });

  it('applies hidden styles when visible is false', () => {
    render(
      <ThemeProvider theme={theme}>
        <StickyHeroBar
          portfolioName="Retirement Growth"
          currency="GBP"
          visible={false}
          onJumpToSection={vi.fn()}
          onAddAccount={vi.fn()}
          onExportReport={vi.fn()}
        />
      </ThemeProvider>
    );

    const bar = screen.getByTestId('sticky-hero-bar');
    expect(bar).toHaveStyle('opacity: 0');
  });
});

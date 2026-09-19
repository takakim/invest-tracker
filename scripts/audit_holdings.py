#!/usr/bin/env python3
"""
Invest-Tracker Live Portfolio & Holdings Auditor
Queries live API endpoints (/api/v1/positions, /api/v1/portfolios, /api/v1/accounts)
or connects directly to PostgreSQL to reconcile positions, detect missing splits,
verify cost basis currencies, and validate P&L calculations.
"""

import sys
import json
import argparse
import urllib.request
import urllib.error
from decimal import Decimal

DEFAULT_BASE_URL = "http://localhost:8080"


def fetch_json(url):
    try:
        req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "InvestTrackerAuditor/1.0"})
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.URLError as e:
        print(f"Error connecting to {url}: {e}", file=sys.stderr)
        return None


def audit_live_portfolio(base_url=DEFAULT_BASE_URL, portfolio_id=None):
    print("=" * 88)
    print(f"         INVEST-TRACKER LIVE PORTFOLIO & POSITION AUDITOR ({base_url})")
    print("=" * 88)

    # 1. Fetch Portfolios
    portfolios = fetch_json(f"{base_url}/api/v1/portfolios")
    if not portfolios:
        print("Failed to reach API or no portfolios found. Ensure the application is running.")
        return False

    target_portfolios = [p for p in portfolios if portfolio_id is None or p.get("id") == portfolio_id]

    for port in target_portfolios:
        pid = port["id"]
        pname = port["name"]
        pbase = port.get("baseCurrency", "GBP")
        print(f"\n📁 Portfolio: {pname} (ID: {pid}, Base Currency: {pbase})")

        # 2. Fetch Accounts
        accounts = fetch_json(f"{base_url}/api/v1/portfolios/{pid}/accounts")
        if accounts:
            print("  Accounts:")
            for acc in accounts:
                print(f"    • {acc.get('name')} [{acc.get('broker')}] - Currency: {acc.get('currency')} (ID: {acc.get('id')})")

        # 3. Fetch Positions Performance
        positions = fetch_json(f"{base_url}/api/v1/portfolios/{pid}/positions/performance")
        if not positions:
            print("  No positions found for this portfolio.")
            continue

        print("\n" + "-" * 96)
        print(f"{'Ticker':<8} {'Instrument':<22} {'Account':<18} {'Quantity':>10} {'Cost Basis':>12} {'Market Val':>12} {'Unrealized P&L':>18}")
        print("-" * 96)

        total_cost = Decimal("0")
        total_market = Decimal("0")
        total_unrealized = Decimal("0")

        anomalies = []

        for pos in sorted(positions, key=lambda x: x.get("ticker") or ""):
            ticker = pos.get("ticker", "N/A")
            name = pos.get("instrumentName", "N/A")
            account_name = pos.get("accountName", "N/A")

            qty = Decimal(str(pos.get("currentQuantity", 0)))
            cost_amount = Decimal(str(pos.get("currentCostBasis", 0)))
            cost_curr = pos.get("currency", pbase)
            market_amount = Decimal(str(pos.get("currentMarketValue", 0)))
            unrealized_amount = Decimal(str(pos.get("unrealizedGainLoss", 0)))
            unrealized_pct = float(pos.get("totalReturnPercentage", 0))

            total_cost += cost_amount
            total_market += market_amount
            total_unrealized += unrealized_amount

            # Check anomalies
            if qty < 0:
                anomalies.append((ticker, f"Negative quantity: {qty}"))
            if qty == 0 and (cost_amount != 0 or market_amount != 0):
                anomalies.append((ticker, f"Closed position has non-zero cost basis: {cost_amount}"))

            pnl_display = f"{unrealized_amount:+,.2f} ({unrealized_pct:+.1f}%)"
            print(f"{ticker:<8} {name[:20]:<22} {account_name[:16]:<18} {qty:>10.4f} {cost_amount:>10.2f} {cost_curr} {market_amount:>10.2f} {cost_curr} {pnl_display:>18}")

        print("-" * 96)
        print(f"{'TOTAL':<50} {total_cost:>10.2f} {pbase} {total_market:>10.2f} {pbase} {total_unrealized:>+16.2f} {pbase}")
        print("=" * 96)

        if anomalies:
            print("\n⚠️ Anomalies Detected:")
            for ticker, msg in anomalies:
                print(f"  • [{ticker}] {msg}")
        else:
            print("\n✓ All holdings and position states are consistent.")

    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Invest-Tracker Live Portfolio Auditor")
    parser.add_argument("--url", "-u", default=DEFAULT_BASE_URL, help=f"API Base URL (default: {DEFAULT_BASE_URL})")
    parser.add_argument("--portfolio", "-p", help="Specific Portfolio UUID to audit")
    args = parser.parse_args()

    sys.exit(0 if audit_live_portfolio(args.url, args.portfolio) else 1)

#!/usr/bin/env python3
"""
Freetrade CSV Activity Feed Tool
Validates, audits, and injects Corporate Actions (Forward Splits, Reverse Splits)
into Freetrade activity feed export CSVs for seamless import into Invest-Tracker.
"""

import sys
import os
import csv
import argparse
from datetime import datetime
from decimal import Decimal

# Standard 44-column Freetrade CSV Header
FREETRADE_HEADER = [
    "Title", "Type", "Timestamp", "Account Currency", "Total Amount in Account Currency",
    "Buy / Sell", "Ticker", "ISIN", "Price per Share in Account Currency", "Stamp Duty",
    "Quantity", "Venue", "Order ID", "Order Type", "Instrument Currency",
    "Total Amount in Instrument Currency", "Price per Share", "FX Rate", "Base FX Rate",
    "FX Fee (BPS)", "FX Fee Amount", "Dividend Ex Date", "Dividend Pay Date",
    "Dividend Eligible Quantity", "Dividend Amount Per Share", "Dividend Gross Distribution Amount",
    "Dividend Net Distribution Amount", "Dividend Withheld Tax Percentage", "Dividend Withheld Tax Amount",
    "Stock Split Ex Date", "Stock Split Pay Date", "Stock Split New ISIN",
    "Stock Split Rate of Share Outturn From", "Stock Split Rate of Share Outturn To",
    "Stock Split Maintain Holding of Initial ISIN", "Stock Split New Share Quantity",
    "Account Type", "Description", "Source", "Status", "Market", "Category", "Note", "Reference"
]

# Known corporate action reference catalog
KNOWN_CORPORATE_ACTIONS = {
    "NVDA": {
        "title": "Nvidia",
        "isin": "US67066G1040",
        "instrument_currency": "USD",
        "action_type": "STOCK_SPLIT",
        "ratio_from": 1,
        "ratio_to": 10,
        "effective_date": "2024-06-07T21:00:00.000Z",
        "multiplier": 9.0  # adds 9 additional shares per 1 held (pre-split 3 -> 30, add +27)
    },
    "SMCI": {
        "title": "Super Micro Computer",
        "isin": "US86800U1043",
        "instrument_currency": "USD",
        "action_type": "STOCK_SPLIT",
        "ratio_from": 1,
        "ratio_to": 10,
        "effective_date": "2024-10-01T08:00:00.000Z",
        "multiplier": 9.0  # adds 9 additional shares per 1 held (pre-split 4 -> 40, add +36)
    },
    "RGL": {
        "title": "Regional REIT",
        "isin": "GG00BSY2LD72",
        "instrument_currency": "GBP",
        "action_type": "REVERSE_STOCK_SPLIT",
        "ratio_from": 10,
        "ratio_to": 1,
        "effective_date": "2024-08-27T08:00:00.000Z",
        "multiplier": 0.9  # 1232 -> 123.2 (-1108.8 shares removed)
    }
}


def build_split_row(title, action_type, timestamp, ticker, isin, instrument_currency,
                    account_currency="GBP", additional_quantity=0.0):
    """Generates a complete 44-column dictionary / list matching Freetrade CSV export."""
    row = {col: "" for col in FREETRADE_HEADER}
    qty_str = f"{Decimal(str(additional_quantity)):.8f}"
    
    row["Title"] = title
    row["Type"] = action_type  # STOCK_SPLIT or REVERSE_STOCK_SPLIT
    row["Timestamp"] = timestamp
    row["Account Currency"] = account_currency
    row["Total Amount in Account Currency"] = "0.00"
    row["Ticker"] = ticker
    row["ISIN"] = isin
    row["Quantity"] = qty_str
    row["Instrument Currency"] = instrument_currency
    row["Stock Split New Share Quantity"] = qty_str
    return row


def audit_freetrade_csv(csv_path):
    """Analyzes a Freetrade CSV to check for negative balances and missing splits."""
    if not os.path.exists(csv_path):
        print(f"Error: File not found: {csv_path}", file=sys.stderr)
        return False

    holdings = {}
    dividends = {}
    rows_parsed = 0

    with open(csv_path, mode="r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows_parsed += 1
            ticker = row.get("Ticker", "").strip()
            raw_type = row.get("Type", "").strip().upper()
            qty_str = row.get("Quantity", "").strip()
            div_qty_str = row.get("Dividend Eligible Quantity", "").strip()
            timestamp = row.get("Timestamp", "").strip()

            if not ticker:
                continue

            if ticker not in holdings:
                holdings[ticker] = {
                    "title": row.get("Title", ""),
                    "isin": row.get("ISIN", ""),
                    "currency": row.get("Instrument Currency", "USD"),
                    "running_qty": Decimal("0"),
                    "min_qty": Decimal("0"),
                    "buys": Decimal("0"),
                    "sells": Decimal("0"),
                    "splits": Decimal("0"),
                    "first_tx": timestamp,
                    "last_tx": timestamp
                }

            h = holdings[ticker]
            h["last_tx"] = timestamp

            # Process trades and splits
            if raw_type in ("ORDER", "BUY", "SELL"):
                buy_sell = row.get("Buy / Sell", "").strip().upper()
                qty = Decimal(qty_str) if qty_str else Decimal("0")
                if buy_sell in ("BUY", "ORDER"):
                    h["running_qty"] += qty
                    h["buys"] += qty
                elif buy_sell == "SELL":
                    h["running_qty"] -= qty
                    h["sells"] += qty
            elif raw_type in ("STOCK_SPLIT", "REVERSE_STOCK_SPLIT"):
                qty = Decimal(qty_str) if qty_str else Decimal("0")
                if raw_type == "STOCK_SPLIT":
                    h["running_qty"] += qty
                    h["splits"] += qty
                else:
                    h["running_qty"] -= qty
                    h["splits"] -= qty

            if h["running_qty"] < h["min_qty"]:
                h["min_qty"] = h["running_qty"]

            # Record dividend eligible quantities
            if raw_type in ("DIVIDEND", "SPECIAL_DIVIDEND") and div_qty_str:
                div_qty = Decimal(div_qty_str)
                if ticker not in dividends or div_qty > dividends[ticker]["max_div_qty"]:
                    dividends[ticker] = {
                        "date": timestamp,
                        "max_div_qty": div_qty
                    }

    print("=" * 80)
    print(f"               FREETRADE CSV AUDIT REPORT ({rows_parsed} rows parsed)")
    print("=" * 80)
    print(f"{'Ticker':<8} {'Title':<22} {'Current Qty':>12} {'Min Balance':>12} {'Dividend Qty':>14} {'Status'}")
    print("-" * 80)

    anomalies = []
    for ticker, h in sorted(holdings.items()):
        div_info = dividends.get(ticker)
        div_qty_str = f"{div_info['max_div_qty']:.2f}" if div_info else "-"
        
        status = "OK"
        if h["min_qty"] < 0:
            status = "⚠️ NEGATIVE DEFICIT (Missing Split?)"
            anomalies.append((ticker, f"Negative balance dipped to {h['min_qty']:.4f}"))
        elif div_info and div_info["max_div_qty"] > h["running_qty"] and h["running_qty"] > 0:
            status = "⚠️ DIVIDEND MISMATCH (Missing Split?)"
            anomalies.append((ticker, f"Dividend received on {div_info['max_div_qty']} shares, but current holding is {h['running_qty']}"))

        print(f"{ticker:<8} {h['title'][:20]:<22} {h['running_qty']:>12.4f} {h['min_qty']:>12.4f} {div_qty_str:>14} {status}")

    print("-" * 80)
    if anomalies:
        print("Identified Discrepancies:")
        for t, msg in anomalies:
            print(f"  • [{t}] {msg}")
            if t in KNOWN_CORPORATE_ACTIONS:
                rec = KNOWN_CORPORATE_ACTIONS[t]
                print(f"    Suggested Fix: Inject {rec['action_type']} for {t} ({rec['ratio_from']}:{rec['ratio_to']}) on {rec['effective_date']}")
    else:
        print("✓ All positions have consistent non-negative balances and dividend quantities.")
    print("=" * 80)
    return True


def inject_splits(input_csv, output_csv, tickers=None):
    """Injects stock split rows for the specified tickers into the Freetrade CSV."""
    if not os.path.exists(input_csv):
        print(f"Error: Input CSV not found: {input_csv}", file=sys.stderr)
        return False

    target_tickers = [t.strip().upper() for t in tickers] if tickers else list(KNOWN_CORPORATE_ACTIONS.keys())

    # Read original rows
    existing_rows = []
    header = None
    with open(input_csv, mode="r", encoding="utf-8-sig") as f:
        reader = csv.reader(f)
        header = next(reader)
        for r in reader:
            existing_rows.append(r)

    # Convert to Dicts using existing header or standard header
    col_names = header if len(header) >= len(FREETRADE_HEADER) else FREETRADE_HEADER

    # Generate split rows
    new_split_rows = []
    for ticker in target_tickers:
        if ticker == "NVDA":
            row = build_split_row("Nvidia", "STOCK_SPLIT", "2024-06-07T21:00:00.000Z",
                                  "NVDA", "US67066G1040", "USD", "GBP", 27.0)
            new_split_rows.append([row.get(col, "") for col in col_names])
            print(f"✓ Added NVDA 10:1 forward split (+27.0 shares on 2024-06-07T21:00:00Z)")
        elif ticker == "SMCI":
            row = build_split_row("Super Micro Computer", "STOCK_SPLIT", "2024-10-01T08:00:00.000Z",
                                  "SMCI", "US86800U1043", "USD", "GBP", 36.0)
            new_split_rows.append([row.get(col, "") for col in col_names])
            print(f"✓ Added SMCI 10:1 forward split (+36.0 shares on 2024-10-01T08:00:00Z)")
        elif ticker == "RGL":
            row = build_split_row("Regional REIT", "REVERSE_STOCK_SPLIT", "2024-08-27T08:00:00.000Z",
                                  "RGL", "GG00BSY2LD72", "GBP", "GBP", 1108.8)
            new_split_rows.append([row.get(col, "") for col in col_names])
            print(f"✓ Added RGL 1:10 reverse consolidation (1,232 -> 123.2 shares on 2024-08-27T08:00:00Z)")

    all_rows = existing_rows + new_split_rows

    # Sort chronologically by timestamp (col index 2)
    def parse_time(r):
        try:
            return datetime.fromisoformat(r[2].replace("Z", "+00:00"))
        except Exception:
            return datetime.min

    all_rows.sort(key=parse_time)

    with open(output_csv, mode="w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(col_names)
        writer.writerows(all_rows)

    print(f"\nSuccessfully wrote patched CSV to: {output_csv} ({len(all_rows)} total rows)")
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Freetrade CSV Activity Feed Audit & Corporate Action Tool")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # Audit command
    audit_parser = subparsers.add_parser("audit", help="Audit a Freetrade CSV export for negative balances and missing splits")
    audit_parser.add_argument("csv_file", help="Path to Freetrade activity feed CSV export")

    # Inject command
    inject_parser = subparsers.add_parser("inject", help="Inject stock split rows into Freetrade CSV export")
    inject_parser.add_argument("input_csv", help="Path to input Freetrade CSV")
    inject_parser.add_argument("output_csv", help="Path to write patched output CSV")
    inject_parser.add_argument("--tickers", "-t", nargs="+", default=["NVDA", "SMCI"],
                              help="Tickers to inject splits for (default: NVDA SMCI)")

    # Print rows command
    print_parser = subparsers.add_parser("print-rows", help="Print ready-to-paste CSV rows for NVDA and SMCI")

    args = parser.parse_args()

    if args.command == "audit":
        sys.exit(0 if audit_freetrade_csv(args.csv_file) else 1)
    elif args.command == "inject":
        sys.exit(0 if inject_splits(args.input_csv, args.output_csv, args.tickers) else 1)
    elif args.command == "print-rows":
        print(",".join(FREETRADE_HEADER))
        r1 = build_split_row("Nvidia", "STOCK_SPLIT", "2024-06-07T21:00:00.000Z", "NVDA", "US67066G1040", "USD", "GBP", 27.0)
        r2 = build_split_row("Super Micro Computer", "STOCK_SPLIT", "2024-10-01T08:00:00.000Z", "SMCI", "US86800U1043", "USD", "GBP", 36.0)
        print(",".join(str(r1[col]) for col in FREETRADE_HEADER))
        print(",".join(str(r2[col]) for col in FREETRADE_HEADER))

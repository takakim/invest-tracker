#!/usr/bin/env python3
"""
audit_csv_ledger.py — CLI tool to audit transaction ledger cash balances,
fees, taxes, and net amounts across broker CSV exports.
"""

import argparse
import csv
import os
import re
import sys
from datetime import datetime


def parse_decimal(val):
    if not val:
        return 0.0
    try:
        clean = str(val).replace(",", "").replace("£", "").replace("$", "").replace("€", "").strip()
        if clean.startswith("(") and clean.endswith(")"):
            return -float(clean[1:-1])
        return float(clean)
    except ValueError:
        return 0.0


def extract_tbill_maturity(title):
    if not title:
        return None
    m = re.match(r".*T-Bill\s+(\d{2})/(\d{2})/(\d{2,4}).*", title, re.IGNORECASE)
    if m:
        d, mth, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
        if y < 100:
            y += 2000
        return datetime(y, mth, d)
    return None


def audit_freetrade(rows, target_cash=None, verbose=False):
    deposits = 0.0
    withdrawals = 0.0
    interest = 0.0
    gross_dividends = 0.0
    net_dividends = 0.0
    dividend_taxes = 0.0
    
    buys_total_account = 0.0
    sells_total_account = 0.0
    buy_fees = 0.0
    sell_fees = 0.0
    tbill_redemptions = 0.0
    
    anomalies = []
    
    for idx, r in enumerate(rows, start=2):
        if not r:
            continue
        title = r[0].strip() if len(r) > 0 else ""
        raw_type = r[1].strip() if len(r) > 1 else ""
        curr = r[3].strip() if len(r) > 3 else "GBP"
        total_account = parse_decimal(r[4]) if len(r) > 4 else 0.0
        buy_sell = r[5].strip().upper() if len(r) > 5 else ""
        ticker = r[6].strip() if len(r) > 6 else ""
        stamp_duty = parse_decimal(r[9]) if len(r) > 9 else 0.0
        quantity = parse_decimal(r[10]) if len(r) > 10 else 0.0
        fx_fee = parse_decimal(r[20]) if len(r) > 20 else 0.0
        div_tax = parse_decimal(r[28]) if len(r) > 28 else 0.0
        fx_rate = parse_decimal(r[17]) if len(r) > 17 else 1.0
        fee = stamp_duty + fx_fee

        if raw_type == "TOP_UP":
            deposits += total_account
        elif raw_type == "WITHDRAWAL":
            withdrawals += total_account
        elif raw_type == "INTEREST_FROM_CASH":
            interest += total_account
        elif raw_type in ("DIVIDEND", "SPECIAL_DIVIDEND"):
            net_dividends += total_account
            tax_in_gbp = div_tax * fx_rate if fx_rate > 0 else div_tax
            dividend_taxes += tax_in_gbp
            gross_dividends += (total_account + tax_in_gbp)
        elif raw_type == "ORDER":
            if buy_sell == "BUY":
                buys_total_account += total_account
                buy_fees += fee
                # Auto-check T-Bill maturity
                mat = extract_tbill_maturity(title)
                if mat and quantity > 0:
                    tbill_redemptions += quantity
            elif buy_sell == "SELL":
                sells_total_account += total_account
                sell_fees += fee

    # Correct Ledger Cash:
    # In Freetrade, total_account is ALREADY NET of all fees and taxes:
    # Outflows = Buys (all inclusive) + Withdrawals
    # Inflows = Deposits + Interest + Net Dividends + Net Sells + T-Bill Redemptions
    true_ledger_cash = deposits + interest + net_dividends + sells_total_account + tbill_redemptions - buys_total_account - withdrawals
    
    # Bugged Cash (if fees/taxes are double-deducted):
    double_deduction_delta = buy_fees + sell_fees + dividend_taxes
    bugged_cash = true_ledger_cash - double_deduction_delta

    print("=" * 80)
    print("                 FREETRADE TRANSACTION LEDGER AUDIT REPORT")
    print("=" * 80)
    print(f"Total CSV Rows Analyzed:           {len(rows)}")
    print("-" * 80)
    print(f"1. Cash Deposits (TOP_UP):        +£{deposits:>12.2f}")
    print(f"2. Cash Interest (INTEREST):      +£{interest:>12.2f}")
    print(f"3. Net Dividends Received:        +£{net_dividends:>12.2f}  (Gross: £{gross_dividends:.2f}, Tax: £{dividend_taxes:.2f})")
    print(f"4. Stock Sales Net Proceeds:      +£{sells_total_account:>12.2f}  (Embedded FX Fees: £{sell_fees:.2f})")
    print(f"5. T-Bill Maturity Redemptions:   +£{tbill_redemptions:>12.2f}")
    print(f"6. Stock Purchases Net Outlay:    -£{buys_total_account:>12.2f}  (Embedded Stamp/FX Fees: £{buy_fees:.2f})")
    print(f"7. Cash Withdrawals:              -£{withdrawals:>12.2f}")
    print("-" * 80)
    print(f"✅ TRUE LEDGER CASH BALANCE:        £{true_ledger_cash:>12.2f}")
    print(f"⚠️  Double-Deduction Delta:         £{double_deduction_delta:>12.2f} (Occurs if fees/taxes subtracted again)")
    print(f"❌ BUGGED (DOUBLE-DEDUCTED) CASH:   £{bugged_cash:>12.2f}")
    print("-" * 80)

    if target_cash is not None:
        target = float(target_cash)
        diff = target - true_ledger_cash
        print(f"Target Live App Cash:              £{target:>12.2f}")
        print(f"Reconciliation Variance (Diff):    £{diff:>12.2f}")
        if abs(diff) < 0.01:
            print("🎉 STATUS: PERFECT RECONCILIATION (0.00 difference)")
        else:
            print(f"ℹ️  STATUS: £{abs(diff):.2f} {'under' if diff > 0 else 'over'} target live balance.")
            print("   Possible reasons: un-exported promotion/referral bonuses, interest timing, or balancing deposit.")
    print("=" * 80)


def main():
    parser = argparse.ArgumentParser(
        description="Audit transaction ledger cash balances and fee/tax handling in broker CSV exports."
    )
    parser.add_argument("csv_file", help="Path to broker CSV file (e.g. activity-feed-export.csv)")
    parser.add_argument(
        "--target-cash",
        type=float,
        default=None,
        help="Optional live broker app cash balance to reconcile against (e.g. --target-cash 3637.84)"
    )
    parser.add_argument("--verbose", action="store_true", help="Print detailed row information")

    args = parser.parse_args()

    if not os.path.isfile(args.csv_file):
        print(f"Error: File not found: {args.csv_file}", file=sys.stderr)
        sys.exit(1)

    with open(args.csv_file, "r", encoding="utf-8-sig") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        if not header:
            print("Error: Empty CSV file", file=sys.stderr)
            sys.exit(1)
        rows = [r for r in reader if r]

    audit_freetrade(rows, target_cash=args.target_cash, verbose=args.verbose)


if __name__ == "__main__":
    main()

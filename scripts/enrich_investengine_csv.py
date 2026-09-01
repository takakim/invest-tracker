#!/usr/bin/env python3
"""
enrich_investengine_csv.py — CLI tool to enrich InvestEngine transaction CSV exports
with standard ticker symbols, native currency, and London Stock Exchange (LSE) metadata.
"""

import argparse
import csv
import os
import sys

# Master ISIN to Ticker / Metadata mapping
ISIN_DIRECTORY = {
    "IE000716YHJ7": {"ticker": "FWRG", "name": "Invesco FTSE All-World UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE0032077012": {"ticker": "EQQQ", "name": "Invesco EQQQ Nasdaq-100 UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00B3YCGJ38": {"ticker": "SPXP", "name": "Invesco S&P 500 UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00BHZRQZ17": {"ticker": "FLXI", "name": "Franklin FTSE India UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00BK5BQT80": {"ticker": "VWRP", "name": "Vanguard FTSE All-World UCITS ETF (Acc)", "currency": "GBP", "exchange": "LSE"},
    "IE00BK5BQV03": {"ticker": "VEVE", "name": "Vanguard FTSE Developed World UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00BK5BR733": {"ticker": "VFEM", "name": "Vanguard FTSE Emerging Markets UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00BM8R0J59": {"ticker": "QYLD", "name": "Global X Nasdaq 100 Covered Call UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00B3XXRP09": {"ticker": "VUSA", "name": "Vanguard S&P 500 UCITS ETF (Dist)", "currency": "GBP", "exchange": "LSE"},
    "IE00BFMXXD54": {"ticker": "VUAG", "name": "Vanguard S&P 500 UCITS ETF (Acc)", "currency": "GBP", "exchange": "LSE"},
    "IE00B3RBWM25": {"ticker": "VWRL", "name": "Vanguard FTSE All-World UCITS ETF (Dist)", "currency": "GBP", "exchange": "LSE"},
    "GB00B59G4H30": {"ticker": "V80A", "name": "Vanguard LifeStrategy 80% Equity", "currency": "GBP", "exchange": "LSE"},
    "IE00B4L5Y983": {"ticker": "SWDA", "name": "iShares Core MSCI World UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00B5BMR087": {"ticker": "CSPX", "name": "iShares Core S&P 500 UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00BLPK3577": {"ticker": "CYSE", "name": "WisdomTree Cybersecurity UCITS ETF", "currency": "GBX", "exchange": "LSE"},
    "IE00BDVPNG13": {"ticker": "INTL", "name": "WisdomTree Artificial Intelligence UCITS ETF", "currency": "GBX", "exchange": "LSE"},
    "IE000940RNE6": {"ticker": "BKCN", "name": "Wisdomtree Blockchain UCITS ETF", "currency": "GBX", "exchange": "LSE"},
    "IE00BJGWQN72": {"ticker": "KLWD", "name": "WisdomTree Cloud Computing UCITS ETF", "currency": "GBX", "exchange": "LSE"},
    "IE000W8WMSL2": {"ticker": "QWTM", "name": "WisdomTree Quantum Computing UCITS ETF", "currency": "GBX", "exchange": "LSE"},
    "IE000O8KMPM1": {"ticker": "WBIO", "name": "Wisdomtree Biorevolution UCITS ETF", "currency": "GBX", "exchange": "LSE"},
    "IE000MO2MB07": {"ticker": "WTNR", "name": "WisdomTree New Economy Real Estate UCITS ETF", "currency": "GBX", "exchange": "LSE"},
    "IE000YDZG487": {"ticker": "HNSS", "name": "HSBC Nasdaq Global Semiconductor UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00BMC38736": {"ticker": "SMGB", "name": "VanEck Semiconductor UCITS ETF", "currency": "GBP", "exchange": "LSE"},
    "IE00B579F325": {"ticker": "SGLD", "name": "iShares Physical Gold ETC", "currency": "GBP", "exchange": "LSE"},
    "IE00BM67HX07": {"ticker": "XDPG", "name": "Xtrackers S&P 500 Equal Weight UCITS ETF", "currency": "GBP", "exchange": "LSE"},
}


def parse_security_and_isin(sec_str):
    if not sec_str:
        return "", ""
    if "/ ISIN" in sec_str:
        parts = sec_str.split("/ ISIN")
        name = parts[0].strip()
        isin = parts[1].replace(":", "").strip()
        return name, isin
    return sec_str.strip(), ""


def enrich_file(input_path, output_path=None):
    with open(input_path, "r", encoding="utf-8-sig") as f:
        lines = f.readlines()

    if not lines:
        print(f"Error: Empty file: {input_path}", file=sys.stderr)
        return

    # Find header line
    header_idx = -1
    for i, line in enumerate(lines[:5]):
        if "security / isin" in line.lower() or ("security" in line.lower() and "total trade value" in line.lower()):
            header_idx = i
            break

    if header_idx == -1:
        print(f"Error: Unrecognized InvestEngine statement header in: {input_path}", file=sys.stderr)
        return

    out_rows = []
    # Preserve pre-header metadata banner if present
    for i in range(header_idx):
        out_rows.append([lines[i].strip()])

    header_cols = [c.strip() for c in lines[header_idx].split(",")]
    new_header = [
        "Security", "Ticker", "ISIN", "Currency", "Exchange",
        "Transaction Type", "Quantity", "Share Price", "Total Trade Value",
        "Trade Date/Time", "Settlement Date", "Broker"
    ]
    out_rows.append(new_header)

    enriched_count = 0
    reader = csv.reader(lines[header_idx + 1:])
    for r in reader:
        if not r or not any(r):
            continue
        sec_raw = r[0].strip() if len(r) > 0 else ""
        raw_type = r[1].strip() if len(r) > 1 else ""
        qty = r[2].strip() if len(r) > 2 else ""
        price = r[3].strip() if len(r) > 3 else ""
        total = r[4].strip() if len(r) > 4 else ""
        dt = r[5].strip() if len(r) > 5 else ""
        settle = r[6].strip() if len(r) > 6 else ""
        broker = r[7].strip() if len(r) > 7 else ""

        name, isin = parse_security_and_isin(sec_raw)
        meta = ISIN_DIRECTORY.get(isin, {})
        ticker = meta.get("ticker", "")
        currency = meta.get("currency", "GBP")
        exchange = meta.get("exchange", "LSE" if isin.startswith(("GB", "IE", "LU", "GG")) else "")
        display_name = meta.get("name", name)

        out_rows.append([
            display_name, ticker, isin, currency, exchange,
            raw_type, qty, price, total, dt, settle, broker
        ])
        if ticker:
            enriched_count += 1

    target = output_path or input_path
    with open(target, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerows(out_rows)

    print(f"✅ Enriched {enriched_count} transactions with Ticker, Currency & Exchange in: {target}")


def main():
    parser = argparse.ArgumentParser(
        description="Enrich InvestEngine CSV statement with Ticker, ISIN, Currency, and LSE Exchange metadata."
    )
    parser.add_argument("input_csv", help="Path to input InvestEngine statement CSV")
    parser.add_argument("-o", "--output", help="Optional output CSV path (defaults to overwriting input or printing)", default=None)
    args = parser.parse_args()

    if not os.path.isfile(args.input_csv):
        print(f"Error: File not found: {args.input_csv}", file=sys.stderr)
        sys.exit(1)

    enrich_file(args.input_csv, args.output)


if __name__ == "__main__":
    main()

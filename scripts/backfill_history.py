#!/usr/bin/env python3
"""
scripts/backfill_history.py

Backfills 2-year (or custom range) historical daily market observations for
portfolio instruments from Yahoo Finance into the Invest-Tracker PostgreSQL database.

Usage:
  # Backfill all active portfolio instruments with 2y daily bars
  python3 scripts/backfill_history.py

  # Backfill specific tickers with 5y daily bars
  python3 scripts/backfill_history.py --range 5y --tickers NVDA AAPL RR. VUAG
"""

import argparse
import json
import re
import subprocess
import sys
import time
import urllib.request
import datetime

LSE_PATTERN = re.compile(r"LON|LSE", re.IGNORECASE)

def resolve_symbol(ticker, isin, currency, exchange, name):
    if not ticker:
        return None
    clean = ticker.strip().upper()
    if clean.endswith("."):
        clean = clean[:-1]
    if not clean:
        return None
    if clean in ("BRK.B", "BRK/B"):
        return "BRK-B"
    if "." in clean:
        return clean
    
    # Ignore UK Treasury bills as Yahoo has no chart data for them
    is_gb_isin = isin and isin.upper().startswith("GB")
    upper_name = (name or "").upper()
    if is_gb_isin and ("T-BILL" in clean or "T-BILL" in upper_name or "TREASURY" in upper_name):
        return None
    
    # Check LSE
    is_gbp = currency in ("GBP", "GBX", "GBp")
    is_lse_ex = exchange and bool(LSE_PATTERN.search(exchange))
    
    if is_lse_ex or is_gb_isin or is_gbp:
        return clean + ".L"
    
    return clean

def main():
    parser = argparse.ArgumentParser(description="Backfill historical daily prices from Yahoo Finance into PostgreSQL")
    parser.add_argument("--range", default="2y", help="Historical range (e.g. 1y, 2y, 5y, max). Default: 2y")
    parser.add_argument("--tickers", nargs="+", help="Specific tickers to backfill (default: all active portfolio holdings)")
    args = parser.parse_args()

    print(f"Querying active portfolio instruments from PostgreSQL (range: {args.range})...")
    sql = """
    SELECT DISTINCT i.id, i.ticker, i.name, i.currency, COALESCE(i.isin, ''), COALESCE(i.exchange, '')
    FROM positions p
    JOIN instruments i ON p.instrument_id = i.id
    WHERE p.status = 'ACTIVE'
    ORDER BY i.ticker;
    """
    cmd = ['docker', 'exec', 'invest-tracker-postgres-1', 'psql', '-U', 'invest_tracker', '-d', 'invest_tracker', '-t', '-A', '-c', sql]
    try:
        raw = subprocess.check_output(cmd).decode().strip()
    except Exception as e:
        print(f"Failed to query instruments: {e}", file=sys.stderr)
        sys.exit(1)
    
    lines = [l for l in raw.split("\n") if l.strip()]
    
    target_tickers = [t.strip().upper() for t in args.tickers] if args.tickers else None
    
    filtered_lines = []
    for line in lines:
        parts = line.split("|")
        if len(parts) >= 6:
            t = parts[1].strip().upper()
            if not target_tickers or t in target_tickers or (t.endswith(".") and t[:-1] in target_tickers):
                filtered_lines.append(parts)
                
    print(f"Found {len(filtered_lines)} target active instruments.")
    
    total_inserted = 0
    
    for parts in filtered_lines:
        inst_id, ticker, name, currency, isin, exchange = parts[:6]
        symbol = resolve_symbol(ticker, isin, currency, exchange, name)
        if not symbol:
            print(f"  Skipping {ticker} ({name}) - no Yahoo symbol mapping or UK T-Bill")
            continue
        
        url = f"https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range={args.range}&interval=1d"
        req = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json"
        })
        
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode())
        except Exception as e:
            print(f"  [!] Yahoo Finance failed for {ticker} ({symbol}): {e}")
            time.sleep(0.3)
            continue
        
        result = data.get("chart", {}).get("result")
        if not result:
            print(f"  [!] No chart result for {ticker} ({symbol})")
            time.sleep(0.3)
            continue
            
        entry = result[0]
        timestamps = entry.get("timestamp") or []
        quotes = entry.get("indicators", {}).get("quote", [{}])[0]
        closes = quotes.get("close") or []
        
        resp_currency = entry.get("meta", {}).get("currency", "")
        is_pence = currency.upper() == "GBP" and resp_currency.lower() in ("gbp", "gbx") and resp_currency != "GBP"
        
        sql_lines = []
        valid_points = 0
        for ts, close in zip(timestamps, closes):
            if ts is None or close is None:
                continue
            p = float(close)
            if is_pence:
                p = p / 100.0
            if p <= 0:
                continue
            dt = datetime.datetime.fromtimestamp(ts, tz=datetime.timezone.utc).isoformat()
            sql_lines.append(f"""
            INSERT INTO market_observations (id, instrument_id, price, currency, observed_at, source_type, source_reference, created_at)
            SELECT gen_random_uuid(), '{inst_id}', {p:.4f}, '{currency}', '{dt}', 'PROVIDER', 'YAHOO_FINANCE', NOW()
            WHERE NOT EXISTS (
                SELECT 1 FROM market_observations WHERE instrument_id = '{inst_id}' AND observed_at = '{dt}'
            );
            """)
            valid_points += 1
            
        if sql_lines:
            full_sql = "\n".join(sql_lines)
            proc = subprocess.Popen(
                ['docker', 'exec', '-i', 'invest-tracker-postgres-1', 'psql', '-U', 'invest_tracker', '-d', 'invest_tracker'],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE
            )
            proc.communicate(full_sql.encode())
            total_inserted += valid_points
            print(f"  [+] {ticker:8} ({symbol:10}): Synced {valid_points} historical daily bars (currency: {currency})")
        
        time.sleep(0.3)
        
    print(f"\nCompleted backfill. Total processed price points: {total_inserted}")

if __name__ == "__main__":
    main()

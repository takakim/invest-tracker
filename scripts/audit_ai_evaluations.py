#!/usr/bin/env python3
"""
Invest-Tracker AI Evaluation Auditor
Inspects and audits AI evaluations (portfolio & individual holding levels),
detecting formatting anomalies, thinking trace leaks, missing valuation metrics,
and providing options to trigger re-evaluations against live or local LLMs.
"""

import sys
import json
import argparse
import urllib.request
import urllib.error

DEFAULT_BASE_URL = "http://localhost:8080"


def fetch_json(url, method="GET", data=None):
    try:
        body = json.dumps(data).encode("utf-8") if data is not None else None
        headers = {"Accept": "application/json", "User-Agent": "InvestTrackerAiAuditor/1.0"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        with urllib.request.urlopen(req, timeout=300) as response:
            if response.status == 204:
                return None
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8", errors="replace")
        print(f"HTTP Error {e.code} for {url}: {err_msg}", file=sys.stderr)
        return None
    except urllib.error.URLError as e:
        print(f"Connection error for {url}: {e}", file=sys.stderr)
        return None


def audit_ai_evaluations(base_url=DEFAULT_BASE_URL, portfolio_id=None, filter_ticker=None, re_evaluate=False):
    print("=" * 96)
    print(f"          INVEST-TRACKER AI EVALUATION AUDITOR ({base_url})")
    print("=" * 96)

    # 1. Check AI Provider Status
    status = fetch_json(f"{base_url}/api/v1/ai/status")
    if status:
        active_model = status.get("activeModel") or status.get("defaultModel")
        print(f"🤖 AI Provider: {status.get('provider')} | Connected: {status.get('connected')} | Active Model: {active_model}")
        if status.get("discoveredModels"):
            print(f"   Available Models: {', '.join(status.get('discoveredModels')[:5])}")
    else:
        print("⚠️ Could not retrieve AI service status.")

    # 2. Fetch Portfolios
    portfolios = fetch_json(f"{base_url}/api/v1/portfolios")
    if not portfolios:
        print("No portfolios found.")
        return False

    target_portfolios = [p for p in portfolios if portfolio_id is None or p.get("id") == portfolio_id]

    anomalies_total = 0

    for port in target_portfolios:
        pid = port["id"]
        pname = port["name"]
        print(f"\n📁 Portfolio: {pname} (ID: {pid})")

        # 3. Fetch Portfolio AI Evaluation
        port_eval = fetch_json(f"{base_url}/api/v1/portfolios/{pid}/ai/evaluation")
        if port_eval:
            print(f"  • Overall Risk: {port_eval.get('overallRiskScore')}/10 ({port_eval.get('overallRiskLevel')}) | Model: {port_eval.get('modelUsed')}")
            summary = port_eval.get('executiveSummary', '')
            print(f"    Summary: {summary[:120]}..." if len(summary) > 120 else f"    Summary: {summary}")
        else:
            print("  • No portfolio-level AI evaluation found.")

        # 4. Fetch Positions to know instrument IDs
        positions = fetch_json(f"{base_url}/api/v1/portfolios/{pid}/positions/performance")
        if not positions:
            print("  No positions found for this portfolio.")
            continue

        # 5. Fetch Holding AI Evaluations
        evaluations = fetch_json(f"{base_url}/api/v1/portfolios/{pid}/holdings/ai/evaluations") or []
        eval_map = {}
        for e in evaluations:
            sym = e.get("symbol") or e.get("ticker")
            if sym:
                eval_map[sym.upper()] = e
            if e.get("instrumentId"):
                eval_map[e.get("instrumentId")] = e

        print("\n" + "-" * 96)
        print(f"{'Ticker':<8} {'Stance':<10} {'Risk':<12} {'Model / Provider':<20} {'Eval Date':<20} {'Status':<15}")
        print("-" * 96)

        target_positions = [
            pos for pos in positions
            if filter_ticker is None or (pos.get("ticker") and pos.get("ticker").upper() == filter_ticker.upper())
        ]

        for pos in sorted(target_positions, key=lambda x: x.get("ticker") or ""):
            ticker = pos.get("ticker", "N/A")
            inst_id = pos.get("instrumentId")

            h_eval = eval_map.get(ticker.upper()) or (eval_map.get(inst_id) if inst_id else None)

            if re_evaluate and inst_id:
                print(f"  ⚡ Triggering AI evaluation for {ticker} ({inst_id})...")
                new_eval = fetch_json(f"{base_url}/api/v1/portfolios/{pid}/holdings/{inst_id}/ai/evaluate", method="POST")
                if new_eval:
                    h_eval = new_eval
                    print(f"  ✓ Successfully re-evaluated {ticker} with {new_eval.get('modelUsed')}")

            if not h_eval:
                print(f"{ticker:<8} {'NONE':<10} {'N/A':<12} {'N/A':<20} {'N/A':<20} {'Missing'}")
                continue

            stance = h_eval.get("stance", "N/A")
            risk = f"{h_eval.get('riskScore', '?')}/10 ({h_eval.get('riskLevel', '?')})"
            model = str(h_eval.get("modelUsed") or "Unknown")[:19]
            eval_date = str(h_eval.get("evaluatedAt", "N/A"))[:19]

            summary = h_eval.get("executiveSummary", "")
            has_thinking_leak = "thinking process:" in summary.lower() or "<think>" in summary.lower()

            metrics = h_eval.get("fundamentalMetrics") or {}
            has_pe = metrics.get("trailingPe") is not None or metrics.get("forwardPe") is not None

            status_notes = []
            if has_thinking_leak:
                status_notes.append("LEAK: Thinking")
                anomalies_total += 1
            if not has_pe:
                status_notes.append("No P/E")

            status_str = ", ".join(status_notes) if status_notes else "OK"

            print(f"{ticker:<8} {stance:<10} {risk:<12} {model:<20} {eval_date:<20} {status_str}")

            if filter_ticker or has_thinking_leak:
                print(f"\n    [Details for {ticker}]")
                print(f"    Executive Summary ({len(summary)} chars):")
                print(f"    {summary}")
                print(f"    Strengths ({len(h_eval.get('strengths', []))}): {h_eval.get('strengths')}")
                print(f"    Risks ({len(h_eval.get('risks', []))}): {h_eval.get('risks')}")
                print(f"    Tradeoff: {h_eval.get('holdingVsSellingTradeoff')}")
                print(f"    Fundamental Metrics: P/E: {metrics.get('trailingPe')}, Fwd P/E: {metrics.get('forwardPe')}, P/B: {metrics.get('priceToBook')}, Yield: {metrics.get('dividendYield')}\n")

        print("-" * 96)

    if anomalies_total > 0:
        print(f"\n⚠️ Total formatting/reasoning anomalies detected: {anomalies_total}")
    else:
        print("\n✓ No formatting anomalies or thinking leaks detected.")

    print("=" * 96)
    return anomalies_total == 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Invest-Tracker AI Evaluation Auditor")
    parser.add_argument("--url", "-u", default=DEFAULT_BASE_URL, help=f"API Base URL (default: {DEFAULT_BASE_URL})")
    parser.add_argument("--portfolio", "-p", help="Specific Portfolio UUID to audit")
    parser.add_argument("--ticker", "-t", help="Filter inspection to a specific ticker (e.g. MU)")
    parser.add_argument("--re-evaluate", "-r", action="store_true", help="Trigger live re-evaluation for holdings")
    args = parser.parse_args()

    audit_ai_evaluations(args.url, args.portfolio, args.ticker, args.re_evaluate)

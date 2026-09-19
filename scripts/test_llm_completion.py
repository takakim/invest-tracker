#!/usr/bin/env python3
"""
Invest-Tracker LLM Completion & Benchmark Tool
Sends test prompts to local or remote LLM endpoints (LM Studio, OpenAI, etc.),
measuring latency, token generation, thinking/reasoning blocks, and JSON compliance.
"""

import sys
import time
import json
import argparse
import urllib.request
import urllib.error

DEFAULT_URL = "http://localhost:1234/v1/chat/completions"


def test_completion(endpoint=DEFAULT_URL, model="qwen/qwen3.5-9b", max_tokens=1024, prompt=None, disable_thinking=False, reasoning_param=None):
    if prompt is None:
        prompt = (
            "Evaluate Micron Technology (MU). Output strictly JSON with keys: "
            "stance, riskScore, riskLevel, executiveSummary."
        )

    sys_prompt = "You are a financial analyst. Output strictly valid JSON and no chain-of-thought or reasoning blocks."
    if disable_thinking:
        sys_prompt += " /no_think"

    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": sys_prompt},
            {"role": "user", "content": prompt}
        ],
        "temperature": 0.2,
        "max_tokens": max_tokens
    }

    if disable_thinking:
        payload["chat_template_kwargs"] = {"enable_thinking": False}

    if reasoning_param == "effort_low":
        payload["reasoning_effort"] = "low"
    elif reasoning_param == "effort_none":
        payload["reasoning_effort"] = "none"
    elif reasoning_param == "reasoning_off":
        payload["reasoning"] = "off"
    elif reasoning_param == "reasoning_disabled":
        payload["reasoning"] = {"enabled": False}

    print(f"📡 Sending request to {endpoint} (model: {model}, max_tokens: {max_tokens}, disable_thinking: {disable_thinking})...")
    start = time.time()
    try:
        req = urllib.request.Request(
            endpoint,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=300) as response:
            elapsed = time.time() - start
            data = json.loads(response.read().decode("utf-8"))
            print(f"⏱️ Completed in {elapsed:.2f}s")
            choices = data.get("choices", [])
            if choices:
                choice0 = choices[0]
                msg = choice0.get("message", {})
                content = msg.get("content", "")
                reasoning = msg.get("reasoning_content", "")
                finish = choice0.get("finish_reason")
                print(f"🏁 Finish reason: {finish}")
                print(f"🧠 Reasoning length: {len(reasoning)} chars")
                print(f"📄 Content length: {len(content)} chars")
                if content:
                    print(f"\n--- Content ---\n{content}\n")
                if reasoning and not content:
                    print(f"\n--- Reasoning (Content was empty!) ---\n{reasoning[:500]}...\n")
            return True
    except Exception as e:
        print(f"❌ Error during LLM completion: {e}", file=sys.stderr)
        return False


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test LLM Completion & Measure Latency")
    parser.add_argument("--endpoint", "-e", default=DEFAULT_URL, help=f"Endpoint URL (default: {DEFAULT_URL})")
    parser.add_argument("--model", "-m", default="qwen/qwen3.5-9b", help="Model name (default: qwen/qwen3.5-9b)")
    parser.add_argument("--max-tokens", type=int, default=1024, help="Max tokens (default: 1024)")
    parser.add_argument("--prompt", "-p", help="Custom user prompt")
    parser.add_argument("--disable-thinking", "-d", action="store_true", help="Pass enable_thinking: false and /no_think")
    parser.add_argument("--reasoning-param", "-r", choices=["effort_low", "effort_none", "reasoning_off", "reasoning_disabled"], help="Reasoning parameter to test")
    args = parser.parse_args()

    test_completion(args.endpoint, args.model, args.max_tokens, args.prompt, args.disable_thinking, args.reasoning_param)

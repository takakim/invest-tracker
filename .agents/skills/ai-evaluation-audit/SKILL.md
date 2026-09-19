---
name: ai-evaluation-audit
description: Audits persisted AI portfolio & holding evaluations, detects reasoning leaks, tests LLM latencies, and triggers live re-evaluations.
---

# AI Evaluation & Reasoning Audit Skill

Use this skill when inspecting AI portfolio and holding evaluations for format leaks, reasoning artifacts, or validating local LLM responses.

## Capabilities

1. **Evaluation Audit**: Scans latest persisted evaluations for unparsed JSON, chain-of-thought leaks, and missing fundamental metrics.
2. **Targeted Re-Evaluation**: Triggers immediate on-demand re-evaluation of specific holdings (e.g. `--ticker MU`).
3. **LLM Completion Benchmarking**: Measures generation speed, token counts, and tests reasoning effort parameters directly against LLM endpoints.

## Usage

### 1. Audit Live Evaluations
Inspect all evaluations or a specific ticker against the backend:
```bash
python3 scripts/audit_ai_evaluations.py
python3 scripts/audit_ai_evaluations.py --ticker MU
```

### 2. Trigger Re-Evaluation
Trigger a clean re-evaluation of a specific holding:
```bash
python3 scripts/audit_ai_evaluations.py --ticker MU --re-evaluate
```

### 3. Benchmark Local or Remote LLM
Test response latency and verify reasoning control:
```bash
python3 scripts/test_llm_completion.py --model "qwen/qwen3.5-9b" --reasoning-param effort_none
```

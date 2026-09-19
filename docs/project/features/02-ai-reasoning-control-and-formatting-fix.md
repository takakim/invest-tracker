# Feature 02: AI Reasoning Control, Latency Reduction & Formatting Leak Fix

- **Status**: Completed & Merged (PR #74)
- **Primary Focus**: Local LLM stability, chain-of-thought suppression, prompt currency disambiguation

---

## 1. Problem Statement

1. **Reasoning Trace Leaks into UI**:
   - `logs/backend.log` showed Jackson parse errors: `Unrecognized token 'Thinking': was expecting...`.
   - When evaluating holdings using local reasoning models (such as `qwen/qwen3.5-9b` in LM Studio), the model emitted 4,000–7,000 characters of chain-of-thought (`Thinking Process: ...`).
   - `AiEvaluationService.fallbackHoldingEvaluation` assigned the raw unparsed response directly to `executiveSummary`, dumping thousands of characters of internal reasoning into the user interface.
2. **Qwen 3.5 Token Exhaustion & Long Latency**:
   - The user prompt displayed prices without currency qualification (e.g. `Average Buy Price: 769.8600`, `Current Market Price: 738.2950`), alongside `Native Currency: USD`.
   - Because Micron Technology (`MU`) trades around $100–$140 USD in reality, Qwen spent its entire token limit trying to rationalize the $738 price, hitting `finish_reason: length` with `content: ""` and leaving all text in `reasoning_content`.
   - Inferences took 40–150 seconds per holding.

---

## 2. Architecture & Design Decisions

### Reasoning Control (`reasoning_effort: "none"`)
- Integrated `"reasoning_effort": "none"` in `LmStudioGateway.java`, `AiProperties.java`, and `application.yml`.
- Tells LM Studio / Qwen 3.5 to bypass the chain-of-thought loop entirely, reducing inference latency from ~150s to **~11–16s** while immediately outputting clean JSON.

### Defense-in-Depth Leak Prevention
- **`extractJson(String text)`**: Strips `<think>...</think>` tags and `Thinking Process:` prefixes, extracting code fences or raw JSON objects cleanly.
- **`sanitizeSummary(String text, Instrument instrument)`**: If fallback occurs on raw text containing thinking markers, code fences, or length `> 500` characters, safely replaces it with a concise summary instead of leaking internal thought processes.
- **Currency Disambiguation**: Cost basis and current price are explicitly qualified with the reporting base currency (`GBP`), and native price context (`(Native: 988.81 USD)`) is appended when native and base currencies differ.

### Reusable Diagnostic Tooling
- Added `scripts/audit_ai_evaluations.py` to audit live evaluations and trigger targeted re-evaluations (`--re-evaluate`).
- Added `scripts/test_llm_completion.py` to benchmark LLM latency, measure tokens, and test reasoning flags.

---

## 3. Tasks & Implementation Checklist

- [x] Create reusable Python audit scripts (`scripts/audit_ai_evaluations.py`, `scripts/test_llm_completion.py`)
- [x] Document CLI scripts in `AGENTS.md` and `scripts/README.md`
- [x] Add currency labels and native price context to the holding analysis prompt in `AiEvaluationService.java`
- [x] Update `extractJson` to strip `<think>` tags and reasoning prefixes
- [x] Add `sanitizeSummary` to guard `fallbackHoldingEvaluation`
- [x] Add `reasoningEffort` property to `AiProperties.java` and `application.yml`
- [x] Pass `reasoning_effort` in `LmStudioGateway.java` completion payloads
- [x] Add unit test coverage for `AiProperties`, `LmStudioGateway`, and `AiEvaluationService`
- [x] Verify JaCoCo coverage (>= 90% lines and branches)
- [x] Re-evaluate Micron (`MU`) through live API and confirm clean formatting

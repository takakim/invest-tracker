# Archived Task Record: AI Reasoning Control, Latency Reduction & Formatting Leak Fix

- **Linked Feature Specification:** [02-ai-reasoning-control-and-formatting-fix.md](../features/02-ai-reasoning-control-and-formatting-fix.md)
- **Feature ID:** `02-ai-reasoning-control-and-formatting-fix`
- **Completed Date:** 2026-09-19
- **Pull Request:** [#74](https://github.com/takakim/invest-tracker/pull/74)
- **Status:** Completed & Merged to `main`

---

## Tasks & Execution Checklist

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

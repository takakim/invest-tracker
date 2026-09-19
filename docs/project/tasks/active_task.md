# Active Task Tracking

This document tracks active development tasks, implementation checklists, and progress state for the current feature or refactoring phase.

---

## Current Work Stream: Documentation Reorganization & Governance Structure

### Tasks
- [x] Create dedicated folder structure: `docs/project/`, `docs/project/features/`, and `docs/project/tasks/`
- [x] Move and update `PROJECT_CONTEXT.md` to `docs/project/PROJECT_CONTEXT.md`
- [x] Move and replace `SECURITY.md` with comprehensive policy in `docs/project/SECURITY.md`
- [x] Maintain root symlinks for `PROJECT_CONTEXT.md` and `SECURITY.md`
- [x] Archive completed features:
  - [x] `docs/project/features/01-historical-price-backfill-and-corporate-actions.md`
  - [x] `docs/project/features/02-ai-reasoning-control-and-formatting-fix.md`
- [x] Create active task checklist (`docs/project/tasks/active_task.md`)
- [ ] Update `AGENTS.md` directory structure and context rules
- [ ] Update `README.md` with new features, scripts, and documentation links
- [ ] Run full repository verification suite (`mvn -B verify`, `npm run build`, `npm test`, `npm audit`)

---

## Guidelines for AI Agents & Developers
1. When starting a new feature, update the "Current Work Stream" section above with your structured task checklist.
2. Mark tasks as pending (`[ ]`), in-progress (`[/]`), or completed (`[x]`) as work progresses.
3. Upon completing a feature, archive the specification and completed tasks into `docs/project/features/<number>-<feature-name>.md`.

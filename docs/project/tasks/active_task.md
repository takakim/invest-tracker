# Active Task Tracking

This document tracks active development tasks, implementation checklists, and progress state for the current feature or refactoring phase.

---

## Current Work Stream: Ready for Next Feature

- **Linked Feature Specification:** `<!-- e.g. [04-next-feature.md](../features/04-next-feature.md) -->`
- **Feature ID:** `<!-- e.g. 04-next-feature -->`
- **Archive Target Upon Completion:** `<!-- e.g. docs/project/tasks/archived_04-next-feature.md -->`
- **Status:** Idle / Awaiting Next Feature Assignment

### Tasks
- [ ] Initialize feature specification in `docs/project/features/`
- [ ] Define work stream checklist and implementation steps
- [ ] Implement required domain, API, or frontend changes
- [ ] Run test verification (`mvn verify`, `npm test`, `npm run build`, `npm audit`)
- [ ] Archive completed task list to `docs/project/tasks/archived_<feature_id>.md`

---

## Guidelines for AI Agents & Developers
1. **Link to Feature Specification**: `active_task.md` MUST always include an explicit link and Feature ID referencing its specification in `docs/project/features/<feature-id>.md`.
2. **Maintain Real-Time Transparency**: Keep the task list continuously updated as work progresses, marking items as pending (`[ ]`), in-progress (`[/]`), or completed (`[x]`).
3. **Archival Upon Completion**: Once all tasks for the feature are completed and verified, this file MUST be renamed to `docs/project/tasks/archived_<feature_id>.md` (e.g., `archived_03-ui-ux-enhancements-and-refinements.md`). A fresh `active_task.md` is then created for the next active work stream.

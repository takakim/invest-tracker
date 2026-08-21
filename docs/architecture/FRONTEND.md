# Frontend Architecture

## Stack

- React
- TypeScript
- Vite
- Material UI
- React Router
- TanStack Query for server state
- React Hook Form + Zod for forms/validation
- TanStack Table for transaction/holding tables
- Recharts or Apache ECharts for analytics, selected when chart requirements are finalized

Dependency versions must be re-checked for current stable releases and vulnerabilities before Phase 1 implementation.

## Structure

Organize by feature/domain rather than a single global component folder:

```text
src/
  app/
  features/
    portfolios/
    accounts/
    instruments/
    transactions/
    positions/
    performance/
    imports/
  components/
  api/
  hooks/
  forms/
  routing/
  theme/
  types/
```

Reusable UI primitives belong in `components`; domain-specific UI belongs with its feature.

## Server state

TanStack Query owns API/server state. Avoid duplicating server data into an unrelated global state store.

Local UI state remains local unless it has cross-feature value.

## API client

The API client should be generated or strongly typed from the OpenAPI contract where practical. Never duplicate domain validation rules only in TypeScript.

## Core flows

Phase 1 UI should cover:

1. Portfolio list/create/edit.
2. Account list/create/edit.
3. Portfolio/account navigation.
4. Empty-state dashboard.

Phase 2 adds transaction entry/listing.

Phase 3 adds import/preview/conflict-resolution flows.

Phase 4 adds performance and data-quality views.

## Accessibility

Use semantic HTML and accessible Material UI components. Keyboard navigation, focus management, labels and error messaging are requirements rather than polish.

## Responsive strategy

Design desktop-first because financial tables and analytics require space, but maintain responsive layouts for smaller screens. Mobile-specific workflows are not a Phase 0.5 deliverable.

## Security

- Never store secrets in the frontend.
- Do not use unsafe HTML injection.
- Treat imported/displayed strings as untrusted.
- Validate uploaded files on the server regardless of client validation.
- Prepare CSP/security-header requirements for deployment.

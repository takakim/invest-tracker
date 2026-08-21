# REST API Architecture

## API principles

- RESTful resources with stable identifiers.
- JSON over HTTPS in deployed environments.
- DTOs at API boundaries; persistence entities are not exposed directly.
- Bean Validation at input boundaries.
- Consistent problem/error response shape.
- Pagination for potentially unbounded collections.
- Explicit filtering and sorting parameters.
- Idempotency for import/command operations where duplicate submission is possible.

## Initial resource model

```text
/portfolios
/portfolios/{portfolioId}
/portfolios/{portfolioId}/accounts
/accounts/{accountId}
/accounts/{accountId}/transactions
/instruments
/instruments/{instrumentId}
/accounts/{accountId}/positions
/portfolios/{portfolioId}/performance
/portfolios/{portfolioId}/warnings
```

These are design-level resources; exact endpoints may be refined before Phase 1 implementation.

## Error model

Use an RFC 9457-style Problem Details response with at least:

```json
{
  "type": "https://example.invalid/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/portfolios",
  "errors": []
}
```

Do not expose stack traces, SQL, credentials, internal class names, or infrastructure details.

## Validation

Validation occurs both in API DTOs and domain services. Client-side validation is convenience only and never replaces server-side validation.

## Pagination

Collections use explicit page size limits. The server enforces a maximum regardless of client input.

The exact pagination model (page/size or cursor) will be selected based on expected query patterns during Phase 1 implementation; cursor pagination is preferred for large transaction histories.

## Versioning

Use `/api/v1` as the initial public API boundary. Breaking changes require a new version or an explicitly reviewed compatibility strategy.

## OpenAPI

The API contract should be maintained in version control and validated in CI once implementation begins. Generated documentation must not be the only source of the contract.

## Security

- Authentication is deferred, but endpoints must be designed so authorization can be introduced without rewriting the domain.
- Public exposure requires authentication/authorization before deployment.
- Mutating endpoints must validate content type and payload size.
- CSV upload endpoints require strict multipart/file limits and content validation.

# ADR-0012 / D-11 - Defer Ktor Until an API-Based Remote Implementation Exists

## Status

Deferred

## Context

Ktor is the preferred KMP HTTP client if the project later migrates from Firebase to an API-based backend such as Supabase, AWS, or a custom service. For the MVP, Firebase is the selected database backend, so adding Ktor immediately would increase dependency surface without an active API contract.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Defer Ktor | Keeps MVP dependency graph smaller while preserving future migration path. | Future API implementation still needs explicit work. |
| Use Ktor for Firestore REST now | Strong common HTTP story and provider-like boundary. | More manual auth, serialization, paging, and Firestore error mapping. |
| Add Ktor unused | Makes future work easier superficially. | Unused dependency and unclear agent behavior. |

## Decision

Do not add Ktor during the MVP while Firebase Firestore is the selected remote database implementation.

## Consequences

### Positive

- Smaller MVP dependency surface.
- Agents cannot accidentally build a parallel remote path.
- Future API migration remains clean through `RemoteSyncSource`.

### Negative

- Future Supabase/AWS/custom API work must add Ktor and define API DTOs later.

### Constraints Introduced

- Ktor dependencies require an ADR update and backlog story.
- Future Ktor code must live in a dedicated integration module and implement existing provider abstractions.
- Ktor types must not appear in domain, repository, sync engine, or presentation contracts.

## Verification

- Version catalog should not include Ktor until an approved story adds it.
- Architecture checks forbid Ktor outside approved integration modules.

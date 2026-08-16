# ADR-0006 / D-5 - Use Firebase Firestore Behind RemoteSyncSource

## Status

Accepted

## Context

The MVP uses Firebase as the selected database backend. The sync engine needs a common abstraction for remote reads and writes without leaking provider details. Ktor is reserved for a future API-based implementation if the project migrates from Firebase to Supabase, AWS, or a custom backend.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Firebase Firestore integration behind `RemoteSyncSource` | Uses selected backend directly while preserving replacement boundary. | Firebase integration must remain isolated and heavily tested. |
| Firestore REST through Ktor | Fully common HTTP approach and closer to future API migration. | More manual auth, serialization, paging, and error mapping; unnecessary for MVP. |
| Native SDK wrappers | Official platform SDKs. | Two implementations and higher maintenance. |

## Decision

Use Firebase Firestore behind `RemoteSyncSource`. The concrete Firebase access mechanism is isolated in `:integration:firebase-firestore`.

## Consequences

### Positive

- Firestore can be replaced by implementing `RemoteSyncSource`.
- Ktor can be introduced later as `HttpApiRemoteSyncSource` without changing domain, repositories, or sync engine contracts.

### Negative

- Firebase behavior must be validated in the walking skeleton.
- The implementation choice must not leak beyond integration/wiring modules.

### Constraints Introduced

- Firebase, GitLive, or native SDK types cannot leave `:integration:firebase-firestore`.
- Ktor is not an MVP dependency.
- Adding Ktor requires a new ADR update and an API implementation story.

## Verification

- Integration boundary architecture checks.
- Walking skeleton validates Android and iOS behavior.
- Firestore errors are mapped to app-level errors.

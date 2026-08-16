# ADR-0006 - Access Firestore Through GitLive Behind RemoteSyncSource

## Status

Accepted

## Context

There is no official Firestore SDK for Kotlin Multiplatform. The sync engine needs a common abstraction for remote reads and writes without leaking provider details.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| GitLive Firestore 2.6.x behind `RemoteSyncSource` | Shared KMP API, less native duplication. | Community dependency, must be isolated. |
| Native SDKs with expect/actual wrappers | Official SDKs per platform. | Two implementations and higher maintenance. |
| Firestore REST through Ktor | Fully common implementation. | More manual auth, serialization, and error mapping. |

## Decision

Use GitLive Firestore 2.6.x behind `RemoteSyncSource`.

## Consequences

### Positive

- One KMP integration path.
- Firestore can be replaced by implementing the same interface.

### Negative

- Wrapper behavior must be validated in the walking skeleton.

### Constraints Introduced

- Do not use GitLive 3.0 alpha during the MVP.
- GitLive and Firestore types cannot leave `:integration:firebase-firestore`.
- REST through Ktor remains the fallback if GitLive blocks progress.

## Verification

- Integration boundary architecture checks.
- Walking skeleton validates Android and iOS behavior.
- Firestore errors are mapped to app-level errors.

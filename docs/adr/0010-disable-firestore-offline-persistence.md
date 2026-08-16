# ADR-0010 - Disable Firestore Offline Persistence

## Status

Accepted

## Context

The app has its own offline-first strategy based on Room, an outbox, cursors, tombstones, and conflict resolution. Enabling Firestore offline persistence would introduce a second cache with separate invalidation and sync semantics.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Disable Firestore offline persistence | One offline model, easier reasoning, UI observes Room only. | Remote reads require explicit sync engine behavior. |
| Enable Firestore offline persistence | Built-in SDK cache. | Two caches, unclear conflict semantics, harder debugging. |

## Decision

Disable Firestore offline persistence.

## Consequences

### Positive

- Room remains the only local source of truth.
- Sync correctness is centralized in `:core:sync`.

### Negative

- The app cannot rely on Firestore SDK cache behavior.

### Constraints Introduced

- UI never observes Firestore directly.
- Firestore client configuration must disable offline persistence.

## Verification

- E0-07 walking skeleton acceptance criteria.
- E3-01 Firestore client configuration check.
- Architecture rules preventing UI/network coupling.

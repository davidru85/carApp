# ADR-0001 / D-0 - Use Cloud Firestore as Remote Backend

## Status

Accepted

## Context

The MVP needs a remote replica for user data so anonymous and authenticated users can synchronize vehicles and fuel entries across devices. The app remains local-first: the local database is the UI source of truth.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Cloud Firestore | Low operational cost at MVP scale, client-generated document IDs, server timestamps, simple owner-scoped collections. | No official KMP SDK; requires wrapper behind an interface. |
| Firebase SQL Connect / Data Connect | Relational model, SQL constraints, PostgreSQL semantics. | Fixed Cloud SQL cost, no official KMP client, more complex LWW upserts. |
| Custom backend | Full control. | Too much scope for MVP. |

## Decision

Use Cloud Firestore as the remote replica.

## Consequences

### Positive

- Low-cost backend for MVP scale.
- Remote writes are naturally idempotent by client-generated document ID.
- Server timestamps support authoritative conflict ordering.

### Negative

- Firestore access must be isolated behind `RemoteSyncSource`.
- Rule testing becomes critical because authorization is path-based.

### Constraints Introduced

- Firestore is never the UI source of truth.
- Firestore offline persistence is disabled.
- Firestore types cannot cross integration boundaries.

## Verification

- Firestore emulator rule tests.
- Provider decoupling check excluding `:integration:*` and `:wiring:firebase`.
- Architecture check preventing Firebase types outside integration modules.

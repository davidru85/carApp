# ADR-0050 - MVP Firestore Schema Version Is Exact

## Status

Accepted

## Context

The closed Firestore schema and E3-01 criteria require `schemaVersion == 1`, while one quarantine
paragraph said rules validate only a lower bound. Accepting arbitrary higher values would conflict
with `CLIENT_MAX_SCHEMA_VERSION` and allow unsupported documents to be written by compromised
clients.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Accept exactly version 1 during the MVP | Matches the closed schema and client maximum. | A future schema needs an explicit rollout plan. |
| Accept every version greater than or equal to 1 | Newer clients can write without a rule update. | Arbitrary unsupported versions become writable. |
| Accept a predeclared closed range | Establishes a future migration mechanism now. | Adds rollout policy before a second schema exists. |

## Decision

Mobile-client Firestore rules accept exactly `schemaVersion == 1` during the MVP. The contradictory
lower-bound sentence is corrected. A future remote schema change must decide and document client,
rules and deployment sequencing explicitly.

## Consequences

### Positive

- The rules, `CLIENT_MAX_SCHEMA_VERSION` and remote DTO contract agree.
- A compromised current client cannot invent a future schema version.

### Negative

- Rules and clients need coordinated review when schema version 2 is designed.

### Constraints Introduced

- E3-01 tests both version 1 acceptance and rejection of lower or higher versions.
- Higher versions can still be quarantined if introduced later by a reviewed rollout or an Admin
  path; quarantine remains defensive.
- No schema-version range is introduced before a story owns schema version 2.

## Verification

- `contractCheck` compares `CLIENT_MAX_SCHEMA_VERSION` with the highest version accepted by rules.
- Emulator tests reject schema versions 0 and 2 and accept schema version 1.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-49`)
- `docs/CONTRACTS.md §9.5` and `§16`
- `docs/BACKLOG.md` (`E3-01`)

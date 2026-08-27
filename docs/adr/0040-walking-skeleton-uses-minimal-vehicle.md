# ADR-0040 - Walking Skeleton Uses a Minimal Vehicle Slice

## Status

Accepted

Amended by `D-64`: E0-07 now proves the Vehicle local/remote path while the same anonymous Firebase
Auth session remains available. Permanent-account clean-device recovery is verified later by
E3-12.

## Context

`E0-07` must prove a value can cross native UI, shared presentation, SQLDelight, Firebase Auth and
Firestore, then be fetched back after local product rows are cleared without transferring the
anonymous credential. The closed Firestore schema permits only
vehicles and fuel entries; a temporary walking-skeleton collection is forbidden. Implementing a
full vehicle or fuel-entry feature would consume later Phase 1 stories inside an already-large gate.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Minimal valid vehicle slice | Uses the final schema and keeps the gate narrow. | A small internal adapter is replaced or extended by E1-02 and E1-03. |
| Full vehicle form and repository | More production-shaped code immediately. | Pulls E1-02, E1-03 and native UI work into E0-07. |
| Fuel entry with seeded vehicle | Exercises remote dependency order. | Requires two entities and much more domain behavior than the proof needs. |

## Decision

The selected option is a minimal valid `Vehicle` slice. The user-edited proof value is the vehicle
name; every other required field receives a contract-valid deterministic or platform-injected
value. No temporary remote collection or schema is introduced.

## Consequences

### Positive

- The proof uses the final local and remote vehicle schemas.
- `E0-07` remains a walking skeleton instead of absorbing multiple Phase 1 stories.
- The remote refetch proves a real product entity survives the round trip.

### Negative

- E1-02 and E1-03 will replace or extend the deliberately narrow adapter.

### Constraints Introduced

- `E0-07` MUST NOT introduce a walking-skeleton-only Firestore collection.
- The slice MUST write a complete valid vehicle document even though only its name is edited.
- The slice MUST NOT claim the complete vehicle domain or repository stories are delivered.

## Verification

- E0-07 tests and native-host acceptance evidence use a vehicle document.
- Firestore rules reject unknown collections and incomplete vehicle payloads.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-39`)
- `docs/SPECIFICATION.md §9`
- `docs/CONTRACTS.md §16`
- `docs/BACKLOG.md` (`E0-07`)

# ADR-0039 / D-38 - Use a Transaction Facade for Database Mutations

## Status

Accepted

Accepted by the owner on 2026-08-24.

## Context

`docs/CONTRACTS.md §3.1` assigns `vehicle.currentOdometerKm` and
`fuel_entry.odometerInconsistent` exclusively to `:core:database`. Every fuel-entry mutation must
update those read models in the same transaction, and updates require the exact de-duplicated set
of the edited row, its pre-write successor and its post-write successor. Vehicle deletion has a
different cascade set that must exclude successors that are themselves being tombstoned.

SQLDelight generates observable queries from the tables directly referenced by a statement.
Changes made indirectly by SQLite triggers are not a reliable source of invalidation for a `Flow`
observing another table. Triggers also make the pre/post successor set and cascade behavior harder
to inspect, test and migrate.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Kotlin/SQLDelight `DatabaseMutations` transaction facade | Captures pre-write rows before mutation, computes the exact set explicitly, keeps the mutation and read models atomic, and lets generated statements notify every observed table they touch. | Writers must use the facade, so an executable architecture rule is required to reject direct generated entity mutations elsewhere. |
| SQLite triggers | Protects invariants even when a caller bypasses a Kotlin API. | Pre/post and cascade sets become hidden trigger logic; indirect changes do not reliably invalidate SQLDelight observers; migrations and debugging become more difficult. |
| Split Kotlin/trigger implementation | A trigger could maintain the whole-vehicle maximum while Kotlin handles successor flags. | One invariant is spread across two execution models, increasing ordering, recursion and migration risk. |

## Decision

Use a Kotlin/SQLDelight transaction facade named `DatabaseMutations`, owned by `:core:database`,
for synchronized entity inserts, updates and tombstone writes. The facade captures the required
pre-write state, executes the write, recomputes the exact `docs/CONTRACTS.md §3.1` set and updates
the affected vehicle maximum before the transaction commits.

Generated SQLDelight entity-mutation functions are implementation details. Calls from
`:feature:*`, `:core:sync`, `:shared`, integrations or wiring are forbidden by an executable source
check with a failing fixture. Generated read queries and the outbox, cursor and quarantine control
operations remain available to the stories that own them.

SQLite triggers are not used for read-model recomputation. Schema constraints remain the database
mechanism for the tombstone relation and closed enum/boolean domains.

## Consequences

### Positive

- The exact create, update, tombstone and cascade recompute sets are visible Kotlin logic and can
  be tested independently with deliberately stale non-target rows.
- SQLDelight notifications include the tables changed by the generated statements, so database
  `Flow` values remain observable.
- Local, pull and adoption writers share one invariant-preserving boundary while retaining their
  different mutation-sequence and outbox rules.

### Negative

- The architecture checker must maintain a closed list or naming convention for generated entity
  mutation functions.
- Adding a new synchronized entity mutation requires extending the facade and its architecture
  fixture in the same story.

### Constraints Introduced

- Code outside `:core:database` MUST NOT call generated SQLDelight synchronized-entity mutation
  functions directly.
- Every facade operation that changes multiple rows MUST use one SQLDelight transaction.
- Recompute tests MUST prove both the required target set and the absence of writes to rows outside
  that set.
- Local-write facade entry points consume `local_sequence`; pull and local-owner adoption entry
  points preserve supplied `localMutationSeq` values and do not consume it.

## Verification

- RED/GREEN tests cover create, chronological move, odometer-only update, unrelated-field update,
  coincident successors, single tombstone and three-row vehicle cascade behavior.
- Android host and `iosSimulatorArm64` execute the same tests against bundled SQLite.
- An architecture fixture proves direct generated entity mutation calls outside `:core:database`
  fail the build.

## References

- `docs/DECISION_BOARD.md` (`D-38`)
- `docs/CONTRACTS.md §3.1`, `§4`, `§8`
- `docs/TECHNICAL_PLAN.md §4`, `§6`
- `docs/BACKLOG.md` (`E1-01`)

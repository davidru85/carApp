# ADR-0124 / D-123 - Own the Adoption Transaction in `:core:database` and Inject Its Payloads

## Status

Accepted

Taken while implementing `E2-06` on 2026-09-06. It is an implementation-structure decision, not one
of the owner decisions reserved by `AGENTS.md`, and it is recorded here so the next agent is bound
by it.

## Context

`docs/CONTRACTS.md §11.4` requires local owner adoption to rewrite every `LOCAL_OWNER` row, bump its
`localRevision`, reset every non-`SYNCED` row and enqueue one outbox snapshot per reset row **in one
transaction**. Atomicity is the point: a half-applied adoption would leave rows owned by a real UID
with no outbox row, which `§7` calls an illegal state, and the failure mode is silent data that never
reaches the backup.

`D-38` already makes `DatabaseMutations` the sole transaction boundary for synchronized entity
writes, and the architecture rules forbid `:core:database` from depending on a feature. But the
outbox payload is feature knowledge: `§8` requires the full entity snapshot under the canonical field
names of `§3`, and the two mappers that produce it live in `:feature:vehicle` and `:feature:fuel`.

The transaction therefore needs something only the features can build, and the features cannot be
reached from where the transaction must live.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| One `DatabaseMutations` transaction with injected payload mappers (Selected) | Atomic by construction; reuses the `tombstoneVehicleWithFuelEntries` shape already in the file; `:core:database` keeps knowing nothing about either feature; the adopted snapshot is produced by the same mapper as an edited one, so the two cannot drift | The mapper runs inside the transaction, so a mapper that blocks holds the write lock; the row types cross the boundary as `VehicleDatabaseRow` and `FuelEntryDatabaseRow` rather than domain types |
| Build the payloads inside `:core:database` | No lambda, no boundary crossing | `:core:database` would have to know the canonical field names, the scaled-value semantics and the tombstone shape of both entities; two copies of the payload that CI cannot prove identical; a gated module grows product knowledge |
| Orchestrate from `:shared` over the existing narrow mutations | No new `:core:database` API | Several transactions instead of one, which is exactly the atomicity `§11.4` requires; `:shared` would assign the outbox order, which is contract behaviour, not composition |

## Decision

`DatabaseMutations.adoptLocalOwner(newOwnerId, vehicleOutboxPayload, fuelEntryOutboxPayload)` owns
the whole operation in one transaction. The two payload arguments are functions over the module's own
row types, supplied by the composition root.

Each feature exposes exactly one public mapper for this — `VehicleDatabaseRow.toAdoptionOutboxPayload()`
and `FuelEntryDatabaseRow.toAdoptionOutboxPayload()` — implemented by delegating to the same internal
mapper the ordinary write path uses. The rewrite happens before the payload is built, so the mapper
always sees the adopting UID and never the sentinel.

Ordering is resolved inside the transaction. Both selections are already ordered by
`localMutationSeq ASC, id ASC`, so producing the four push dependency groups of `§8` is a stable
partition of those lists and never a re-sort, which is what `§8` requires.

## Consequences

### Positive

- The rewrite, the reset and the enqueue commit together or not at all.
- An adopted snapshot and an edited snapshot are produced by the same code, so a future payload change
  cannot apply to one and miss the other.
- The push dependency order is decided where the rows are read, so no caller can get it wrong.

### Negative

- A payload mapper now runs inside a database transaction. It is pure JSON construction over an
  in-memory row, but the constraint is real and is stated below.
- Two feature-data functions became public API for one consumer.

### Constraints Introduced

- The payload functions passed to `adoptLocalOwner` MUST be pure and non-suspending. They run inside
  the write transaction and MUST NOT perform I/O.
- The adoption rewrite MUST NOT consume a `localMutationSeq`. The counter records local mutation
  order, and adoption is not a local mutation.
- Any future entity added to the outbox MUST be added to both the selection and the dependency-group
  partition in this function, not to a second adoption path.

## Verification

- `LocalOwnerAdoptionTest` in `:core:database` covers the rewrite, the `localRevision` bump, the
  preservation of `localMutationSeq` and of the mutation counter, the four-group order resolved by
  `localMutationSeq` then `id`, the reset of `PENDING`, `FAILED_RETRYABLE` and `FAILED_POISONED` with
  the error context cleared, the monotonic `seq` starting one past the highest pre-existing value,
  idempotency over a second run, and a populated database with interleaved edits.
- `architectureCheck` proves `:core:database` still depends on no feature.
- Both suites run on the JVM and on `iosSimulatorArm64`.

## References

- `docs/DECISION_BOARD.md` (`D-123`)
- `docs/CONTRACTS.md` sections 7, 8 and 11.4
- `docs/TECHNICAL_PLAN.md` sections 4 and 5
- [ADR-0039](0039-database-mutations-use-transaction-facade.md) (`D-38`)

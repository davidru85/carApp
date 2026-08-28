# ADR-0077 / D-76 - Validate Vehicle Commands from Pre-Write Facts

## Status

Accepted

## Context

E1-02 must implement pure Vehicle domain rules that normalise and validate commands. Two rules
require local facts that are not present in a command: name uniqueness among the current owner's
active vehicles, and whether an updated vehicle has any non-deleted fuel entries. The canonical
`VehicleRepository` deliberately exposes product operations rather than validation-shaped database
queries, and E1-02 cannot depend on SQLDelight, `:core:database`, `:core:sync` or another feature.

The functional-core pattern is already contractual. `CalculateConsumption` is a pure function that
receives pre-filtered facts, while `FuelEntryRepository.observeConsumption` filters to one vehicle,
excludes deleted entries and invokes that function. D-76 applies the same established boundary to
writes: pure domain rules consume prepared facts, while the E1-03 data implementation owns the
imperative sequence.

There is no database uniqueness backstop. `vehicle.nameFold` exists without
`UNIQUE(ownerId, nameFold)`, and `upsertRemoteVehicleRow` applies remote rows without business
validation. This is deliberate because `docs/SPECIFICATION.md §5.1` defines Vehicle-name
uniqueness as a local pre-write check that cannot be enforced across devices. A unique index would
make remote ingestion fail when different devices create the same folded name. Consequently, the
single local transaction containing fact loading, validation and mutation is the only available
local guarantee.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Pure validators receive immutable pre-write facts | Reuses the contractual `CalculateConsumption` functional-core pattern; keeps every rule deterministic, Kotlin-pure and independently testable; leaves the canonical repository API unchanged. | E1-03 must load the fact snapshot, invoke the validator and write inside the same local transaction. |
| CRUD use cases query through additional domain repository ports | Gives create and update a conventional orchestration shape and lets each use case acquire its own facts. | Introduces a new architectural shape, adds persistence-oriented read methods solely for validation and cannot provide the required guarantee when the port call returns before the write transaction starts. The data layer would still need to repeat the transaction. |
| Validate only inside the E1-03 data repository | Keeps fact loading close to SQLDelight and makes transaction ownership straightforward. | Mixes independently testable business rules into infrastructure, prevents E1-02 from satisfying its pure domain scope and encourages rule duplication across write paths. |

## Decision

E1-02 exposes the functional core through `ValidateCreateVehicle` and `ValidateUpdateVehicle`.
They receive their immutable command plus a validation context containing active Vehicle name
candidates for the current owner. The update context also contains
`hasNonDeletedFuelEntries`.

The validators return the corresponding normalised command in `Outcome.Ok` or one of their exact
declared `ValidationError` leaves in `Outcome.Err`. The odometer numeric range returns
`OutOfRange`; an otherwise-valid odometer edit locked by existing fuel entries returns
`EditNotAllowed`. The validators perform no I/O and never call a repository.

E1-03 is the imperative shell and owns the complete sequence: load facts, invoke the validator and
apply the database mutation inside one local transaction. This is the explicit Vehicle exception
to the general write-use-case sequencing rule in `docs/CONTRACTS.md §5`; the exception moves the
sequence, not the rules, into the data implementation.

Validation order is deterministic: required name, string lengths, numeric odometer range, odometer
edit lock, then name uniqueness. Tests use otherwise-valid commands so every error branch remains
independently observable.

## Consequences

### Positive

- E1-02 remains Kotlin-pure and depends only on `:core:model` and `:core:common`.
- Vehicle writes reuse the existing functional-core boundary established by
  `CalculateConsumption` rather than introducing a new orchestration pattern.
- Normalised values are the exact values checked for uniqueness and handed to E1-03 for storage.
- The public `VehicleRepository` remains the product-operation contract from `§12`.
- Every declared validation outcome is directly covered without a database fake.

### Negative

- E1-03 must add transactional fact loading and validator invocation before its writes.
- The context is a public domain type even though its values originate from local queries.
- The public context type could technically be called with stale facts inside `:feature:vehicle`,
  but `AppGraph` exposes state-holder factories, `SyncController` and `close()` rather than
  repositories, use cases or DAOs. The misuse surface does not justify a `ValidatedCommand` key
  type or a change to `VehicleRepository`.

### Constraints Introduced

- `VehicleNameCandidate` contains only `id` and `name`; it MUST NOT expose local rows or sync
  metadata.
- Candidate lists contain only non-deleted vehicles for the current owner.
- Update validation excludes the candidate whose ID equals the target command ID.
- E1-03 loads the facts, validates and writes in one local database transaction.
- E1-03 proves that transactional boundary with a test.
- No unique Vehicle-name index, `ValidatedCommand` key type or validation query port is introduced.

## Verification

- E1-02 common tests cover successful create/update normalisation and every declared validation
  error.
- Android-host and `iosSimulatorArm64` tests execute the same validator suite.
- Architecture and contract checks reject forbidden dependencies and decision-mirror drift.
- E1-03 tests will prove fact loading and mutation share a transaction.

## References

- `docs/DECISION_BOARD.md` (`D-76`)
- `docs/SPECIFICATION.md §5.1`, `§8.3`, `§11`, `§12`
- `docs/CONTRACTS.md §5`, `§11`, `§12`, `§13`, `§20.2`, `§20.5`
- `docs/TECHNICAL_PLAN.md §4`
- `docs/BACKLOG.md` (`E1-02`, `E1-03`)
- `core/database/src/commonMain/sqldelight/com/ruizurraca/carapp/core/database/schema.sq`
- `core/database/src/commonMain/sqldelight/com/ruizurraca/carapp/core/database/database.sq`

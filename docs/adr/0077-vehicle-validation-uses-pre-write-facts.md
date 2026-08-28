# ADR-0077 / D-76 - Validate Vehicle Commands from Pre-Write Facts

## Status

Accepted

## Context

E1-02 must implement pure Vehicle domain use cases that normalise and validate commands before a
repository write. Two rules require local facts that are not present in a command: name uniqueness
among the current owner's active vehicles, and whether an updated vehicle has any non-deleted fuel
entries. The canonical `VehicleRepository` deliberately exposes product operations rather than
validation-shaped database queries, and E1-02 cannot depend on SQLDelight, `:core:database`,
`:core:sync` or another feature.

The previous contract stated that use cases own pre-write validation but declared no Vehicle use
case signatures. Implementing E1-02 without closing that gap would either move business validation
into the data layer, expose storage-shaped queries through the public repository, or invent a
public API without recording it in `docs/CONTRACTS.md`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Pure validators receive immutable pre-write facts | Keeps validation deterministic and Kotlin-pure; every error and normalisation rule is directly unit-testable; leaves the canonical repository API unchanged. | E1-03 must load the fact snapshot and pass it to the validator before each write; correctness depends on loading and writing inside the same local transaction. |
| CRUD use cases query through additional domain repository ports | Gives create and update a conventional orchestration shape and lets each use case acquire its own facts. | Adds persistence-shaped read methods solely for validation, enlarges the public contract and risks a time-of-check/time-of-write gap unless the data layer still repeats the transaction. |
| Validate only inside the E1-03 data repository | Keeps fact loading close to SQLDelight and makes transaction ownership straightforward. | Violates the contract that use cases normalise and validate before repository writes, hides business rules in infrastructure code and prevents E1-02 from satisfying its testable domain scope. |

## Decision

E1-02 exposes two pure use cases, `ValidateCreateVehicle` and `ValidateUpdateVehicle`. They receive
their immutable command plus a validation context containing active Vehicle name candidates for the
current owner. The update context also contains `hasNonDeletedFuelEntries`.

The validators return the corresponding normalised command in `Outcome.Ok` or one of their exact
declared `ValidationError` leaves in `Outcome.Err`. They perform no I/O and never call a
repository. E1-03 owns loading the facts and invoking the validator immediately before the
database mutation inside the same local transaction.

Validation order is deterministic: required name, string lengths, odometer range and edit lock,
then name uniqueness. Tests use otherwise-valid commands so every error branch remains independently
observable.

## Consequences

### Positive

- E1-02 remains Kotlin-pure and depends only on `:core:model` and `:core:common`.
- Normalised values are the exact values checked for uniqueness and handed to E1-03 for storage.
- The public `VehicleRepository` remains the product-operation contract from `§12`.
- Every declared validation outcome is directly covered without a database fake.

### Negative

- E1-03 must add transactional fact loading before its writes.
- The context is a public domain type even though its values originate from local queries.
- Callers outside the E1-03 repository could validate against stale facts; the contract therefore
  reserves authoritative invocation to the transactional data implementation.

### Constraints Introduced

- `VehicleNameCandidate` contains only `id` and `name`; it MUST NOT expose local rows or sync
  metadata.
- Candidate lists contain only non-deleted vehicles for the current owner.
- Update validation excludes the candidate whose ID equals the target command ID.
- E1-03 loads the facts and writes in one local database transaction.

## Verification

- E1-02 common tests cover successful create/update normalisation and every declared validation
  error.
- Android-host and `iosSimulatorArm64` tests execute the same validator suite.
- Architecture and contract checks reject forbidden dependencies and decision-mirror drift.
- E1-03 tests will prove fact loading and mutation share a transaction.

## References

- `docs/DECISION_BOARD.md` (`D-76`)
- `docs/SPECIFICATION.md §5.1`, `§8.3`, `§11`, `§12`
- `docs/CONTRACTS.md §5`, `§12`, `§13`, `§20.5`
- `docs/TECHNICAL_PLAN.md §4`
- `docs/BACKLOG.md` (`E1-02`, `E1-03`)

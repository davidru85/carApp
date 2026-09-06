# ADR-0126 / D-125 - Hold Vehicle Reads Until Adoption Has Run for the Current Owner

## Status

Accepted

Taken while implementing `E2-06` on 2026-09-06 and extended the same day, before merge, in the
owner's review of pull request #55. The gate itself was approved; leaving an adoption failure as a
silent, indefinite wait was not. This record describes the extended, implemented behaviour.

## Context

`D-116` fixed the meaning of `VehicleListUiState.isLoading`: the vehicle list is not known yet. Hosts
gate F-1 first-run routing on it, and `D-121` made that first-run creation mandatory, with the
Android system and predictive back gestures consumed and iOS interactive dismissal disabled.
`D-120` then made an owner transition reopen the list synchronously with the authentication change
and keep it unknown until that owner publishes a successful result.

Adoption creates a window those decisions did not have to consider. Authentication publishes the new
UID, `VehicleListStateHolder` reopens the list and starts an observation for that UID, and the rows
are still owned by the sentinel because adoption has not committed yet. The observation succeeds and
publishes an **empty** list. That is a confirmed empty list under `D-116`, so the host opens mandatory
first-run creation, with no back affordance, over an owner's existing vehicles that are one
transaction away from arriving.

Adoption cannot simply run first. The auth state is published by the provider, the rewrite is a
suspending database transaction, and no ordering between them can be guaranteed by observing both.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Hold the reads until adoption has run (Selected) | The empty list is never published, so `D-116` and `D-121` keep their exact meaning; the wait is expressed where the data is read, so no state holder and no host changes; naturally ordered, with no flag to observe | Every Vehicle read passes through a decorator; a permanently failing adoption would hold the list unknown rather than showing it empty |
| Accept the transient and let the list correct itself | Nothing to build | The correction arrives after the host has already routed into a screen the owner cannot leave; the visible symptom is indistinguishable from data loss |
| Add an "adoption pending" field to `VehicleListUiState` | Explicit in the state | Widens an exported UI contract and pushes the ordering problem into both hosts, which must then agree on it; `D-116` exists precisely so hosts do not need a second flag |

## Decision

`AdoptionGatedVehicleRepository` wraps the Vehicle repository in the composition root and awaits
`LocalOwnerAdoption.awaitAdoption()` before every read and every write.

The gate returns immediately for the `LOCAL_OWNER` sentinel: an offline session reads its own rows and
is never held. For an authenticated owner it returns only after any waiting rows have been rewritten.
When nothing is waiting it costs one indexed count.

`isLoading` keeps the meaning `D-116` gave it, and gains no exception: while the gate is closed, the
authenticated owner's list genuinely is not known yet.

**A failure is typed, not silent.** `awaitAdoption()` returns `Outcome<Unit, AppError>`. A failed
adoption transaction becomes `PersistenceError.TransactionFailed`, the same leaf the repository
already uses for a failed write, and never an exception crossing the gate. `CancellationException` is
rethrown, so the caller's own cancellation still behaves like cancellation and an adoption failure
never arrives disguised as one.

On a read the error is emitted as the flow's first value. `VehicleListStateHolder` already models an
`Outcome.Err` listing as unreadable: `isLoading` stays true, the error is published as a message, and
`refresh()` starts a new observation, which runs the gate again. The retry is therefore real work and
not a redraw, and no confirmed empty list is ever published. On a write the error is the write's
error, returned to the caller.

**The two automatic triggers are independent.** They are siblings under a supervisor, so one failing
cannot cancel the other, and each also survives its own failure with a logged warning rather than
disappearing. Losing a trigger permanently would leave a device under the sentinel with no way back,
which is the failure this decision exists to avoid.

## Consequences

### Positive

- Mandatory first-run creation cannot open over data that adoption is about to deliver.
- Hosts, state holders and the exported UI contract are unchanged.
- The `D-120` synchronous reopen still happens; the gate only delays the resolution that follows it.

### Negative

- An adoption that fails repeatedly leaves the list unknown instead of empty. That is the safer of
  the two failures, and it is now visible and retryable rather than silent, but the retry is
  owner-driven: nothing retries it on a schedule. Automatic retry with backoff belongs to the sync
  engine and is an acceptance criterion of `E3-03`.
- Only the Vehicle side is gated. Fuel entries are reached through a vehicle, so no fuel-entry read
  can precede a resolved vehicle list.
- Every Vehicle read and write now goes through one more suspension point.

### Constraints Introduced

- The gate MUST be a no-op for the `LOCAL_OWNER` sentinel. Holding the offline list would break the
  `E2-03` offline start.
- A read path added to the Vehicle repository MUST pass through the gate, or it can publish the empty
  list the gate exists to prevent.
- An adoption failure MUST reach the caller as a typed `AppError`. It MUST NOT be thrown across the
  gate, MUST NOT cancel the caller, and MUST NOT be left as an indefinite wait.
- `CancellationException` MUST be rethrown rather than converted, so cancellation and failure stay
  distinguishable.
- The automatic triggers MUST NOT be able to cancel one another.

## Verification

- `LocalOwnerAdoptionTest` in `:shared` covers both gate directions and the end-to-end path through
  `DefaultAppGraph`: the sentinel's list resolves to its own rows, and after authentication the next
  resolved list is the adopted one, never an empty one.
- `LocalOwnerAdoptionFailureTest` in `:shared` covers the failure semantics against a transaction
  that fails and can then be made to succeed: the gate reports `PersistenceError.TransactionFailed`
  instead of throwing and the caller stays active; the list stays unknown with a message and no
  confirmed empty list, and `refresh()` recovers it; a write behind a failed gate returns the same
  typed error without cancelling its caller; and a failing owner observer leaves the connectivity
  trigger working.
- The existing `VehicleListStateHolderTest` and `AppGraphContractTest` suites still pass, so the
  decorator changed no existing behavior.

## References

- `docs/DECISION_BOARD.md` (`D-125`)
- `docs/CONTRACTS.md` sections 11.4 and 20.10
- [ADR-0117](0117-vehicle-list-loading-means-unknown.md) (`D-116`)
- [ADR-0121](0121-resolve-the-vehicle-list-per-owner.md) (`D-120`)
- [ADR-0122](0122-make-first-run-vehicle-creation-mandatory.md) (`D-121`)

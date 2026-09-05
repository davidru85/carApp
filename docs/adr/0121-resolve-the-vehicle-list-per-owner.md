# ADR-0121 / D-120 - Resolve the Vehicle List Per Owner

## Status

Accepted

Selected by the owner on 2026-09-05, in the second E2-03 review round of pull request #54.

## Context

`D-116` made `VehicleListUiState.isLoading` mean "the vehicle list is not known yet", and `D-115`
gates F-1 first-vehicle creation on it. Two paths could still present that form over a list that was
never actually confirmed empty:

- `AuthOwnerContext` maps `AuthState.Unknown` and `AuthState.SignedOut` to the `LOCAL_OWNER`
  sentinel, so at every launch the repository is first queried for `LOCAL_OWNER`. That query returns
  an empty list, which resolved the state before the restored signed-in owner's list arrived. A
  returning owner with vehicles could therefore be shown mandatory first-vehicle creation.
- `SqlDelightVehicleRepository.observeVehicles` maps an observation failure to `Outcome.Err`, and the
  state holder published it as `isLoading = false` with an empty list. An unreadable list was
  indistinguishable from a confirmed empty one, and it opened creation as well.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Re-subscribe the observation per owner and publish "not known" until that owner emits successfully (Selected) | Each emission belongs to the owner that was current when it was subscribed, because `flatMapLatest` cancels the previous scope; deterministic to test; no contract or ABI change | Re-subscribes the local observation on every owner transition |
| Pair each emission with the owner read when it is mapped | No re-subscription | An emission produced before the transition can be paired with the owner resolved after it, which is the same staleness in a subtler form |
| Add an owner field to `VehicleListUiState` | Explicit to every host | Changes `docs/CONTRACTS.md §20.10` and the Swift-facing ABI for state the hosts do not otherwise need |
| Keep resolving on the first emission of any owner | No change | Leaves both defects in place |

## Decision

`VehicleListStateHolder` takes the `OwnerContext` that `AppGraphDependencies` already carries and
scopes the observation to it: `ownerContext.observe().flatMapLatest { repository.observeVehicles(...) }`,
emitting an unresolved marker when a scope starts.

`isLoading` is `true` while the current owner's list is unresolved **and** while the latest result for
that owner is a failure. A failure also publishes its error code through `UiMessage`, so an
unreadable list stays distinguishable from a confirmed empty one.

The constructor is `internal` and the published field set is unchanged, so `docs/CONTRACTS.md §20.10`,
the Swift-facing ABI and the committed Objective-C golden header are unchanged. The `§20.10`
semantics sentence is extended to state the owner scope and the failure case.

`D-116` still holds: an ordinary refresh over an already known list does not reopen it and therefore
cannot unmount navigation.

## Consequences

### Positive

- F-1 mandatory creation can only open after a successful result for the owner in scope.
- A read failure no longer looks like an empty account.
- Both hosts inherit the fix from shared presentation, with no host-side duplication.

### Negative

- An owner transition briefly covers the mounted UI while the new owner's list resolves.
- A persistent read failure keeps that cover in place; the error message states why.

### Constraints Introduced

- An owner transition MUST reopen the list.
- A read failure MUST NOT be published as a confirmed empty list.
- The gate MUST stay a shared-state concern; hosts MUST NOT re-derive owner scoping.

## Verification

- Shared tests drive an owner-scoped repository fake: an empty result for one owner does not resolve
  the next owner's list; an initial read failure leaves the list unknown and publishes its code; a
  later successful result resolves it.
- The existing `D-116` refresh test still proves that a refresh over a known list does not reopen it.
- The Objective-C golden header is byte-identical, and `contractCheck` passes.

## References

- `docs/DECISION_BOARD.md` (`D-120`)
- `docs/CONTRACTS.md` section 20.10
- `docs/SPECIFICATION.md` sections 7 F-1 and 12
- [ADR-0117](0117-vehicle-list-loading-means-unknown.md) (`D-116`)
- [ADR-0116](0116-mount-onboarding-navigation-once.md) (`D-115`)
- [ADR-0122](0122-make-first-run-vehicle-creation-mandatory.md) (`D-121`)

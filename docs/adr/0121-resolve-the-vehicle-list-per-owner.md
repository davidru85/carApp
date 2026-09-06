# ADR-0121 / D-120 - Resolve the Vehicle List Per Owner

## Status

Accepted

Selected by the owner on 2026-09-05, in the second E2-03 review round of pull request #54.

## Update, 2026-09-06

Review found two gaps in the first mechanism.

The `flatMapLatest` scoping published its unresolved marker only once the holder's collector was
scheduled. Until then `state.value` still held the previous owner's successful result, while
`SessionStateHolder`, which observes the same authentication state through its own collector, could
already expose the new session. A host combining the two could therefore open mandatory first-vehicle
creation for a returning owner who has vehicles, and the one-shot presentation marker then froze that
decision. The holder now publishes through a `MutableStateFlow` and observes owner resolution
undispatched and unconfined, so the list becomes unknown inside the same call stack that changes the
authentication state. It also clears the selection and the message of the previous owner, so nothing
owner-scoped crosses the boundary.

The failure semantics were correct but unusable. The production repository emits a read failure and
then completes, so the observation ends and the hosts covered an unrecoverable state with an
indefinite indicator; on Android the refresh action that would have been the retry was disabled. A
refresh over an unreadable list now creates a new local observation, and both hosts distinguish an
unreadable list, which reports the localized error with a retry action, from one that is still
arriving, which keeps the indicator. A known list that becomes unknown without an error is the owner
transition, and it is the only case in which the hosts reset owner-scoped navigation.

`docs/CONTRACTS.md §20.10` carries these semantics. No field, type or signature changed, so the
Swift-facing ABI and the golden header are unchanged.

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
| Re-subscribe the observation per owner and publish "not known" until that owner emits successfully (Selected) | Each emission belongs to the owner that was current when it was subscribed; deterministic to test; no contract or ABI change. The 2026-09-06 update made that publication synchronous with the authentication change, because a scheduled one still left a stale interval | Re-subscribes the local observation on every owner transition, and the holder observes the database from construction rather than only while subscribed |
| Pair each emission with the owner read when it is mapped | No re-subscription | An emission produced before the transition can be paired with the owner resolved after it, which is the same staleness in a subtler form |
| Add an owner field to `VehicleListUiState` | Explicit to every host | Changes `docs/CONTRACTS.md §20.10` and the Swift-facing ABI for state the hosts do not otherwise need |
| Keep resolving on the first emission of any owner | No change | Leaves both defects in place |

## Decision

`VehicleListStateHolder` takes the `OwnerContext` that `AppGraphDependencies` already carries and
owns its published value directly through a `MutableStateFlow`.

Owner resolution is collected undispatched and unconfined, so the list becomes unknown inside the
same call stack that changes the authentication state. No other observer of that state can expose a
new session while this holder still publishes the previous owner's list. The transition also clears
that owner's selection and message and starts a fresh observation for the new owner.

`isLoading` is `true` while the current owner's list is unresolved **and** while the latest result
for that owner is a failure. A failure publishes its error code through `UiMessage`, so the two
unknown states are distinguishable: unknown without a message is a list that is still arriving, and
unknown with a message is a list that could not be read. `refresh()` over an unreadable list creates
a new local observation, which is the owner's retry; over a known list it never reopens it, which is
what `D-116` protects.

The constructor is `internal` and the published field set is unchanged, so `docs/CONTRACTS.md §20.10`,
the Swift-facing ABI and the committed Objective-C golden header are unchanged. The `§20.10`
semantics carry the owner scope, the failure case and the host obligations.

## Consequences

### Positive

- F-1 mandatory creation can only open after a successful result for the owner in scope.
- A read failure no longer looks like an empty account, is reported, and can be retried.
- Both hosts inherit the fix from shared presentation, with no host-side duplication.
- The one-shot first-run marker can no longer freeze a decision taken on another owner's data,
  because an owner transition resets owner-scoped navigation.

### Negative

- An owner transition briefly covers the mounted UI while the new owner's list resolves.
- The holder observes the local database from construction until `close()`, rather than only while
  its state is subscribed.

### Constraints Introduced

- An owner transition MUST reopen the list before any other observer can expose the new session, and
  MUST clear that owner's selection and message.
- A read failure MUST NOT be published as a confirmed empty list, MUST publish its error and MUST be
  retryable through a new observation.
- Hosts MUST reset owner-scoped navigation only on an owner transition, never on a read failure.
- The gate MUST stay a shared-state concern; hosts MUST NOT re-derive owner scoping.

## Verification

- Shared tests drive an owner-scoped repository fake on queued dispatchers and observe the interval
  between the owner change and the collector: the list is unknown immediately, exposes none of the
  previous owner's vehicles, and carries neither its selection nor its message. Proved non-vacuous:
  with the owner collector dispatched instead of undispatched, all three fail.
- A repository fake that emits a read failure and completes, exactly as the production repository
  does, leaves the list unknown with its error; a refresh then creates a second observation that
  resolves.
- Host decision tests on both platforms pin the three-way gate and the reset rule.
- The Objective-C golden header is byte-identical, and `contractCheck` passes.

## References

- `docs/DECISION_BOARD.md` (`D-120`)
- `docs/CONTRACTS.md` section 20.10
- `docs/SPECIFICATION.md` sections 7 F-1 and 12
- [ADR-0117](0117-vehicle-list-loading-means-unknown.md) (`D-116`)
- [ADR-0116](0116-mount-onboarding-navigation-once.md) (`D-115`)
- [ADR-0122](0122-make-first-run-vehicle-creation-mandatory.md) (`D-121`)

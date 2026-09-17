# ADR-0172 / D-171 - Awaitable Sync Cycle for User-Initiated Refresh

## Status

Accepted

The owner selected option B on 2026-09-13 during the `E3-03` owner-review round.

## Context

Before `E3-03`, `VehicleSliceRuntime.refresh()` had a small, observable contract:

- `LOCAL_OWNER` or offline -> `Outcome.Ok(Unit)`, no error surfaced;
- otherwise, a failed `pullChanges` returned `Outcome.Err`.

`E3-03` replaced the staged direct pull with the singleton `SyncController`. Its first shape made
`refresh()` a fire-and-forget `requestSync(SyncTrigger.PullToRefresh)` that unconditionally returned
`Ok(Unit)`. Two regressions followed and neither was intended:

1. The `if (result is Outcome.Err) publish(message = result.error.toErrorMessage())` branch in
   `VehicleListStateHolder.refresh()` became dead in production. A failed pull surfaced no message,
   so the user saw a silent no-op.
2. `refreshing` returned to `false` before the cycle had even started, so the pull-to-refresh
   indicator stopped immediately and reported success regardless of the outcome.

The underlying cause is a missing public contract: `SyncController` only exposed the fire-and-forget
`requestSync`, so a caller that needed the result had no way to obtain it. `retryFailed()` already
demonstrated the symmetric outcome-returning shape.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Derive the refresh indicator from the existing `status` flow | No new public method; the `status` flow already carries `Syncing`/`Pending`/`Failed`. | Conflates a user-initiated refresh with background cycles: any automatic `Periodic`/`PostWriteDebounce` cycle would animate the pull-to-refresh indicator. A cycle refused for offline or `LOCAL_OWNER` never passes through `Syncing`, so the indicator would either never start or never stop. It also cannot carry the underlying `Err`, so the error branch stays dead. |
| B. Add `suspend fun sync(reason): Outcome<Unit, AppError>` and have `refresh()` await it | Restores the exact pre-`E3-03` outcomes; a failed pull returns `Err` and the indicator stays on until the outcome is known. Symmetric with `retryFailed()`. Keeps the single-cycle serialization because the awaited caller joins the pending follow-up. | Widens the public `SyncController` surface in `docs/CONTRACTS.md §20.7`, which is an owner-level contract change, so it needs a decision, its ADR and the four mirror rows. |

## Decision

**Option B**: add `suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError>` to
`SyncController`, implemented so that it suspends until the cycle serving the request completes.
When a cycle is already running, the caller joins the single pending follow-up cycle through a
completion handle rather than starting a second cycle or polling `status`. `VehicleSliceRuntime.refresh()`
delegates to it and returns its result unchanged. `createVehicle`/`updateVehicle` keep using the
fire-and-forget `requestSync(PostWriteDebounce)`, because a write MUST NOT block on a network round
trip; `requestSync(reason)` is unchanged for every other caller.

## Consequences

### Positive

- The pre-`E3-03` `refresh()` error contract is restored exactly: offline or `LOCAL_OWNER` is
  `Ok(Unit)`, a failed pull is `Err`, and the pull-to-refresh indicator tracks the real outcome.
- The serialization rules of `§9.1` are unchanged: one active cycle and one pending follow-up,
  whichever entry point the trigger used.

### Negative

- `SyncController` gains one method on its public surface, and `§20.7` changes with it.
- A caller of `sync()` is held for the duration of the cycle it joins; a caller that must not block
  (a local write) has to keep using `requestSync`.

### Constraints Introduced

- `sync(reason)` MUST join the pending follow-up when a cycle is already running; it MUST NOT start a
  second concurrent cycle and MUST NOT busy-wait on `status`.
- `sync(reason)` MUST preserve the `LOCAL_OWNER`/offline `Ok(Unit)` and the failed-pull `Err`
  outcomes.
- `createVehicle`/`updateVehicle` and every non-blocking trigger MUST keep using `requestSync`.
- `failPullCycle()` MUST retain the underlying error so the awaited outcome can carry it rather than
  a bare boolean.

## Verification

- A failing pull during `refresh()` publishes the error message and clears `refreshing`.
- An offline `refresh()` returns `Ok` with no message.
- A `refresh()` issued while a cycle is already running resolves against the follow-up cycle and
  still produces exactly one follow-up.
- `DefaultSyncControllerTest` asserts `sync()` returns `Ok(Unit)` for offline and `LOCAL_OWNER`,
  carries a pull failure as `Err`, and joins the single follow-up without a second push.

## References

- `docs/CONTRACTS.md §9.1`, `§9.2`, `§9.9`, `§20.7`, `§20.10`
- `docs/SPECIFICATION.md §8`, `§12`
- `docs/TECHNICAL_PLAN.md §2`, `§9`
- `docs/BACKLOG.md` (`E3-03`)
- `docs/handoff-E3-03.md`

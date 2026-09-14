# ADR-0173 / D-172 - Make `AppGraph.close()` Safe Against an In-Flight Sync Cycle

## Status

Proposed

The `E3-03` owner-review round raised this on 2026-09-13. `E3-17` MUST NOT start until the owner
confirms the mechanism. No production fix is delivered by `E3-03`; this ADR records the hazard and
the options only.

## Context

`E1-12` (GitHub issue #42) fixed a Kotlin/Native `SIGSEGV` in `:shared` tests by cancelling
test-owned collectors before `AppGraph.close()`. It deferred the production hardening of
`AppGraph.close()` with this justification, quoted verbatim from `docs/BACKLOG.md`:

> This story is a test-infrastructure defect, not a production defect: no production code path
> closes an `AppGraph` while its state holders are still collecting.

and required that the production fix "MUST get its own story with its own human review gate".

**`E3-03` invalidates that premise.** Before it, `graphScope` hosted only short bootstrap jobs.
`E3-03` puts long-running detached sync cycles on the same scope, each performing many SQLite calls,
started by `VehicleSliceRuntime.createVehicle`/`updateVehicle` (`PostWriteDebounce`), by
`VehicleSliceRuntime.refresh` (`PullToRefresh`) and by `scheduleAdoptionRetry`.

`DefaultAppGraph.close()` runs:

```kotlin
graphScope.cancel()
try {
    (dependencies.authClient as? AutoCloseable)?.close()
} finally {
    databaseHandle.close()
}
```

`cancel()` does not join. A coroutine suspended inside an asynchronous SQLite call is still running
when `databaseHandle.close()` releases the driver, so the close path can race an in-flight query.
Both production close paths — `MainActivity.onCleared()` and `SwiftAppGraph.close()` — can now hit
it. `SyncStateHolder.close()` does not mitigate it: it cancels the holder's own collectors, not the
controller's cycle on `graphScope`.

This is a **reachable hazard, not an observed production crash**. It is exactly the production
defect `E1-12` deferred, and it touches the D-89 handle-ownership contract
(`docs/CONTRACTS.md §20.3.2`) and the gated path `core/database/**`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Make `close()` await the graph scope (`cancelAndJoin`) before releasing the handle | Correct by construction: every graph-owned coroutine is finished before the driver closes. Reuses the exact ordering `AppGraphTestHarness` already proves for tests. | `close()` becomes suspending, but `MainActivity.onCleared()` and `SwiftAppGraph.close()` are synchronous. The synchronous hosts would need a bounded blocking bridge, which reintroduces its own liveness question on the main thread. A cycle waiting on a slow remote call would delay close. |
| B. Have `:core:sync` expose an awaitable drain, and make the close path wait for the active cycle with a bounded deadline before closing the handle | Keeps the cycle lifecycle owned by `SyncController` and gives `close()` a well-defined upper bound; a bounded wait cannot hang the host forever. Directly builds on the `D-171` completion-handle shape. | A bounded deadline means a cycle exceeding it is abandoned, so the hazard is narrowed rather than eliminated; the residual window MUST be stated. The sync controller gains a drain entry point, and the graph close path becomes asynchronous or calls a suspend drain. |
| C. Keep `close()` synchronous and never close the handle until the scope reports idle, by having each cycle own its own database access and release it last | No host API change: the graph simply does not close the driver while work is live. | Requires a reference count or per-cycle handle ownership, which spreads D-89 lifetime across the controller; a cycle that never completes strands the handle and leaks the connection. It also moves the handle-ownership contract out of `AppGraph`, which is where D-89 puts it. |

## Recommendation

**Option B**, with Option A's join semantics as the backstop: `:core:sync` exposes an awaitable drain
that completes when no cycle is running and refuses new automatic cycles, `AppGraph.close()` drains
the controller and then joins `graphScope` before closing the handle, and each synchronous host calls
a bounded bridge that records the residual deadline. Option B is preferred over A because the
synchronous host surface cannot `join` without an unbounded or blocking wait, and over C because it
keeps the D-89 handle owned by `AppGraph`. The owner MUST still confirm the deadline and the residual
window.

## Consequences

### Positive

- The close paths stop racing an in-flight SQLite call.
- The fix is scoped to `:core:sync`, `:shared` and the two host close paths, and reuses the `D-171`
  completion-handle machinery rather than inventing a second mechanism.

### Negative

- A cycle that exceeds the chosen deadline is abandoned; the hazard is narrowed, not fully removed.
- `AppGraph.close()` changes shape or gains a bounded bridge, and both hosts change with it.

### Scope note added by the sixth review round: `sync()` awaiters

`D-171` added `SyncController.sync(reason)`, whose caller suspends on a `CompletableDeferred` that
`drainCycles` completes. The option B mechanism above waits for the active cycle with a bounded
deadline, but it does **not** by itself complete or fail that deferred: if the graph scope is
cancelled between `registerTrigger` and the launched `drainCycles`, or while a cycle is running,
`drainCycles` never reaches `completion.complete(...)` and `VehicleSliceRuntime.refresh()` suspends
forever. `sync()` is awaited from a caller outside `graphScope`, so cancelling `graphScope` does not
cancel the awaiting coroutine.

Option B MUST therefore be understood to include this obligation, and `E3-17` MUST satisfy it:

- On any shutdown path, the drain MUST complete or fail every in-flight `sync()` awaiter — the active
  cycle's completion, and the pending follow-up's completion — with a closed `Outcome`, so no caller
  remains suspended. A bounded deadline that abandons a cycle MUST also fail that cycle's awaiter
  rather than leaving the deferred pending.
- A test MUST suspend a `sync()` in a cycle, close the graph, and assert the `sync()` caller returns
  (with `Ok` or `Err`) rather than hanging.

### Constraints Introduced

- `E3-03` MUST NOT change `AppGraph.close()`, `DatabaseFactory`, `DatabaseHandle` or anything under
  `core/database/**`; the fix belongs to `E3-17`.
- Exactly one owner closes the `DatabaseHandle` (D-89), and closure stays idempotent.
- The fix MUST NOT delete the `E1-12` test-level mitigation; the production fix and the test
  ordering are independent guarantees.
- The drain MUST complete or fail every in-flight `sync()` awaiter before or during close, so no
  caller is left suspended on a deferred the drain will never complete.
- Any residual window left by a bounded deadline MUST be stated in the evidence and, if it is a
  crash risk, recorded in `docs/SECURITY.md`.

## Verification

- A test closes the graph while a cycle is suspended inside the remote call and asserts no SQLite
  call runs after the driver closes.
- A test closes the graph while a cycle is executing a local transaction and asserts the same.
- Both `MainActivity.onCleared()` and `SwiftAppGraph.close()` are exercised.
- The chosen deadline, and what happens to a cycle that exceeds it, is stated with the evidence.

## References

- `docs/BACKLOG.md` `E1-12`, `E3-17`
- GitHub issue #42
- `docs/CONTRACTS.md §11.6`, `§14`, `§20.3.2`
- `docs/adr/0090-*` (D-89 handle ownership), ADR-0172 / D-171
- `docs/handoff-E1-12.md`, `docs/handoff-E3-03.md`

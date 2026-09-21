# ADR-0188 - Await the periodic sync before completing platform background work

## Status

Accepted

The ADR status MUST equal the status of the same decision ID in `docs/DECISION_BOARD.md`.

## Context

`D-181` made the `SyncTriggerAdapter` the real platform scheduling port and said, in its ADR, that the
platform worker "performs the trigger by asking the process graph for its controller". `E3-04`
implemented that as a fire-and-forget request: the Android `PeriodicSyncWorker.doWork()` called
`requestPeriodicSync()` and returned `Result.success()` immediately, and the iOS `BGTask` handler
called `requestSync(SyncTrigger.Periodic)` and then `setTaskCompletedWithSuccess(true)`.

Both platforms tie the process's runnable life to that call. `WorkManager` keeps the process alive
while `doWork()` executes, and returning releases the execution lease; `BGTask.setTaskCompleted`
tells iOS the task has ended and the system may then suspend the process. `requestSync` is
deliberately fire-and-forget - it returns as soon as admission has decided, and the cycle runs on the
graph scope's own dispatcher - so both hosts were reporting completion *before* the cycle they had
just triggered, and the cycle could be cut off mid-flight by the platform suspending the process.

`docs/CONTRACTS.md §9.8` states the cadence and `§9.1` requires the single in-process controller.
Neither says which controller entry point the platform lease must use, which is what let the two hosts
choose the one that ends before the work does.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: the platform worker and handler await `sync(SyncTrigger.Periodic)` on the existing process graph before reporting completion, and iOS installs an expiration handler | The lease covers the work it was granted for, which is the only reason the platform granted it. `SyncController.sync(reason)` already exists and already awaits the accepted cycle's real outcome, so this adds no controller API and no new state. The controller still owns persistence and retry, so a sync failure stays a sync concern rather than becoming a platform retry | The host now blocks on graph work, so an unbounded cycle can hold a lease until the platform's own deadline. That is what the iOS expiration handler is for, and it is why Android keeps returning success rather than a retry policy |
| Option B: keep fire-and-forget and let the platform cut the cycle off | No change, and the worker returns immediately | The defect this ADR exists to fix: WorkManager releases the lease and iOS may suspend the process while the cycle is still pushing and pulling. A periodic backup that is routinely interrupted mid-cycle is worse than no periodic trigger, because it also leaves outbox rows marked `SYNCING` |
| Option C: have the worker build its own controller or graph so it can await one | The worker would own its own lifetime | Forbidden by `D-89` and `§9.1`: one `AppGraph` per process, because it owns the single `DatabaseHandle`, and one `SyncController`. A second graph would also open a second connection to the same database file |
| Option D: give the worker a repository or database dependency so it can observe progress | The worker could poll instead of awaiting | Makes the platform boundary a product consumer, which `§11.6` and the module rules exist to prevent. It would also duplicate retry and status logic the controller already owns |

## Decision

The selected option is: **Option A**.

`D-187` supersedes **only** the immediate-return clause of `D-181` - the part of ADR-0182 that
described the worker and handler as requesting a cycle and reporting success without waiting for it.
Everything else `D-181` decided stays in force, unchanged:

- the `SyncTriggerAdapter` remains the real platform scheduling port, arranged once per graph;
- there is still exactly one `AppGraph` per process, one `SyncController`, and the platform path
  reaches it rather than constructing its own;
- Android still arranges the cadence with `enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, <periodic request>)`;
- iOS still uses the single `BGTaskScheduler` identifier.

What changes is how the platform-owned execution lease ends. The Android worker calls
`runPeriodicWork { AndroidAppGraph.runPeriodicSync() }`, which awaits
`syncController().sync(SyncTrigger.Periodic)` before returning `Result.success()`. The iOS handler
launches the same `sync(SyncTrigger.Periodic)` on a process-owned scope and calls
`setTaskCompletedWithSuccess` through an idempotent gate that is also released by the task's
expiration handler.

Neither path gains a repository, a database handle or a second graph. A sync `Outcome.Err` does not
become a WorkManager retry and does not fail the `BGTask`: the controller already owns persistence,
backoff and retry state, and the lease exists to keep the process runnable rather than to introduce a
second retry engine.

## Consequences

### Positive

- The background lease now covers the cycle it triggered, so a periodic backup is not cut off
  mid-flight by the platform reclaiming the process.
- No new controller API: `sync(reason)` already awaited the accepted outcome, so the fix is which
  entry point the two hosts call.
- The iOS expiration handler gives the platform a way to end a lease that overruns, and the idempotent
  gate makes a double completion impossible whichever side wins.
- The one-graph, one-controller invariant is preserved and now provable at the source, because both
  hosts name the same controller call.

### Negative

- The host blocks on graph work, so a cycle that outlives the platform deadline is cancelled rather
  than allowed to finish. On iOS that is the expiration handler; on Android it is the system's own
  execution limit. The work is not lost, because the outbox and the retry state survive the process.
- `Outcome.Err` is discarded at the platform boundary. That is deliberate - the controller is the
  retry authority - but it does mean a platform-side observer cannot tell a failed cycle from a
  successful one without reading the controller's status.
- A test seam was added to `:core:sync` (`SyncConcurrencyHooks`) so the admission-versus-shutdown
  handshake is reachable from a single-threaded scheduler. It defaults to no-ops and changes no
  behaviour, but it is production surface that exists for tests.

### Constraints Introduced

- A platform-owned periodic execution lease MUST await `sync(SyncTrigger.Periodic)` before reporting
  completion to the platform.
- A platform worker or handler MUST NOT construct a graph or a controller, and MUST NOT take a
  repository, a database handle or any product dependency.
- A sync failure MUST NOT be converted into a platform retry policy; the controller owns retry.
- iOS MUST install an expiration handler and MUST call `setTaskCompletedWithSuccess` at most once.
- `requestSync(reason)` remains the entry point for foreground and in-process triggers; `sync(reason)`
  is the entry point for a platform lease that must outlive the request.

## Verification

`AndroidSyncSchedulingTest.periodicWorkDoesNotCompleteBeforeTheControllerCall` proves the worker
stays suspended while the controller call is pending, using the pure `runPeriodicWork` helper and the
already accepted coroutine stack, with no `work-testing` dependency. Source fixtures in
`IosCompositionContractTest` and `PlatformHostContractTest` assert that Android `doWork()` delegates to
that helper and no production code calls `requestPeriodicSync()` any more, and that iOS calls
`sync(SyncTrigger.Periodic)`, installs `expirationHandler` before starting the job, and completes the
task only through the idempotent gate. The exact commands and their results are in
`docs/handoff-E3-04.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-187`)
- `docs/CONTRACTS.md §9.1`, `§9.8`, `§20.10`
- `docs/adr/0182-make-the-sync-trigger-adapter-the-scheduling-port.md` (`D-181`, clause superseded)
- `docs/adr/0183-make-the-app-graph-process-scoped.md`
- Android `CoroutineWorker`: <https://developer.android.com/reference/androidx/work/CoroutineWorker>
- Apple `BGTask`: <https://developer.apple.com/documentation/backgroundtasks/bgtask>
- Apple task completion: <https://developer.apple.com/documentation/backgroundtasks/bgtask/settaskcompleted(success:)>
- `docs/BACKLOG.md` (`E3-04`)

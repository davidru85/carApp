# ADR-0182 - Make the `SyncTriggerAdapter` the real platform scheduling port

## Status

Accepted

## Context

`docs/CONTRACTS.md §20.10` and `§9.8` describe a `SyncTriggerAdapter` that fires `Periodic` from
platform wiring, and `§9.1` requires the Android platform trigger to use
`enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, …)` and the iOS one a single `BGTaskScheduler` identifier. When
`E3-04` started, the adapter was dead wiring: the provider graph supplied `SyncTriggerAdapter { }`,
an empty lambda, so no host ever arranged a periodic cycle. The `Periodic` trigger existed as an enum
member and as a declaration in prose, and nothing reached it. The 6-hour cadence of `§9.8` was
therefore unreachable on both platforms, and the only automatic triggers were the ones the graph
already derived in process (the connectivity edge and the post-write call site).

The adapter cannot be implemented in shared code. `WorkManager` and `BGTaskScheduler` are platform
APIs, and the architecture rules keep every `:integration:*` and platform type out of `:shared`. So
the question was not whether the port is needed — it is already declared — but who calls it, and with
what.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: the graph calls `adapter.schedule(SyncTrigger.Periodic)` once at construction; each host's adapter arranges the recurring background work, and the platform's worker/handler requests the cycle by calling `requestSync(Periodic)` on the same in-process `SyncController` | The graph owns *when* scheduling is arranged, which is the graph's own lifecycle, and the platform owns *how*, which is the only part it can implement. `§9.1`'s single-controller invariant is preserved because the worker routes through the graph's controller rather than building a second graph. It matches the shape `D-108` and `D-126` already established for `LocaleProvider` and `ConnectivityObserver`: a real platform boundary injected into the provider graph | The scheduler is asked to hold a cadence without firing immediately, so the adapter carries the recurring nature (a `WorkManager` periodic request repeats itself; a `BGAppRefreshTask` resubmits from inside its handler). That asymmetry is real and must be commented rather than hidden |
| Option B: the graph schedules a one-shot cycle whenever it wants a sync, and the platform reschedules each time | No adapter at all | Abandons the declaration `§20.10` and `§9.8` already make, and puts a 6-hour policy decision about background wake-ups into shared product code. It also duplicates the cadence the platform scheduler is designed to own |
| Option C: each host builds its own sync controller for the background path | No change to the graph | Breaks `D-89`: two `AppGraph` instances in one process hold two `DatabaseHandle`s over one SQLite file, and `§9.1` permits exactly one `SyncController` |

## Decision

The selected option is: **Option A**.

`DefaultAppGraph` calls `dependencies.syncTriggerAdapter.schedule(SyncTrigger.Periodic)` exactly once,
immediately after it arms the local-owner adoption and connectivity recovery observation. It is
requested once per graph because re-submitting the same periodic work on every trigger would keep
resetting the platform's schedule.

Only `Periodic` is routed through the adapter. `PostWriteDebounce` is fired where the write commits
(it has its own owner in the vehicle slice runtime) and `ConnectivityRecovered` is derived from the
injected `ConnectivityObserver`'s offline-to-online edge by the graph itself. Routing either of them
through a platform scheduler would add latency to a trigger the graph already observes exactly, for no
benefit. The distinction is: the adapter exists for triggers whose *cadence survives process death*,
not for triggers whose cause is an in-process event.

`firebaseAppProviders` gained a `syncTriggerAdapter: SyncTriggerAdapter` parameter whose default is
`noSyncScheduling`, a named `SyncTriggerAdapter { }` that schedules nothing. The default is the
honest state for a provider graph built without a host (a test or the staged path): nothing is
arranged, and it is not presented as a success.

The Android adapter arranges `enqueueUniquePeriodicWork(SYNC_WORK, KEEP, PeriodicWorkRequest)`; its
`PeriodicSyncWorker` calls `androidAppGraph.requestPeriodicSync()`, which requests the cycle on the
process graph's controller and returns `Result.success()` because a deferred cycle is not a worker
failure. The iOS adapter submits a `BGAppRefreshTaskRequest` under the single
`com.ruizurraca.carapp.sync` identifier and its handler resubmits the next request before requesting
the same cycle. The Android library that cadence needs is pinned by `D-184`.

## Consequences

### Positive

- The `Periodic` trigger is reachable on both platforms, which is the substance of the `E3-04`
  acceptance criterion "the five triggers of `§9.8` exist with the stated constants".
- The scheduling policy stays on the platform and the lifecycle stays in the graph. The graph does
  not learn what `WorkManager` or `BGTaskScheduler` are; the hosts do not learn when the graph is
  ready to schedule.
- The provider graph keeps one shape for every platform boundary: `LocaleProvider`,
  `ConnectivityObserver` and now `SyncTriggerAdapter` are all injected with a documented default.
- `AppGraphTriggerAdapterTest` proved the wiring was dead before this change and now pins that the
  graph asks for `Periodic` exactly once, so the exact defect cannot return.

### Negative

- The two platforms implement the recurring schedule differently: `WorkManager` repeats a periodic
  request by itself, while `BGTaskScheduler` requires the next request to be submitted from inside
  the handler. The adapter contract does not model that difference, so each adapter's comment carries
  it.
- `noSyncScheduling` means a provider graph built without a host silently arranges nothing. That is
  intended for tests, and it is why the default is a named constant with a comment rather than an
  inline lambda: a reader can see that "no scheduling" is a deliberate state, not an oversight.

### Constraints Introduced

- `SyncTriggerAdapter.schedule(SyncTrigger.Periodic)` is called by `DefaultAppGraph` once per graph.
  A second call site would reset the platform schedule.
- A platform trigger MUST request its cycle on the graph's own `SyncController`. A worker or handler
  MUST NOT construct a second `AppGraph`.
- The Android platform trigger MUST use `enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, …)`.
- A provider graph built without a host arranges no background scheduling and MUST NOT be presented
  as a build that does.

## Verification

`AppGraphTriggerAdapterTest` mounts the real graph with a `RecordingSyncTriggerAdapter` and asserts
that `Periodic` was requested exactly once. `:build-logic:convention:test` and the Android host
compile task prove the injection path and the worker. The Android instrumented suite on the API 36
emulator exercises the worker's controller request; its evidence is in `docs/handoff-E3-04.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-181`)
- `docs/CONTRACTS.md §9.1`, `§9.8` and `§20.10`
- `docs/adr/0185-pin-workmanager-for-the-android-background-cadence.md`
- `docs/BACKLOG.md` (`E3-04`)

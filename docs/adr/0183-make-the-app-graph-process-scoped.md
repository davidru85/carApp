# ADR-0183 - Make the app graph process-scoped and never Activity-scoped

## Status

Accepted

## Context

`docs/CONTRACTS.md §9.1` requires the `Periodic` platform trigger to route through the same in-process
`SyncController`, and `D-89` permits exactly one `AppGraph` per process because an `AppGraph` owns the
single `DatabaseHandle` over the SQLite file. `E3-04` introduces a `WorkManager` worker whose only job
is to request a periodic cycle. A worker has no Activity and no `ViewModel`: it is created by the
platform with an `Application` context, at a time when no UI exists.

Until this change the graph was built by `VehicleAppViewModel`, one per Activity, and released in
`onCleared`. That shape cannot serve a background wake-up, and it was also already wrong for the
`AppGraph`'s own lifetime: the graph is expensive (it opens the database and starts collectors) and it
was being rebuilt on every Activity recreation, including every configuration change.

The question was how the worker reaches the graph without violating `D-89`, and what the graph's
lifetime becomes once it is not owned by an Activity.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: make the graph process-scoped, built once by `CarAppApplication` and held by a process-level container; the worker and the UI both reach that one graph, and the Activity no longer closes it | One graph per process, which is what `D-89` demands and what a background trigger needs. The `AppGraph` is built once instead of per Activity recreation, so a configuration change stops reopening the database. The worker's path is the same object the UI is using, so `§9.1`'s single controller is literally the same controller | The graph is never closed during a normal process lifetime. Release happens only when the OS tears the process down, which is what iOS already does (`D-89`/`D-172`). The contract test that pinned `graph.close()` in `MainActivity.onCleared` must move to the new owner, and the instrumented test that relied on the Activity closing the graph needs a fresh graph per test |
| Option B: keep the Activity-scoped graph and let the worker build its own | No change to the UI path | Two `AppGraph` instances in one process over one SQLite file, which `D-89` forbids and `D-172` already showed to be a real hazard: a released handle leaves a live worker with no graph. The worker would also open a second database connection to a file the UI holds open |
| Option C: give the worker a lightweight path that requests a sync without a graph | The worker holds no reference at all | There is no such path: `§9.1` puts the single controller inside the graph, and a controller without the graph's repositories, outbox and remote source cannot run a cycle. It would be a second controller, which is the same violation as Option B |

## Decision

The selected option is: **Option A**.

The `AppGraph` is process-scoped. `CarAppApplication` builds it once in `onCreate` through a
process-level container (`androidAppGraph`), which exposes the installed `Application`, the graph
itself, `requestPeriodicSync()` and `requestSync(reason)`. The `PeriodicSyncWorker` calls
`androidAppGraph.requestPeriodicSync()`. `MainActivity` consumes the graph's state holders and closes
only the holders it created, never the graph. The Activity-scoped `VehicleAppViewModel` no longer
builds or owns an `AppGraph`.

The worker reaches its `Application` through the graph container, not through a second graph
construction, so the controller it requests is the one in force.

The `AppGraph.close()` release path therefore belongs to process death on Android, exactly as it
already does on iOS, where the graph lives for the app's lifetime and the system reclaims the process.
This is the semantics `D-172` accepted as its option D.

The instrumented onboarding test that previously relied on the Activity closing the graph between
tests now uses a test-only reset entry point on the graph container so each test starts from a clean
database. That entry is `internal`, documented as test-only, and is not reachable from the UI.

## Consequences

### Positive

- One `AppGraph`, one `DatabaseHandle`, one `SyncController` per process, which is what `D-89` and
  `§9.1` require and what the background trigger needs.
- A configuration change no longer reopens the database or restarts the graph's collectors.
- The Android release semantics now match the iOS ones, so the two hosts are described by one rule
  instead of two: the graph is released by process death, not by a UI callback.
- The two build-logic contract tests that had pinned the old ownership were repointed at the files
  that now own each responsibility, so the surface each one guards is asserted against its real owner.

### Negative

- The graph is not released during a session. Its database handle stays open for the process
  lifetime, which is a deliberate trade: the alternative is a released handle while a worker may still
  need it, which is a live failure rather than a theoretical leak.
- The test-only reset entry point is production code whose only caller is instrumented test
  scaffolding. It is `internal`, named and documented, and the alternative (a public reset API or a
  database file deleted under an open connection) is worse: deleting the file while the graph holds it
  open is precisely the `D-172` hazard.

### Constraints Introduced

- The `AppGraph` is built once per process by `CarAppApplication`. No Activity, `ViewModel` or worker
  MAY build another.
- A platform background trigger MUST request its cycle on the process graph, never on a graph it
  constructed.
- The Activity MUST NOT close the process graph; it closes only the state holders it created.
- The graph's release path is process death. A test that needs a clean graph MUST use the documented
  test-only reset entry point rather than closing the shared graph.

## Verification

`PlatformHostContractTest` asserts that `CarAppApplication` builds the graph, that `MainActivity`
consumes its state holders and does **not** contain `graph.close()`. `IosCompositionContractTest`
asserts the real connectivity observer and the scheduling adapter are constructed by the graph owner
on both hosts. `:androidApp:testDebugUnitTest` and the API 36 instrumented suite
(`:androidApp:connectedDebugAndroidTest`) exercise the UI path and the reset entry point. Evidence is
in `docs/handoff-E3-04.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-182`)
- `docs/CONTRACTS.md §9.1`
- `docs/DECISION_BOARD.md` (`D-89`, `D-172`)
- `docs/adr/0182-make-the-sync-trigger-adapter-the-scheduling-port.md`
- `docs/BACKLOG.md` (`E3-04`)

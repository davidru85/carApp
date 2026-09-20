# ADR-0185 - Pin WorkManager for the Android background cadence

## Status

Accepted

## Context

`docs/CONTRACTS.md §9.1` requires the Android platform trigger to use
`enqueueUniqueWork(SYNC_WORK, KEEP)`, and `§9.8` sets the periodic interval at 6 hours. Implementing
that needs a background-work library: nothing in the current dependency set can arrange work that
survives process death, and `§9.1` explicitly forbids the alternatives (an in-process timer would die
with the process, and an `AlarmManager` registration is not the shape the contract names).

`docs/DECISION_BOARD.md` is authoritative for allowed libraries, so adding one is a decision with an
ID and an ADR, not an implementation detail. The pinned version also has to be justified in
`docs/versions-matrix.md`, per the repository rule that every pin is explained there and never
repeated as a literal in CI.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: `androidx.work:work-runtime` `2.11.2` | It is the library `§9.1` already names by its API (`enqueueUniqueWork`), it is the AndroidX-endorsed background-work API, its `CoroutineWorker` matches the coroutine model the rest of the app uses, and 2.11.2 is the newest **stable** release rather than the 2.12.0-rc line | Adds a dependency to `:androidApp` only, so it does not touch the shared or iOS graphs. It is a Google-authored artifact with no transitive conflict with the pinned AGP/Compose set |
| Option B: pin `2.12.0-rc` for the newer API | Newer API surface | A release candidate in a product dependency is contrary to the repository's preference for the boring, stable choice. Nothing in `E3-04` needs an API that 2.11.2 lacks |
| Option C: an in-process scheduler without a platform library | No new dependency | Does not survive process death, which is the entire point of the periodic trigger, and does not satisfy `§9.1`'s named API |

## Decision

The selected option is: **Option A**, `androidx.work:work-runtime` at `2.11.2`.

The pin lives once, in `gradle/libs.versions.toml` as `workManager = "2.11.2"` with the accessor
`androidx-work-runtime`, and is consumed only by `:androidApp`. It is documented in
`docs/versions-matrix.md` under the Android tooling group. It is not restated in CI, which reads the
catalog.

The dependency is `implementation` in `:androidApp` only. It does not reach `:shared`, the provider
graph or the iOS composition, so it changes no shared API and no generated header.

## Consequences

### Positive

- The Android `Periodic` trigger is implementable with the exact API `§9.1` names, in the module that
  owns the Android host, with no effect on the shared or iOS graphs.
- The version is pinned once and explained once, so the rule "every pin is explained by
  `docs/versions-matrix.md`" holds and CI remains free of a literal.

### Negative

- `:androidApp` gains a dependency. It is Android-only and cannot leak into the shared graph, but it
  is a real addition to the Android build's dependency set, and `D-16`'s architecture checks do not
  constrain a host module's external dependencies.
- `WorkManager` initialises itself through its own `androidx.startup` provider. The app does not
  declare a custom `Configuration.Provider`, so the default initialisation is used and no manifest
  change is required.

### Constraints Introduced

- `androidx.work:work-runtime` MUST be pinned only in `gradle/libs.versions.toml` and documented in
  `docs/versions-matrix.md`.
- No module other than `:androidApp` MAY depend on `androidx.work`.
- The Android periodic work MUST use `enqueueUniqueWork(SYNC_WORK, KEEP)`.

## Verification

`:androidApp:assembleDebug` and `:androidApp:testDebugUnitTest` compile and run against the pinned
version. The API 36 instrumented suite exercises the Compose flows on the same build. The pin's
single source is asserted by the repository rule that CI reads the catalog; evidence is in
`docs/handoff-E3-04.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-184`)
- `docs/CONTRACTS.md §9.1`, `§9.8`
- `docs/versions-matrix.md`
- `docs/adr/0182-make-the-sync-trigger-adapter-the-scheduling-port.md`
- `docs/BACKLOG.md` (`E3-04`)

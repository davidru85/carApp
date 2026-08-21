# Agent Handoff - E0-08

## Story

`E0-08 - :core:analytics Abstraction - S` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E0-08`; a hard prerequisite for `E0-07`, because `AnalyticsTracker` is a mandatory `AppGraphDependencies` member.
- [x] Acceptance criteria reviewed — the five criteria listed under `E0-08`.
- [x] Dependencies checked — stacked on `E0-03` for `:core:common` and `:core:testing`.
- [x] Required decisions are not `Proposed` or `Pending` — none pending; applies `D-10`.
- [x] Normative sections reviewed — `docs/CONTRACTS.md §16.1` and `§20.9`; `docs/TECHNICAL_PLAN.md §4`.
- [x] Expected verification identified — exhaustiveness tests, opt-in semantics, host and Kotlin/Native runs.
- [x] Human review gates identified before work — none.
- [x] Rule 0 acknowledged — chat replies in Spanish (es-ES), every artifact in technical English.

## Scope Completed

- `:core:analytics` with `AnalyticsTracker`, the 13-leaf closed `AnalyticsEvent` hierarchy, `SyncStatusCategory`, `ConversionFailureReason`, `DeletionFailureReason`, `AnalyticsUserProperties` and `CountBucket`, all matching `§20.9`.
- `CountBucket.ofCount` writes the exact bounds of `§20.9` in one place, so a caller cannot invent a different bucketing.
- The two normative `AuthError` mappings of `§20.9` as extension functions, each with an exhaustive `when` and no `else`.
- `NoOpAnalyticsTracker` and `RecordingAnalyticsTracker` in `:core:testing`.

## Acceptance Evidence

| Criterion | Evidence |
|-----------|----------|
| The seven types match `§20.9` exactly | `AnalyticsEvent.kt`; `AnalyticsContractTest` pins the enum orders and the 13 leaves. |
| `AnalyticsEvent` is closed and no leaf carries a free-text `String`, proven by an exhaustive `when` | `AnalyticsContractTest.discriminator()` is an exhaustive `when` with **no `else`**: adding, renaming or removing a leaf stops the test compiling. No leaf declares a `String` property. |
| A no-op `AnalyticsTracker` lives in `:core:testing` | `NoOpAnalyticsTracker`. **The "and is the default in `testAppGraphDependencies(...)`" half cannot be met** — that factory does not exist; see `DEC-2` in `docs/handoff-E0-03.md`. |
| `setEnabled(false)` makes `track` and `setUserProperties` no-ops that buffer nothing | `AnalyticsOptInTest` asserts collection is off by default, that enabling afterwards replays nothing, and that disabling stops collection immediately. The "replays nothing" case is the one an implementation is most likely to get wrong. |
| No Firebase, GitLive or Android type appears in this module | The module's only dependency is `:core:common`; imports are limited to `AuthError` and `AuthProvider`. |

## Out of Scope / Not Done

- The `SyncStatus -> SyncStatusCategory` mapping of `§20.9` is **not** implemented. `SyncStatus` is a `:core:sync` type and `:core:sync` is a Phase 3 module that Phase 0 forbids creating. The mapping, including the case where a `Failed` whose counted rows all carry a connectivity `lastErrorCode` maps to `PENDING`, belongs to `E3-03`/`E3-09`. `E0-08`'s criteria do not name it.
- No Firebase Analytics implementation. That is `E3-09`, in `:integration:firebase-analytics`.
- The `§16.1` call-cadence fixture for `setUserProperties` is an `E3-09` obligation and needs presentation code that does not exist.

## Files Changed

- `core/analytics/` — new module: `AnalyticsEvent.kt`, `AnalyticsTracker.kt`, `FailureReasons.kt`, plus `AnalyticsContractTest`.
- `core/testing/src/commonMain/.../AnalyticsFakes.kt` and `AnalyticsOptInTest` (new); `core/testing/build.gradle.kts` gained the dependency.
- `settings.gradle.kts` — `include(":core:analytics")`.

## Decisions Made

- **No `SHOULD` deviated from**, and no new decision was taken, so no ADR is created by this story.
- **`CountBucket.ofCount` was added although `§20.9` declares only the enum.** The section states the bounds as exact prose; leaving them unimplemented would mean every caller re-derives them, which is how two call sites end up bucketing `20` differently. It introduces no new type.
- **`RecordingAnalyticsTracker` honours the opt-in state rather than recording unconditionally.** A tracker that always records could not express "buffers nothing", which is the part of `§16.1` most likely to be implemented wrongly.

## Verification Run

- [x] Relevant tests pass
- [ ] Lint passes (ktlint, detekt) — not configured yet; `E0-05`
- [ ] Coverage thresholds hold — Kover not applied yet; `E0-05`
- [ ] Architecture checks pass — not implemented yet; `E0-04`
- [ ] Contract check passes — not implemented yet; `E0-05`
- [x] Relevant builds pass
- [x] Documentation updated

```text
./gradlew :core:analytics:testAndroidHostTest :core:analytics:iosSimulatorArm64Test
./gradlew :core:testing:testAndroidHostTest :core:testing:iosSimulatorArm64Test
```

## Contract Impact

- [x] No contract changes.

## Decision Board Impact

- [x] No decision changes.

## Shared-Write Modules Touched

- [x] None.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Risks or Follow-ups

- The no-op tracker cannot become the `testAppGraphDependencies(...)` default until `DEC-2` is resolved.
- `E3-09` owns the `SyncStatus` mapping, the Firebase implementation and the `setUserProperties` cadence fixture. None of them is covered here.

## Human Review Gate

- [x] Not applicable.

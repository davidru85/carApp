# Agent Handoff

Fill in every section. This template is the canonical field list; `AGENTS.md` links here rather than restating it, and `.github/pull_request_template.md` is a superset of it.

## Story

`E3-04 - Repository Sync Wiring - M`

## Ready Check

- Backlog story: `E3-04 - Repository Sync Wiring - M`, `docs/BACKLOG.md` (Phase 3).
- Acceptance criteria reviewed: the five criteria of the backlog entry — (1) the UI observes only local database flows, (2) the five `§9.8` triggers exist with the stated constants, (3) platform workers only call `SyncController.requestSync(reason)`, (4) no state-holder change is required for sync correctness, and (5) `SYNC_POST_WRITE_DEBOUNCE_MS` and `SYNC_MIN_AUTOMATIC_INTERVAL_MS` are enforced or the reason each is not is recorded.
- Dependencies checked: `E3-03` (merged, PR #69) supplied the engine and the post-write call sites; `E3-17`/`D-172` (merged, PR #70) fixed `AppGraph.close()` against an in-flight cycle; `E3-08` (merged, PR #71) supplied the app graph and the provider wiring; `E2-06` (merged) supplied local owner adoption.
- Decisions checked: no blocking `Proposed`/`Pending` decision. `D-89`, `D-172`, `D-108`, `D-126` and `D-146` were read as precedents. Six new decisions were taken in this story: `D-181`–`D-186`.
- Normative sections reviewed: `docs/CONTRACTS.md §9.1` (single controller, `enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, …)`, one iOS `BGTaskScheduler` identifier), `§9.2` (admission and order), `§9.8` (the five triggers and their constants), `§9.9` (aggregate status), `§10` (`RemoteSyncSource`), `§11.6` (one `AppGraph` per process, one `DatabaseHandle` owned and released by its `close()`), `§20.7`/`§20.10` (exported sync surface), `§18` assertions 14, 34 and 35, and `docs/TECHNICAL_PLAN.md §4`.
- Expected verification: the complete required command of `AGENTS.md` (`ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test` with the four `D-75` `-x` paths), plus the Objective-C golden-header comparison and the API 36 instrumented suite.
- Human review gates identified before work: **applies**. `core/sync/**` is a CODEOWNERS-gated path, and `E3-04` changes the synchronization algorithm's admission behaviour, which is a gated topic. The story is therefore not merged on agent judgement alone.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it produces is in technical English.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-22 (fifth correction round, review of pull request #72)
- Branch and base: `story/E3-04-repository-sync-wiring`, based on `main` at `c38d1fc`; not rebased, not force-pushed, not merged.
- Current phase and latest commit: the fifth correction round, complete and verified. The round is the RED and GREEN commits for the post-write trigger, the D-185 allowlist, the `BGTaskScheduler` identifier fixtures, the `AGENTS.md` state refresh, the backlog gate and the single-read refactor, followed by the records commit.
- Push and pull-request status: pull request #72 open and not merged; the owner's gated review is the merge gate.
- Completed since the previous checkpoint: six findings from the review of pull request #72 are closed. The `§9.8` post-write trigger now fires for all six synchronized write paths through `SyncRequestingVehicleRepository` and `SyncRequestingFuelEntryRepository`, so a Vehicle delete and all three Fuel Entry writes no longer leave an outbox row waiting for an unrelated later trigger. The `D-185` trigger ban is a receiver allowlist rather than a `StateHolder` denylist, so a one-line alias can no longer evade it, and the four documents that describe `D-185` were corrected with it. The single `BGTaskScheduler` identifier is bound to a contract fixture that compares the Kotlin constant with `iosApp/Info.plist`, and it is registered in `docs/identifiers.md`. `AGENTS.md` now records `E3-08` as merged and `E3-04` as implemented on this branch with pull request #72 open, `docs/BACKLOG.md` marks the story's review gate, and `PlatformHostContractTest` reads the graph file once.
- Verification evidence and known failures: no known failures. Every command of Step 8 was run and each result is recorded in the Verification Run section.
- Open decisions or blockers: none. No decision ID was minted in this round; findings 1 and 6 are defect fixes against existing rules, finding 2 corrects how `D-185` is described, and finding 3 registers an identifier `D-181`/`D-187` already introduced.
- Exact next step: the owner's gated review of pull request #72.

## Scope Completed

- `SYNC_POST_WRITE_DEBOUNCE_MS` and `SYNC_MIN_AUTOMATIC_INTERVAL_MS` are enforced in `DefaultSyncController`: an automatic cycle is deferred until 2 s after the triggering commit, and no two automatic cycles start closer together than 30 s. Pull-to-refresh bypasses the minimum interval and never the mutex, as `§9.8` requires.
- The graph owns the triggers that are in-process: `ConnectivityRecovered` is derived from the injected `ConnectivityObserver`'s offline-to-online edge, and local owner adoption is launched with the graph.
- `Periodic` is handed to the platform through the real `SyncTriggerAdapter`: `DefaultAppGraph` asks once per graph, the Android adapter arranges `enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, …)` and the Android worker awaits the cycle on the process graph's controller for as long as its execution lease is held (`D-187`).
- `SyncStateHolder.onForegroundReturn(backgroundMillis: Long?)` applies `FOREGROUND_RESUME_THRESHOLD_MS`, with `null` meaning a cold start, which is always a trigger.
- The Android host graph is process-scoped, built once by `CarAppApplication` and consumed by the Activity without being closed.
- The iOS platform path: `BGTaskScheduler` registration and the first submission folded into `createSwiftAppGraph` (so no new exported symbol and the golden header is untouched), the handler resubmitting from inside itself before requesting the cycle on the process graph's `SyncController`, the two `Info.plist` keys, and the scene-phase foreground duration measured with `ContinuousClock`, which keeps counting across device sleep and is not a required-reason API.
- `WalkingSkeletonModel.evaluateAnonymousReminder()` was replaced by `onSceneActivated(backgroundMillis:)`, which performs the reminder evaluation and the `§9.8` foreground trigger for one foreground entry. The old wrapper had no remaining caller, and leaving it would have been a second entry point that silently skips the sync trigger.

- Every synchronized write requests the `§9.8` post-write trigger: the two repository decorators cover Vehicle create, update and delete and Fuel Entry create, update and delete, so an outbox row is never left waiting for an unrelated later trigger.

## Acceptance Evidence

- **The UI still observes only local database flows.** The Activity and the Swift model observe the state holders, which read the local database; no UI layer gained a remote read. `PlatformHostContractTest` asserts the host-to-state-holder bindings, and no view observes `RemoteSyncSource`.
- **The five `§9.8` triggers exist with the stated constants.** `AppGraphTriggerWiringTest` and `SyncStateHolderForegroundTest` pin `ConnectivityRecovered`, `AppForeground`, the post-write path and the periodic arrangement; `SyncAdmissionPolicyTest` pins the 2 s debounce and the 30 s floor against the declared constants. A single grep proves no consumer of the two constants existed before this story. The post-write trigger is covered for every synchronized write by `PostWriteSyncTriggerTest`, which asserts the six write paths and that a read and a rejected write request nothing.
- **Platform workers and handlers enter only the process graph's `SyncController`.** `PeriodicSyncWorker.doWork()` delegates to `runPeriodicWork(AndroidAppGraph::runPeriodicSync)`, and `AndroidAppGraph.runPeriodicSync()` is `require().syncController().sync(SyncTrigger.Periodic)`, so the WorkManager execution lease is held until the cycle finishes; the iOS `BGTask` handler awaits the same `sync(SyncTrigger.Periodic)` on the process graph behind an idempotent completion gate (`D-187`). Neither holds a repository, a database handle or a second graph. `PlatformHostContractTest` and `IosCompositionContractTest` assert both lease orderings and assert that the fire-and-forget entry points are absent, and `SwiftTriggerSurfaceContractTest` is the executable ban that keeps a UI-layer `SyncStateHolder.requestSync` call site from reappearing.
- **No state holder change is required for sync correctness.** No state-holder change was made for that purpose; the only holder change is the new foreground entry point, which is the `§9.8` trigger itself rather than a correctness dependency.
- **Both constants are enforced (criterion 5).** Enforcement is in the controller's admission path, tested by `SyncAdmissionPolicyTest`; neither is recorded as unenforced.
- `contractCheck` output: assertions 14, 34 and 35 `PASS`; decision registry `PASS` at 187 decisions and 187 ADRs, which is the final count after `D-186` was added in the first correction round; no `PENDING` assertion. The `186` figures under Verification Run are the readings of the runs that preceded `D-186` and are left as observed.

## Out of Scope / Not Done

- `E3-05` (backup status UI), `E3-07` (tombstone purge), `E3-12` (cross-device recovery proof) and the other Phase 3 stories are untouched.
- The iOS host implementation landed in the branch; the gate runs below execute it on a real simulator.
- No change was made to the `RemoteSyncSource` providers themselves: `E3-08` already replaced the staged sources with the real Firestore-backed one, so "replace no-op remote sources" was satisfied by the merged graph and this story's work is the wiring and the triggers.

## Files Changed

- `core/sync/src/commonMain/kotlin/com/ruizurraca/carapp/core/sync/SyncEngine.kt` — the `§9.8` admission windows, the single admission funnel and the graph-independent trigger wiring.
- `core/sync/src/commonTest/kotlin/com/ruizurraca/carapp/core/sync/SyncAdmissionPolicyTest.kt` — the admission-window regressions.
- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/AppGraph.kt` — the connectivity edge, the periodic arrangement and `onForegroundReturn` plumbing.
- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt` — `SyncStateHolder.onForegroundReturn`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphTriggerWiringTest.kt`, `AppGraphTriggerAdapterTest.kt`, `SyncStateHolderForegroundTest.kt`.
- `wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt` — the `syncTriggerAdapter` boundary.
- `androidApp/src/main/java/com/ruizurraca/carapp/AndroidAppGraph.kt`, `CarAppApplication.kt`, `MainActivity.kt`, `AndroidSyncScheduling.kt`, `AndroidForegroundDuration.kt`, `AnonymousReminderCopy.kt`.
- `androidApp/src/test/java/com/ruizurraca/carapp/AndroidForegroundDurationTest.kt`.
- `androidApp/src/androidTest/java/com/ruizurraca/carapp/FirstVehicleOnboardingTest.kt` — the test-only reset.
- `iosApp/carAppApp.swift`, `iosApp/WalkingSkeletonModel.swift`, `iosApp/SceneBackgroundTracking.swift` (new), `iosApp/Info.plist`, `iosApp/project.yml` and the regenerated `iosApp/carApp.xcodeproj/project.pbxproj`.
- `composition/ios/src/iosMain/kotlin/com/ruizurraca/carapp/scheduling/IosSyncScheduling.kt` (new) and `CreateSwiftAppGraph.kt`.
- `build-logic/convention/src/main/kotlin/.../contract/SwiftTriggerSurfaceRule.kt` and its test; `PlatformHostContractTest.kt`, `IosCompositionContractTest.kt`.
- `docs/CONTRACTS.md §20.10`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/README.md`, seven ADRs (ADR-0182 through ADR-0188), `docs/versions-matrix.md`, `gradle/libs.versions.toml`, `docs/BACKLOG.md`, `shared/build/generated/objc-header/Shared.h.golden`.

## Decisions Made

Include any `SHOULD` you deviated from, and why.

- `D-181` — the `SyncTriggerAdapter` is the real platform scheduling port, asked once per graph for `Periodic` only. See ADR-0182.
- `D-182` — the app graph is process-scoped and released by process death. See ADR-0183.
- `D-183` — the foreground threshold is applied in shared code, with a nullable duration carrying the cold start. See ADR-0184.
- `D-184` — `androidx.work:work-runtime` is pinned at `2.11.2` and consumed by `:androidApp` only. See ADR-0185.
- `D-185` — the iOS trigger ban is enforced as a source rule over both file kinds, not as the Konsist fixture `§20.10` declared, and `§20.10` is corrected to require what is executable. See ADR-0186.
- `D-186` — the `§9.8` admission policy is recorded as a decision rather than living only in code comments and this handoff: a trigger inside an open window is parked rather than refused, the interval is anchored to the moment a cycle reaches its remote steps, the single follow-up is exempt from both windows, and a pull-to-refresh cycle still re-arms the floor. See ADR-0187.
- **Concurrency guarantees are tested, not exempted.** The `armFloorWindowLocked` change and the first round's `admit` publish-then-re-check were landed before their tests. The owner granted a retrospective exception for that commit order only; it cannot waive tests, because `docs/SPECIFICATION.md §11` requires every exempted change to carry executable coverage. The deterministic coverage is `SyncConcurrencyHooks` plus the three tests in `SyncAdmissionPolicyTest`: `shutdownBetweenClaimPublicationAndAdmissionReturnCompletesTheAwaiter`, `shutdownDuringFollowUpPromotionCompletesThePromotedAwaiter` and `pullToRefreshDoesNotServeAnAutomaticTriggerBeforeItsWindowBoundary`. All three were observed failing against the pre-correction controller and pass now. Their reason is the harness: both are data races between a graph-scope coroutine on a multi-threaded dispatcher and a caller of `shutdown()`, while `runTest` drives a single-threaded virtual scheduler whose interleavings are deterministic, so no failing test can be written for either race and a test that passed before and after the fix would assert nothing. The regression guard is the existing `SyncAdmissionPolicyTest` and `DefaultSyncControllerTest` suites, which step 11.1 runs and which must stay green. Steps 7 and 8 were **not** exempt: step 7 carries two new fixtures that were observed failing against the old masker before the fix, and step 8 is covered by the existing iOS UI suite on a real host.
- **The shutdown snapshot replaced the second round's two concurrency fixes.** `PendingFollowUp.requests` is still copy-on-write, and `drainCycles` still publishes before clearing, but both now sit behind one immutable `ShutdownSnapshot` published under `cycleMutex` and re-read against `shuttingDown`, so the guarantee is the handshake rather than the field ordering. Coverage is the two shutdown tests above, and the regression guard is `:core:sync:testAndroidHostTest` plus `AppGraphCloseSafetyTest`, both green.
- **The Android foreground measurement is process-scoped.** This is a defect fix against `§9.8`, not a new decision, so it mints no ID: `§9.8` already determines which returns are triggers, and holding the measurement in the composition made an Activity recreation report `null`, which the shared holder reads as a cold start. `§20.10` now states the host-scope obligation explicitly, and `PlatformHostContractTest` is the executable guard. It was observed failing on the composition-scoped construction (`Expected value to be true.`) and passing on the process-scoped one.
- Deviation: `§20.10` promised a Konsist fixture, and the enforcement is a source rule. The deviation is a **MUST** that could not be satisfied as written — Konsist parses Kotlin only and the prohibited surface includes Swift — so the normative text was corrected in the same change rather than the rule being narrowed to what Konsist can see. Recorded as `D-185` with its ADR.
- `PostWriteDebounce` and `ConnectivityRecovered` are deliberately **not** routed through the platform adapter, although `§20.10` groups the three together. Both are in-process events the graph already observes exactly, and a platform scheduler would add latency to a trigger whose cause is already known. The grouping in `§20.10` is about which layer may fire them (never Swift UI), which this story preserves.

- **A spent window no longer strands its parked batch.** `serveParkedWhenWindowsOpen()` is reached only from a window timer, so a window that opened while a `PullToRefresh` cycle was running served nothing and spent itself. When that cycle then returned before `armFloorWindowLocked()` - the connectivity gate, the `LOCAL_OWNER` gate, or an `adoption()` that throws and therefore schedules no retry - no timer remained and the parked batch waited for an unrelated later trigger. That is the dropped trigger `D-186` forbids, so `drainCycles` now applies the same `canClaimForParkedRequests()` boundary predicate when a cycle ends. This is a defect fix against an existing decision and mints no new ID. The regression is `SyncAdmissionPolicyTest.aWindowThatOpensDuringAManualCycleStillServesItsParkedTrigger`, observed failing against the pre-correction controller and passing now.
- **The post-write trigger covers every synchronized write.** Before this round only `VehicleSliceRuntime.createVehicle` and `updateVehicle` requested `SyncTrigger.PostWriteDebounce`. A Vehicle delete and all three Fuel Entry writes produced an outbox row with no trigger behind it, so the row waited for a foreground return past `FOREGROUND_RESUME_THRESHOLD_MS`, a connectivity recovery, a pull-to-refresh, or the six-hour `Periodic` cadence. `§9.8` already determines that a post-write is a trigger, so this is a defect fix against an existing rule and mints no new decision ID, exactly as the process-scoped Android foreground measurement above. The trigger moved into `SyncRequestingVehicleRepository` and `SyncRequestingFuelEntryRepository` because a delete reaches the repository directly from its list holder and never passes through a runtime wrapper. The regression is `PostWriteSyncTriggerTest`, observed failing before the decorators existed.
- **`D-185` is scoped by a receiver allowlist.** The first shape of the rule required the literal text `StateHolder` near the call site, which a one-line alias evaded. The rule now accepts only the `syncController()` route of `§9.1` and rejects every other receiver carrying a platform-owned trigger. The four mirroring documents and ADR-0186 were corrected in the same change, and the two fixtures `aPlatformOwnedTriggerBehindAnAliasedReceiverIsRejected` and `thePlatformControllerRouteIsAccepted` prove both directions.

## Verification Run

Every command below was run from the repository root in this round, and each result is the literal one.

### Fifth correction round (review of pull request #72)

- `./gradlew :shared:compileAndroidHostTest` before the decorators existed — `BUILD FAILED` with
  `Unresolved reference 'SyncRequestingVehicleRepository'` at six call sites and
  `Unresolved reference 'SyncRequestingFuelEntryRepository'` at three, which is the RED evidence for
  the post-write trigger.
- `./gradlew :shared:testAndroidHostTest --tests "com.ruizurraca.carapp.PostWriteSyncTriggerTest"` —
  `BUILD SUCCESSFUL`, **4 tests, 0 failures**.
- `./gradlew :shared:testAndroidHostTest` — `BUILD SUCCESSFUL`, **196 tests, 0 failures**.
- `./gradlew :build-logic:convention:test --tests "*SwiftTriggerSurfaceContractTest*"` — before the
  allowlist, `aPlatformOwnedTriggerBehindAnAliasedReceiverIsRejected` failed with
  `expected:<[iosApp/Fixture.swift:4 requests Periodic]> but was:<[]>`; after it,
  **9 tests, 0 failures**, including `theRepositoryDoesNotFirePlatformOwnedTriggersFromTheIosUiSurface`
  and `thePlatformControllerRouteIsAccepted`.
- The `BGTaskScheduler` identifier fixtures: with `iosApp/Info.plist` temporarily changed to
  `com.ruizurraca.carapp.sync.broken`,
  `theSingleBackgroundTaskIdentifierIsDeclaredInBothPlacesThatMustAgree` failed with
  `Info.plist MUST permit exactly the identifier the Kotlin registration uses; declared=com.ruizurraca.carapp.sync`.
  `iosApp/Info.plist` was restored with `git checkout --`, and `git diff -- iosApp/Info.plist` is now
  empty. `theSingleBackgroundTaskIdentifierIsRegisteredInTheIdentifierRegistry` failed before the
  `docs/identifiers.md` row and passes after it.
- The complete `AGENTS.md` command with the four `D-75` `-x` paths — `BUILD SUCCESSFUL`, 642 tasks,
  31 `contractCheck` assertions all `PASS` and **none `PENDING`**.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` followed by the `diff -u` against
  `shared/build/generated/objc-header/Shared.h.golden` — no output, so the golden header is
  byte-identical. Both new classes are `internal`, so nothing reached the exported surface.
- The iOS simulator, erased first: `xcodebuild … test` — `** TEST SUCCEEDED **`, 45 unit tests and
  27 UI tests with 1 skipped and 0 failures.
- `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest` on the `D-84` API 36
  device — `BUILD SUCCESSFUL`, 17 tests, 0 failures.
- `git status --porcelain` — clean after the records commit.

**Two corrections to the review brief, both reported rather than worked around.**

1. The brief's step 1.6 removes `import com.ruizurraca.carapp.core.common.SyncTrigger` from
   `VehicleSliceRuntime.kt` and its criterion 3 asserts the file then contains no `SyncTrigger` at all.
   That is wrong: `refresh()` calls `syncController.sync(SyncTrigger.PullToRefresh)`, which is the
   pull-to-refresh bypass of `§9.8` that `D-186` fixes, so the import is still required and removing it
   fails compilation. The import was kept and criterion 3 does not hold; the two remaining occurrences
   are that one call and its import.
2. The brief's fixture for the receiver alias in step 2.1 does not reproduce the defect it describes.
   In its two-line form the alias sits 42 characters before `requestSync`, inside the 120-character
   receiver window, so the old denylist still sees the literal `StateHolder` and the fixture passes
   before the fix. The committed fixture separates the alias from the call by more than the window
   (169 characters), which is what makes it fail against the denylist with
   `expected:<[iosApp/Fixture.swift:4 requests Periodic]> but was:<[]>` and pass against the allowlist.

## Contract Impact

- `docs/CONTRACTS.md §9.1`, `§9.8` and `§20.10` now say that every trigger enters the one controller through `requestSync(reason)` or `sync(reason)`, and name `sync(Periodic)` as the path a platform execution lease awaits (`D-187`).
- `docs/CONTRACTS.md §20.10` declares `suspend fun awaitClosed()` on the Kotlin-facing `AppGraph`, immediately after `close()`.
- Added one sentence to `docs/CONTRACTS.md §20.10`: the host's background measurement MUST outlive the UI that reports it, because recreating a view, a scene or an Activity is not a cold start, so a measurement discarded with that UI would report `null` and request a cycle `§9.8` does not permit. It states the existing `§9.8` rule at the host boundary and introduces no option.
- Updated `docs/CONTRACTS.md §9.1`, which now names `enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, <periodic request>)` for the `Periodic` cadence and keeps `enqueueUniqueWork(SYNC_WORK, KEEP)` for a one-shot platform trigger; the previous text named the one-shot API for the periodic cadence, which cannot express a 6 h cadence because `enqueueUniqueWork` accepts only a `OneTimeWorkRequest`.
- Updated `docs/CONTRACTS.md §20.10`: `SyncStateHolder.onForegroundReturn(backgroundMillis: Long?)` is declared as the `§9.8` foreground entry point, and the trigger-ban obligation now requires an executable check over both iOS platform file kinds instead of a Konsist fixture scoped to `iosMain`.

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md` (`D-181`, `D-182`, `D-183`, `D-184`, `D-185`, `D-186`) with ADR-0182, ADR-0183, ADR-0184, ADR-0185, ADR-0186 and ADR-0187, and the matching rows in `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- None.

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [x] Entry appended

## Risks or Follow-ups

- The iOS `BGTaskScheduler` cadence is best-effort by platform design: iOS decides whether and when a `BGAppRefreshTask` runs, so a 6-hour request is not a 6-hour guarantee. `§9.8` already states "best effort" and the foreground and connectivity triggers remain the ones a user actually observes.
- `AndroidForegroundDuration` and the Swift duration measurement implement the same four-line arithmetic on each host. Only the rule is shared. The Android copy is unit-tested; the iOS copy is exercised by the iOS host build.
- `resetForTests` is production code whose only caller is instrumented scaffolding. It is `internal`, documented as test-only and ordered so the graph closes before the file is removed.
- `SwiftTriggerSurfaceRule` is a textual rule, not a parser. Its limits (a bounded call window, a required `StateHolder` receiver, comment masking) are enumerated in ADR-0186.
- The unenforced remainder of `§9.8` is nil: all five triggers and all their constants now have a consumer.

## Human Review Gate

Applies: `core/sync/**` is a CODEOWNERS-gated path, and the change alters the synchronization admission behaviour, which is a gated topic. The owner's review is required before merge, and the `E3-04` story itself is not on the gated-stories list, so no additional story-level gate applies.

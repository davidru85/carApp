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

- Date: 2026-09-21 (second correction round, review of pull request #72)
- Branch and base: `story/E3-04-repository-sync-wiring`, based on `main` at `c38d1fc`; not rebased, not force-pushed, not merged.
- Current phase and latest commit: the second correction round, complete and verified. The round is `f51f77f` (the two shutdown races), `f0d4c53` (the red host assertion), `5850e4f` (the process-scoped foreground measurement and the `§20.10` sentence) and the handoff commit that follows; it sits on `b2f7044`.
- Push and pull-request status: pull request #72 open and not merged; the owner's gated review is the merge gate.
- Completed since the previous checkpoint: five defects found in the review of pull request #72 are closed. `PendingFollowUp.requests` is a copy-on-write `@Volatile` `List`, so `shutdown()` can no longer iterate a list `admit` is adding to. `drainCycles` builds the next cycle's run inside the same critical section that clears `pendingFollowUp`, and before clearing it, so a concurrent `shutdown()` observes the follow-up, the new active cycle, or both, and never neither. The Android foreground measurement moved from the composition to a process-scoped holder, so an Activity recreation no longer reads as a cold start and no longer requests a `SyncTrigger.AppForeground` that `§9.8` does not permit. `§20.10` states that host-scope obligation explicitly. `PlatformHostContractTest` gained the executable guard for it, and ADR-0186 and this handoff no longer carry the stale five-fixture and 186-decision claims.
- Verification evidence and known failures: no known failures. Every command of step 10 was run and each literal result is in the Verification Run section: `:core:sync:testAndroidHostTest` 101 tests green, `:shared:testAndroidHostTest` 191 tests green, `:build-logic:convention:test` 169 tests green with `androidHostBindsThePersistentGraphToSharedStateHolders` passing, `:androidApp:testDebugUnitTest` green, `contractCheck --rerun-tasks` all `PASS` with no `PENDING` at 187 decisions and 187 ADRs, the complete `AGENTS.md` command green at 642 tasks, the golden header byte-identical, and the API 36 instrumented suite green at 17 tests. The new host assertion was proved to fire by temporarily restoring the composition-scoped construction, which failed `androidHostBindsThePersistentGraphToSharedStateHolders` with `Expected value to be true.`, and passing again after the restore.
- Open decisions or blockers: none. `D-186` is `Accepted` and this round mints no decision ID. The `shared-tests` stall still needs a new owner, which is an owner decision and is recorded rather than minted.
- Exact next step: the owner's gated review of pull request #72.

## Scope Completed

- `SYNC_POST_WRITE_DEBOUNCE_MS` and `SYNC_MIN_AUTOMATIC_INTERVAL_MS` are enforced in `DefaultSyncController`: an automatic cycle is deferred until 2 s after the triggering commit, and no two automatic cycles start closer together than 30 s. Pull-to-refresh bypasses the minimum interval and never the mutex, as `§9.8` requires.
- The graph owns the triggers that are in-process: `ConnectivityRecovered` is derived from the injected `ConnectivityObserver`'s offline-to-online edge, and local owner adoption is launched with the graph.
- `Periodic` is handed to the platform through the real `SyncTriggerAdapter`: `DefaultAppGraph` asks once per graph, the Android adapter arranges `enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, …)` and the Android worker requests the cycle on the process graph's controller.
- `SyncStateHolder.onForegroundReturn(backgroundMillis: Long?)` applies `FOREGROUND_RESUME_THRESHOLD_MS`, with `null` meaning a cold start, which is always a trigger.
- The Android host graph is process-scoped, built once by `CarAppApplication` and consumed by the Activity without being closed.
- The iOS platform path: `BGTaskScheduler` registration and the first submission folded into `createSwiftAppGraph` (so no new exported symbol and the golden header is untouched), the handler resubmitting from inside itself before requesting the cycle on the process graph's `SyncController`, the two `Info.plist` keys, and the scene-phase foreground duration with a monotonic `ProcessInfo.systemUptime` clock.
- `WalkingSkeletonModel.evaluateAnonymousReminder()` was replaced by `onSceneActivated(backgroundMillis:)`, which performs the reminder evaluation and the `§9.8` foreground trigger for one foreground entry. The old wrapper had no remaining caller, and leaving it would have been a second entry point that silently skips the sync trigger.

## Acceptance Evidence

- **The UI still observes only local database flows.** The Activity and the Swift model observe the state holders, which read the local database; no UI layer gained a remote read. `PlatformHostContractTest` asserts the host-to-state-holder bindings, and no view observes `RemoteSyncSource`.
- **The five `§9.8` triggers exist with the stated constants.** `AppGraphTriggerWiringTest` and `SyncStateHolderForegroundTest` pin `ConnectivityRecovered`, `AppForeground`, the post-write path and the periodic arrangement; `SyncAdmissionPolicyTest` pins the 2 s debounce and the 30 s floor against the declared constants. A single grep proves no consumer of the two constants existed before this story.
- **Platform workers only call `SyncController.requestSync(reason)`.** `PeriodicSyncWorker.doWork()` calls `androidAppGraph.requestPeriodicSync()`, which is `require().syncController().requestSync(SyncTrigger.Periodic)`; the iOS handler does the same through `syncController()`. The worker holds no repositories, no database and no second graph. `SwiftTriggerSurfaceContractTest` is the executable ban that keeps a UI-layer `SyncStateHolder.requestSync` call site from reappearing.
- **No state holder change is required for sync correctness.** No state-holder change was made for that purpose; the only holder change is the new foreground entry point, which is the `§9.8` trigger itself rather than a correctness dependency.
- **Both constants are enforced (criterion 5).** Enforcement is in the controller's admission path, tested by `SyncAdmissionPolicyTest`; neither is recorded as unenforced.
- `contractCheck` output: assertions 14, 34 and 35 `PASS`; decision registry `PASS` at 187 decisions and 187 ADRs, which is the final count after `D-186` was added in the first correction round; no `PENDING` assertion. The `186` figures under Verification Run are the readings of the runs that preceded `D-186` and are left as observed.

## Out of Scope / Not Done

- `E3-05` (backup status UI), `E3-07` (tombstone purge), `E3-12` (cross-device recovery proof) and the other Phase 3 stories are untouched.
- The iOS host implementation was delegated; the full end-to-end gate run and the instrumented-suite run are pending its landing.
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
- `docs/CONTRACTS.md §20.10`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/README.md`, five ADRs, `docs/versions-matrix.md`, `gradle/libs.versions.toml`, `docs/BACKLOG.md`, `shared/build/generated/objc-header/Shared.h.golden`.

## Decisions Made

Include any `SHOULD` you deviated from, and why.

- `D-181` — the `SyncTriggerAdapter` is the real platform scheduling port, asked once per graph for `Periodic` only. See ADR-0182.
- `D-182` — the app graph is process-scoped and released by process death. See ADR-0183.
- `D-183` — the foreground threshold is applied in shared code, with a nullable duration carrying the cold start. See ADR-0184.
- `D-184` — `androidx.work:work-runtime` is pinned at `2.11.2` and consumed by `:androidApp` only. See ADR-0185.
- `D-185` — the iOS trigger ban is enforced as a source rule over both file kinds, not as the Konsist fixture `§20.10` declared, and `§20.10` is corrected to require what is executable. See ADR-0186.
- `D-186` — the `§9.8` admission policy is recorded as a decision rather than living only in code comments and this handoff: a trigger inside an open window is parked rather than refused, the interval is anchored to the moment a cycle reaches its remote steps, the single follow-up is exempt from both windows, and a pull-to-refresh cycle still re-arms the floor. See ADR-0187.
- **TDD exemption for the two concurrency fixes.** The `armFloorWindowLocked` change and the `admit` publish-then-re-check are exempt from the TDD workflow, and no test accompanies them. Their reason is the harness: both are data races between a graph-scope coroutine on a multi-threaded dispatcher and a caller of `shutdown()`, while `runTest` drives a single-threaded virtual scheduler whose interleavings are deterministic, so no failing test can be written for either race and a test that passed before and after the fix would assert nothing. The regression guard is the existing `SyncAdmissionPolicyTest` and `DefaultSyncControllerTest` suites, which step 11.1 runs and which must stay green. Steps 7 and 8 were **not** exempt: step 7 carries two new fixtures that were observed failing against the old masker before the fix, and step 8 is covered by the existing iOS UI suite on a real host.
- **TDD exemption for the second round's two concurrency fixes.** The `PendingFollowUp.requests` copy-on-write change and the `drainCycles` publish-before-clear change carry the same exemption already recorded for the earlier concurrency fixes, for the same reason: `runTest` drives a single-threaded virtual scheduler, and `drainCycles` has no suspension point between the `withLock` and the former `toCycleRun()` call, so neither interleaving with `shutdown()` can be expressed by a failing test. The regression guard is `:core:sync:testAndroidHostTest` plus `AppGraphCloseSafetyTest` staying green, and both do.
- **The Android foreground measurement is process-scoped.** This is a defect fix against `§9.8`, not a new decision, so it mints no ID: `§9.8` already determines which returns are triggers, and holding the measurement in the composition made an Activity recreation report `null`, which the shared holder reads as a cold start. `§20.10` now states the host-scope obligation explicitly, and `PlatformHostContractTest` is the executable guard. It was observed failing on the composition-scoped construction (`Expected value to be true.`) and passing on the process-scoped one.
- Deviation: `§20.10` promised a Konsist fixture, and the enforcement is a source rule. The deviation is a **MUST** that could not be satisfied as written — Konsist parses Kotlin only and the prohibited surface includes Swift — so the normative text was corrected in the same change rather than the rule being narrowed to what Konsist can see. Recorded as `D-185` with its ADR.
- `PostWriteDebounce` and `ConnectivityRecovered` are deliberately **not** routed through the platform adapter, although `§20.10` groups the three together. Both are in-process events the graph already observes exactly, and a platform scheduler would add latency to a trigger whose cause is already known. The grouping in `§20.10` is about which layer may fire them (never Swift UI), which this story preserves.

## Verification Run

Exact commands, and their result.

- `./gradlew :build-logic:convention:test` — `BUILD SUCCESSFUL`, 162 tests plus the five new `SwiftTriggerSurfaceContractTest` fixtures.
- `./gradlew contractCheck --rerun-tasks` — `BUILD SUCCESSFUL`; every assertion `PASS`, no `PENDING`; 186 decisions and 186 ADRs.
- `./gradlew :shared:testAndroidHostTest` — `BUILD SUCCESSFUL`, 191 tests, 0 failures.
- `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugAndroidTestKotlin` — `BUILD SUCCESSFUL`.
- `./gradlew :wiring:firebase:compileAndroidHostTest :wiring:firebase:compileKotlinIosSimulatorArm64` — `BUILD SUCCESSFUL`.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test` with the four `D-75` `-x` paths — `BUILD SUCCESSFUL`, 642 tasks.
- The `objc-header-golden-check` step locally: `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64`, then `diff -u shared/build/generated/objc-header/Shared.h.golden composition/ios/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h` — byte-identical, 1766 lines.
- `:androidApp:connectedDebugAndroidTest` on the `E1_07_API_36` emulator — `BUILD SUCCESSFUL`, 17 tests, 0 failures, including both `FirstVehicleOnboardingTest` cases that now reset through the graph.
- The `ios-simulator-build` step locally: `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -destination "id=$DEVICE_ID" ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` — `** BUILD SUCCEEDED **`, and the built bundle's `Info.plist` carries both keys.
- The `ios-simulator-build` test action locally: `xcodebuild … test` — `** TEST SUCCEEDED **`, 27 tests with 1 skipped and 0 failures, including `VehicleAndFuelFlowUITests.testVehicleAndFuelEntryCreationFlow` (41.7 s) which drives the real app against the real graph.
- `./iosApp/generate-project.sh` reproduces the committed `project.pbxproj` byte for byte, so the four-line delta is the deterministic output of the repo's own generator.
- Pull request #72, run `35577147782`, first attempt: `ktlint`, `detekt`, `architecture-check`, `contract-check`, `android-assemble`, `android-instrumented-tests`, `ios-simulator-build`, `objc-header-golden-check` and `provider-decoupling` were green. `shared-tests` is covered by the paragraph that follows: it was green only on re-run, after the pre-existing stall.
- **`shared-tests` failed by the pre-existing ten-minute host-step stall, and the evidence below shows it is not caused by this change.** **What failed.** `The action 'Run Android application and KMP host tests' has timed out after 10 minutes`. It failed on three of four attempts (two distinct commits, and one commit that changed nothing but Markdown). Every one of those logs has several hundred `STARTED` lines, **zero** `PASSED`, **zero** `FAILED`, and no test result: the step never reported a single test outcome, so no assertion ever ran to completion and no test of this story failed. It passed on re-run twice. The stall is always in the same place — after `> Task :feature:vehicle:testAndroidHostTest`, on `VehicleStateHoldersTest` — and it is intermittent: the set of test names in a failed log is a strict subset of the set in a passing one, so no test is uniquely red.

**Why it is not this change.**

- `:feature:vehicle` imports no symbol this story touched. It declares a Gradle dependency on `:core:sync` but imports nothing from it (`grep` for `core.sync`/`SyncController` under its sources returns only the build-file line), and `VehicleListStateHolder` takes no `SyncController` and requests no sync. The stalled test class exercises holders that never reach the controller whose admission path this story changed.
- The change is additive and confined to `:core:sync`, `:shared`, `:wiring:firebase`, `androidApp` and `build-logic`. Nothing in it is on the code path the stalled test executes.
- The third failure was on commit `46784ff`, whose only changes are two Markdown files and **zero** Kotlin files. A documentation-only commit cannot introduce a Kotlin hang, and the same step failed on it.
- Locally the identical step passes in 40 s with `--rerun-tasks --no-build-cache`, and the complete required command passes in 31 s. The failure therefore depends on the CI environment, not on the sources.
- The base commit of this branch (`c38d1fc`, the merge of PR #71) also failed its own `shared-tests`. It is a repository condition, not one introduced here.
- The mechanism is the one `docs/PROJECT_LOG.md` already records, for runs `35333547781` and `35336079709`, and assigns to the test-infrastructure stories `E1-14`/`E1-17`. The same stall reached `provider-decoupling` on that round, so it is not specific to any one job.

**Measured headroom, which is what settles the attribution.** The stalled step is bimodal, not gradually slow:

| Run | Android-host step | Kotlin/Native step |
|---|---|---|
| `main` at `c38d1fc` (this branch's base) | 230 s, success | 613 s, **failed** its 600 s limit |
| This branch on a green attempt | 246 s, success | 266 s, success |
| Limit | 600 s | 600 s |

The healthy time is 246 s — **41 % of the limit**, so this change leaves 59 % headroom and does not sit near any ceiling. The change adds about 16 s to a step that took 230 s on the base, which cannot convert 354 s of headroom into a hang that produces no test result at all. The failure is qualitatively different from a slow step, and the base commit had its own `shared-tests` failure on a *different* step (Native, 613 s against the same 600 s limit), while this branch runs that same Native step in 266 s.

**It outlives its owning stories.** `docs/PROJECT_LOG.md` assigns the stall to `E1-14` and `E1-17`, and both have merged (PR #66 and PR #67). The mechanism therefore still reproduces after its owners closed, which means it now needs a new owner rather than another citation of the old one. That reassignment is an owner decision, so it is reported here and not minted as a new backlog ID.

**What was done about it.** Nothing in this story's sources, deliberately. The failure reports no test result and no assertion, so there is nothing to fix in a passing test and re-pinning one would be inventing a cause. It is recorded here and in `docs/PROJECT_LOG.md` as the pre-existing stall, which keeps it owned by `E1-14`/`E1-17` instead of quietly transferring ownership to `E3-04`. `D-175` and `D-177` already fixed the two mechanisms that were found inside `E3-03` itself; what remains is the environment-level stall those decisions left open.

### Correction round (review of pull request #72)

Every command of the review's step 11 was run from the repository root, and each result is the
observed one.

- `./gradlew :core:sync:testAndroidHostTest` — `BUILD SUCCESSFUL`, 101 tests, 0 failures. This is the
  regression guard for the two concurrency fixes.
- `./gradlew :build-logic:convention:test` — `BUILD SUCCESSFUL`; `SwiftTriggerSurfaceContractTest`
  reports **7** tests, the five original fixtures plus the two added for the masker defects.
  Both new fixtures were observed **failing** against the old local `stripComments` before the fix
  commit, and passing after the rule reused `KotlinSourceText.code`. The observed failures were
  `aNestedBlockCommentDoesNotMakeCommentedTextLookLikeACallSite` expecting `[]` and receiving
  `[iosApp/Fixture.swift:2 requests Periodic]` — a commented-out call site read as code — and
  `aSlashSlashInsideAStringLiteralDoesNotHideARealCallSite` expecting
  `[iosApp/Fixture.swift:1 requests Periodic]` and receiving `[]` — a real call site hidden by a `//`
  inside a URL string. The test commit `cbb2818` precedes the fix commit `7d6d063`.
- `./gradlew contractCheck --rerun-tasks` — `BUILD SUCCESSFUL`; every assertion `PASS`, no `PENDING`,
  and the decision registry reports **187 decisions and 187 ADRs**, one more than the 186 recorded
  before, so `D-186` is complete.
- The complete `AGENTS.md` command with the four `D-75` `-x` paths — `BUILD SUCCESSFUL`, 642 tasks.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` followed by `diff -u` against
  `shared/build/generated/objc-header/Shared.h.golden` — no output, so the golden header is
  byte-identical. The correction round does not appear in `git diff c791122..HEAD` for that file, so
  no exported declaration moved in it.
- The iOS simulator was erased first, then built and tested with the `docs/handoff-E0-06.md`
  invocation including `ARCHS=arm64`: `** BUILD SUCCEEDED **` and `** TEST SUCCEEDED **`, 27 tests
  with 1 skipped and 0 failures. No re-run was needed, so the `E1-15` flake did not fire.
- `./gradlew :androidApp:connectedDebugAndroidTest` with `ANDROID_SERIAL=emulator-5554` on the `D-84`
  API 36 device — `BUILD SUCCESSFUL`, 17 tests, 0 failures.

**One deviation inside the correction round, recorded because it changes a line the review specified.**
`admit` was restructured from the specified sequence of guard statements into a single
`if / else if / else` expression. The specified form adds a third `return` to the function and
`:core:sync:detekt` fails it with `Function admit has 5 return statements which exceeds the limit of 4
[ReturnCount]`. A baseline or suppression file is forbidden, so the function was rewritten to have two
exits while keeping the specified semantics exactly: the parker is still removed from
`parkedRequests` and completed with `PersistenceError.DatabaseUnavailable` on the re-check, the
debounce window is still armed before it is read, `PullToRefresh` still bypasses the window check, and
`windowsOpen()` is still the condition that decides a claim. `:core:sync:testAndroidHostTest` stays
green, which is the guard for that behaviour.

### Second correction round (review of pull request #72)

Every command of step 10 was run from the repository root, and each result below is the literal one.

- `./gradlew :core:sync:testAndroidHostTest` — `BUILD SUCCESSFUL`, 101 tests, 0 failures.
- `./gradlew :shared:testAndroidHostTest` — `BUILD SUCCESSFUL`, 191 tests, 0 failures.
- `./gradlew :build-logic:convention:test` — `BUILD SUCCESSFUL`, 169 tests, 0 failures, with
  `androidHostBindsThePersistentGraphToSharedStateHolders` passing.
- `./gradlew :androidApp:testDebugUnitTest` — `BUILD SUCCESSFUL`.
- `./gradlew contractCheck --rerun-tasks` — `BUILD SUCCESSFUL`; every assertion `PASS`, no `PENDING`;
  **187 decisions and 187 ADRs**.
- The complete `AGENTS.md` command with the four `D-75` `-x` paths — `BUILD SUCCESSFUL`, 642 tasks.
  Its first run failed `:androidApp:ktlintMainSourceSetCheck` with
  `AndroidForegroundDuration.kt:37:1: Needless blank line(s) (standard:no-consecutive-blank-lines)`,
  which was a stray blank line from appending the process-scoped object; removing it made the command
  pass and the fix was folded into the same commit.
- `xcrun simctl shutdown all` then `xcrun simctl erase all`, then
  `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` and the `diff -u` against
  `shared/build/generated/objc-header/Shared.h.golden` — the diff produced no output, so the golden
  header is byte-identical. No exported declaration moved in this round.
- `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest` on the `D-84` API 36
  device — `BUILD SUCCESSFUL`, 17 tests, 0 failures.
- **The new host assertion was proved to fire.** With line 88 of `AnonymousReminderCopy.kt` temporarily
  restored to `val duration = AndroidForegroundDuration()` — that is, composition-scoped —
  `./gradlew :build-logic:convention:test` reported `PlatformHostContractTest >
  androidHostBindsThePersistentGraphToSharedStateHolders FAILED` with
  `java.lang.AssertionError: Expected value to be true.` and `2 tests 1 failures`. After restoring
  `AndroidForegroundTracking.duration` the same task reported `BUILD SUCCESSFUL` with `2 tests 0
  failures`.

## Contract Impact

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

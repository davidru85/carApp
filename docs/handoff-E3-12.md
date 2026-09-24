# Agent Handoff — E3-12 Permanent-Account Cross-Device Recovery Proof

## Story

`E3-12 - Permanent-Account Cross-Device Recovery Proof - S` (`docs/BACKLOG.md`, Phase 3).

## Ready Check

- Backlog story: `E3-12 - Permanent-Account Cross-Device Recovery Proof - S`, `docs/BACKLOG.md`.
- Acceptance criteria reviewed: the four criteria of the backlog entry — (1) a contract-valid
  Vehicle written and backed up on Android under a permanent provider identity is restored from
  Firestore on iOS after starting from clean local product data and signing into the same permanent
  identity; (2) the reverse iOS-to-Android direction is covered or the handoff records why the same
  shared path makes it redundant and supplies equivalent provider-boundary evidence; (3) the proof
  never transfers an anonymous Firebase Auth session or describes anonymous identity as
  cross-device recoverable; (4) recovery remains pull-based, uses no real-time listener and does not
  introduce simultaneous multi-device use.
- Dependencies checked: `E2-02` (Firebase Auth integration) merged through pull request #53.
  `E3-04` (repository sync wiring) merged through pull request #72 as `65e7056`, which is the current
  `main` and `origin/main`. `E3-03` (the engine), `E3-08` (graph and wiring) and `E3-17`
  (`AppGraph.close()` safety) are merged.
- Decisions checked: no `Proposed` or `Pending` decision blocks this story. `D-149`, `D-150` and
  `D-173` gate `E3-15`, `E3-16` and `E3-18` only, and do not touch recovery. One decision was taken
  by this story: `D-188`, registered with ADR-0189.
- Normative sections reviewed: `docs/SPECIFICATION.md` §2 (P2, P4), §3.1, §3.2, §7 F-1/F-4/F-5, §9.1,
  §9.3, §9.4, §9.5, §11, §12; `docs/CONTRACTS.md` §9.1, §9.2, §9.3, §9.4, §11.1, §11.2, §11.4,
  §11.5, §11.6, §15.3, §20.3, §20.7, §20.8, §20.10; `docs/TECHNICAL_PLAN.md` §4, §6, §8, §9, §12, §13;
  `docs/BACKLOG.md`; `docs/DECISION_BOARD.md`.
- Expected verification: the complete non-instrumented command of `AGENTS.md`, the Objective-C
  golden-header comparison, the provider-decoupling check, the API 36 instrumented suite, the iOS
  simulator action, and a real permanent-identity acceptance on both hosts.
- Human review gates identified before work: **applies**. `E3-12` is not on the gated-stories list,
  but the story changes the Swift-facing surface (one additive `SyncTrigger` case) and touches the
  gated topics *authentication*, *synchronization* and *Swift-facing API surface*. The owner's review
  is required before merge.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-24 (third correction round applied; pull request #73 still open and unmerged)
- Branch and base: `story/E3-12-cross-device-recovery-proof`, based on `main` / `origin/main` at
  `65e7056`; not rebased, not force-pushed, not merged.
- Current phase and latest commit: complete, plus a review correction round and a CI-stall diagnosis
  round. The delivery cycle is RED `3d406ae`, GREEN `6e9f08f` (the trigger), GREEN `56d0f86` (the
  list gate), REFACTOR `7dd5bd8` (decision records, mirrors, golden header), `368af27` (story
  records), `3302e04` (the non-vacuity correction to the anonymous-identity assertion) and `fedccb1`
  (the per-wait clock advance). The review correction round is RED `0e7ae07`, GREEN `cf09d0c`,
  `b77b2ae` (test corrections and coverage) and `30670e5` (document repairs). The CI-stall diagnosis
  round is `d7aa56c`, which carries no code change.
- Push and pull-request status: pushed. Pull request #73 is open against `main`; the owner's gated
  review is the merge gate, and the agent does not merge it.
- Completed in the delivery rounds: both defects identified at intake are fixed, the six
  cross-device assertions pass, two mutation probes prove the suite is bound to the production code,
  `D-188` and ADR-0189 are registered with the four mirroring rows, `§9.8` and `§20.10` carry the new
  rules, the golden Objective-C header is regenerated, and the story records are written.
- Completed in the review correction round: `OwnerRecoveryGate` counts outstanding recoveries under
  one lock, so a cycle that completes while a later owner transition is still recovering can no
  longer resolve that owner's empty list; `SwiftTriggerSurfaceRule` bans `OwnerChanged`, which makes
  ADR-0189's enforcement claim true and `§20.10`'s new ban executable; `CrossDeviceRecoveryTest`
  asserts the whole trigger recording instead of a filtered list that could not fail;
  `VehicleStateHoldersTest` covers both branches of the new `isLoading` rule;
  `InMemoryRemoteSyncSource` applies the provider's push preconditions; and the `D-188` registry row,
  the `§9.8` routing sentence and the `§20.10` check specification are repaired.
- Verification evidence and known failures: the complete non-instrumented command passes; the API 36
  instrumented suite passes 17 tests with 0 failures; the iOS simulator action reports
  `** TEST SUCCEEDED **` with 45 unit and 27 UI tests. On CI, **all ten required checks pass** on the
  second-review head: `architecture-check`, `detekt`, `ktlint`, `contract-check`, `android-assemble`,
  `android-instrumented-tests`, `objc-header-golden-check`, `ios-simulator-build`, `shared-tests` and
  `provider-decoupling`. Both macOS steps needed re-runs to get there, and that is the `E1-18` deadlock
  rather than this change: the re-runs are the remedy `D-175` set as precedent, and no assertion has
  ever failed on this branch. The steps pass locally and repeatedly — the exact
  `provider-decoupling` Android-host command ran 6 consecutive times in 15-19 s in the delivery round
  and **10 consecutive times** in the second review round, and the whole shared suite passed 8
  consecutive runs. The failure signature is a step timeout with truncated progress, not a failed
  assertion: the `STARTED` count reaches only 77 of the 204 the same command starts locally, and a
  green CI job of the same suite shows 759. `PASSED` and `FAILED` are both zero in a hung job **and**
  in a green one, because `--console=plain` does not print per-test result lines, so neither is
  evidence on its own.
  The defect is pre-existing and not attributable to this story, and three
  independent facts establish that the head does not cause it. `main` at `65e7056` fails the identical
  step, `Run provider-free Android host tests`, with the identical 8-minute timeout and the identical
  signature (run `35718785707`, job `106716495077`: 68 `STARTED`, versus 77 on this branch, and 0
  `PASSED` in both). The stall point moves between environments: `main` dies inside
  `FuelEntryStateHolderTest`, while on this branch the three consecutive `provider-decoupling` stalls
  of the second review round all died in the same place, at
  `FuelEntryStateHolderTest > deleteRequiresConfirmationAndRemovesTheEntryFromTheReactiveList`, the
  test whose `finally` closes the harness over a live graph. A point that is stable inside one branch
  but differs across branches is what an intermittent deadlock at a shared seam looks like rather
  than a defective test; the root-cause paragraph below names that seam. And the steps still
  fit their ceilings comfortably on a healthy runner: in that same `main`
  run, `Run Android application and KMP host tests` completed in **211 s** against its 600 s limit, so
  the timeout is not a budget this story's work consumes. On this branch the exact
  `provider-decoupling` Android-host command passed **6 consecutive times** under
  `-Pcarapp.excludeFirebaseProviders=true`, and the whole shared suite passed 8 consecutive times.
  The `provider-decoupling` job was re-requested on the pushed head, and `shared-tests` then
  **passed in 6 m 58 s** on a run whose code is identical to the one whose `shared-tests` timed out
  at 10 minutes — the same commit, the same workflow, different runners. Identical code that both
  passes and hangs is the 1-in-10 recurrence the root-cause paragraph below measures; it is not a
  property of this change. The runner-environment reading of that observation was this round's
  working hypothesis and is superseded by that paragraph, which is the diagnosis of record.

  **Root cause, reproduced and proved.** The stall is not an environment mystery and not a budget
  problem. It is a deterministic deadlock in the repository's own test seam, reproduced four times
  with a live thread dump of the Gradle test worker (JDK 21 `jstack` against the `GradleWorkerMain`
  process while the step was stalled):

  - `Test worker` is parked in
    `SqlDriverDatabaseHandle.close()` -> `AndroidxSqliteDriver.close` ->
    `AndroidxDriverConnectionPool.close(ConnectionPool.kt:262)` -> `runBlocking`.
  - The blocked `runBlocking` is waiting on `writerMutex.withLock` inside the connection pool. That
    mutex is held by graph-owned work that is suspended on the *test* `TestCoroutineScheduler` and
    can therefore never be resumed: the injected `DispatcherProvider` maps `main`, `default` and `io`
    to one `Dispatchers.Unconfined` / `StandardTestDispatcher(testScheduler)`, and the pool's
    `runBlocking` seizes that scheduler's own thread. Scheduler thread blocked by `runBlocking`, and
    the coroutine it waits for needs that same thread - a self-deadlock with no timeout inside it.
  - Two stack traces pin both sides of it. `LocalOwnerAdoptionTest.tearDown:40` closes the handle
    from the test thread; `DefaultAppGraph.releaseDatabase` (`AppGraph.kt:413`) closes it from the
    deferred release waiter that `close()` starts on `dependencies.dispatchers.io`
    (`AppGraph.kt:400`) - i.e. on the same seized scheduler thread.

  This also explains why the stall follows the `SqlDriverDatabaseHandle.close` seam whenever a
  graph-owned coroutine is still in flight: `LocalOwnerAdoptionTest`,
  `VehicleListStateHolderTest`, `FuelEntryStateHolderTest` and `AppGraphTriggerWiringTest` all
  closed the driver while scheduler-bound work could still be running. The defect is
  **pre-existing and independent of this story**: `main` at `65e7056` hangs at the identical seam,
  and `D-172` (merged `E3-17`) introduced the deferred `releaseDatabase` that makes the second form
  reachable. Measured hang rate at the story HEAD, `:shared:testAndroidHostTest
  -Pcarapp.excludeFirebaseProviders=true --rerun-tasks`: **1 in 10**. Measured rate on `main`:
  ~**1 in 12**. The head does not change the rate.

  Two candidate fixes were implemented and measured against that rate, and **neither is this
  story's to ship**: they are test-infrastructure changes to shared fixtures, they would land
  unreviewed inside a gated story's diff, and the correct owner is a dedicated defect story.

  1. Making `AppGraphTestHarness.close()` await `graph.awaitClosed()` (the `E1-12` boundary) does
     not remove the deadlock. It converts the `tearDown` form into the `releaseDatabase` form,
     which was then captured in the worker dump: still `runBlocking` on the seized scheduler thread.
     Measured: still ~**1 in 6** hangs.
  2. Splitting `io` away from the scheduler (so the driver's blocking `close()` runs on a free
     thread) is the shape that removes the deadlock, but `TestDispatcherProvider` deliberately maps
     all three dispatchers to one, and `GraphTestDependenciesTest` pins that behaviour as a
     contract. Changing it supersedes `E1-14`'s confinement decision, so it cannot be done
     unilaterally here.

  The honest consequence for this story: the CI failure is a real, now-diagnosed infrastructure
  defect that this branch neither introduces nor can fix within its scope. The step's 10-minute
  limit is under-provisioned only in the sense that it turns an unbounded hang into a red step; the
  cure is the deadlock, not a larger timeout or a retry, because the hang never self-heals. This is
  recorded as a follow-up story with the evidence above so the owner can size it; it SHOULD precede
  any story that relies on a red `shared-tests` meaning a real regression.
- Completed in the second review round: the checkpoint states one commit list and one diagnosis
  instead of appended, contradictory ones; this story's log entry names `E1-18` as the stall's owner;
  `InMemoryRemoteSyncSource` drops the three members no test consumed; the device helper is named
  `signIn` because the anonymous-identity test calls it on an anonymous device; `§20.3` now requires
  `OwnerContext.observe()` to emit the current owner on subscription, which is what `D-188`'s
  `drop(1)` baseline depends on; and `E1-18` keeps only the criteria it can verify.
- Language correction: one chat reply of this round was emitted in Chinese instead of Spanish; the
  correction is stated once and recorded under Decisions Made. No artifact was affected.
- CI outcome for this round: **all ten required checks green** on `138a5d5`. Both macOS steps needed
  re-runs, which is the `E1-18` deadlock and the remedy `D-175` set as precedent; no assertion failed
  at any point. This round also re-measured the recurrence on the local exact
  `provider-decoupling` command: **10 of 10 runs passed**, 15-34 s each, so the hazard is
  latency-dependent rather than fixed-rate, and it is far more likely to be observed on a CI runner
  than on this workstation. The `STARTED` count is the only valid progress metric: a hung job stops
  at 77 of the 204 the same command starts locally, while `PASSED` and `FAILED` are zero in a green
  job too.
- Completed in the third correction round: `OwnerRecoveryGate` became the `OwnerContext` every
  owner-scoped component observes and now counts a recovery **before** publishing the owner that
  causes it, so the ordering is one relation rather than two independent collectors;
  `observeOwnerChanges()` and its `drop(1)` baseline are deleted, and a transition landing between
  construction and subscription is now detected by value comparison; `VehicleListStateHolder` reads
  the atomic count directly; the `syncController()` receiver allowlist matches the member-access shape
  so an identifier merely containing `syncController` is rejected; the clean-device assertion installs
  its observer before the transition; and `D-189` records the temporary timeout raise with Option B
  registered in `E1-18` as the real fix. Evidence for each is in the Verification Run section.
- Open decisions or blockers: none for this story. The real permanent-provider acceptance on both
  hosts is owner-run by construction; see Risks.
- Completed since the previous checkpoint (CI-stall diagnosis round): the intermittent failure of
  the two macOS required steps was diagnosed to its root cause under a live thread dump instead of
  attributed to the environment; two candidate fixes were implemented and measured, and both were
  reverted because the deadlock is pre-existing and its owner is a separate story; the diagnosis was
  recorded here with the two captured stacks and the measured rates; `E1-18` was registered in
  `docs/BACKLOG.md` with acceptance criteria, and `AGENTS.md` and the backlog's follow-up sequencing
  paragraph now state that a red `shared-tests` or `provider-decoupling` is not by itself evidence of
  a regression until `E1-18` lands. The working tree carries no code change from this round.
- Verification evidence for this round: `:shared:testAndroidHostTest --tests
  com.ruizurraca.carapp.CrossDeviceRecoveryTest --tests com.ruizurraca.carapp.OwnerRecoveryGateTest
  --rerun-tasks` - `BUILD SUCCESSFUL`, `CrossDeviceRecoveryTest` 6 tests / 0 failures; the
  `provider-decoupling` Android-host command - `BUILD SUCCESSFUL`; `contractCheck architectureCheck
  :build-logic:convention:test ktlintCheck detekt --rerun-tasks` - `BUILD SUCCESSFUL`, no `PENDING`
  assertion, 189 decisions and 3 unresolved decisions unchanged.
- Exact next step: hand pull request #73 to the owner's gated review. The two macOS required steps
  may still time out on the `E1-18` deadlock; their failure signature is now identified, is
  independent of this story, and is documented above.

## Reconnaissance Findings

Recorded before any change, with file:line evidence, from the working tree at `65e7056`. This work
found two independent product defects. Both are fixed here because the story's first acceptance
criterion — recovery *after signing in on a clean device* — is unreachable while either stands.

### Defect 1 — nothing triggered recovery on a newly resolved owner

- `SyncEngine.executeCycle` returns `Ok(Unit)` without starting a cycle when the owner is the
  sentinel: `core/sync/src/commonMain/kotlin/com/ruizurraca/carapp/core/sync/SyncEngine.kt:729`.
  Offline is refused the same way at `:724`.
- Before this story the five `§9.8` triggers and every call site were: `AppForeground` from
  `shared/.../StateHolders.kt:715`, `ConnectivityRecovered` from `shared/.../AppGraph.kt:235`,
  `PostWriteDebounce` from `shared/.../SyncRequestingVehicleRepository.kt:53` and
  `.../SyncRequestingFuelEntryRepository.kt:54`, `PullToRefresh` from
  `shared/.../VehicleSliceRuntime.kt:52`, and `Periodic` from the platform adapters.
- `core/sync` reads `ownerContext.current` once, at `:728`, and observes `OwnerContext` nowhere. The
  graph observed the auth state only to resume an interrupted conversion
  (`shared/.../AppGraph.kt:182-186`). A permanent sign-in therefore pulled nothing until an unrelated
  lifecycle trigger happened to fire.

### Defect 2 — an unrecovered empty list was presented as a confirmed one

- `VehicleListStateHolder` observes the owner undispatched
  (`feature/vehicle/.../VehicleStateHolders.kt:85`) and clears the list, the selection and the
  message in `onOwnerResolved` (`:152-161`), then starts a local observation (`:164`). On a clean
  device that observation succeeds with zero rows, so `isLoading` became `false` on an empty list.
- That is exactly the state the first-run gate acts on:
  `shouldPresentFirstVehicleCreation(isVehicleListKnown, vehicleCount, alreadyPresented)`
  (`androidApp/.../AndroidOnboarding.kt:90-94`, used at `MainActivity.kt:272-280`). On iOS the same
  decision runs in `VehicleListView.presentFirstVehicleCreationIfNeeded`
  (`iosApp/VehicleListView.swift:186`) and the presentation is non-dismissible
  (`iosApp/Onboarding.swift:68`, `iosApp/VehicleListView.swift:148`). On Android the first-run route
  removes the back affordance and swallows the system back gesture (`MainActivity.kt:378`, `:430`).
- The owner was therefore placed in a form they could not leave while their vehicle sat in
  Firestore.

### The test surface the proof needed, which did not exist

- No `RemoteSyncSource` fake stored pushes and served them back. `:core:testing`'s
  `FakeRemoteSyncSource` is scripted only (`core/testing/.../GraphDependencyFakes.kt:88-110`), and the
  only Firestore-faithful fake was `private` to `:core:sync`'s own `commonTest`
  (`core/sync/src/commonTest/.../DefaultSyncControllerTest.kt:1744-1857`). Kotlin Multiplatform
  cannot consume another module's `commonTest` (`D-56`), so the faithful replica had to move into
  `:core:testing` to be reachable from `:shared` `commonTest`.
- No test crossed two local databases. All 18 canonical backup and recovery scenarios run on one
  `FakeSyncPersistence` plus one scripted remote.
- Two independent in-memory databases are available and already exercised: `createStagedDatabaseFactory()`
  uses `AndroidxSqliteDatabaseType.Memory` (`core/database/.../StagedDatabaseFactory.kt:11-18`), and
  each `create()` builds a new driver, so two handles are genuinely isolated.

### Prerequisites for the real acceptance, and what is not automatable

- Android Google Sign-In is Credential Manager behind `GetSignInWithGoogleOption`
  (`androidApp/.../AndroidOnboarding.kt:145-178`), which needs a Google account on a
  Play-services-capable emulator; the CI emulator is `target: default` (AOSP) and cannot host it
  (`.github/workflows/ci.yml:181-187`). iOS Google Sign-In is the interactive web flow
  (`iosApp/NativeSignIn.swift:96-106`) and Apple Sign-In is `AuthenticationServices` (`:65-93`).
- No automated test on either platform completes a permanent sign-in: every end-to-end test drives
  the anonymous path (`androidApp/src/androidTest/.../OnboardingInstrumentedTestSupport.kt:16-18`,
  `iosApp/UITests/OnboardingWait.swift:5-25`).
- There is no Auth emulator, no service account and no helper script that mints a permanent-identity
  ID token without UI. `firebase.json:23-26` declares only the Firestore emulator.
- `docs/handoff-E2-03.md:3-11` states that the manual provider acceptance "stays owner-owned and is
  not a repository artifact". No completion of it is recorded anywhere.

## Scope Completed

- `SyncTrigger.OwnerChanged` is the sixth value of the closed inventory, and `DefaultAppGraph`
  requests one cycle whenever the resolved owner becomes a non-sentinel identity, observed with
  `drop(1)` so the owner already resolved at construction is a baseline rather than a transition.
- `OwnerRecoveryGate` (`shared/.../OwnerRecoveryGate.kt`) raises when the owner changes and lowers
  when the cycle it requested completes, including on failure and on cancellation. The vehicle list
  consults it before publishing `isLoading`, so an empty list whose owner's recovery is outstanding
  is never presented as a list known to be empty.
- `InMemoryRemoteSyncSource` in `:core:testing` is a replica whose pull semantics match Firestore:
  it stores pushes under the owner path, assigns the server-owned `updatedAt` the way the provider
  does, orders by the total order `(updatedAt, documentId)`, isolates owners by path, and exposes a
  page-request log. It replaces nothing: the module's existing `FakeRemoteSyncSource` stays scripted.
- `CrossDeviceRecoveryTest` (`shared/src/commonTest/...`) builds two `AppGraph`s over two
  independent in-memory databases sharing one replica, and asserts the four acceptance criteria plus
  the two defect regressions.

## Acceptance Evidence

- **Criterion 1 — Android-to-iOS recovery.** `aVehicleBackedUpByOneDeviceIsRestoredOnACleanDeviceWithTheSamePermanentIdentity`
  writes a Vehicle through device A's production form, waits for the replica to hold it, then signs
  device B in with a permanent Google session for the same UID. The restored row carries the name
  device A wrote and the permanent `ownerId`, and the list publishes it. Device B starts from clean
  local product data: its database is fresh, its outbox empty and its cursor unset.
- **Criterion 2 — the reverse direction.** `theReverseDirectionRestoresAFuelEntryWrittenByTheOtherDevice`
  covers it rather than arguing it away. The roles are swapped, so the reader is the other graph, and
  it asserts the same vehicle id, the same fuel-entry id, the odometer, the volume and the vehicle
  attachment survive the crossing.
- **Criterion 3 — no anonymous cross-device promise.** `anAnonymousIdentityBackupIsNeverRecoverableFromAnotherIdentity`
  proves the anonymous device does back up under its own UID, and that a *different* identity
  recovers nothing: its own backup path is empty and its database stays empty. The proof shares a
  *permanent identity* between devices, never a credential, so no test and no path describes an
  anonymous identity as recoverable elsewhere.
- **Criterion 4 — pull-based, no listener, one device.** `recoveryReadsBoundedPagesAndNeverOpensAListener`
  asserts the replica was reached only through bounded page requests. `InMemoryRemoteSyncSource`
  exposes no subscription at all, so there is no listener to open, and each device owns its own
  database and graph, so no simultaneous multi-device path is exercised.
- **Defect 1 regression.** `aPermanentSignInRequestsExactlyOneOwnerChangeCycle` asserts exactly one
  recovery cycle is admitted and that no write, lifecycle, connectivity or adapter trigger fired.
- **Defect 2 regression.** `aCleanDeviceNeverPublishesAKnownEmptyListForAnOwnerWhoseRecoveryIsOutstanding`
  observes the list across the owner transition and fails if it ever publishes a resolved empty list.
  The observer is installed **before** `signIn()` — it resolves the signed-out baseline, subscribes
  undispatched, then signs in — because a `StateFlow` does not replay intermediate states and a
  collector started after the transition could not have seen a publication that happened during it.
  The second review round corrected the ordering of this helper for exactly that reason.
- **Objective-C golden header.** Regenerated from `:composition:ios` and byte-identical after the
  update. The only change is one additive member,
  `SharedSyncTrigger *ownerchanged`, which is the exported ABI change `D-188` records.

## Out of Scope / Not Done

- `E3-05` (backup status UI), `E3-07` (tombstone purge) and the other Phase 3 stories are untouched.
- The real permanent-provider acceptance on two physical/emulated hosts remains owner-run: it needs
  an interactive Google or Apple sign-in, which no automated test in this repository can perform.
- The proof uses the deterministic replica rather than the live Firestore backend. The provider
  boundary itself is covered by `E3-02`'s tests and the Firestore emulator suite; this story adds no
  Kotlin test against the real backend, because none exists and the CI topology does not provide one.

## Files Changed

- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/OwnerRecoveryGateTest.kt` (new) — the counted
  recovery window.
- `build-logic/convention/src/main/kotlin/com/ruizurraca/carapp/buildlogic/contract/SwiftTriggerSurfaceRule.kt`
  and its contract test — the executable `OwnerChanged` ban.
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/presentation/VehicleStateHoldersTest.kt`
  — both branches of the recovery-window `isLoading` rule.
- `core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/PlatformAbstractions.kt` —
  `SyncTrigger.OwnerChanged`.
- `core/common/src/commonTest/kotlin/com/ruizurraca/carapp/core/common/AppErrorCodesTest.kt` — the
  inventory assertion now pins the six documented causes.
- `core/testing/src/commonMain/kotlin/com/ruizurraca/carapp/core/testing/InMemoryRemoteSyncSource.kt`
  (new) — the Firestore-faithful replica.
- `core/testing/build.gradle.kts` — the serialization dependency the replica needs.
- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/AppGraph.kt` — `observeOwnerChanges()` and the
  gate's wiring into the vehicle list holder.
- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/OwnerRecoveryGate.kt` (new).
- `feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/presentation/VehicleStateHolders.kt`
  — the `recoveryPending` input, its observer and the `isLoading` rule.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/CrossDeviceRecoveryTest.kt` (new) — the proof.
- `docs/CONTRACTS.md` (§9.8, §20.3, §20.10), `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`,
  `docs/DECISION_BOARD.md`, `docs/adr/README.md`, `docs/adr/0189-recover-a-newly-resolved-owner-through-a-dedicated-trigger.md`
  (new), `shared/build/generated/objc-header/Shared.h.golden`.
- `docs/CONTRACTS.md §20.3` — the `OwnerContext.observe()` replay requirement `D-188` depends on.
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthOwnerContextTest.kt` — the
  assertion message that names that requirement.

## Decisions Made

Include any `SHOULD` you deviated from, and why.

- `D-188` — a non-sentinel owner transition is its own sync cause, and an empty list whose owner's
  recovery is outstanding stays unresolved. Chosen by the owner at the intake decision point over
  reusing `SyncTrigger.AppForeground` (which would make the trigger name lie about its cause and blur
  the `§9.8` table, whose value is that each row names one cause) and over gating first-run creation
  without making recovery happen (which would leave the list unresolved indefinitely and redefine
  `isLoading` without fixing the pull). See ADR-0189.
- The second defect is fixed inside this story, not deferred. The owner confirmed both are in scope
  at the same decision point, because criterion 1 is unreachable while either stands. They are
  independent: the trigger alone still leaves the window the first-run gate reads.
- The proof is the deterministic two-device test, chosen by the owner over a manual-only acceptance
  and over doing both. The real provider leg stays owner-run regardless, so the automatic test is the
  only form of this evidence that can regress.
- **Rule 0 violation, self-corrected.** One chat reply of the second review round was emitted in
  Chinese (zh) instead of Spanish (es-ES). The owner flagged it; the next reply switched back to
  Spanish immediately, without re-sending the translated text. No repository artifact was affected:
  every file written in this story is in technical English. Recorded here as `AGENTS.md` requires.
- Deviation: none. No `SHOULD` was deviated from.

## Verification Run

Exact commands, and their result.

### Third correction round (`D-188` ordering, receiver shape, `D-189` stopgap)

- `:build-logic:convention:test --tests '…SwiftTriggerSurfaceContractTest' --rerun-tasks` — 12 tests,
  0 failures, including the two new fixtures.
- **Non-vacuity of the receiver fixtures.** With the allowlist temporarily reverted to the old
  substring test (`receiver.contains("syncController", ignoreCase = true)`), exactly and only
  `anAliasWhoseNameContainsSyncControllerIsRejected` and
  `anUnrelatedSyncControllerTokenDoesNotPermitAHolderCall` failed; the ten pre-existing fixtures still
  passed. The allowlist was restored and all 12 pass. Those two fixtures therefore fire against the
  defect and assert nothing else.
- `:shared:testAndroidHostTest --tests CrossDeviceRecoveryTest --tests OwnerRecoveryGateTest
  --rerun-tasks` — `BUILD SUCCESSFUL`; `CrossDeviceRecoveryTest` 6 tests / 0 failures,
  `OwnerRecoveryGateTest` 6 tests / 0 failures.
- `:feature:vehicle:testAndroidHostTest --tests VehicleStateHoldersTest --rerun-tasks` —
  `BUILD SUCCESSFUL`, 16 tests / 0 failures.
- **Non-vacuity of the count.** Removing `mutableOutstanding.update { count -> count + 1 }` from
  `OwnerRecoveryGate.launchIn` failed four gate tests —
  `aNonSentinelTransitionCountsItsRecoveryBeforeAnythingCanObserveTheNewOwner`,
  `aTransitionBetweenConstructionAndSubscriptionIsStillDetected`,
  `theCountStaysAboveZeroWhileALaterRecoveryIsStillRunning` and
  `theCountLowersWhenItsOnlyRecoveryCompletes` — and left the baseline and sentinel tests passing,
  which is the correct discrimination. The increment was restored.
- **Ordering non-falsifiability, stated rather than implied.** Moving the increment to *after* the
  owner assignment was measured: the instrumentation showed the observer reading `outstanding=1` in
  both orders, because `MutableStateFlow` conflates and a `StateFlow` observer is not resumed inline
  from the assignment. The instruction order is therefore guaranteed by construction in `launchIn`
  and documented there, and no test in this repository can falsify it. The assertion kept in
  `OwnerRecoveryGateTest` pins the observable consequence (a non-sentinel transition leaves one
  recovery counted and its cycle requested) instead of claiming to pin instruction order; the test
  says so in its own comment.
- **The clean-device regression was not RED under the new helper.** With the observer installed before
  `signIn()`, the instrumented state sequence was
  `isLoading=true vehicles=0` → `isLoading=true vehicles=0` → `isLoading=false vehicles=1`: no resolved
  empty state is published, before or after the coordinator change. The helper improvement
  (subscribe before the transition) closes the observation gap the review identified; the production
  guarantee it protects is the ordering, whose absence is not reproducible on this host at this rate.
- `contractCheck --rerun-tasks` — `[PASS] 2 … 190 decisions`, `[PASS] 3 … 190 ADRs`, `[PASS] 4 … 3
  listed`, `[PASS] 27 every protected CI job declares a timeout`, `[PASS] 28 CI jobs do not exceed the
  40-minute safety limit`, `[PASS] 29 shared-tests has stricter platform-specific step limits`,
  `[PASS] 30 all protected CI check names remain present`; no `PENDING` assertion, with the `D-189`
  15-minute step limits in place.
- Complete non-instrumented command of `AGENTS.md` — `BUILD SUCCESSFUL in 41s`, 642 tasks.
- `-Pcarapp.excludeFirebaseProviders=true :shared:testAndroidHostTest --rerun-tasks` —
  `BUILD SUCCESSFUL in 22s`.
- `:build-logic:convention:test` — `SwiftTriggerSurfaceContractTest` 12 tests / 0 failures;
  `:feature:vehicle:testAndroidHostTest` — `VehicleStateHoldersTest` 16 tests / 0 failures.
- `git diff --check` — no whitespace errors.
- **CI on `9d3ad28`.** **All ten required checks are green** on this head. `shared-tests` failed once
  by step timeout **at the raised 15-minute limit**, which is exactly what `D-189` predicted, and
  passed on the re-run; `provider-decoupling` passed first time. The temporary measure therefore
  behaves as recorded: it buys attempts rather than fixing anything, and the hung step's silence shows
  no limit converts it into a pass. `E1-18` still owes Option B.

- **Review correction round.** `OwnerRecoveryGateTest.theGateStaysRaisedWhileALaterRecoveryIsStillRunning`
  was observed failing against the single-boolean gate on the assertion that an earlier cycle must
  not resolve the newest owner's list, and passing after the counter. `anOwnerChangedRequestFromSwiftIsRejected`
  was observed failing against the three-trigger banned set and passing after the fourth was added.
- `./gradlew :shared:compileAndroidHostTest` before `OwnerChanged` existed — RED, with
  `Unresolved reference 'OwnerChanged'` at `CrossDeviceRecoveryTest.kt:370`, which is the intended
  failing state for a missing trigger.
- `./gradlew :shared:testAndroidHostTest --tests "com.ruizurraca.carapp.CrossDeviceRecoveryTest"`
  after the trigger and before the list gate — 5 of 6 pass, and
  `aCleanDeviceNeverPublishesAKnownEmptyListForAnOwnerWhoseRecoveryIsOutstanding` fails with
  `a known empty list is the state F-1 mandatory first-run creation reads`. That failure is the
  second defect, observed rather than assumed.
- `./gradlew :shared:testAndroidHostTest --tests "com.ruizurraca.carapp.CrossDeviceRecoveryTest"`
  after the gate — 6 tests, 6 passed, 0 failures.
- `./gradlew :shared:testAndroidHostTest :feature:vehicle:testAndroidHostTest :core:sync:testAndroidHostTest --rerun-tasks`
  — `BUILD SUCCESSFUL in 20s`.
- `./gradlew ktlintCheck detekt` — one formatting failure in the new replica file and two
  long lines in the new test, both fixed by `ktlintFormat`; a dangling-KDoc failure in
  `OwnerRecoveryGate.kt` was an authoring mistake and was deleted rather than suppressed.
- `./gradlew contractCheck` — `[PASS] 2. decision IDs and statuses identical across the four
  documents — 189 decisions`, `[PASS] 4. unresolved decisions are listed with a Needed by story —
  3 listed`, no `PENDING` assertion.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` then `diff -u` against
  `shared/build/generated/objc-header/Shared.h.golden` — the only difference is the added
  `ownerchanged` member; the golden was updated from the generated header and the diff is now empty.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify
  :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test
  -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test
  -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` —
  `BUILD SUCCESSFUL in 33s`.
- `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest --rerun-tasks` on the
  `D-84` API 36 `E1_07_API_36` emulator (a Play-services image) — `BUILD SUCCESSFUL`, 17 tests, 0
  failures.
- `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator
  -destination 'platform=iOS Simulator,id=56F1AD0C-42E0-499C-9469-DC91CDD8AD21' -derivedDataPath /tmp/carapp-e312-dd
  ARCHS=arm64 test` from `iosApp/` — `** TEST SUCCEEDED **`, 45 unit tests and 27 UI tests with 1
  skipped and 0 failures.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true :shared:testAndroidHostTest --rerun-tasks` — the
  exact command of the `provider-decoupling` Android-host step — `BUILD SUCCESSFUL in 23s`.
- **Mutation probe 1 (the trigger).** Removing the `ownerRecoveryGate.awaitRecovery()` call from
  `observeOwnerChanges()` fails **all six** tests of `CrossDeviceRecoveryTest`, which proves the suite
  is bound to the trigger rather than to a fixture that happens to seed data. The mutation was
  reverted.
- **Mutation probe 2 (the list gate).** Reverting the `isLoading` rule to its pre-story form fails
  exactly `aCleanDeviceNeverPublishesAKnownEmptyListForAnOwnerWhoseRecoveryIsOutstanding` with
  `a known empty list is the state F-1 mandatory first-run creation reads`, which is the second
  defect observed against the unfixed code. The mutation was reverted.
- **`settle()` advances once per wait, not once per poll.** The first version advanced a bounded span
  on every attempt, and the attempts repeat until a real-time deadline, so each round re-crossed the
  30 s automatic floor, re-armed it and released another window: hundreds of virtual seconds and a
  storm of graph cycles accumulated while the test was merely waiting for one write to commit. The
  advance and `runCurrent()` now happen once, before the real-time wait. All six assertions still
  pass and the trigger mutation still fails all six.
- **Poll-span reduction.** `settle()` now advances one post-write debounce plus a margin per poll
  instead of a full 60 s graph span. Repeatedly crossing the 30 s automatic floor would re-arm it in
  virtual time and keep a cycle chain alive instead of letting the awaited work finish, so the smaller
  span is both cheaper and closer to what the test means. All six assertions still pass, mutation
  probe 1 still fails all six, and `:shared:testAndroidHostTest --rerun-tasks` passed 8 consecutive
  times after the change.
- **Non-vacuity correction.** The anonymous-identity test originally let its second device start
  already signed in, so no owner transition occurred and "it recovered nothing" was trivially true.
  The device now starts signed out and the test waits for its recovery cycle to run, so the assertion
  is about a device that really pulled.

## Contract Impact

- `docs/CONTRACTS.md §9.8` gains the owner-change row and now states that all **six** values enter
  the same controller.
- `docs/CONTRACTS.md §20.3`'s `SyncTrigger` declaration gains `OwnerChanged` as its first member.
- `docs/CONTRACTS.md §20.10`'s trigger ban now names `OwnerChanged` alongside the three
  platform-owned triggers, and its `isLoading` rule gains the third unknown case: an empty list whose
  owner's recovery is still outstanding.
- `docs/CONTRACTS.md §20.7`'s `SyncController` surface is unchanged; the gate uses the existing
  `sync(reason)`.

## Decision Board Impact

- Added `D-188` with ADR-0189 and matching rows in `docs/SPECIFICATION.md §12`,
  `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- None.

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [x] Entry appended

## Risks or Follow-ups

- **The recovery window has no upper bound.** While the gate is raised, an empty list is `isLoading`
  with no message, which `§20.10` tells hosts to answer with a covering indicator rather than an
  error and a retry. A cycle that is slow rather than refused therefore holds a clean device's first
  run behind an indicator for as long as the provider takes to fail. Offline is unaffected: `§9.2`
  refuses the cycle immediately and the gate lowers at once.
- **A failed recovery resolves the list.** The gate lowers on failure by design, so an owner whose
  first cycle fails online still reaches first-vehicle creation over data that is still in Firestore.
  The alternative - holding the list unresolved - strands the owner behind an indicator with no exit,
  and `§20.10` records the chosen behaviour normatively.
- **The two macOS required checks can fail by step timeout on a pre-existing test-harness deadlock,
  now diagnosed and reproduced, whose owner is a separate defect story, not this one.** The deadlock,
  the two captured thread stacks and the measured rates are in the In-Progress Checkpoint above. It
  is pre-existing (`main` at `65e7056` hangs at the same seam), it is latency-dependent rather than
  fixed-rate - this round measured **10 of 10** local runs of the exact command passing - and it is
  registered as `E1-18` in `docs/BACKLOG.md`, a Phase 1 test-infrastructure follow-up carrying that
  evidence. A re-run does clear it and that is what took this round to ten green checks; what no
  timeout or retry can do is make it *impossible*, because the hang never self-heals once it starts.
  `D-176`'s job ceilings are unrelated; the fix is the deadlock, not a larger step limit.
- The permanent-provider acceptance is not automatable in this repository, and the precedent story
  that owns it (`E2-03`) has no completion record. The real two-host proof therefore needs owner-run
  interactive sign-ins; the deterministic test is what protects the behaviour from regression.
- The CI emulator cannot host the Android provider leg (`target: default`, no Play services). A
  CI-resident provider proof would need a different image and would touch the ten protected check
  names of `docs/CONTRACTS.md §18`.
- `OwnerRecoveryGate.awaitRecovery()` holds the gate on a graph-scope coroutine that awaits a cycle.
  A graph closed mid-recovery cancels it, and the `finally` lowers the gate; `sync` completes refused
  callers on shutdown (`D-172`), so no caller is stranded.
- `E3-04`'s handoff recorded the same pre-existing `shared-tests` stall as unowned. This story
  diagnoses it (see the In-Progress Checkpoint), registers the follow-up story, and states the
  consequence the earlier record could not: a red `shared-tests` or `provider-decoupling` is **not**
  evidence of a regression while `E1-18` is open, and the correct response starts by inspecting the
  step for the `SqlDriverDatabaseHandle.close` seam. This round corrected the second half of that
  guidance: inspecting is how the reader identifies the seam, and a re-run is then what actually
  clears it - which is why `AGENTS.md`'s warning about the "re-run it" reflex is about re-running
  *instead of* investigating, not about re-running at all.

## Human Review Gate

Applies: the story changes the exported `SyncTrigger` surface by one additive case, so the golden
Objective-C header changes, and it touches the gated topics *authentication*, *synchronization* and
*Swift-facing API surface*. The owner's review is required before merge.

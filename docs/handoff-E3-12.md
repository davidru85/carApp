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

- Date: 2026-09-22 (implementation complete, verification in flight)
- Branch and base: `story/E3-12-cross-device-recovery-proof`, based on `main` / `origin/main` at
  `65e7056`; not rebased, not force-pushed, not merged.
- Current phase and latest commit: REFACTOR complete at `7dd5bd8`. The cycle beneath it is RED
  `3d406ae`, GREEN `6e9f08f` (the trigger), GREEN `56d0f86` (the list gate) and REFACTOR `7dd5bd8`
  (decision records, mirrors, golden header).
- Push and pull-request status: branch is local only. Nothing pushed; no pull request yet.
- Completed since the previous checkpoint: both defects identified at intake are fixed, the six
  cross-device assertions pass, `D-188` and ADR-0189 are registered with the four mirroring rows,
  `§9.8` and `§20.10` carry the new rules, the golden Objective-C header is regenerated, and the
  complete `AGENTS.md` command is green.
- Verification evidence and known failures: the complete non-instrumented command passes
  (`BUILD SUCCESSFUL in 33s`, 31 or more contract assertions with none `PENDING`); the API 36
  instrumented suite passes 17 tests with 0 failures; the iOS simulator action is recorded under
  Verification Run. No known failure is outstanding.
- Open decisions or blockers: none for this story. The real permanent-provider acceptance on both
  hosts is owner-run by construction; see Risks.
- Exact next step: finish the iOS simulator run, then push the branch and open the pull request for
  the owner's gated review.

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
- Deviation: none. No `SHOULD` was deviated from.

## Verification Run

Exact commands, and their result.

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

- The permanent-provider acceptance is not automatable in this repository, and the precedent story
  that owns it (`E2-03`) has no completion record. The real two-host proof therefore needs owner-run
  interactive sign-ins; the deterministic test is what protects the behaviour from regression.
- The CI emulator cannot host the Android provider leg (`target: default`, no Play services). A
  CI-resident provider proof would need a different image and would touch the ten protected check
  names of `docs/CONTRACTS.md §18`.
- `OwnerRecoveryGate.awaitRecovery()` holds the gate on a graph-scope coroutine that awaits a cycle.
  A graph closed mid-recovery cancels it, and the `finally` lowers the gate; `sync` completes refused
  callers on shutdown (`D-172`), so no caller is stranded.
- `E3-04`'s handoff records a pre-existing `shared-tests` CI stall whose owner is still unassigned. A
  red `shared-tests` on this story may therefore be unrelated to it.

## Human Review Gate

Applies: the story changes the exported `SyncTrigger` surface by one additive case, so the golden
Objective-C header changes, and it touches the gated topics *authentication*, *synchronization* and
*Swift-facing API surface*. The owner's review is required before merge.

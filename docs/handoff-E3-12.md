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
  `D-173` gate `E3-15`, `E3-16` and `E3-18` only, and do not touch recovery.
- Normative sections reviewed: `docs/SPECIFICATION.md` §2 (P2, P4), §3.1, §3.2, §7 F-1/F-4/F-5, §9.1,
  §9.3, §9.4, §9.5, §11, §12; `docs/CONTRACTS.md` §9.1, §9.2, §9.3, §9.4, §11.1, §11.2, §11.4,
  §11.5, §11.6, §15.3, §20.7, §20.8, §20.10; `docs/TECHNICAL_PLAN.md` §4, §6, §8, §9, §12, §13;
  `docs/BACKLOG.md`; `docs/DECISION_BOARD.md`.
- Expected verification: the complete non-instrumented command of `AGENTS.md`, the Objective-C
  golden-header comparison, the provider-decoupling check, the API 36 instrumented suite, the iOS
  simulator action, and a real permanent-identity acceptance on both hosts.
- Human review gates identified before work: **applies**. `E3-12` is not on the gated-stories list,
  but the story changes the Swift-facing behaviour of an owner transition, and it touches the gated
  topics *authentication*, *synchronization* and *Swift-facing API surface*. The owner's review is
  required before merge.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-22 (intake)
- Branch and base: `story/E3-12-cross-device-recovery-proof`, based on `main` / `origin/main` at
  `65e7056`.
- Current phase and latest commit: intake. No product code and no test has been changed yet.
- Push and pull-request status: branch is local only. Nothing pushed; no pull request yet.
- Completed since the previous checkpoint: the reconnaissance recorded under "Reconnaissance
  Findings" below, which establishes that no test in the repository crosses two local databases, and
  that the production recovery path is gated by a permanent-identity transition that no code
  triggers.
- Verification evidence and known failures: none yet. Two product defects are identified and are
  *not* yet covered by a test; see "Reconnaissance Findings".
- Open decisions or blockers: the story has one architectural choice to settle before product code
  is written. It is stated in "Decision Required" below and MUST be answered by the owner because it
  changes the exported Swift-facing surface and the synchronization trigger inventory, both gated.
- Exact next step: the owner's answer to "Decision Required", then the RED commit.

## Reconnaissance Findings

Recorded before any change, with file:line evidence. Everything below was read from the working tree
at `65e7056`.

### The recovery path is gated by a permanent-identity transition that nothing triggers

- `SyncEngine.executeCycle` returns `Ok(Unit)` without starting a cycle when the owner is the
  sentinel: `core/sync/src/commonMain/kotlin/com/ruizurraca/carapp/core/sync/SyncEngine.kt:729`.
  Offline is refused the same way at `:724`.
- The five `§9.8` triggers and every call site: `AppForeground` from
  `shared/.../StateHolders.kt:715`, `ConnectivityRecovered` from `shared/.../AppGraph.kt:235`,
  `PostWriteDebounce` from `shared/.../SyncRequestingVehicleRepository.kt:53` and
  `shared/.../SyncRequestingFuelEntryRepository.kt:54`, `PullToRefresh` from
  `shared/.../VehicleSliceRuntime.kt:52`, `Periodic` from the platform adapters
  (`androidApp/.../AndroidAppSyncScheduling.kt`, `composition/ios/.../IosSyncScheduling.kt:122`).
- `core/sync/src/commonMain/.../SyncEngine.kt` reads `ownerContext.current` once, at `:728`. There is
  no observer of `OwnerContext.observe()` anywhere in `core/sync`.
- The graph observes the auth state only to resume an interrupted conversion
  (`shared/.../AppGraph.kt:182-186`). It requests no cycle on a sign-in.
- Consequence: on a clean device, a fresh sign-in with a permanent provider performs no pull until
  one of the five triggers happens to fire. The fastest of them is `AppForeground`, which the iOS
  host emits from `onAppear` and from a scene-phase return that actually recorded a departure
  (`iosApp/carAppApp.swift:36-52`), and which the Android host emits from the activity `ON_START`
  observer (`androidApp/.../AnonymousReminderCopy.kt:91`, called at `MainActivity.kt:185`). A cold
  sign-in therefore only recovers because the host happens to report a foreground entry.

### The vehicle list clears on the owner transition and stays empty until something pulls

- `VehicleListStateHolder` observes the owner undispatched (`feature/vehicle/.../VehicleStateHolders.kt:85`)
  and clears the list, the selection and the message in `onOwnerResolved` (`:152-161`), then starts a
  local observation (`:164`). The observation reads the local database, which on a clean device is
  empty. Nothing in this transition requests remote work.
- The result is a published, resolved empty list. That is precisely the state the first-run gate
  waits for: `shouldPresentFirstVehicleCreation(isVehicleListKnown, vehicleCount, alreadyPresented)`
  opens mandatory creation when the list is known and empty
  (`androidApp/.../AndroidOnboarding.kt:90-94`, used at `MainActivity.kt:272-280`). On iOS the same
  decision runs in `VehicleListView.presentFirstVehicleCreationIfNeeded` (`iosApp/VehicleListView.swift:186`)
  and the presentation is non-dismissible (`iosApp/Onboarding.swift:68`,
  `iosApp/VehicleListView.swift:148` `interactiveDismissDisabled`). On Android the first-run route
  removes the back affordance and swallows the system back gesture
  (`MainActivity.kt:377-381` `offersBackAffordance = false`, `:430` `BackHandler(enabled = !offersBackAffordance) {}`).
- Consequence: on a clean device, the app can present mandatory creation over data that a pull would
  have restored, and the owner has no way out of the form until they create something. `E3-12`'s
  criterion 1 requires recovery *after signing in on a clean device*, so this is in scope.

### No test in the repository crosses two local databases

- No `RemoteSyncSource` fake stores pushes and serves them back. `:core:testing`'s
  `FakeRemoteSyncSource` is scripted only (`core/testing/.../GraphDependencyFakes.kt:88-110`). The
  only Firestore-faithful fake is `private` in `:core:sync`'s `commonTest`
  (`core/sync/src/commonTest/.../DefaultSyncControllerTest.kt:1744-1857`), and KMP cannot consume
  another module's `commonTest` (`D-56`).
- The 18 canonical backup and recovery scenarios (`docs/TECHNICAL_PLAN.md` §9) all run on one
  `FakeSyncPersistence` plus one scripted remote. "A clean recovery device restores backed-up
  vehicles and fuel entries" (`DefaultSyncControllerTest.kt:88`) uses a scripted remote page, not the
  output of a push.
- The closest graph-level test, `VehicleListStateHolderTest.refreshRestoresRemoteVehicleIntoEmptyLocalDatabaseForTheSameOwner`
  (`shared/src/commonTest/.../VehicleListStateHolderTest.kt:83`), uses one in-memory database and a
  hand-written remote document, and it drives recovery from `refresh()`, which is the `PullToRefresh`
  trigger.
- No Kotlin test exercises the real `:integration:firebase-firestore` GitLive path; all 24 tests use
  an internal `RecordingFirestoreGateway`. The Firestore emulator is reached only from the Node rules
  suite (`firestore/tests/firestore.rules.test.mjs`).
- Two independent in-memory databases are available and already exercised:
  `createStagedDatabaseFactory()` uses `AndroidxSqliteDatabaseType.Memory`
  (`core/database/.../StagedDatabaseFactory.kt:11-18`), and each `create()` builds a new driver, so
  two factories or two handles from one factory are genuinely isolated.

### Prerequisites for the real acceptance, and what is not automatable

- Android Google Sign-In is Credential Manager behind `GetSignInWithGoogleOption`
  (`androidApp/.../AndroidOnboarding.kt:145-178`), which needs a Google account on a
  Play-services-capable emulator; the CI emulator is `target: default` (AOSP) and cannot host it
  (`.github/workflows/ci.yml:181-187`). iOS Google Sign-In is the interactive web flow
  (`iosApp/NativeSignIn.swift:96-106`) and Apple Sign-In is `AuthenticationServices`
  (`:65-93`). No automated test on either platform completes a permanent sign-in: every
  end-to-end test drives the anonymous path
  (`androidApp/src/androidTest/.../OnboardingInstrumentedTestSupport.kt:16-18`,
  `iosApp/UITests/OnboardingWait.swift:5-25`).
- App Check is `ENFORCED` for Authentication and Firestore, so both hosts need a registered debug
  token for a local acceptance (`docs/runbooks/development-firebase-app-check.md:27-48`).
- There is no Auth emulator, no service account and no helper script that mints a permanent-identity
  ID token without UI. `firebase.json:23-26` declares only the Firestore emulator.
- `docs/handoff-E2-03.md:3-11` states that the manual provider acceptance "stays owner-owned and is
  not a repository artifact". No completion of it is recorded anywhere. `E3-12` therefore cannot
  assume that any device is already signed in to a permanent provider.

## Decision Required

The story cannot be implemented without settling one architectural choice, and it is a gated one.
It is presented to the owner before any product code is written.

**Problem statement.** `docs/CONTRACTS.md` §9.8 closes the `SyncTrigger` inventory at exactly five
values, and `SyncStateHolder.requestSync` is restricted to user-initiated sync. A permanent sign-in
that must recover a clean device is, however, a remote-work cause that none of those five values
describes: it is not a write, not a connectivity edge, not a lifecycle return and not a periodic
cadence. Today recovery depends on a lifecycle trigger happening to fire, and `§11.2`/`§11.4` already
require the device to stop being `LOCAL_OWNER` and to adopt its rows as part of the same transition.

---

💡 **Problem Statement:** the app has no `§9.8` trigger that means "the owner changed and the new
owner's data has never been pulled". On a clean device the list resolves empty before any pull,
which opens mandatory first-run creation over data that is sitting in Firestore.

🛠️ **Solution Option A — add `SyncTrigger.OwnerChanged` and fire it once per owner transition from the graph.**
The graph observes `OwnerContext.observe()` and requests one cycle per resolved non-sentinel owner.
It is the sixth `§9.8` value, documented with the other five, and `SyncController` stays the single
entry point.
PROS: the cause becomes explicit and testable; it works for every owner transition, not only the
clean-device case; it does not depend on host lifecycle code, so both hosts behave identically by
construction; the existing admission windows still apply.
CONS: widens the closed `SyncTrigger` enum, which is exported to Swift and is part of the golden
Objective-C header, so the ABI changes and `D-`-level documentation must be updated in four
mirroring documents; adds one more trigger to the inventory the architecture and contract checks
police.

🛠️ **Solution Option B — reuse `SyncTrigger.AppForeground` and have the graph request it on the owner transition.**
No enum change and no ABI change. The graph fires the lifecycle trigger for a non-lifecycle cause.
PROS: smallest diff; no exported-surface change; no new decision row beyond the policy itself; the
admission windows and the single-controller rule are untouched.
CONS: the trigger name then lies about its cause, which makes log-based diagnosis and future
admission policy harder; it blurs the `§9.8` table, whose whole value is that each row names one
cause; a later story that needs to distinguish a real foreground return from an owner transition
would have to split them anyway.

🛠️ **Solution Option C — gate the first-run creation on the pull instead of adding a trigger.**
Keep the five triggers; make the vehicle list treat "this owner has never pulled" as not-yet-known,
so the list stays unresolved and creation is not offered until a cycle has run and settled.
PROS: no trigger and no ABI change; the fix is one predicate where the defect actually shows; it
addresses the symptom the owner sees (the mandatory form over unrecovered data) without widening the
sync contract.
CONS: it does not make recovery happen, it only stops the wrong screen from appearing — recovery
still waits for a lifecycle or connectivity trigger, so the list can stay unresolved for as long as
the device is foregrounded and online with no trigger; it adds a new "not known yet" state whose
interaction with `D-116`/`D-120` (`isLoading` semantics) has to be settled, and `docs/CONTRACTS.md`
§20.10 currently defines `isLoading` as exactly two cases.

The two defects are independent, and the option chosen for recovery does not answer the second one:
even with Option A or B, a clean device can still publish an empty list in the window between the
owner transition and the cycle settling, and the first-run gate reads exactly that state.

## Scope Completed

- none yet.

## Acceptance Evidence

- none yet.

## Out of Scope / Not Done

- `E3-05` (backup status UI), `E3-07` (tombstone purge) and the other Phase 3 stories are untouched.

## Files Changed

- `docs/handoff-E3-12.md` (this file).

## Decisions Made

- none yet.

## Verification Run

- none yet.

## Contract Impact

- No contract changes yet.

## Decision Board Impact

- No decision changes yet.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- None.

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [ ] Entry appended

## Risks or Follow-ups

- The permanent-provider acceptance is not automatable in this repository, and the precedent story
  that owns it (`E2-03`) has no completion record. The real cross-device proof therefore needs
  owner-run interactive sign-ins on both hosts.
- The CI emulator cannot host the Android provider leg (`target: default`, no Play services). A
  CI-resident proof would need a new emulator image and would touch the ten protected check names of
  `docs/CONTRACTS.md` §18.
- `E3-04`'s handoff records a pre-existing `shared-tests` CI stall whose owner is still unassigned.
  A red `shared-tests` on this story may therefore be unrelated to it.

## Human Review Gate

Applies: the story changes the Swift-facing surface and touches the gated topics *authentication*,
*synchronization* and *Swift-facing API surface*. The owner's review is required before merge.

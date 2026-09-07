# Agent Handoff

## Story

`E2-07 - Anonymous Sign-In Benefit Reminders - S`

## Ready Check

- Backlog story: `E2-07 - Anonymous Sign-In Benefit Reminders - S`, `docs/BACKLOG.md`. It is the
  first Ready Phase 2 story; `E2-04` is blocked on `E3-11`, which merged through pull request #60.
- Acceptance criteria reviewed:
  1. One configuration constant holds exactly the elapsed-day thresholds `1, 3, 8, 18`, anchored to
     the Firebase anonymous user-creation timestamp.
  2. Evaluation runs on launch and foreground return only; no operating-system notification,
     scheduler or background alarm is introduced.
  3. Only the highest unseen due reminder is emitted, and persisting its index consumes every lower
     pending reminder.
  4. The last-shown index survives process and app restarts, clears after permanent sign-in or
     successful linking, and index 3 completes the sequence.
  5. Reminders are dismissible, never gate product functionality and explain permanent-sign-in
     recovery plus the device-bound 30-day cleanup risk.
  6. Deterministic tests cover 12 hours and elapsed days 1, 2, 4, 9, 20 and 31. The day-20 case
     emits only reminder 4 and consumes reminders 1 through 3.
- Dependencies checked: `E2-02` (Firebase Auth integration) is complete and merged.
  `AuthSession.createdAt` already carries the Firebase user-creation timestamp
  (`core/auth/.../AuthContracts.kt`, populated by `FirebaseAuthClient.parseCreationTime`), so the
  schedule anchor exists and this story adds no provider work.
- Decisions checked: `D-60` (ADR-0061, anonymous identity is device-bound), `D-62` (ADR-0063, fixed
  reminder timeline), `D-64` (ADR-0065, anonymous lifecycle story split), `D-105` (continuous
  progress documentation), `D-18` (coverage), `D-38` (`DatabaseMutations` write boundary), `D-56`
  (`:shared:testing` from `commonTest` only). No dependency of this story is `Proposed` or
  `Pending`. `docs/PROJECT_LOG.md` records that `D-62` deliberately left the physical persistence
  location to this story's intake, so persistence is a new decision this story MUST register.
- Normative sections reviewed: `docs/CONTRACTS.md` §11.2, §11.3 (including
  "Anonymous sign-in benefit reminders"), §11.6, §14, §15.1, §15.3, §20.10, §18;
  `docs/SPECIFICATION.md` §3.2 (scope), §7 F-1, §11 (TDD rule and TDD commit/push workflow), §12;
  `docs/TECHNICAL_PLAN.md` §4 (dependency rules) and §6 (local data model and migration policy).
- Expected verification:
  - `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
    koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
    iosSimulatorArm64Test` with the four D-75 `-x` Firebase paths.
  - `./gradlew :androidApp:connectedDebugAndroidTest` on the D-84 API 36 emulator.
  - `xcodebuild` iOS simulator build and the iOS unit-test target.
- Human review gates identified before work: the story is marked "Human review required" in
  `docs/BACKLOG.md`. Gated paths touched: `docs/CONTRACTS.md`, `docs/SPECIFICATION.md`,
  `docs/DECISION_BOARD.md`, `AGENTS.md`, `docs/adr/**`, `core/database/**` and `core/auth/**` if
  reached. Gated topics touched: authentication and the Swift-facing API surface.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-07
- Branch and base: `story/E2-07-anonymous-benefit-reminders`, based on `main` at `7a79fab`
  (merge of pull request #60, `E3-11`).
- Current phase and latest commit: refactor phase; the decision records, the contract updates and
  this handoff are the last change of the story.
- Push and pull-request status: pull request #61 is open with all ten required checks green, after
  one `E1-14` flake re-run of `shared-tests` on the identical commit.
- Completed since the previous checkpoint: red phase (18 failing tests), green phase (schedule,
  schema version 2 and its migration, persistence, session behaviour, both hosts and the
  regenerated golden header), and the refactor phase recorded here.
- Verification evidence and known failures: the full non-instrumented CI command exits `0`;
  `:androidApp:connectedDebugAndroidTest` runs 17 tests on the D-84 API 36 emulator; the iOS app
  builds and its 40 unit tests pass. One unrelated `ViewModelLifecycleTests` crash
  (`AndroidxDriverConnectionPool.close() called while 1 reader connection(s) still checked out`)
  appeared in a full `xcodebuild test` run and did not reproduce in the following runs; it is the
  known `E1-12` / `E1-14` class of test-infrastructure flake, not a production path.
- Open decisions or blockers: none. `D-144`, `D-145` and `D-146` are recorded with their ADRs and
  the four mirrors.
- Exact next step: gated owner review of pull request #61.

## Scope Completed

- The `D-62` schedule as one configuration constant, `ANONYMOUS_REMINDER_ELAPSED_DAYS = [1, 3, 8,
  18]`, with the pure `dueAnonymousReminderIndex(accountCreatedAt, now, lastShownIndex)` in the
  `:feature:session` `domain` package, anchored to `AuthSession.createdAt`.
- Schema version 2: the device-local `anonymous_reminder` table, the committed `1.sqm` migration,
  the typed queries and `AnonymousReminderDatabaseAccess` in `:core:database`.
- `AnonymousReminderRepository` and its SQLDelight implementation, scoping the stored position to
  the anonymous UID that produced it.
- `SessionStateHolder.evaluateAnonymousReminder()`, `dismissAnonymousReminder()`, the typed
  `SessionUiState.anonymousReminderIndex`, and clearing on permanent sign-in or successful linking.
- The Android dismissible banner driven by the activity `ON_START` event, and the iOS equivalent
  driven by the active scene phase, with four escalating bodies in English and Spanish on each
  platform.
- `D-144`, `D-145` and `D-146` with their ADRs, the four decision mirrors and the contract updates.

## Acceptance Evidence

1. *One configuration constant with `1, 3, 8, 18`, anchored to the Firebase creation timestamp.*
   `ANONYMOUS_REMINDER_ELAPSED_DAYS` in
   `feature/session/.../domain/AnonymousReminder.kt`;
   `AnonymousReminderScheduleTest.theScheduleIsTheFixedElapsedDayThresholdListOfDecision62`. The
   anchor is `AuthSession.createdAt`, populated from Firebase user metadata by `E2-02`.
2. *Evaluation on launch and foreground return only; no notification, scheduler or alarm.*
   `SessionStateHolder.evaluateAnonymousReminder()` is the only entry point; Android registers an
   `ON_START` observer (`OnForegroundReturn`), iOS reacts to `scenePhase == .active`. No scheduler,
   alarm, background task or notification permission is added on either host (`D-146`).
3. *Only the highest unseen due reminder is emitted, and persisting it consumes the lower ones.*
   `dueAnonymousReminderIndex` returns `indexOfLast { elapsedDays >= threshold }` only when it
   exceeds the persisted index;
   `AnonymousReminderScheduleTest.dayTwentyEmitsOnlyTheLastReminderAndConsumesEveryEarlierOne` and
   `AnonymousReminderSessionTest.theEmittedReminderIndexIsPersistedForTheAnonymousIdentityThatSawIt`.
4. *The index survives restarts, clears after permanent sign-in or linking, and index 3 completes.*
   `SqlDelightAnonymousReminderRepositoryTest.theRecordedIndexSurvivesANewRepositoryOverTheSameDatabase`;
   `AnonymousReminderSessionTest.signingInPermanentlyClearsThePendingReminderState`;
   `AnonymousReminderScheduleTest.noReminderIsDueOnDayThirtyOneOnceTheScheduleIsComplete`.
5. *Dismissible, never gates functionality, explains recovery plus the 30-day risk.*
   `dismissAnonymousReminder()` and `AnonymousReminderSessionTest.dismissingTheReminderRemovesItFromTheStateWithoutReopeningIt`;
   both hosts render the notice above the product surface with no modal and no action of its own;
   `AnonymousReminderBannerTest.everyReminderStatesTheRecoveryBenefitAndTheThirtyDayRisk` and
   `AnonymousReminderCopyTests.testEveryReminderBodyStatesTheRecoveryBenefitAndTheThirtyDayRisk`,
   the latter asserting the English and Spanish catalogues explicitly.
6. *Deterministic tests for 12 hours and days 1, 2, 4, 9, 20, 31, with day 20 emitting only
   reminder 4.* The seven boundary tests in `AnonymousReminderScheduleTest`, each one behaviour,
   plus the clock-behind-anchor case. Determinism comes from `FakeAppClock` and a fixed anchor;
   no test reads the wall clock.

## Out of Scope / Not Done

- The notice explains permanent sign-in but offers no sign-in action. The settings entry point that
  starts conversion belongs to `E2-04`, and sign-out and deletion to `E2-05`.
- No analytics event is emitted for a shown or dismissed reminder. `docs/CONTRACTS.md §16.1` has no
  event for it and adding one would be a new decision.
- No accessibility audit beyond the existing baseline; `E4-02` owns that.
- `E1-14` and `E1-15` remain open; this story did not touch them.

## Files Changed

- `feature/session/src/commonMain/.../domain/AnonymousReminder.kt` (new),
  `.../data/SqlDelightAnonymousReminderRepository.kt` (new) and their two test files (new).
- `core/database/.../AnonymousReminderDatabaseAccess.kt` (new),
  `core/database/src/commonMain/sqldelight/.../schema.sq`, `.../database.sq`, `.../1.sqm` (new),
  `AnonymousReminderDatabaseAccessTest.kt` (new), `AnonymousReminderMigrationTest.kt` (new),
  `SchemaV1Test.kt`.
- `shared/src/commonMain/.../UiModels.kt`, `.../StateHolders.kt`, `.../AppGraph.kt`,
  `shared/src/commonTest/.../AnonymousReminderSessionTest.kt` (new),
  `shared/build/generated/objc-header/Shared.h.golden`.
- `androidApp/.../AnonymousReminderCopy.kt` (new), `.../MainActivity.kt`,
  `res/values/strings.xml`, `res/values-es/strings.xml`, `AnonymousReminderCopyTest.kt` (new),
  `AnonymousReminderBannerTest.kt` (new), `OnboardingFlowTest.kt`.
- `iosApp/AnonymousReminderView.swift` (new), `ContentView.swift`, `WalkingSkeletonModel.swift`,
  `carAppApp.swift`, `en.lproj/Localizable.strings`, `es.lproj/Localizable.strings`,
  `project.yml`, `carApp.xcodeproj/project.pbxproj`,
  `Tests/AnonymousReminderCopyTests.swift` (new).
- `docs/CONTRACTS.md`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md`,
  `docs/DECISION_BOARD.md`, `docs/adr/0145`–`0147` (new), `docs/adr/README.md`,
  `docs/BACKLOG.md`, `docs/PROJECT_LOG.md`, `AGENTS.md`, `README.md`, this handoff.

## Decisions Made

- `D-144` (ADR-0145): the last-shown index lives in a dedicated device-local `anonymous_reminder`
  table introduced by schema version 2, scoped by the anonymous UID that produced it. `D-62`
  deliberately left this to the story intake.
- `D-145` (ADR-0146): the reminder is carried by the typed `SessionUiState.anonymousReminderIndex`
  with its own dismissal intent, not by the shared `UiMessage` channel, which authentication errors
  already own.
- `D-146` (ADR-0147): evaluation is a `SessionStateHolder` intent each host calls from its own
  foreground lifecycle, rather than a new `AppGraphDependencies` member.
- TDD order exemption used, per `docs/SPECIFICATION.md §11`: the Compose and SwiftUI host code and
  the SQLDelight schema and migration. Both still carry tests. The migration tests were in fact
  written before the schema change, so only the two host banners used the relaxed order; they are
  covered by `AnonymousReminderBannerTest` and `AnonymousReminderCopyTests`.
- Rule 0 held for the whole story: every chat reply was in Spanish (es-ES) and every repository
  artifact is in technical English. No violation occurred.

## Verification Run

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test` with the four D-75 `-x` Firebase paths — exit `0`.
- `./gradlew contractCheck` — 18 assertions, all `PASS`, 147 aligned decisions and ADRs, no
  `PENDING`.
- `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest` on the D-84
  `E1_07_API_36` emulator — 17 tests, `BUILD SUCCESSFUL`.
- `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` — `** BUILD SUCCEEDED **`.
- `xcodebuild ... -destination "id=<erased simulator>" -only-testing:carAppTests test` — 40 tests,
  0 failures.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` followed by `diff -u` against
  `shared/build/generated/objc-header/Shared.h.golden` — no difference after the golden update.
- Red-phase evidence before the implementation: 18 failing tests across `:feature:session` (7),
  `:core:database` (4), `:shared` (5) and `:androidApp` (2).
- Pull request #61, first run: nine of the ten required checks green and `shared-tests` red on
  `FuelEntryStateHolderTest.litersAndPriceDeriveTotalCostWhileTyping` with
  `kotlinx.coroutines.test.UncompletedCoroutinesError` in `:shared:testAndroidHostTest`. That is the
  `E1-14` flake, not a regression of this story: the failing test builds a Fuel Entry form holder
  and never touches the session, the schedule or the schema this story changed; it fails on a
  `runTest` timeout rather than an assertion; re-running the identical commit turned all ten checks
  green; and the same test passed 25 consecutive local `--rerun-tasks` runs. The `E1-14` evidence
  in `docs/BACKLOG.md` was extended with this occurrence, because it had only ever been recorded on
  `iosSimulatorArm64`.

## Contract Impact

- Updated `docs/CONTRACTS.md` §11.3 (persistence location and UID scoping, the typed presentation
  channel and its independence from `message`, the single evaluation entry point) and §20.10
  (`SessionStateHolder.evaluateAnonymousReminder()`, `dismissAnonymousReminder()` and
  `SessionUiState.anonymousReminderIndex`, plus the note on its meaning).
- Updated `docs/TECHNICAL_PLAN.md` §6 with the `anonymous_reminder` table, its device-local
  boundary and the schema version 2 migration statement.

## Decision Board Impact

- Added `D-144` ([ADR-0145](adr/0145-store-the-anonymous-reminder-position-in-a-dedicated-local-table.md)),
  `D-145` ([ADR-0146](adr/0146-carry-the-anonymous-reminder-on-a-typed-session-state-field.md)) and
  `D-146` ([ADR-0147](adr/0147-evaluate-the-anonymous-reminder-from-a-host-foreground-intent.md)),
  all `Accepted`, with identical rows in the four mirroring documents.

## Shared-Write Modules Touched

- `:core:database`. This story owns it for its duration: schema version 2, the `1.sqm` migration,
  the `anonymous_reminder` queries and `AnonymousReminderDatabaseAccess`.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- Schema version 2 is the new migration baseline. Every later schema change extends the chain and
  ships its own populated previous-version migration test.
- A host that forgets to call `evaluateAnonymousReminder()` silently shows no reminder. Only the
  two current hosts exist, and both are covered, but a third host would have to implement it.
- The reminder cannot start sign-in yet. If `E2-04` slips, an owner who acts on the notice has no
  in-app path from it; the notice still states the risk accurately.
- The `ViewModelLifecycleTests` connection-pool crash seen once in a full `xcodebuild test` run is
  the known `E1-12` / `E1-14` flake class. It did not reproduce and no production path depends on
  it, but it makes a red `ios-simulator-build` ambiguous until `E1-14` is fixed.
- `E1-14` also fires on `:shared:testAndroidHostTest`, which this story observed and recorded in the
  backlog. Its acceptance criteria still name only `:shared:iosSimulatorArm64Test`; whoever takes
  `E1-14` MUST cover both targets, or a red `shared-tests` stays ambiguous on the JVM side.

## Human Review Gate

Applies. The story is marked "Human review required" in `docs/BACKLOG.md`, and it touches the gated
authentication and Swift-facing API surface topics of `AGENTS.md` plus the gated paths
`docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `AGENTS.md`, `docs/adr/**`
and `core/database/**`.

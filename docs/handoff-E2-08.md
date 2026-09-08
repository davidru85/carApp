# Agent Handoff

## Story

`E2-08 - Anonymous Reminder Launch and Sign-In Race Fixes - S`

## Ready Check (additional fix, 2026-09-08)

- Backlog story: the same `E2-08` story, whose scope this additional fix extends on branch
  `story/E2-08-anonymous-reminder-launch-and-race-fixes`, pull request #62. The story is already
  implemented and awaiting its gated owner review; the owner ordered an additional defect fix within
  the same scope rather than a new story.
- Defect: the auth-state collector in `SessionStateHolder` carried
  `anonymousReminderIndex` across ANY transition whose new phase was `SessionPhase.ANONYMOUS`, with
  no UID comparison. A direct move from anonymous identity A — which had already published a
  reminder index — to a different anonymous identity B let B inherit A's banner: an index never
  evaluated for B and never persisted for B, out of order with the escalating copy a later
  evaluation for B would publish (index 0 after index 2).
- Acceptance criteria for the fix:
  1. A published reminder index is dropped when the session becomes a different anonymous UID.
  2. A published reminder index is kept when the same anonymous UID re-emits.
  3. The fix stays inside `SessionStateHolder`; `AnonymousReminderRepository`, the schedule in
     `feature/session/.../domain/AnonymousReminder.kt`, `publishDueReminder`, the
     `awaitingRestoredSession` one-shot logic and any host code are untouched.
  4. `SessionUiState`'s public shape is unchanged, so the Objective-C golden header does not move.
- Decisions checked: none. This is a defect fix under the existing contracts. `D-62`, `D-144`,
  `D-145`, `D-146` and `D-147` MUST NOT change and no new decision is added; the UID scoping this
  fix enforces is already the normative behaviour of `§11.3` and of
  `SqlDelightAnonymousReminderRepository.lastShownIndex`.
- Normative sections reviewed: `docs/CONTRACTS.md` §11.3 (the reminder index belongs to the
  anonymous UID that produced it; a stored position belonging to a different anonymous UID reads
  as absent), §20.10; `docs/SPECIFICATION.md` §11 (TDD rule and the red, green, refactor commit
  order).
- Expected verification: the full non-instrumented CI command of the AGENTS.md "Build and verify"
  section; the new test class must fail when run on the green commit's parent.
- Human review gates identified before work: the same gates as the story. Gated paths:
  `docs/CONTRACTS.md`; gated topic: authentication.
- Rule 0 acknowledged: chat replies for this fix are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## Ready Check (original story, 2026-09-07)

- Backlog story: `E2-08 - Anonymous Reminder Launch and Sign-In Race Fixes - S`, added to
  `docs/BACKLOG.md` in this story from the three non-blocking observations of the `E2-07` owner
  review of pull request #61.
- Acceptance criteria reviewed:
  1. A launch evaluation blocked by `AuthState.Unknown` completes once when the state resolves to an
     anonymous `SignedIn`, without becoming a new evaluation trigger.
  2. A reminder is never published for a session that is no longer the anonymous session its index
     was computed for.
  3. The iOS launch evaluation does not depend on `onChange(of: scenePhase)` firing after a cold
     launch, and the resulting duplicate call is collapsed.
  4. Each fix has a shared test; the SwiftUI host change is TDD-exempt with manual evidence.
- Dependencies checked: `E2-07`. Its pull request #61 is **open, not merged**, and `main` is at
  `7a79fab`, which contains none of the code under repair. The branch is therefore based on
  `story/E2-07-anonymous-benefit-reminders` and its pull request targets that branch; basing on
  `main` as originally requested would produce a branch in which
  `SessionStateHolder.evaluateAnonymousReminder()`, `publishDueReminder` and the iOS trigger do not
  exist. This deviation is deliberate and is recorded under "Decisions Made".
- Decisions checked: `D-62` (ADR-0063), `D-144` (ADR-0145), `D-145` (ADR-0146) and `D-146`
  (ADR-0147) are `Accepted` and MUST NOT be changed by this story. `D-105` governs this checkpoint.
  No decision this story depends on is `Proposed` or `Pending`.
- Normative sections reviewed: `docs/CONTRACTS.md` §11.1 (`AuthState` distinguishes *not yet
  determined* from *signed out*), §11.2, §11.3 (the reminder schedule and its single evaluation
  entry point), §14, §20.10; `docs/SPECIFICATION.md` §3.2 (no schedulers, no operating-system
  notifications), §11 (TDD rule, TDD commit and push workflow, host-UI exemption).
- Expected verification:
  - `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
    koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
    iosSimulatorArm64Test` with the four D-75 `-x` Firebase paths.
  - `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug
    ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` and the simulator test invocation.
  - `./gradlew :androidApp:connectedDebugAndroidTest` on the D-84 API 36 emulator.
- Human review gates identified before work: the story is marked "Human review required". Gated
  paths expected: `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`,
  `docs/SPECIFICATION.md`, `AGENTS.md`. Gated topic: authentication. `core/database/**` is **not**
  touched by this story.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-08
- Branch and base: `story/E2-08-anonymous-reminder-launch-and-race-fixes`, based on
  `story/E2-07-anonymous-benefit-reminders` at `aad3f96`.
- Current phase and latest commit: additional-fix intake recorded; RED phase for the two new tests
  is the next step.
- Push and pull-request status: pushed; pull request #62 is open, targeting the `E2-07` branch,
  awaiting its gated owner review.
- Completed since the previous checkpoint: the truncated `docs/handoff-E2-08.md` was restored (see
  "Decisions Made") and this additional-fix ready check was recorded.
- Verification evidence and known failures: the head commit `6201d5f` reproduced all ten required
  checks locally before this fix was ordered. No known failure.
- Open decisions or blockers: none. This fix adds no decision.
- Exact next step: RED phase — two failing tests in
  `AnonymousReminderEvaluationRaceTest`, one per behaviour, committed on their own.

## In-Progress Checkpoint (original story, 2026-09-07)

- Date: 2026-09-07
- Branch and base: `story/E2-08-anonymous-reminder-launch-and-race-fixes`, based on
  `story/E2-07-anonymous-benefit-reminders` at `aad3f96` (see the Ready Check for why not `main`).
- Current phase and latest commit: refactor phase; the decision record, the contract update and
  this handoff are the last change of the story.
- Push and pull-request status: the intake, red and green commits are pushed; the pull request is
  opened after this commit, targeting the `E2-07` branch.
- Completed since the previous checkpoint: red phase (5 failing tests, 2 complementary halves),
  green phase (the deferred launch evaluation, the stale-reminder re-check and cancellation, and
  the iOS initial-appearance trigger), and the refactor phase recorded here.
- Verification evidence and known failures: the full non-instrumented CI command exits `0`; the iOS
  app builds and its whole suite passes on an erased simulator (40 unit tests, 7 UI tests with 1
  skipped); the committed Objective-C golden header is unchanged, because no exported declaration
  changed. No known failure.
- Open decisions or blockers: none. `D-147` is recorded with ADR-0148 and the four mirrors.
- Exact next step: open the pull request and request the gated owner review.

## Scope Completed

- Observation 1: a launch evaluation blocked by `AuthState.Unknown` is remembered and completed
  once by the existing auth-state collector when the session resolves to an anonymous one.
- Observation 2: the iOS host also evaluates on the initial appearance, so the launch moment no
  longer depends on a scene-phase change being delivered after a cold launch.
- Observation 3: `publishDueReminder` re-reads the session after its last suspension point and
  publishes only for the same anonymous identity; the collector cancels the in-flight evaluation
  when the session stops being anonymous.
- `D-147` with ADR-0148, the four decision mirrors and the `docs/CONTRACTS.md §11.3` rules.

## Acceptance Evidence

1. *A launch evaluation blocked by `Unknown` completes once when the state resolves, without
   becoming a new trigger.*
   `AnonymousReminderEvaluationRaceTest.anEvaluationRequestedBeforeTheSessionIsRestoredCompletesWhenItResolves`
   proves the completion;
   `aSessionThatResolvesWithoutAnyRequestedEvaluationRemindsNobody` proves a restored session is not
   a trigger of its own (zero repository reads);
   `aPendingEvaluationIsConsumedByAPermanentResolution` proves a non-anonymous resolution consumes
   it without running it; and `aResolvedPendingEvaluationIsNotRunAgainByALaterSessionChange` proves
   the one-shot rule by counting exactly one repository read across a signed-out and a second
   anonymous emission.
2. *A reminder is never published for a session that is no longer the one its index was computed
   for.* `aPermanentSignInWhileAnEvaluationIsInFlightPublishesNoReminder` leaves the index null,
   `aPermanentSignInWhileAnEvaluationIsInFlightLeavesTheScheduleStateCleared` proves no position is
   written back after `clear()`, and
   `aSwitchToADifferentAnonymousIdentityWhileAnEvaluationIsInFlightPublishesNoReminder` exercises the
   re-check on its own, because that transition is still anonymous and therefore not cancelled.
3. *The iOS launch evaluation does not depend on the scene-phase change.* `iosApp/carAppApp.swift`
   calls the same single entry point from `.onAppear` and from `.onChange(of: scenePhase)`. The
   duplicate is collapsed by the in-flight guard in `evaluateAnonymousReminder()` and by the
   schedule itself, since a completed evaluation has already persisted the index it published.
4. *No new trigger anywhere.* No scheduler, alarm, background task, notification permission or
   observer beyond the `D-146` host intents exists in the change.

## Out of Scope / Not Done

- `D-62`, `D-144`, `D-145` and `D-146` are untouched, as required. The schedule, its thresholds, its
  persistence location and its presentation channel are exactly as accepted in `E2-07`.
- The reminder still offers no sign-in action; that entry point remains `E2-04` and `E2-05` work.
- No Android host change: `OnForegroundReturn` already uses `ON_START`, which fires on launch as
  well as on every foreground return, so observation 2 has no Android counterpart.
- `E1-14`, `E1-15` and `E1-17` remain open and were not touched.

## Files Changed

- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt` — the deferred evaluation,
  the session re-check, the in-flight cancellation and the pending-request cleanup in `close()`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AnonymousReminderEvaluationRaceTest.kt` (new).
- `iosApp/carAppApp.swift` — the initial-appearance trigger.
- `docs/BACKLOG.md` (the `E2-08` story and the index row), `docs/CONTRACTS.md` §11.3,
  `docs/SPECIFICATION.md` §12, `docs/TECHNICAL_PLAN.md` §2, `docs/DECISION_BOARD.md`,
  `docs/adr/0148-complete-a-launch-evaluation-when-the-session-resolves.md` (new),
  `docs/adr/README.md`, `docs/PROJECT_LOG.md`, this handoff.

## Decisions Made

- Additional fix (2026-09-08): `docs/handoff-E2-08.md` had been truncated to an empty file by the
  last pushed commit `6201d5f`, whose message describes adding a paragraph but whose diff deleted
  all 218 lines. The full content from `6201d5f^` was restored in the intake commit of this fix; the
  paragraph that commit intended to add — that no GitHub check runs on the stacked pull request #62
  and that all ten required checks were reproduced locally instead — is recorded in
  "Verification Run (additional fix)" below.
- Additional fix (2026-09-08): the UID-binding repair is a defect fix under the existing
  contracts, not a decision. `D-62`, `D-144`, `D-145`, `D-146` and `D-147` are untouched and no new
  decision is added; `docs/CONTRACTS.md` §11.3 already scopes a stored position to the anonymous
  UID that produced it.
- `D-147` (ADR-0148): a launch evaluation blocked by `AuthState.Unknown` is remembered and completed
  exactly once when the session resolves to an anonymous one. Bounded so it cannot become a trigger:
  one-shot, consumed by any other resolution, never created by a resolution nobody asked for.
- The stale-reminder repair is a defect fix under the existing contracts, not a decision. Both
  mechanisms the review offered are implemented, and they are not redundant: cancelling the job when
  the session stops being anonymous is what keeps `clear()` authoritative, while the re-read after
  the last suspension point is what actually closes the race, because cancellation is cooperative
  and the state write is not a suspension point. The third test isolates the re-read.
- The iOS initial-appearance trigger is host wiring under `D-146`, which already requires evaluation
  "on launch"; it changes how that moment is detected, not the contract.
- **Deviation from the request:** the branch is based on `story/E2-07-anonymous-benefit-reminders`
  rather than on `main`, and its pull request targets that branch. Pull request #61 is open, not
  merged, and `main` at `7a79fab` contains none of the code under repair, so a branch based on
  `main` could not compile these fixes. Once #61 merges, this branch rebases onto `main` without
  conflict.
- TDD order exemption per `docs/SPECIFICATION.md §11` for the SwiftUI host change only. Every
  `:shared` change was test-first: the five failing tests are commit `87c064e` and the fix is
  `0fd22fd`.
- Rule 0 held for the whole story: every chat reply was in Spanish (es-ES) and every repository
  artifact is in technical English. No violation occurred.

## Verification Run (additional fix, 2026-09-08)

- Recorded at intake, to be completed by the fix: the stacked pull request #62 targets the `E2-07`
  branch, so `.github/workflows/ci.yml` runs no GitHub check on it; an empty check list there is a
  property of the base branch, not a green or red signal. All ten required checks are reproduced
  locally on the head commit instead. This paragraph was the content the truncated commit
  `6201d5f` intended to add.
- Pending: RED evidence for the two new tests, GREEN evidence, the full local suite run and the
  parent-commit failure proof. See the In-Progress Checkpoint.

## Verification Run (original story, 2026-09-07)

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test` with the four D-75 `-x` Firebase paths — exit `0`.
- `./gradlew contractCheck` — all assertions `PASS`, 148 aligned decisions and ADRs, no `PENDING`.
- `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` — `** BUILD SUCCEEDED **`.
- `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -destination
  "id=<erased simulator>" test` — `** TEST SUCCEEDED **`; 40 unit tests and 7 UI tests, 1 skipped,
  0 failures. The UI tests cold-launch the app, so they exercise the new `.onAppear` path.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` followed by `diff -u` against
  `shared/build/generated/objc-header/Shared.h.golden` — no difference; no exported declaration
  changed.
- Red-phase evidence: 5 failing tests before the fix. Re-verified after the harness change by
  stashing only `StateHolders.kt` and re-running the class: the same 5 failed again, so the fix and
  not the harness is what makes them pass.

### Manual verification of the iOS launch trigger

The SwiftUI host change is TDD-exempt, so its evidence is manual and is stated with its limits.

- Erased the simulator, installed the Debug build carrying the change and cold-launched it. The app
  reached the welcome screen with no crash, and `log show` reported zero crash-like lines for the
  process. Screenshot: cold launch.
- Sent the app to the background and returned it to the foreground. The app resumed to the same
  screen with no crash and no notice. Screenshot: foreground return.
- The full XCUITest suite passes against this build, and its onboarding tests cold-launch the app
  and drive it to vehicle creation, so the added `.onAppear` demonstrably does not disturb the
  launch path.
- **What this does not prove:** that a *due* reminder appears at launch. Showing one requires an
  anonymous Firebase account older than one day, and the schedule anchors to the provider's
  user-creation timestamp, which cannot be faked locally. On a fresh account zero days have elapsed,
  so the correct observable outcome is exactly what was observed: an evaluation that runs and shows
  nothing. The publication half of the path is covered by the shared tests instead.

## Contract Impact

- Updated `docs/CONTRACTS.md` §11.3 with three rules: the per-platform launch and foreground
  moments and the collapsing of a duplicate call; the deferred evaluation and its one-shot bounds
  (`D-147`); and the requirement that a reminder is never published for a session that is no longer
  the anonymous session its index was computed for.

## Decision Board Impact

- Added `D-147` ([ADR-0148](adr/0148-complete-a-launch-evaluation-when-the-session-resolves.md)),
  `Accepted`, with identical rows in the four mirroring documents. `D-62`, `D-144`, `D-145` and
  `D-146` are unchanged.

## Shared-Write Modules Touched

- None. `:core:database` is not touched by this story; the schema stays at version 2.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- The deferred evaluation adds a second responsibility to the auth-state collector. A future reader
  could mistake it for a new trigger and either remove it as dead code or generalise it into an
  observer; the KDoc, ADR-0148 and the `§11.3` wording exist to prevent both, and the one-shot test
  fails if it is generalised.
- The iOS launch path has no automated proof that a due reminder is published at launch, for the
  reason stated above. If `E3-12` or a later story ever provisions a long-lived anonymous account
  for device testing, that gap can be closed cheaply.
- This branch is stacked on the unmerged `E2-07` branch. If `E2-07` is revised during its review,
  this branch must be rebased before it can merge.

## Human Review Gate

Applies. The story is marked "Human review required" in `docs/BACKLOG.md`, and it touches the gated
authentication topic and the gated paths `docs/CONTRACTS.md`, `docs/SPECIFICATION.md`,
`docs/DECISION_BOARD.md` and `docs/adr/**`.

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
- Current phase and latest commit: story intake recorded; no implementation commit yet.
- Push and pull-request status: not pushed; no pull request.
- Completed since the previous checkpoint: repository sync (`main` fast-forwarded to `7a79fab`),
  branch creation, ready check, and a green baseline run of the full non-instrumented CI command
  on the untouched branch.
- Verification evidence and known failures: baseline
  `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test` with the four D-75 `-x` paths exited `0`. No known failures.
- Open decisions or blockers: three implementation decisions are open and will be registered with
  decision IDs and ADRs in this story: the physical persistence location of the last-shown reminder
  index, the presentation channel that carries the reminder to both hosts, and the foreground
  evaluation trigger mechanism. None of them blocks starting the RED phase of the pure schedule
  behavior, which is fixed by `docs/CONTRACTS.md §11.3`.
- Exact next step: RED phase for the pure reminder-schedule evaluation in the `:feature:session`
  `domain` package, covering the seven normative time boundaries.

## Scope Completed

-

## Acceptance Evidence

-

## Out of Scope / Not Done

-

## Files Changed

-

## Decisions Made

-

## Verification Run

-

## Contract Impact

-

## Decision Board Impact

-

## Shared-Write Modules Touched

-

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

-

## Human Review Gate

Applies. The story is marked "Human review required" in `docs/BACKLOG.md`, and it touches the
gated authentication and Swift-facing API surface topics of `AGENTS.md`.

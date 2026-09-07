# Agent Handoff

## Story

`E2-08 - Anonymous Reminder Launch and Sign-In Race Fixes - S`

## Ready Check

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

- Date: 2026-09-07
- Branch and base: `story/E2-08-anonymous-reminder-launch-and-race-fixes`, based on
  `story/E2-07-anonymous-benefit-reminders` at `aad3f96` (see the Ready Check for why not `main`).
- Current phase and latest commit: story intake recorded; no implementation commit yet.
- Push and pull-request status: not pushed; no pull request.
- Completed since the previous checkpoint: the three observations were reproduced by reading the
  delivered code, the backlog story was written, and this ready check was recorded.
- Verification evidence and known failures: the base commit `aad3f96` has all ten required checks
  green on pull request #61.
- Open decisions or blockers: one decision is expected for the deferred launch evaluation, because
  it changes when a requested evaluation effectively runs and the next agent must not remove it as
  dead code. The stale-banner fix and the iOS trigger are defect repairs under the existing
  contracts and `D-146`.
- Exact next step: RED phase for the two shared behaviours, one failing test per behaviour.

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

Applies. The story is marked "Human review required" in `docs/BACKLOG.md` and it touches the gated
authentication topic.

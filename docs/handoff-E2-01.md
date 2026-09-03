# Agent Handoff - E2-01

## Story

`E2-01 - :core:auth - S`

## Ready Check

- Backlog story: `E2-01 - :core:auth - S` is explicit and is the next open Phase 2 story in
  `docs/BACKLOG.md`.
- Acceptance criteria reviewed: the complete provider-free auth contracts and models match
  `docs/CONTRACTS.md` sections 6, 11.1 and 20.8; `AuthState.Unknown` remains distinct from
  `AuthState.SignedOut`; the auth-backed `OwnerContext` implementation moves into `:core:auth` and
  wiring binds it without exposing `AuthClient` to feature modules; no Firebase type enters
  `:core:auth`.
- Dependencies checked: E0-03 supplied `AuthError`, `AuthProvider`, `OwnerContext`, `Outcome` and
  model identifiers; E0-07 staged the final auth contract shapes and Firebase wiring; both are
  complete. E2-01 has no open predecessor in the backlog. The branch starts from synchronized
  `main` / `origin/main` at `91b2a78`.
- Decisions checked: D-6 fixes GitLive Auth behind `AuthClient`; D-42 fixes prerequisite order;
  D-55 permits the E0-07 staged module/contracts; D-60 keeps `OwnerContext` as the feature-data
  boundary; D-61 fixes device-bound anonymous identity; D-105 requires this live checkpoint. All
  decision-board rows are resolved; no `Proposed` or `Pending` decision blocks the story.
- Normative sections reviewed: `docs/SPECIFICATION.md` sections 7, 8.3 and 11;
  `docs/CONTRACTS.md` sections 6, 11.1, 11.2, 11.6, 12, 18, 20.2, 20.3 and 20.8;
  `docs/TECHNICAL_PLAN.md` sections 3, 4 and 12; ADR-0007 / D-6, ADR-0056 / D-55 and
  `AGENTS.md` Definition of Ready, Continuous Progress Documentation, Definition of Done and Human
  Review Gates.
- Expected verification: focused `:core:auth:testAndroidHostTest` and
  `:core:auth:iosSimulatorArm64Test`; focused Firebase-wiring tests; architecture and contract
  checks; source audit for forbidden provider/platform types and feature references to
  `AuthClient`; lint, static analysis, coverage, Android assembly/unit tests and the exact complete
  non-instrumented command in `AGENTS.md`; `git diff --check`; and all required pull-request checks.
- Human review gates identified before work: `core/auth/**` is a gated path and authentication is a
  gated topic. Owner review is required and the agent will not merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch,
  commit and pull-request field is technical English.
- TDD workflow: the owner's `NETWORK` phase label is interpreted as `RED`, matching the repository's
  closed RED/GREEN/REFACTOR workflow and the same wording used in prior stories. The owner explicitly
  requested three local phase commits followed by one push, which supersedes the default
  push-after-each-phase cadence for this story while preserving test-first order and commit
  separation.

## In-Progress Checkpoint

- Date: 2026-09-03.
- Branch and base: `story/E2-01-core-auth-contracts`, recreated after a second explicit
  synchronization and based on identical `main` / `origin/main` at `91b2a78`.
- Current phase and latest commit: RED committed at `7bd723e`; GREEN is complete and not yet
  committed.
- Push and pull-request status: no story commit is pushed and no pull request exists.
- Completed since the previous checkpoint: RED was committed. GREEN stores the injected auth state
  in `AuthOwnerContext`, maps `Unknown` and `SignedOut` to `LOCAL_OWNER`, maps `SignedIn` to its
  session UID for both snapshot and Flow access, and replaces the private wiring implementation
  with the `:core:auth` class.
- Verification evidence and known failures: the focused GREEN command passed 113 executed tasks.
  `:core:auth` ran 12 tests with zero failures on Android host and 12 with zero failures on the iOS
  simulator; the Firebase-wiring Android-host suite ran two tests with zero failures. Historical RED
  evidence remains below. No current failure is known.
- Open decisions or blockers: none currently identified. The implementation will preserve the
  existing auth-state-to-owner mapping while relocating its ownership to `:core:auth`; this is the
  behavior already staged by E0-07 rather than a new product or architecture choice.
- Exact next step: create the GREEN commit, then perform bounded refactoring and complete the story
  documentation, status mirrors and project log before full verification.

## Scope Completed

- Auth contracts are executable and the auth-backed owner context is implemented and bound in
  wiring; REFACTOR documentation and final verification remain.

## Acceptance Evidence

- `AuthOwnerContextTest` proves `Unknown` and `SignedOut` use `LOCAL_OWNER`, `SignedIn` uses the
  session UID, `current` follows state changes and `observe()` emits owner changes on both Android
  host and iOS simulator.
- `FirebaseAppProvidersTest.providerFactoryKeepsRealBoundariesAndDerivesTheCurrentOwner` proves the
  provider factory binds `AuthOwnerContext` from `:core:auth` and derives the signed-in owner.
- `AuthContractsTest` compiles and exercises the exact model fields, distinct auth states, both
  native credential forms, token validity instants and every typed `AuthClient` / `TokenProvider`
  outcome.

## Out of Scope / Not Done

- E2-02 provider operations, E2-03 onboarding, E2-04 account conversion, E2-05 deletion/sign-out
  orchestration, E2-06 local-owner adoption and E2-07 retention reminders remain out of scope.

## Files Changed

- `core/auth/build.gradle.kts` (coroutines test support).
- `core/auth/src/commonMain/kotlin/com/ruizurraca/carapp/core/auth/AuthOwnerContext.kt` (compiling
  incomplete RED seam).
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthContractsTest.kt` (contract
  surface coverage).
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthOwnerContextTest.kt`
  (behavior-specific RED coverage).
- `wiring/firebase/src/commonTest/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProvidersTest.kt`
  (binding assertion).
- `docs/handoff-E2-01.md` (new live continuity record).

## Decisions Made

- The owner's `NETWORK` phase label is treated as `RED`; no network phase exists in the repository's
  TDD workflow.
- The owner's single-push instruction replaces the default per-phase push cadence for E2-01. The
  RED, GREEN and REFACTOR commits remain separate and ordered.

## Verification Run

- Baseline: `./gradlew :core:auth:testAndroidHostTest architectureCheck contractCheck` — passed;
  `:core:auth:testAndroidHostTest` was `NO-SOURCE`, `architectureCheck` reported 16 rules over 23
  modules and `contractCheck` reported 111 aligned decisions/ADRs, none unresolved and no pending
  assertion.
- RED core auth: `./gradlew :core:auth:testAndroidHostTest
  :wiring:firebase:testAndroidHostTest --rerun-tasks` — failed as intended after compiling and
  executing 12 `:core:auth` tests; three failures identify the absent signed-in mapping, dynamic
  current owner and reactive observation.
- RED wiring: `./gradlew :wiring:firebase:testAndroidHostTest --tests
  "com.ruizurraca.carapp.wiring.firebase.FirebaseAppProvidersTest.providerFactoryKeepsRealBoundariesAndDerivesTheCurrentOwner"
  --rerun-tasks` — failed as intended after executing one test because the bound owner context is
  not the new `AuthOwnerContext`.
- GREEN: `./gradlew :core:auth:testAndroidHostTest :core:auth:iosSimulatorArm64Test
  :wiring:firebase:testAndroidHostTest --rerun-tasks` — passed, 113 executed tasks; 12 core-auth
  tests passed on each platform and two Firebase-wiring Android-host tests passed.

## Contract Impact

- No contract changes are expected; E2-01 implements and verifies the existing auth contracts.

## Decision Board Impact

- No decision change is currently expected.

## Shared-Write Modules Touched

- None. `:core:database` is not in scope.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- The provider operations intentionally remain staged until E2-02.

## Human Review Gate

- Applies: `core/auth/**` is a gated path and authentication is a gated topic. The pull request
  requires owner review and must not be merged by the agent.

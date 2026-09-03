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
- Current phase and latest commit: RED `7bd723e`, GREEN `d9b421e` and REFACTOR `1a582fe` are
  committed; final local verification is complete.
- Push and pull-request status: the three ordered phase commits and the documentation-only delivery
  checkpoint were pushed to `origin/story/E2-01-core-auth-contracts`. Pull request #52 is open at
  `https://github.com/davidru85/carApp/pull/52`; all ten required checks are queued or in progress.
- Completed since the previous checkpoint: the REFACTOR phase was committed and the exact three
  TDD commits were pushed once, as requested. The remote compare page publicly confirms all three
  commits and 12 changed files.
- Verification evidence and known failures: the exact complete non-instrumented command passed 636
  actionable tasks; provider decoupling passed 234 forced tasks; the simulator framework linked,
  its generated Objective-C header matches the golden file and the native iOS app build succeeded.
  Contract checking reports 111 aligned decisions/ADRs, none unresolved and no pending assertion.
  A first sandboxed `xcodebuild` attempt could not access Xcode/Swift caches or CoreSimulator; the
  permitted rerun reached the toolchain and succeeded, so this is an environment restriction rather
  than a product failure. Historical RED evidence remains below.
- Open decisions or blockers: no technical or owner decision is open and no implementation blocker
  remains. GitHub CLI authentication was refreshed and pull-request creation is no longer blocked.
  The implementation preserves the mapping staged by E0-07 and changes no contract, architecture
  rule, dependency version, service or MVP scope.
- Exact next step: commit and push this PR checkpoint, wait for all required checks on the resulting
  HEAD and leave pull request #52 open for mandatory owner review.

## Scope Completed

- Completed the existing provider-free auth model and interface surface with executable
  cross-platform contract coverage.
- Implemented the auth-backed repository owner adapter in `:core:auth` and bound it in Firebase
  wiring without changing feature dependencies.
- Removed the redundant staged wiring implementation and the unused auth serialization dependency.
- Updated Phase 2 status, continuity evidence and the append-only project log.

## Acceptance Evidence

- `AuthOwnerContextTest` proves `Unknown` and `SignedOut` use `LOCAL_OWNER`, `SignedIn` uses the
  session UID, `current` follows state changes and `observe()` emits owner changes on both Android
  host and iOS simulator.
- `FirebaseAppProvidersTest.providerFactoryKeepsRealBoundariesAndDerivesTheCurrentOwner` proves the
  provider factory binds `AuthOwnerContext` from `:core:auth` and derives the signed-in owner.
- `AuthContractsTest` compiles and exercises the exact model fields, distinct auth states, both
  native credential forms, token validity instants and every typed `AuthClient` / `TokenProvider`
  outcome.
- Existing `AppErrorCodesTest` continues to verify every exact `AuthError` code through the complete
  repository command.
- `architectureCheck` and direct source audits prove that `:core:auth` contains no Firebase,
  GitLive or platform API, and that no feature module references `AuthClient` or `:core:auth`.
- Provider decoupling executes the new owner-context tests on Android host and iOS simulator with
  every Firebase provider/composition module excluded.

## Out of Scope / Not Done

- E2-02 provider operations, E2-03 onboarding, E2-04 account conversion, E2-05 deletion/sign-out
  orchestration, E2-06 local-owner adoption and E2-07 retention reminders remain out of scope.

## Files Changed

- `AGENTS.md`, `README.md`, `docs/BACKLOG.md` and `docs/TECHNICAL_PLAN.md` (current Phase 2 status).
- `core/auth/build.gradle.kts` (coroutines test support and unused serialization removal).
- `core/auth/src/commonMain/kotlin/com/ruizurraca/carapp/core/auth/AuthOwnerContext.kt` (auth-backed
  owner implementation).
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthContractsTest.kt` (contract
  surface coverage).
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthOwnerContextTest.kt`
  (behavior-specific RED coverage).
- `wiring/firebase/src/commonTest/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProvidersTest.kt`
  (binding assertion).
- `wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt`
  (core-auth owner binding and duplicate implementation removal).
- `docs/handoff-E2-01.md` (new live continuity record).
- `docs/PROJECT_LOG.md` (append-only completion entry).

## Decisions Made

- The owner's `NETWORK` phase label is treated as `RED`; no network phase exists in the repository's
  TDD workflow.
- The owner's single-push instruction replaces the default per-phase push cadence for E2-01. The
  RED, GREEN and REFACTOR commits remain separate and ordered.
- The owner subsequently reiterated that continuity state must remain fully documented for a
  replacement agent. A documentation-only delivery checkpoint may therefore follow pull-request
  creation if required to record the remote branch, PR and CI state; it does not alter or combine
  the three TDD phase commits.
- No new technical decision was introduced. `AuthOwnerContext` relocates the exact E0-07 mapping to
  the owner module fixed by the E2-01 acceptance criteria; the public contracts, dependency graph,
  provider stack and product behavior are unchanged.

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
- REFACTOR full repository: the exact complete non-instrumented command from `AGENTS.md` — passed,
  636 actionable tasks (106 executed, 530 up-to-date), including lint, detekt, architecture,
  contracts, convention tests, coverage, Android assembly/unit tests and required Android-host /
  iOS-simulator tests.
- REFACTOR provider decoupling: `./gradlew -Pcarapp.excludeFirebaseProviders=true
  testAndroidHostTest iosSimulatorArm64Test --rerun-tasks` — passed, 234 executed tasks.
- REFACTOR framework and ABI: `./gradlew
  :composition:ios:linkDebugFrameworkIosSimulatorArm64 --stacktrace` — passed, 70 actionable tasks;
  `diff -u shared/build/generated/objc-header/Shared.h.golden
  composition/ios/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h`
  returned no difference.
- REFACTOR iOS host: `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator
  -configuration Debug ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` — the first sandboxed attempt failed
  before compilation on denied cache/CoreSimulator access; the permitted rerun succeeded with
  `** BUILD SUCCEEDED **`.
- REFACTOR contract/convention evidence: `./gradlew contractCheck architectureCheck
  :build-logic:convention:test` — passed; 111 decisions and ADRs aligned, none unresolved, no
  pending assertion, 16 architecture rules over 23 modules.
- Source and patch audit: `git diff --check` passed; searches found no Firebase/GitLive/platform
  reference in auth common production sources, no feature reference to `AuthClient` / `:core:auth`,
  and no old wiring-local owner implementation.

## Contract Impact

- No contract changes. E2-01 implements and verifies the existing auth contracts.

## Decision Board Impact

- No decision changes.

## Shared-Write Modules Touched

- None. `:core:database` is not in scope.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- The provider operations intentionally remain staged until E2-02.

## Human Review Gate

- Applies: `core/auth/**` is a gated path and authentication is a gated topic. The pull request
  requires owner review and must not be merged by the agent.

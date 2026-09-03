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
- Branch and base: `story/E2-01-core-auth-contracts`, based on synchronized `main` / `origin/main` at `91b2a78`.
- Current phase and latest commit: REFACTOR phase of PR #52 review remediation complete; latest commit on branch is `9f20f16`.
- Push and pull-request status: pull request #52 is open at `https://github.com/davidru85/carApp/pull/52`. Remediation commits follow strict RED (`3412b9d`), GREEN (`f07927a`), REFACTOR (`9f20f16`) order and are pushed to `origin`.
- Completed since the previous checkpoint: completed all PR #52 review fixes (Findings 1-4). Enforced feature auth boundary with executable architecture check; removed `:core:auth` from `:feature:session`; deduplicated `AuthOwnerContext.observe()`; clarified `AuthContractsTest` compile-time signature conformance; updated project log and handoff continuity documentation.
- Verification evidence and known failures:
  - Complete non-instrumented repository command passed 636 actionable tasks.
  - Provider decoupling (`-Pcarapp.excludeFirebaseProviders=true`) passed 234 actionable tasks.
  - `:composition:ios:linkDebugFrameworkIosSimulatorArm64` passed and `diff -u` against golden header confirmed 0 differences.
  - `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` succeeded with `** BUILD SUCCEEDED **`.
  - `contractCheck` passed all 17 active assertions with 0 pending and 0 unresolved decisions.
- Open decisions or blockers: none.
- Exact next step: push branch to `origin/story/E2-01-core-auth-contracts`, update pull request #52 body on GitHub, and leave PR #52 open for mandatory owner review.

## Scope Completed

- Completed the existing provider-free auth model and interface surface with executable
  cross-platform contract coverage.
- Implemented the auth-backed repository owner adapter in `:core:auth` and bound it in Firebase
  wiring without exposing `AuthClient` to feature modules.
- Removed the redundant staged wiring implementation and the unused auth serialization dependency.
- Corrected feature boundary leak in `:feature:session` and added an executable architecture rule
  in `ArchitectureChecker` preventing `:feature:* -> :core:auth` project dependencies.
- Added deduplication to `AuthOwnerContext.observe()` via `distinctUntilChanged()`.
- Clarified `AuthContractsTest` as compile-time signature conformance with concrete error assertions.
- Updated Phase 2 status, continuity evidence and the append-only project log.

## Acceptance Evidence

- `AuthOwnerContextTest` proves `Unknown` and `SignedOut` use `LOCAL_OWNER`, `SignedIn` uses the
  session UID, `current` follows state changes, and `observe()` emits owner changes on both Android
  host and iOS simulator with deduplication across consecutive identical owners
  (`Unknown -> SignedOut -> SignedIn`) via `distinctUntilChanged()` to prevent redundant SQLDelight
  query restarts.
- `FirebaseAppProvidersTest.providerFactoryKeepsRealBoundariesAndDerivesTheCurrentOwner` proves the
  provider factory binds `AuthOwnerContext` from `:core:auth` and derives the signed-in owner.
- `AuthContractsTest` compiles and verifies the exact model fields, distinct auth states, both
  native credential forms, token validity instants, and pins the compile-time method signatures
  of `AuthClient` and `TokenProvider` (`§11.1`, `§20.8`) with concrete `AuthError` assertions rather
  than erased type checks.
- Existing `AppErrorCodesTest` continues to verify every exact `AuthError` code through the complete
  repository command.
- `architectureCheck` and direct source audits prove that `:core:auth` contains no Firebase,
  GitLive or platform API, and that no feature module references `AuthClient` or `:core:auth`.
  Specifically, PR #52 review identified that `feature/session` originally declared an unused
  compile dependency on `:core:auth` which bypassed `architectureCheck` because `TECHNICAL_PLAN.md §4`
  feature rows are keyed by layer (`feature domain`, `feature data`, `feature presentation`); this was
  corrected by removing the dependency from `feature/session/build.gradle.kts` and introducing an
  executable `feature-to-auth-dependency` rule in `ArchitectureChecker` with a firing test fixture in
  `ArchitectureCheckerTest` (verified to fail against pre-fix `feature/session` and pass after removal).
- Provider decoupling executes the new owner-context tests on Android host and iOS simulator with
  every Firebase provider/composition module excluded.

## Out of Scope / Not Done

- E2-02 provider operations, E2-03 onboarding, E2-04 account conversion, E2-05 deletion/sign-out
  orchestration, E2-06 local-owner adoption and E2-07 retention reminders remain out of scope.

## Files Changed

- `AGENTS.md`, `README.md`, `docs/BACKLOG.md` and `docs/TECHNICAL_PLAN.md` (current Phase 2 status).
- `core/auth/build.gradle.kts` (coroutines test support and unused serialization removal).
- `core/auth/src/commonMain/kotlin/com/ruizurraca/carapp/core/auth/AuthOwnerContext.kt` (auth-backed
  owner implementation with `distinctUntilChanged()`).
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthContractsTest.kt` (contract
  surface coverage and compile-time signature pin).
- `core/auth/src/commonTest/kotlin/com/ruizurraca/carapp/core/auth/AuthOwnerContextTest.kt`
  (behavior-specific RED coverage and discriminating deduplication test).
- `build-logic/convention/src/main/kotlin/com/ruizurraca/carapp/buildlogic/architecture/ArchitectureChecker.kt`
  (executable `feature-to-auth-dependency` rule).
- `build-logic/convention/src/test/kotlin/com/ruizurraca/carapp/buildlogic/architecture/ArchitectureCheckerTest.kt`
  (firing fixture for `feature-to-auth-dependency`).
- `feature/session/build.gradle.kts` (removed unused `:core:auth` compile dependency).
- `wiring/firebase/src/commonTest/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProvidersTest.kt`
  (binding assertion).
- `wiring/firebase/src/commonMain/kotlin/com/ruizurraca/carapp/wiring/firebase/FirebaseAppProviders.kt`
  (core-auth owner binding and duplicate implementation removal).
- `docs/handoff-E2-01.md` (live continuity record and review remediation evidence).
- `docs/PROJECT_LOG.md` (append-only completion entry and review correction entry).

## Decisions Made

- The owner's `NETWORK` phase label is treated as `RED`; no network phase exists in the repository's
  TDD workflow.
- The owner's single-push instruction replaces the default per-phase push cadence for E2-01. The
  RED, GREEN and REFACTOR commits remain separate and ordered.
- The owner subsequently reiterated that continuity state must remain fully documented for a
  replacement agent. A documentation-only delivery checkpoint may therefore follow pull-request
  creation if required to record the remote branch, PR and CI state; it does not alter or combine
  the three TDD phase commits.
- PR #52 review remediation:
  - Finding 1: Removed unused compile dependency on `:core:auth` from `feature/session/build.gradle.kts`.
  - Finding 2: Added executable `feature-to-auth-dependency` rule to `ArchitectureChecker.kt` and
    firing fixture `featureModulesMayNotDependOnCoreAuth` to `ArchitectureCheckerTest.kt`.
  - Finding 3: Applied `distinctUntilChanged()` to `AuthOwnerContext.observe()` to prevent duplicate
    `LOCAL_OWNER` emissions across `Unknown -> SignedOut` transitions.
  - Finding 4: Refined `AuthContractsTest.authClientAndTokenProviderMatchContractSignaturesAtCompileTime`
    to clarify that its force is compile-time signature conformance and asserted concrete `AuthError`
    leaves instead of erased `assertIs`.
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
- Review Fix RED:
  - `./gradlew :build-logic:convention:test` failed as intended on
    `ArchitectureCheckerTest.featureModulesMayNotDependOnCoreAuth`.
  - `./gradlew :core:auth:testAndroidHostTest` failed as intended on
    `AuthOwnerContextTest.ownerObservationDeduplicatesConsecutiveIdenticalOwnersAcrossStateTransitions`.
  - Verified pre-fix `feature/session/build.gradle.kts` had `"commonMainImplementation"(projects.core.auth)`.
- Review Fix GREEN:
  - `./gradlew architectureCheck` failed against pre-fix `feature/session` with `feature-to-auth-dependency`,
    then passed after removing the dependency.
  - `./gradlew :build-logic:convention:test` passed all 58 tests.
  - `./gradlew :feature:session:testAndroidHostTest` compiled and passed without `:core:auth`.
  - `./gradlew :core:auth:testAndroidHostTest :core:auth:iosSimulatorArm64Test` passed including deduplicated owner flow.
- REFACTOR full repository: the exact complete non-instrumented command from `AGENTS.md` — passed,
  636 actionable tasks, including lint, detekt, architecture, contracts, convention tests, coverage,
  Android assembly/unit tests and required Android-host / iOS-simulator tests.
- REFACTOR provider decoupling: `./gradlew -Pcarapp.excludeFirebaseProviders=true
  testAndroidHostTest iosSimulatorArm64Test --rerun-tasks` — passed, 234 executed tasks.
- REFACTOR framework and ABI: `./gradlew
  :composition:ios:linkDebugFrameworkIosSimulatorArm64 --stacktrace` — passed, 70 actionable tasks;
  `diff -u shared/build/generated/objc-header/Shared.h.golden
  composition/ios/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h`
  returned no difference.
- REFACTOR iOS host: `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -sdk iphonesimulator
  -configuration Debug ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` — passed with `** BUILD SUCCEEDED **`.
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

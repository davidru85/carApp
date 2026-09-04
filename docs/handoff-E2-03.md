# Agent Handoff - E2-03

## Story

`E2-03 - Onboarding Flow F-1 - M`

## Ready Check

- Backlog story: `E2-03 - Onboarding Flow F-1 - M` is explicit and is the next open Phase 2 story
  in `docs/BACKLOG.md`.
- Acceptance criteria reviewed: offline `LOCAL_OWNER` start; online Firebase anonymous start;
  no routing while auth is unknown; the exact Android and iOS provider action sets; provider-named
  sign-in intents; recoverable retry state; and post-auth routing based on whether vehicles exist.
- Dependencies checked: E2-01 supplied the provider-free auth contracts and owner context; E2-02
  supplied the Firebase Auth credential exchange and completed on PR #53. E2-06 owns automatic
  local-owner adoption when connectivity returns and remains deliberately outside this story.
  The branch starts from synchronized `main` / `origin/main` at `f7639dc`.
- Decisions checked: D-6, D-7, D-8, D-20, D-53, D-58, D-65, D-71, D-74, D-84, D-86, D-91,
  D-100, D-101, D-102, D-105 and D-111 are accepted. On 2026-09-04 the owner accepted D-112,
  D-113 and D-114, selecting the Android and iOS native credential-acquisition stacks, their exact
  pins and the primitive-only native-to-shared credential handoff. The story is **Ready**; the
  SwiftPM dependency graph was resolved and recorded below before any product-code change.
- Normative sections reviewed: `docs/SPECIFICATION.md` sections 7 F-1, 8 and 11;
  `docs/CONTRACTS.md` sections 11.1, 11.2, 14, 15.1, 15.3, 18, 20.8 and 20.10;
  `docs/TECHNICAL_PLAN.md` sections 3, 4, 5 and 12; ADR-0007 / D-6; and `AGENTS.md` Definition of
  Ready, Story Intake, Continuous Progress Documentation, Definition of Done and Human Review Gates.
- Expected verification: behavior-specific `SessionStateHolder` tests on Android host and iOS
  simulator; Android host tests plus instrumented Compose onboarding tests; iOS unit/UI tests;
  Android and iOS application builds; the complete non-instrumented repository command from
  `AGENTS.md`; forced provider decoupling; Objective-C header golden parity; `git diff --check`; and
  manual provider acceptance on configured development devices where provider UI cannot be
  automated safely.
- Human review gates identified before work: authentication is a gated topic. The likely decision
  record changes `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`,
  `docs/TECHNICAL_PLAN.md`, `docs/adr/**`, `docs/versions-matrix.md` and the Swift-facing ABI, all of
  which require owner review. The agent will not merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch,
  commit and pull-request field is technical English.
- TDD workflow: the owner explicitly requested local RED, GREEN and REFACTOR commits followed by
  one push and pull-request creation. This replaces the default push-after-each-phase cadence for
  E2-03 while preserving failing-test-first order and separate phase commits.

## In-Progress Checkpoint

- Date: 2026-09-04.
- Branch and base: `story/E2-03-onboarding-flow`, based on synchronized `main` / `origin/main` at
  `f7639dc`.
- Current phase and latest commit: RED complete and ready for its phase commit; no E2-03 phase
  commit exists yet. Latest base commit is `f7639dc`.
- Push and pull-request status: branch is local only; no push and no pull request.
- Completed since the previous checkpoint: the owner accepted all three previously blocked
  decisions with binding amendments and confirmed Account Holder / Admin access on a paid Apple
  Developer team. A clean temporary SwiftPM resolution combined exact Firebase Apple SDK 11.8.0
  with exact GoogleSignIn-iOS 9.2.0. It resolved GTMSessionFetcher 3.5.0, GTMAppAuth 5.0.0,
  AppAuth 2.1.0, GoogleUtilities 8.1.3 and app-check 11.2.0. The subsequent resolution into the
  committed application lockfile retained GoogleUtilities 8.1.2 and resolved the other named
  packages identically. The authoritative E2-03 application graph is therefore
  GTMSessionFetcher 3.5.0, GTMAppAuth 5.0.0, AppAuth 2.1.0, GoogleUtilities 8.1.2 and app-check
  11.2.0. Every result remains inside Firebase 11.8.0's declared ranges: GTMSessionFetcher
  `3.4.1..<5.0.0`, GoogleUtilities `8.0.0..<9.0.0` and app-check `11.0.1..<12.0.0`. No D-65
  relaxation is required. D-112, D-113 and D-114 now
  exist in every mandatory mirror with ADR-0113, ADR-0114 and ADR-0115; the Android and iOS pins
  and their compatibility sets are recorded, and the primitive-only native/shared ABI is normative
  in `docs/CONTRACTS.md`. Behavior-specific failing tests now define the shared provider handoff,
  the Android route/coordinator/UI action set, and the iOS route/nonce/failure/UI action set.
- Verification evidence and known failures: the focused pre-story baseline passed 219 actionable
  tasks, including `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`,
  `:androidApp:testDebugUnitTest`, `architectureCheck` and `contractCheck`. `contractCheck` reported
  112 aligned decisions / ADRs, no unresolved decisions and no pending assertions. Static inspection
  found that the committed Android debug Firebase configuration has no OAuth client and the iOS
  debug Firebase plist has no client or reversed-client identifiers. The iOS target also has no
  Sign in with Apple entitlement. These are expected provisioning gaps for E2-03, not code
  regressions. The shared RED run fails at test compilation because `completeGoogleSignIn`,
  `completeAppleSignIn`, `failSignIn` and `NativeSignInFailure` do not exist. The Android RED run
  fails at test compilation because `AndroidGoogleSignInCoordinator`,
  `GoogleCredentialAcquisition`, `OnboardingDestination` and the route resolver do not exist. The
  iOS RED build-for-testing, using the repository-required `ARCHS=arm64` override, fails at test
  compilation because the onboarding resolver/routes and `AppleSignInNonce` do not exist. An
  initial generic-simulator invocation without `ARCHS=arm64` requested the unsupported x86_64 KMP
  framework; it was a setup mismatch and is not counted as RED evidence.
- Open decisions or blockers: none. External provider provisioning remains implementation work, not
  an owner-decision blocker, because the owner confirmed the required Apple account authority.
- Exact next step: run formatting and whitespace checks, create the local RED commit
  `test(E2-03): define native onboarding behavior`, update this checkpoint with that commit, and
  begin the minimum shared, Android and iOS implementation needed to make the tests pass.

## Scope Completed

- Intake and readiness closure, dependency-resolution evidence, accepted decision/ADR/contract
  records, exact dependency pins and the complete cross-platform RED test surface. Product
  implementation has not started.

## Acceptance Evidence

- RED evidence is recorded under `Verification Run`; each platform fails for the intended missing
  E2-03 behavior rather than an unrelated baseline regression.

## Out of Scope / Not Done

- All E2-03 production behavior, provider provisioning and UI implementation remain unstarted.
- E2-06 local-owner adoption remains out of scope.

## Files Changed

- Decision, contract and compatibility documentation listed in the working-tree diff.
- Android and iOS dependency/project metadata required to compile the accepted stacks.
- Shared, Android and iOS behavior-specific RED tests.
- `docs/handoff-E2-03.md`.

## Decisions Made

- The owner selected the stable Android Credential Manager stack as D-112, native Apple plus exact
  GoogleSignIn-iOS 9.2.0 as D-113, and primitive provider-named completion intents with a closed
  failure enum as D-114. Their ADRs contain the alternatives and trade-offs.
- The owner's explicit one-push instruction replaces the default per-phase push cadence for this
  story; RED, GREEN and REFACTOR will remain separate local commits.
- The owner requested continuous, fully recoverable process documentation because the active agent
  may reach its session limit. Under D-105 this handoff will be updated before every phase commit
  and after every material verification, provisioning, blocker, push or pull-request change.

## Verification Run

- Baseline: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
  :androidApp:testDebugUnitTest architectureCheck contractCheck` — passed, 219 actionable tasks;
  `contractCheck` reported 112 aligned decisions / ADRs, none unresolved and no pending assertion.
- Decision and contract checkpoint: `./gradlew contractCheck` — passed; 115 decisions and 115 ADRs
  are aligned, no decision is unresolved and no contract assertion is pending.
- Shared RED: `./gradlew :shared:testAndroidHostTest` — failed at test compilation on the missing
  primitive provider completion intents and closed native-failure enum.
- Android RED: `./gradlew :androidApp:testDebugUnitTest` — failed at test compilation on the missing
  onboarding route model/resolver and Google sign-in coordinator boundary.
- iOS RED: `xcodebuild -quiet -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator
  -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build-for-testing`
  from `iosApp/` — failed at test compilation on the missing onboarding route/resolver and Apple
  nonce implementation. The earlier run without `ARCHS=arm64` is excluded because it exercised an
  unsupported simulator architecture rather than story behavior.

## Contract Impact

- D-114 is represented in `docs/CONTRACTS.md` sections 11.1, 15.1, 15.3 and 20.10. The exported
  boundary accepts primitive tokens only, exports the closed `NativeSignInFailure` enum, and does
  not export `NativeAuthCredential`.

## Decision Board Impact

- D-112, D-113 and D-114 are owner-accepted and recorded with ADR-0113, ADR-0114 and ADR-0115 in
  every required mirror before implementation.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- The development Firebase configurations do not yet expose the OAuth client identifiers required
  by the native Google flows; E2-03 must regenerate them before provider acceptance.
- The owner has the Apple Developer authority needed to enable Sign in with Apple. Provisioning is
  still pending and must be recorded with the exact changed configuration files and the D-53-safe
  committed values.

## Human Review Gate

- Applies: authentication is a gated topic; expected contract, decision, version and Swift ABI
  paths are gated. The pull request must be reviewed and merged by the owner.

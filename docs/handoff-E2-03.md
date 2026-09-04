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
- Current phase and latest commit: GREEN verification is complete and ready to commit. The RED
  phase is committed as `0472e11` (`test(E2-03): define native onboarding behavior`).
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
  the Android route/coordinator/UI action set, and the iOS route/nonce/failure/UI action set. The
  shared implementation now observes the authoritative auth state, performs the offline local-owner
  fallback, exchanges provider primitives through internal credentials, rejects mismatched
  completions, and exposes only the closed failure enum to Swift. Android now has the F-1 router,
  exact Google/guest action set and Credential Manager adapter. iOS now has the F-1 router, exact
  Apple/Google/guest action set, native Apple nonce flow, GoogleSignIn adapter and URL callback.
  The owner explicitly declined credential sharing and authorized use of already authenticated
  external CLIs only. An automatic-device build with Xcode account team `QGPVH5G7ST` succeeded,
  causing Apple to register the development App ID capability and generate a new development
  provisioning profile. Inspection of the signed development app proves the application identifier
  is `QGPVH5G7ST.com.ruizurraca.carapp.debug`, the `com.apple.developer.applesignin` entitlement is
  `Default`, App Attest uses the production environment and the signed team identifier is
  `QGPVH5G7ST`. The profile itself remains outside the repository.
- Verification evidence and known failures: the focused pre-story baseline passed 219 actionable
  tasks, including `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`,
  `:androidApp:testDebugUnitTest`, `architectureCheck` and `contractCheck`. `contractCheck` reported
  112 aligned decisions / ADRs, no unresolved decisions and no pending assertions. Static inspection
  found that the committed Android debug Firebase configuration has no OAuth client and the iOS
  debug Firebase plist has no client or reversed-client identifiers. These are expected Google
  provisioning gaps for E2-03, not code regressions. The iOS Sign in with Apple entitlement is now
  present in source, the development App ID and regenerated profile, and the signed development
  build. The shared RED run fails at test compilation because `completeGoogleSignIn`,
  `completeAppleSignIn`, `failSignIn` and `NativeSignInFailure` do not exist. The Android RED run
  fails at test compilation because `AndroidGoogleSignInCoordinator`,
  `GoogleCredentialAcquisition`, `OnboardingDestination` and the route resolver do not exist. The
  iOS RED build-for-testing, using the repository-required `ARCHS=arm64` override, fails at test
  compilation because the onboarding resolver/routes and `AppleSignInNonce` do not exist. An
  initial generic-simulator invocation without `ARCHS=arm64` requested the unsupported x86_64 KMP
  framework; it was a setup mismatch and is not counted as RED evidence.
  Before provisioning, Firebase listed only the Android SHA-256 registration. The owner-local debug
  SHA-1 was registered through Firebase CLI without storing it in the repository. The repository's
  pinned Firebase CLI then created `Default Web App` and enabled anonymous plus Google Sign-In from
  the declarative `firebase.json` Auth configuration. Firebase generated client types 1 and 3 for
  Android and both `CLIENT_ID` and `REVERSED_CLIENT_ID` for iOS. The generated Android file was
  deliberately normalized to omit the client-type-1 record because Firebase includes the forbidden
  D-41 debug certificate fingerprint in that record; the retained client-type-3 web client is the
  runtime input consumed as `default_web_client_id`, while the Android client remains registered
  external state. The generated iOS client and reversed client identifier are committed, and the
  reversed identifier is the app URL scheme. The official Identity Toolkit Admin API enabled Apple
  with `com.ruizurraca.carapp.debug` as its only bundle ID and no web/code-flow credentials, as the
  E2-03 native Apple-only path requires. Current Cloud API Key metadata still restricts the Android
  key to the debug package plus owner-local certificate and the iOS key to the debug bundle.
- Open decisions or blockers: none. Real Google/Apple account selection cannot be automated without
  user credentials and remains an explicit manual owner acceptance item; the owner explicitly
  instructed the agent not to request credentials.
- Exact next step: commit the verified GREEN phase, then perform the bounded REFACTOR phase,
  finalize completion records, rerun the affected gates, close every emulator and simulator used
  for E2-03, push once and create the pull request.

## Scope Completed

- Intake and readiness closure, dependency-resolution evidence, accepted decision/ADR/contract
  records, exact dependency pins, the complete cross-platform RED test surface, shared/Android/iOS
  GREEN product implementation, Firebase provider configuration, Google OAuth provisioning, Apple
  development provisioning and all local GREEN verification.

## Acceptance Evidence

- RED evidence is recorded under `Verification Run`; each platform fails for the intended missing
  E2-03 behavior rather than an unrelated baseline regression.
- Focused shared, Android and iOS GREEN suites pass. The complete non-instrumented repository gate,
  forced provider-decoupling graph, D-84 API 36 Android instrumented suite, iOS onboarding and
  pre-existing vehicle/fuel UI suites, Android assemblies and signed iOS device build all pass.
- The Apple capability is present in source, on the development App ID, in the regenerated profile
  and in the signed development application. Firebase reports anonymous, Google and Apple enabled.

## Out of Scope / Not Done

- Real provider-account UI acceptance is not automated because it requires an owner-controlled
  Google or Apple account. It remains the pull-request human acceptance item.
- E2-06 local-owner adoption remains out of scope.

## Files Changed

- Decision, contract, ADR and compatibility documentation for D-112 through D-114.
- Shared session behavior, primitive provider completion ABI, closed native failure enum, Objective-C
  golden header and behavior tests.
- Android Credential Manager acquisition, onboarding routing/UI/resources, Firebase configuration,
  host tests and onboarding-aware instrumented regression tests.
- iOS Apple/Google acquisition, nonce hashing, onboarding routing/UI/resources, URL callback,
  entitlement/signing/project configuration, Firebase configuration and onboarding-aware UI tests.
- Declarative Firebase Auth provider configuration and this continuously maintained handoff.

## Decisions Made

- The owner selected the stable Android Credential Manager stack as D-112, native Apple plus exact
  GoogleSignIn-iOS 9.2.0 as D-113, and primitive provider-named completion intents with a closed
  failure enum as D-114. Their ADRs contain the alternatives and trade-offs.
- The owner's explicit one-push instruction replaces the default per-phase push cadence for this
  story; RED, GREEN and REFACTOR will remain separate local commits.
- The owner requested continuous, fully recoverable process documentation because the active agent
  may reach its session limit. Under D-105 this handoff will be updated before every phase commit
  and after every material verification, provisioning, blocker, push or pull-request change.

The owner also requested that every Android emulator and iOS simulator used for this story be shut
down when verification finishes to release laptop resources. This is an execution constraint, not a
product or architecture decision.

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
- Shared GREEN checkpoint: `./gradlew :shared:testAndroidHostTest` — passed, 39 tests.
- Android GREEN checkpoint: `./gradlew :androidApp:testDebugUnitTest` — passed; 142 actionable
  tasks. `./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest` first exposed an
  invalid test-only import; after its removal, `:androidApp:assembleDebugAndroidTest` passed.
- iOS GREEN checkpoint: the corrected arm64 `xcodebuild ... build-for-testing` passed. The focused
  `xcodebuild ... test -only-testing:carAppTests` run passed on the iOS 26.4 iPhone 17 Pro simulator.
- External provider checkpoint: Firebase CLI registered the Android SHA-1, created `Default Web
  App`, enabled anonymous plus Google, and generated Android OAuth client types 1 / 3 plus iOS
  client and reversed-client identifiers. Identity Toolkit reports Apple enabled for only the
  development bundle. Google Cloud reports the Android and iOS Firebase API keys retain their D-53
  platform application restrictions.
- Post-provisioning GREEN checkpoint: shared Android-host tests, Android host tests, both Android
  APK assemblies, build-logic tests and `contractCheck` pass together; `contractCheck` reports 115
  aligned decisions / ADRs, the complete Swift allowlist and no forbidden Kotlin construction
  types. The regenerated Objective-C header exports `NativeSignInFailure` plus the three new
  provider completion/failure intents and omits `NativeAuthCredential`. The focused iOS unit suite
  passes after Google configuration, and the onboarding XCUITest passes after one simulator-busy
  infrastructure retry. The complete 10-test Android instrumented suite passes on the D-84 API 36
  emulator, and both pre-existing iOS Vehicle/Fuel XCUITests pass through the new onboarding route.
  An initial unscoped Android invocation also targeted a connected Android 9 phone and failed there
  because its Compose test host did not mount; the same test passed both before that failure on the
  required emulator and again in an emulator-only run, so the phone result is excluded from D-84
  acceptance evidence.
- Signed iOS device build: `xcodebuild -quiet -project carApp.xcodeproj -scheme carApp -sdk iphoneos
  -destination 'generic/platform=iOS' -allowProvisioningUpdates build` — passed using the persisted
  project team and entitlement configuration, with only the existing orientation and always-run
  framework-script warnings.
- Configuration hygiene: `git diff --check`, `plutil -lint` for the two plists and entitlements, and
  `jq empty` for both changed JSON files — passed. A targeted secret check found no certificate hash
  or SHA-1 fingerprint in any committed provider configuration.
- Complete non-instrumented GREEN gate: the first run stopped on Kotlin formatting findings in the
  new closed mappings; after formatting, the next run stopped on one overly broad Android exception
  catch and a complexity threshold reached by the expanded message mapping. The catch was narrowed
  to Credential Manager and Google token parsing exceptions and the auth mapping was isolated. The
  final exact `AGENTS.md` command passed 636 actionable tasks, including ktlint, detekt,
  `architectureCheck`, `contractCheck`, build-logic tests, Kover verification, Android build/host
  tests and all eligible iOS simulator KMP tests. `contractCheck` reports 115 aligned decisions and
  ADRs, a complete Swift allowlist and no pending assertion.
- Provider decoupling: `./gradlew -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest
  iosSimulatorArm64Test` — passed 234 actionable tasks with the Firebase provider graph removed.

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

- Real provider-account selection is intentionally left to owner review because no user credentials
  may be supplied to the agent. The protected keychain/App Check UI path continues to require its
  registered debug-token environment in CI.
- Sign in with Apple is enabled for the development App ID and a matching development profile has
  been regenerated. The Apple profile, signing credentials and Android debug certificate
  fingerprint remain uncommitted. D-53 permits the restricted Firebase API keys, Firebase app IDs,
  OAuth client IDs, reversed client identifier, fixed bundle/package identifier and Apple team ID
  committed by this story; none is a credential or user token. The changed configuration files are
  `firebase.json`, `androidApp/src/debug/google-services.json`,
  `iosApp/Config/Debug/GoogleService-Info-Debug.plist`, `iosApp/Info.plist`,
  `iosApp/carApp.entitlements`, `iosApp/project.yml` and the generated
  `iosApp/carApp.xcodeproj/project.pbxproj`.

## Human Review Gate

- Applies: authentication is a gated topic; expected contract, decision, version and Swift ABI
  paths are gated. The pull request must be reviewed and merged by the owner.

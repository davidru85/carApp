# Agent Handoff - E0-07

## Story

`E0-07 - Walking Skeleton - L` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — E0-07 builds the single native-UI-to-SQLDelight-to-Firebase
  vehicle slice and validates the Swift-facing application graph.
- [x] Acceptance criteria reviewed — real anonymous authentication; a complete contract-valid
  vehicle crossing native UI, SQLDelight and Firestore on both native paths while the same retained
  Firebase Auth session remains available; no anonymous cross-device promise; disabled Firestore
  persistence; direct framework integration; generated Objective-C golden header; app-graph fake
  parity; protected multiplatform CI; D-66 billing containment and destructive recovery evidence;
  and D-67 App Check enforcement on both native paths.
- [x] Dependencies checked — E0-01 through E0-06, E0-08, E1-01, E3-06 and E3-01 are merged; the
  development Firebase project and `europe-west1` Firestore database exist. E0-07 owns creation
  and restriction of the two missing debug app registrations before configuration files land.
- [x] Decisions checked — the active decisions through D-75 that govern this story are `Accepted`;
  D-2 is superseded by D-58; D-53 records the
  owner's 2026-08-24 selection of separate Firebase-provisioned and restricted platform keys, and
  D-56 records the owner's 2026-08-25 correction of the test-factory module boundary; D-58 moves
  the iOS framework composition root out of `:shared`; D-59 fixes the explicit `AppProviders`
  port selected by the owner; and D-60 through D-64 define device-bound anonymous identity,
  destructive collision precedence, retention notices, owned cleanup and their story split;
  D-65 pins the native Firebase Apple SDK to the exact GitLive cinterop version; D-66 fixes the
  development-only billing budget, cutoff, Node.js 22 runtime and OIDC verification; D-67 moves App
  Check into MVP scope for billed Authentication and Firestore; D-68 retains the official
  Functions graph under an expiring, dynamically unreachable moderate advisory acceptance; D-69
  isolates the required Billing Admin privilege with a monitoring control; D-70 disables cutoff
  retries and makes every execution failure visible; D-71 restores normal simulator signing;
  D-72 selects controlled threshold events for destructive cutoff acceptance; D-73 retains the
  actual Cloud Functions artifacts for one day; D-74 selects a dedicated test-only XCUITest target
  for Keychain persistence; and D-75 derives the standalone Native-test exemption from the
  Firebase integration dependency closure. No
  `Proposed` or `Pending` decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §2`, `§7 F-1` and `F-2`, `§8`, `§9`,
  `§10`, `§11`; `docs/CONTRACTS.md §1.1`, `§3`, `§11`, `§14`, `§15`, `§16`, `§18`, `§20.3`,
  `§20.3.2`, `§20.7`, `§20.8` and `§20.10`; `docs/TECHNICAL_PLAN.md §3`–`§5`, `§10`, `§12`, `§13`;
  `docs/SECURITY.md`; `docs/identifiers.md`; `docs/versions-matrix.md`; and the E1-01, E3-06 and
  E3-01 handoffs.
- [x] Expected verification identified — focused RED/GREEN shared behavior tests; graph
  construction and provider integration tests; Android host and `iosSimulatorArm64` tests;
  provider-free graph proof; generated-header diff; Android and iOS builds; real local/remote
  Vehicle evidence on both native application paths under retained anonymous sessions; and the
  complete local and protected CI commands; real billing cutoff and recovery; read-only deployed
  runtime verification; App Check enforcement and rejection without a valid token. Permanent-account
  two-device evidence belongs to E3-12.
- [x] Human review gates identified before work — E0-07 is a gated story; Firebase/auth/backend,
  module boundaries and Swift ABI are gated topics; `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`, `docs/identifiers.md`,
  `docs/versions-matrix.md`, `core/auth/**`, `core/sync/**` and `core/database/**` are gated paths.
  Owner review is required before merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added the final E0-07 module topology: `:core:auth`, `:core:sync`, the three feature shells,
  `:integration:firebase-auth`, `:integration:firebase-firestore`, `:wiring:firebase`,
  `:composition:ios` and `:shared:testing`.
- Implemented the functional Vehicle walking-skeleton slice from shared state holders through the
  bundled SQLDelight database, local-first mutation/outbox handling, Firestore backup and explicit
  remote restore. Later feature state holders remain deterministic contract shells.
- Implemented real anonymous Firebase Auth with retained-session hydration and a local-owner
  fallback; anonymous identity remains device-bound and no permanent-account behavior is claimed.
- Implemented the GitLive Firestore adapter with memory-only caching, closed Vehicle serialization,
  server-confirmed pushes and cursor-independent Vehicle recovery for this slice.
- Added the Android Compose and iOS SwiftUI hosts, localized English/Spanish strings and
  configuration-aware native Firebase/App Check initialization.
- Moved the only Kotlin/Native framework and SKIE processing to `:composition:ios`, retained
  `baseName = "Shared"`, exported `:shared` and committed the exact Objective-C header golden.
- Added the provider-free `AppProviders` boundary and the `:shared:testing` graph factory with
  default fakes, while preserving the prohibited dependency directions.
- Provisioned and documented development Firebase safeguards: restricted debug app credentials,
  Identity Platform anonymous cleanup, enforced App Check, the EUR 10 notification-only budget,
  the deployed `stopBilling` function, monitoring alerts, restricted OIDC runtime verification and
  one-day Functions artifact retention.
- Added the test-only `carAppUITests` target to prove Firebase Auth Keychain persistence across a
  real process restart without changing the application entitlements or shipping archive.
- Extended protected CI and `contractCheck` with Apple header verification, cloud-runtime pinning,
  Firebase configuration guards, platform-host checks and graph-derived Native-test exemptions.

## Acceptance Evidence

- Android and iOS native acceptance each used a registered local App Check debug token, established
  a real anonymous Firebase UID, saved a complete contract-valid Vehicle name through the shared
  state holder, observed it from the bundled SQLDelight database and confirmed the corresponding
  `users/{uid}/vehicles/{vehicleId}` backup. After deleting only local product rows, `Restore
  backup` fetched the same Vehicle under the retained anonymous session. Temporary remote documents,
  anonymous users and debug tokens were removed after acceptance.
- The iOS Keychain XCUITest started from an erased simulator, signed in through the real UI,
  asserted `Session: Anonymous backup active`, executed `terminate()` followed by `launch()` in
  the same test and asserted the retained state only through the relaunched UI. Removing retained
  session hydration produced the required red run before the restored implementation passed.
- A Release archive contains `carApp.app` but no `carAppUITests.xctest`, test-only bundle identifier
  or shared Keychain group. The UI-test target is not embedded and is built only for the test action.
- Live App Check resources report `ENFORCED` for Authentication and Firestore. Unattested Auth was
  rejected after propagation, while registered Android and iOS debug providers succeeded. Release
  guards prove the debug Android provider, development plist and debug token path do not ship.
- The generated simulator Objective-C header is identical to
  `shared/build/generated/objc-header/Shared.h.golden`. It exports one
  `createSwiftAppGraph(isDebugBuild)` plus the approved Swift surface, and omits all Kotlin-only
  graph/provider types.
- `contractCheck` assertion 21 derives the transitive closure rooted at the two Firebase
  integration modules and matches exactly the four declared exclusions:
  `:integration:firebase-auth`, `:integration:firebase-firestore`, `:wiring:firebase` and
  `:composition:ios`. Mutation tests fail for both a missing qualifying module and a stale extra
  declaration.
- The real D-72 cutoff test published two schema-valid threshold events. Logs recorded
  `BILLING_DISABLED` followed by the idempotent `ALREADY_DISABLED` path; billing was detached for
  5m 10.173s and the owner completed recovery end to end in at most 101 seconds. The expected and
  actual behavior of Auth, Firestore, Pub/Sub, the function and stored data is preserved in
  `docs/runbooks/development-firebase-cost-controls.md`.
- The development budget is restored to EUR 10 with actual-cost thresholds 50/90/100%, both alert
  paths, `retry: false`, concurrency 1 and maximum instances 1. `stopBilling` is `ACTIVE` in
  `europe-west1` on Node.js 22 with its dedicated keyless identity.
- D-73 targets the deployed function's actual `europe-west1/gcf-artifacts` repository with
  `DELETE`, `olderThan = 86400s` and `tagState = ANY`. Both recorded images remained present at
  2026-08-27T13:30:04.831Z and the inventory was empty at 2026-08-27T14:31:45Z, with no manual
  deletion. The policy-update audit timestamp, bounded deletion window and absence of a separate
  `BatchDeleteVersions` audit entry are recorded in the cost-control runbook. Billing remained
  enabled and the Gen 2 `stopBilling` function remained `ACTIVE` on Node.js 22 after cleanup.

## Out of Scope / Not Done

- The complete vehicle feature remains owned by E1-02 and E1-03.
- The complete backup/recovery engine and `RemoteSyncSource` remain owned by E3-02 and E3-03.
- Permanent Google/Apple sign-in, local-owner adoption, collision handling, anonymous retention
  notices, backend identity cleanup, permanent-account cross-device recovery and account deletion
  remain in E2-02, E2-04, E2-06, E2-07, E3-10, E3-11 and E3-12.

## Files Changed

- Build and module topology: `settings.gradle.kts`, root/build-logic Gradle files,
  `gradle/libs.versions.toml`, `:core:auth`, `:core:sync`, `:shared:testing`, the three feature
  shells, both Firebase integration modules, `:wiring:firebase` and `:composition:ios`.
- Shared product slice: graph/provider contracts, Swift facade, UI models/state holders, Vehicle
  runtime, database mutations/schema support and their common/host tests.
- Native hosts: Android application/configuration/resources and iOS SwiftUI model, App Check
  factory, debug configuration, XcodeGen source, generated project/scheme, SPM resolution,
  localization, entitlements and XCUITest.
- Backend and controls: `firebase.json`, `functions/`, `infra/google-cloud/`, Firestore rules/tests,
  `scripts/verify-cloud-runtime.sh` and protected CI.
- Documentation: the four decision mirrors, ADR-0054 through ADR-0076 and their index, security
  register/policy, identifiers/version matrices, Firebase runbooks, public/shared READMEs, backlog,
  project log and this handoff.

## Decisions Made

- D-53 selects separate Android and iOS debug app registrations and restricted platform keys.
- D-54 isolates both development configuration files to debug builds and makes release builds fail
  closed until reviewed production configuration exists.
- D-55 stages the final module and public-contract topology in E0-07, limits real product behavior
  to the Vehicle walking-skeleton slice and reserves all remaining behavior for its owning stories.
- D-56 places `testAppGraphDependencies(...)` in `:shared:testing`, keeps `AppGraphDependencies`
  in `:shared` and preserves `:core:testing` as generic test support.
- D-57 pins Google Services Gradle plugin 4.5.0 for Android's debug-only Firebase configuration.
- D-58 makes `:composition:ios` the sole owner of the `Shared` framework and Swift factory while
  `:shared` remains provider-free.
- D-59 defines `AppProviders` as explicit typed properties for every graph dependency except
  `isDebugBuild`, which remains owned by `buildAppGraph`.
- D-60 defines unlinked anonymous identity as device-bound and selects Firebase native 30-day
  cleanup through Authentication with Identity Platform.
- D-61 makes the current anonymous-session snapshot win a credential collision after explicit
  destructive confirmation, with resumable replacement and orphan cleanup.
- D-62 selects foreground retention notices on elapsed days 1, 3, 8 and 18 with highest-due
  collapse.
- D-63 selects an owned idempotent deletion service, a direct Admin collision path and one tracked
  1st gen Auth deletion-trigger exception (`TD-01`).
- D-64 keeps E0-07 narrow and assigns permanent auth, conversion, notices, cleanup and cross-device
  recovery evidence to their owning stories.
- D-65 pins Firebase Apple SDK 11.8.0 exactly with GitLive 2.6.0 and requires them to move
  together, preventing a native cinterop/runtime version mismatch.
- D-66 selects an EUR 10 development budget, actual-cost alerts at 50/90/100%, a project-local
  2nd gen cutoff at 100%, manual recovery, production alert-only policy, Node.js 22 coupled to
  D-63 and repository/ref-restricted read-only OIDC verification.
- D-67 moves App Check into MVP scope because billing converts anonymous-authentication abuse into
  a direct cost vector, and selects App Attest, Play Integrity and local/CI-only debug providers.
- D-68 retains the official Firebase Functions dependency graph after a full-trigger dynamic test
  proves the affected UUID variants unreachable; the production residual expires into the existing
  TD-01 quarterly review and does not weaken the high/critical CI gate.
- D-69 accepts the standard Billing Admin role only on a dedicated keyless cutoff identity because
  the personal billing account cannot host a custom role; an administrative-change alert is the
  compensating visibility control.
- D-70 disables automatic Pub/Sub retry because a persistent fault could bill for seven days;
  every execution failure alerts the owner and the measured recurring budget publication bounds a
  dropped event's second-attempt delay.
- D-71 removes the obsolete global simulator-signing prohibition. The original rule prevented
  accidental Apple-account/signing coupling in a placeholder host; E0-07 now needs normal local
  signing because Firebase Auth persists through the application Keychain.
- D-72 exercises the destructive cutoff with controlled, schema-valid threshold events instead of
  deliberately creating spend or leaving acceptance open-ended.
- D-73 retains Cloud Functions deployment artifacts for one day in the exact repository used by
  the deployed function and requires an observed policy deletion.
- D-74 uses the test-only `com.ruizurraca.carapp.uitests` target to prove retained Auth state through
  a real process restart and visible UI, with no shared Keychain entitlement or shipping artifact.
- D-75 defines a graph-derived exception for standalone Kotlin/Native test binaries that
  transitively link the Firebase integration roots. Its current four-module resolution is checked
  for equality in both directions; XCUITest performs no Firestore operations, so iOS Native
  Firestore read/write paths remain uncovered.
- TDD order exemption used for architecture-rule fixtures, exactly as permitted by
  `docs/SPECIFICATION.md §11`: the fixtures and their checker implementation were completed in the
  architecture RED/GREEN cycle, and each forbidden edge has an executable failing fixture.
- Coverage tests for the staged `:core:model` and `:core:sync` value models were added after their
  implementation, using the explicit coverage-test exemption in `docs/SPECIFICATION.md §11`; no
  production behavior changed and neither coverage threshold was lowered or excluded.

## Verification Run

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test
  -x :integration:firebase-auth:iosSimulatorArm64Test
  -x :integration:firebase-firestore:iosSimulatorArm64Test
  -x :wiring:firebase:iosSimulatorArm64Test
  -x :composition:ios:iosSimulatorArm64Test --stacktrace` — passed; 570 actionable tasks, with the
  D-75 derived/declared set equal and every non-exempt Native test executed.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test
  --stacktrace` — passed; the provider-free graph executed on Android host and Native with no
  Firebase-project exclusions.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64
  :composition:ios:linkDebugFrameworkIosArm64 --stacktrace` — passed for simulator and device
  Apple targets.
- `diff -u shared/build/generated/objc-header/Shared.h.golden
  composition/ios/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h` —
  passed with no difference.
- `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug
  -derivedDataPath /tmp/carapp-e0-07-derived-data ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` from
  `iosApp/` — `BUILD SUCCEEDED`; SwiftPM resolved Firebase Apple 11.8.0 exactly.
- `CARAPP_XCUITEST_APP_CHECK_DEBUG_TOKEN=<redacted> xcodebuild -project carApp.xcodeproj -scheme
  carApp -destination 'platform=iOS Simulator,id=<erased-acceptance-simulator>'
  -only-testing:carAppUITests/CarAppKeychainPersistenceUITests test` — observed red with retained
  hydration temporarily removed, then passed unchanged production code after restoration.
- `xcodebuild ... -configuration Release -archivePath <temporary-path>/carApp.xcarchive archive`
  plus archive inventory — passed; no UI-test bundle or identifier is present.
- `npm run test:firestore-rules` — passed 154 tests against the Firestore emulator.
- `npm test` from `functions/` — TypeScript build and all 10 tests passed, including idempotency and
  full-trigger dependency reachability.
- `npm run audit` from `functions/` — exited successfully at the high/critical gate; seven accepted
  moderate advisories remain explicitly registered under D-68.
- `./scripts/verify-cloud-runtime.sh` — passed; local pins and deployed runtime all equal the
  normative `nodejs22` value.
- Live Android and iOS acceptance — passed the same-session SQLDelight -> Firestore -> local-row
  removal -> Firestore restore path with registered local App Check tokens. All Android emulators
  and iOS simulators were shut down after use.

## Contract Impact

- Updated `docs/CONTRACTS.md` for the final graph/auth/sync/Swift surface, the E0-07 Vehicle slice,
  App Check enforcement, owned deletion contracts, provider-free construction,
  `:shared:testing`, runtime/header checks and the graph-derived D-75 Native-test rule. The generated
  Objective-C header and executable contract checks prove the implementable representation.

## Decision Board Impact

- Added D-53 through D-75 and ADR-0054 through ADR-0076 before the implementation each decision
  governs.

## Shared-Write Modules Touched

- `:core:database` — E0-07 owned the synchronized Vehicle mutation boundary while the story was in
  flight. That exclusive ownership ended when PR #27 merged.

## Project Log Entry

- [x] The 2026-08-27 E0-07 completion entry records PR #27 and the observed D-73 deletion.

## Risks or Follow-ups

- D-75 leaves Firebase Auth, Firestore and provider-wiring unit tests without standalone
  Kotlin/Native execution. XCUITest covers graph/provider construction, Firebase initialization,
  anonymous Auth and Keychain persistence, but performs no Firestore operation; Firestore reads
  and writes have no Native-side automated iOS coverage. `:composition:ios` currently has no tests,
  so it loses nothing until tests are added.
- D-63/TD-01 jointly reviews the 1st gen Auth trigger/Node.js 22 debt and D-75 expiry signals first
  on 2026-12-01 and quarterly thereafter; 2027-10-31 remains the hard runtime deadline.
- D-68 retains seven moderate advisories only while the affected dependency path remains
  dynamically unreachable. Any high/critical advisory, Storage expansion or compatible official
  update triggers re-evaluation.
- Production Firebase configuration, budgets and alert-only intervention remain owned by E4-04;
  production MUST NOT inherit the development cutoff.
- E3-12 retains the permanent-account Android-to-iOS recovery proof. Anonymous credentials remain
  device-bound by design.
- PR #27 received owner review and merged on 2026-08-27. Documentation-only PR #28 then recorded
  the observed D-73 cleanup, received owner review and merged on the same date.

## Human Review Gate

- Applied to implementation PR #27 and documentation-only D-73 closure PR #28. The owner reviewed
  and merged both pull requests.

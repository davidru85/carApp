# ADR Index

One ADR per decision ID. The decision ID is stable across project documents; ADR numbers are file identifiers only.

`docs/DECISION_BOARD.md` is the sole registry of decision IDs, and the ADR `Status` field MUST equal the board status for the same ID. `contract-check` asserts that the decision ID set and status values here are identical to `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12` and `docs/TECHNICAL_PLAN.md §2`.

| Decision ID | ADR | Decision | Status |
|-------------|-----|----------|--------|
| D-0 | [ADR-0001](0001-backend-cloud-firestore.md) | Use Cloud Firestore as remote backend. | Accepted |
| D-1 | [ADR-0002](0002-local-database-room-kmp.md) | Use Room 3.0 KMP for the local database. | Superseded |
| D-2 | [ADR-0003](0003-ios-interop-skie.md) | Use SKIE for Kotlin-to-Swift interop. | Superseded |
| D-3 | [ADR-0004](0004-koin-dependency-injection.md) | Use Koin KMP for dependency injection. | Accepted |
| D-4 | [ADR-0005](0005-vehicle-fuel-type-from-day-one.md) | Store MVP fuel type from day one; defer electric/hybrid values. | Accepted |
| D-5 | [ADR-0006](0006-firestore-remote-sync-source.md) | Use Firebase Firestore behind `RemoteSyncSource`. | Accepted |
| D-6 | [ADR-0007](0007-firebase-auth-gitlive.md) | Use Firebase Auth through GitLive behind `AuthClient`. | Accepted |
| D-7 | [ADR-0008](0008-native-navigation.md) | Use native navigation per platform. | Accepted |
| D-8 | [ADR-0009](0009-shared-presentation-state-holders.md) | Share presentation state holders in KMP. | Accepted |
| D-9 | [ADR-0010](0010-disable-firestore-offline-persistence.md) | Disable Firestore offline persistence. | Accepted |
| D-10 | [ADR-0011](0011-firebase-analytics.md) | Use Firebase Analytics behind `AnalyticsTracker`. | Accepted |
| D-11 | [ADR-0012](0012-defer-ktor-api-client.md) | Defer Ktor until an API-based remote implementation exists. | Deferred |
| D-12 | [ADR-0013](0013-defer-coil-image-loading.md) | Use Coil if image loading becomes necessary. | Deferred |
| D-13 | [ADR-0014](0014-firestore-location-europe-west1.md) | Create the Firestore database in `europe-west1`. | Accepted |
| D-14 | [ADR-0015](0015-firebase-project-topology.md) | One development Firebase project plus the emulator now; separate production project before release. | Accepted |
| D-15 | [ADR-0016](0016-logging-kermit.md) | Use Kermit behind the `Logger` abstraction. | Accepted |
| D-16 | [ADR-0017](0017-architecture-checks-konsist.md) | Konsist for package rules, custom Gradle check for module rules. | Accepted |
| D-17 | [ADR-0019](0019-flow-testing-turbine.md) | Use Turbine for Flow testing. | Accepted |
| D-18 | [ADR-0022](0022-coverage-kover.md) | Use Kover for coverage thresholds. | Accepted |
| D-19 | [ADR-0018](0018-outcome-result-type.md) | Custom `Outcome<T, E>` as the result channel. | Accepted |
| D-20 | [ADR-0020](0020-localization-native-resources.md) | Native resources; no user-facing text in `UiState`. | Accepted |
| D-21 | [ADR-0023](0023-firebase-crashlytics.md) | Use Firebase Crashlytics behind `CrashReporter` in Phase 4. | Accepted |
| D-22 | [ADR-0021](0021-application-identifiers.md) | Application identifiers are owner-decided and fixed in `docs/identifiers.md`. | Accepted |
| D-23 | [ADR-0024](0024-account-deletion-server-admin.md) | Use a Firebase Admin server operation for account deletion hard deletes. | Accepted |
| D-24 | [ADR-0025](0025-module-android-namespaces.md) | Derive module Android namespaces from the Gradle module path. | Accepted |
| D-25 | [ADR-0026](0026-targetsdk-separate-from-compilesdk.md) | Pin `targetSdk` separately from `compileSdk`. | Accepted |
| D-26 | [ADR-0027](0027-monetary-golden-row-correction.md) | Correct the contradictory monetary golden row of `§2`. | Accepted |
| D-27 | [ADR-0028](0028-test-app-graph-dependencies-in-e0-07.md) | Build `testAppGraphDependencies` in `E0-07`. | Accepted |
| D-28 | [ADR-0029](0029-feature-package-rules-in-e1-07.md) | Implement the feature-layer package rules in `E1-07`. | Accepted |
| D-29 | [ADR-0030](0030-contract-declarations-may-live-inline.md) | Contract types may be declared inline, not only in `§20`. | Accepted |
| D-30 | [ADR-0031](0031-walking-skeleton-starts-phase-1.md) | `E0-07` moves to the start of Phase 1. | Accepted |
| D-31 | [ADR-0032](0032-branch-protection-requires-nine-checks.md) | Branch protection requires all nine CI checks. | Accepted |
| D-32 | [ADR-0033](0033-development-firebase-project-id.md) | Development Firebase project ID is `davidruiz-carapp-dev`. | Accepted |
| D-33 | [ADR-0034](0034-repository-stays-private-branch-protection-deferred.md) | Repository stays private; branch protection deferred with an explicit trigger. | Superseded |
| D-34 | [ADR-0035](0035-repository-public-and-branch-protection-active.md) | Repository is public; the `D-31` branch protection is active. | Accepted |
| D-35 | [ADR-0036](0036-ci-keeps-shared-tests-and-ios-build-separate.md) | `shared-tests` and `ios-simulator-build` stay separate CI jobs. | Accepted |
| D-36 | [ADR-0037](0037-local-database-sqldelight-androidx-sqlite.md) | Use SQLDelight with the AndroidX bundled SQLite driver. | Accepted |
| D-37 | [ADR-0038](0038-supported-ios-targets-are-arm64.md) | Support ARM64 iOS device and simulator targets only. | Accepted |
| D-38 | [ADR-0039](0039-database-mutations-use-transaction-facade.md) | Route synchronized entity writes through a Kotlin/SQLDelight transaction facade. | Accepted |
| D-39 | [ADR-0040](0040-walking-skeleton-uses-minimal-vehicle.md) | Use a minimal valid vehicle for the walking-skeleton round trip. | Accepted |
| D-40 | [ADR-0041](0041-firestore-rules-precede-walking-skeleton.md) | Complete Firestore rules before the walking skeleton. | Accepted |
| D-41 | [ADR-0042](0042-development-firebase-key-uses-local-debug-certificate.md) | Restrict the development Firebase key to the owner's local debug certificate. | Accepted |
| D-42 | [ADR-0043](0043-provider-decoupling-precedes-first-integration.md) | Complete provider decoupling before the first integration module. | Accepted |
| D-43 | [ADR-0044](0044-provider-exclusion-uses-gradle-property.md) | Select provider-free settings through a Gradle property. | Accepted |
| D-44 | [ADR-0045](0045-provider-modules-use-explicit-conditional-registry.md) | Use an explicit conditional provider-module registry. | Accepted |
| D-45 | [ADR-0046](0046-provider-proof-runs-jvm-and-kotlin-native.md) | Run the provider proof on JVM and Kotlin/Native. | Accepted |
| D-46 | [ADR-0047](0047-firestore-rules-use-official-node-test-stack.md) | Use the exact official Node/Firebase emulator test stack. | Accepted |
| D-47 | [ADR-0048](0048-firestore-rules-run-in-contract-check.md) | Run Firestore emulator tests inside `contract-check`. | Accepted |
| D-48 | [ADR-0049](0049-walking-skeleton-owns-firestore-client-cache-config.md) | Let E0-07 own executable Firestore client-cache configuration. | Accepted |
| D-49 | [ADR-0050](0050-mvp-firestore-schema-version-is-exact.md) | Accept exactly remote schema version 1 during the MVP. | Accepted |
| D-50 | [ADR-0051](0051-firestore-first-page-cursor-is-timestamp-only.md) | Use a timestamp-only boundary for the first delta-pull page. | Accepted |
| D-51 | [ADR-0052](0052-disable-npm-dependency-install-scripts.md) | Disable npm dependency install scripts repository-wide. | Accepted |
| D-52 | [ADR-0053](0053-retain-firebase-cli-with-moderate-audit-residual.md) | Retain the pinned Firebase CLI with its documented moderate audit residual. | Accepted |
| D-53 | [ADR-0054](0054-use-separate-firebase-debug-app-keys.md) | Use separately provisioned and restricted Android and iOS debug app keys. | Accepted |
| D-54 | [ADR-0055](0055-keep-firebase-configuration-debug-only.md) | Keep development Firebase configuration isolated to debug builds. | Accepted |
| D-55 | [ADR-0056](0056-stage-final-contracts-in-walking-skeleton.md) | Stage final public contracts while implementing only the walking-skeleton Vehicle slice. | Accepted |
| D-56 | [ADR-0057](0057-place-test-app-graph-factory-in-shared-testing.md) | Place the test app graph factory in `:shared:testing`. | Accepted |
| D-57 | [ADR-0058](0058-pin-google-services-gradle-plugin.md) | Pin Google Services Gradle plugin 4.5.0. | Accepted |
| D-58 | [ADR-0059](0059-ios-composition-owns-shared-framework.md) | Move the `Shared` framework and Swift factory to `:composition:ios`. | Accepted |
| D-59 | [ADR-0060](0060-explicit-app-providers-port.md) | Use explicit typed properties in `AppProviders`. | Accepted |
| D-60 | [ADR-0061](0061-anonymous-identity-is-device-bound.md) | Treat unlinked anonymous identities as device-bound and enable native 30-day cleanup. | Accepted |
| D-61 | [ADR-0062](0062-current-anonymous-data-wins-linking-collision.md) | Preserve current anonymous-session data on a confirmed linking collision. | Accepted |
| D-62 | [ADR-0063](0063-anonymous-sign-in-benefit-reminders.md) | Use fixed, collapsible sign-in benefit reminders. | Accepted |
| D-63 | [ADR-0064](0064-own-user-data-cleanup-service.md) | Own idempotent user-data cleanup with one tracked 1st gen Auth trigger. | Accepted |
| D-64 | [ADR-0065](0065-split-anonymous-lifecycle-delivery.md) | Split anonymous lifecycle implementation across owning stories. | Accepted |
| D-65 | [ADR-0066](0066-pin-firebase-apple-to-gitlive-bindings.md) | Pin Firebase Apple to the GitLive cinterop version. | Accepted |
| D-66 | [ADR-0067](0067-contain-development-cloud-costs.md) | Contain development cloud costs with alerts and automatic billing shutdown. | Accepted |
| D-67 | [ADR-0068](0068-enforce-firebase-app-check.md) | Enforce Firebase App Check for Authentication and Firestore. | Accepted |
| D-68 | [ADR-0069](0069-retain-official-functions-stack-with-unreachable-moderate-advisory.md) | Retain the official Functions stack with an expiring unreachable moderate advisory. | Accepted |
| D-69 | [ADR-0070](0070-isolate-required-billing-admin-privilege.md) | Isolate the required Billing Admin privilege and alert on its use. | Accepted |
| D-70 | [ADR-0071](0071-disable-cutoff-retries-and-alert-on-errors.md) | Disable cutoff retries and surface every execution error. | Accepted |
| D-71 | [ADR-0072](0072-use-normal-ios-simulator-signing.md) | Use normal Xcode simulator signing for Keychain-backed Auth acceptance. | Accepted |
| D-72 | [ADR-0073](0073-exercise-cutoff-with-controlled-threshold-events.md) | Exercise the real cutoff with controlled threshold events. | Accepted |
| D-73 | [ADR-0074](0074-retain-functions-artifacts-for-one-day.md) | Retain Cloud Functions deployment artifacts for one day. | Accepted |
| D-74 | [ADR-0075](0075-use-xcuitest-for-keychain-persistence-acceptance.md) | Use a test-only XCUITest target for Keychain persistence acceptance. | Accepted |
| D-75 | [ADR-0076](0076-exempt-firebase-standalone-native-tests.md) | Derive Firebase standalone Native-test exemptions from their transitive project graph. | Accepted |
| D-76 | [ADR-0077](0077-vehicle-validation-uses-pre-write-facts.md) | Apply a functional-core / imperative-shell boundary to Vehicle validation from immutable pre-write facts. | Accepted |
| D-77 | [ADR-0078](0078-fuel-entry-validation-uses-pre-write-facts.md) | Validate Fuel Entry commands from immutable pre-write facts and return canonical persistence values. | Accepted |
| D-78 | [ADR-0079](0079-consumption-invalidation-precedence.md) | Apply an explicit structural-first precedence to singular consumption invalidation reasons. | Accepted |
| D-79 | [ADR-0080](0080-move-consumption-filter-evidence-to-e1-06.md) | Put production repository-filter evidence in E1-06 and prove the E1-05 calculator does not filter. | Accepted |
| D-80 | [ADR-0081](0081-consumption-performance-evidence.md) | Measure consumption with an uninstrumented JVM benchmark and an optimized iOS device-test binary. | Accepted |
| D-81 | [ADR-0082](0082-use-utc-calendar-years-for-fuel-entry-bound.md) | Use UTC calendar years for the Fuel Entry earliest-date bound. | Accepted |
| D-82 | [ADR-0083](0083-align-odometer-inconsistency-with-validation.md) | Align persisted odometer inconsistency with the validation predicate. | Accepted |
| D-83 | [ADR-0084](0084-retain-newest-fuel-entry-window.md) | Retain the newest Fuel Entry projection window under the memory cap. | Accepted |
| D-84 | [ADR-0085](0085-use-compose-navigation-and-instrumented-ui-tests.md) | Use Compose Navigation and instrumented Android UI tests. | Accepted |
| D-85 | [ADR-0086](0086-own-vehicle-presentation-in-feature-module.md) | Own Vehicle presentation in `:feature:vehicle`. | Accepted |
| D-86 | [ADR-0087](0087-separate-kotlin-app-graph-from-swift-facade.md) | Separate the Kotlin AppGraph from the Swift facade. | Accepted |
| D-87 | [ADR-0088](0088-observe-reactive-vehicle-edit-facts.md) | Observe reactive Vehicle edit facts. | Accepted |
| D-88 | [ADR-0089](0089-stage-idle-sync-status-until-e3-03.md) | Stage idle sync status until E3-03. | Accepted |
| D-89 | [ADR-0090](0090-own-database-lifetime-with-closeable-handle.md) | Own database lifetime with a closeable handle. | Accepted |
| D-90 | [ADR-0091](0091-release-swift-holders-and-separate-save-completion.md) | Release Swift holders and separate Vehicle save completion. | Accepted |
| D-91 | [ADR-0092](0092-pin-exported-common-enum-names.md) | Pin exported common enum names. | Accepted |
| D-92 | [ADR-0093](0093-preserve-fuel-entry-row-indicators.md) | Preserve Fuel Entry row indicators in the canonical list projection. | Accepted |
| D-93 | [ADR-0094](0094-reuse-pure-money-input-resolution.md) | Reuse pure MoneyInput resolution for live form derivation. | Accepted |
| D-94 | [ADR-0095](0095-compose-fuel-entry-form-defaults-in-app-graph.md) | Compose Fuel Entry form defaults in AppGraph. | Accepted |
| D-95 | [ADR-0096](0096-stage-fuel-sync-status-until-e3-03.md) | Stage Fuel Entry sync status until E3-03. | Accepted |
| D-96 | [ADR-0097](0097-use-device-local-fuel-entry-calendar-days.md) | Use injected device-local Fuel Entry calendar days. | Accepted |
| D-97 | [ADR-0098](0098-export-fuel-presentation-with-stable-abi.md) | Export Fuel presentation with a stable refined ABI. | Accepted |
| D-98 | [ADR-0099](0099-publish-kotlin-only-fuel-save-completion.md) | Publish Kotlin-only Fuel save completion. | Accepted |
| D-99 | [ADR-0100](0100-preserve-and-rederive-fuel-money-values-on-mode-switch.md) | Preserve and re-derive Fuel money values on mode switch. | Accepted |
| D-100 | [ADR-0101](0101-walking-skeleton-debug-diagnostics-screen.md) | Walking-skeleton debug diagnostics screen. | Accepted |
| D-101 | [ADR-0102](0102-ios-deployment-target-and-observableobject-lifecycle.md) | iOS deployment target and ObservableObject lifecycle. | Accepted |
| D-102 | [ADR-0103](0103-ios-unit-and-ui-test-targets-in-ci.md) | iOS unit and UI test targets in CI. | Accepted |
| D-103 | [ADR-0104](0104-ios-navigationstack-and-sheet-presentation.md) | iOS NavigationStack and sheet presentation. | Accepted |
| D-104 | [ADR-0105](0105-scaled-value-formatting-parity-on-ios.md) | Scaled value formatting parity on iOS. | Accepted |
| D-105 | [ADR-0106](0106-continuous-agent-progress-handoff.md) | Continuous agent progress handoff. | Accepted |
| D-106 | [ADR-0107](0107-bootstrap-device-local-settings-in-app-graph.md) | Bootstrap device-local settings in AppGraph. | Accepted |
| D-107 | [ADR-0108](0108-apply-first-persisted-currency-to-new-fuel-form.md) | Apply the first persisted currency to a new Fuel Entry form. | Accepted |
| D-108 | [ADR-0109](0109-inject-native-locale-providers-at-host-boundaries.md) | Inject native locale providers at host boundaries. | Accepted |
| D-109 | [ADR-0110](0110-enforce-native-locale-provider-verification.md) | Enforce native locale-provider verification. | Accepted |
| D-110 | [ADR-0111](0111-outbox-entity-type-token-ownership.md) | Outbox entity-type token ownership. | Accepted |
| D-111 | [ADR-0112](0112-explicit-uid-change-opt-in-for-account-adoption.md) | Explicit UID change opt-in for account adoption. | Accepted |
| D-112 | [ADR-0113](0113-android-google-credential-acquisition.md) | Use the stable Credential Manager stack for Android Google sign-in. | Accepted |
| D-113 | [ADR-0114](0114-ios-native-provider-acquisition.md) | Use native Apple sign-in and exact GoogleSignIn-iOS 9.2.0. | Accepted |
| D-114 | [ADR-0115](0115-native-to-shared-sign-in-handoff.md) | Use primitive provider-named sign-in completion intents. | Accepted |
| D-115 | [ADR-0116](0116-mount-onboarding-navigation-once.md) | Mount the authenticated navigation graph once. | Accepted |
| D-116 | [ADR-0117](0117-vehicle-list-loading-means-unknown.md) | Publish list loading only while the vehicle list is unknown. | Accepted |
| D-117 | [ADR-0118](0118-recover-from-interrupted-native-sign-in.md) | Recover from an interrupted native sign-in. | Accepted |
| D-118 | [ADR-0119](0119-supply-ios-device-signing-locally.md) | Supply the iOS developer team from an untracked local configuration. | Accepted |
| D-119 | [ADR-0120](0120-declare-guarded-repository-inputs.md) | Declare the repository files the guards read as task inputs. | Accepted |
| D-120 | [ADR-0121](0121-resolve-the-vehicle-list-per-owner.md) | Resolve the vehicle list per owner. | Accepted |
| D-121 | [ADR-0122](0122-make-first-run-vehicle-creation-mandatory.md) | Make first-run vehicle creation mandatory. | Accepted |

New ADRs start from [0000-template.md](0000-template.md).

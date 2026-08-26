# Version Matrix and Measurement Baselines

> Pinned toolchain versions and the compatibility relation between them, plus the reference devices and measurement methods for every performance target.
>
> **Gradle and Kotlin versions are declared only in `gradle/libs.versions.toml`.** Node-only
> dependencies are declared exactly in the root or `functions/` npm manifest that owns them and
> locked transitively by the adjacent lockfile. Dependency lifecycle scripts are disabled by the
> repository `.npmrc` under D-51. This file explains *why* every pin exists, which pins
> move together and which record backs it. There are no `TBD` cells.
>
> Changing any pinned version during the MVP is a human review gate (`AGENTS.md`).

## Compatibility sets

These groups move together. Bumping one member REQUIRES revalidating the whole group, because a mismatch inside a group typically fails at link time or at Objective-C export time rather than at compile time.

| Group | Members | Why they are coupled |
|-------|---------|----------------------|
| **Kotlin toolchain** | Kotlin, KSP, SKIE, `kotlinx-coroutines`, `kotlinx-serialization`, `kotlinx-datetime` | KSP and SKIE are published against exact Kotlin versions. The `kotlinx` libraries follow the Kotlin release train. |
| **SQLDelight database** | SQLDelight, `sqldelight-androidx-driver`, `androidx.sqlite:sqlite-bundled` | The SQL dialect, generated async API, adapter and bundled SQLite must compile and execute together on Android and Kotlin/Native. |
| **Apple toolchain** | Xcode, iOS deployment target, SKIE, macOS CI runner image | SKIE and the Kotlin/Native linker depend on the Xcode version available on the runner. |
| **Firebase** | Firebase BOM, GitLive Firebase SDK, Firebase Apple SDK, Google Services plugin | GitLive 2.6.x wraps platform-specific native Firebase SDKs. Its Apple cinterop bindings require the exact Firebase Apple version they were generated against; GitLive and Firebase Apple move together. GitLive 3.0 alpha is out of scope. |
| **Firestore rules emulator** | Node, Firebase CLI, Firebase JS SDK, `@firebase/rules-unit-testing` | The official rules test library peers with Firebase JS and discovers the emulator started by the CLI; Node must satisfy both packages. |
| **Cloud Functions and D-63 debt** | Node.js 22, Firebase Functions, Firebase Admin, `@google-cloud/billing`, TypeScript, `@types/node` | Node.js 22 is the current runtime that supports the D-63 1st gen Auth exception and all 2nd gen functions in one package. The runtime and trigger move together under TD-01. |
| **Cloud runtime CI identity** | `google-github-actions/auth`, `google-github-actions/setup-gcloud`, Workload Identity provider and the one-permission custom role | The SHA-pinned actions exchange GitHub OIDC for a short-lived identity that can read only the deployed function runtime. Updating an action requires revalidating the provider claims and runtime check. |
| **Android build** | AGP, Gradle, JDK toolchain, Compose BOM, `compileSdk`, `targetSdk` | AGP requires specific Gradle and JDK versions, and a Compose BOM declares a minimum AGP and `compileSdk`. Compose BOM `2026.08.00` requires `compileSdk 37` and AGP 9.1.0 or higher, which is what moved the whole group. |
| **AGP 9 build model** | AGP, `com.android.kotlin.multiplatform.library`, Kotlin Gradle plugin | Since AGP 9.0 Kotlin support is built into AGP: `org.jetbrains.kotlin.android` is rejected, and `com.android.library` is incompatible with the KMP plugin, so `:shared` must use `com.android.kotlin.multiplatform.library` and its `androidLibrary` DSL. |

## Pinned versions

Every Gradle/Kotlin value below is declared in `gradle/libs.versions.toml`; Node-only rows are
declared in the npm manifests identified above. GitHub Action versions are represented by immutable
commit SHAs in the workflow. "Backed by" is the record that justifies the choice: an ADR where a
decision exists, otherwise the story or normative section that fixes it.

| Area | Artifact | Version | Backed by | Notes |
|------|----------|---------|-----------|-------|
| JDK toolchain | — | 21 | `E0-06` | LTS. Same value for Gradle and for the Kotlin JVM toolchain, applied through `kotlin { jvmToolchain(...) }` in each module. |
| Node runtime | — | 22.22.3 | [ADR-0047](adr/0047-firestore-rules-use-official-node-test-stack.md) (`D-46`) | Exact runtime for Firestore emulator rules tests locally and in CI. |
| Cloud Functions runtime | — | 22 | [ADR-0067](adr/0067-contain-development-cloud-costs.md) (`D-66`) | Explicit `nodejs22` deployment runtime. It is coupled to the D-63 1st gen Auth exception and leaves only when TD-01 closes. Deprecation: 2027-04-30; hard decommission deadline: 2027-10-31. |
| Firebase Functions SDK | `firebase-functions` | 7.3.2 | [ADR-0067](adr/0067-contain-development-cloud-costs.md) (`D-66`) | Owns the 2nd gen Pub/Sub trigger now and the sole D-63 1st gen Auth exception later. |
| Firebase Admin SDK | `firebase-admin` | 14.3.0 | [ADR-0067](adr/0067-contain-development-cloud-costs.md) (`D-66`) | Exact server SDK for the accepted D-23/D-63 operations; present in the one Functions package. |
| Cloud Billing client | `@google-cloud/billing` | 6.0.0 | [ADR-0067](adr/0067-contain-development-cloud-costs.md) (`D-66`) | Official stable client used only by the development billing cutoff. Requires Node.js 22 or newer. |
| Functions TypeScript compiler | `typescript` | 7.0.2 | [ADR-0067](adr/0067-contain-development-cloud-costs.md) (`D-66`) | Compiles the single Functions codebase; exact dev dependency, not a global tool. |
| Functions Node types | `@types/node` | 22.20.1 | [ADR-0067](adr/0067-contain-development-cloud-costs.md) (`D-66`) | Matches the managed Node.js 22 runtime major. |
| Google GitHub Actions auth | `google-github-actions/auth` | 3.0.0 (`7c6bc770dae815cd3e89ee6cdf493a5fab2cc093`) | [ADR-0067](adr/0067-contain-development-cloud-costs.md) (`D-66`) | SHA-pinned OIDC exchange; the workflow MUST NOT use a floating tag. |
| Google GitHub Actions setup-gcloud | `google-github-actions/setup-gcloud` | 3.0.1 (`aa5489c8933f4cc7a4f7d45035b3b1440c9c10db`) | [ADR-0067](adr/0067-contain-development-cloud-costs.md) (`D-66`) | SHA-pinned Cloud SDK setup for the read-only deployed-runtime query. |
| Firebase CLI | `firebase-tools` | 15.28.1 | [ADR-0047](adr/0047-firestore-rules-use-official-node-test-stack.md) (`D-46`) | Starts and stops the Firestore emulator through the project-local npm binary. Its accepted moderate dev-tool-only audit residual is recorded by [ADR-0053](adr/0053-retain-firebase-cli-with-moderate-audit-residual.md) (`D-52`). |
| Firebase rules tests | `@firebase/rules-unit-testing` | 5.0.1 | [ADR-0047](adr/0047-firestore-rules-use-official-node-test-stack.md) (`D-46`) | Official emulator-only auth-mocking harness; tested with Node 22 and Firebase JS 12. |
| Firebase rules client | `firebase` | 12.18.0 | [ADR-0047](adr/0047-firestore-rules-use-official-node-test-stack.md) (`D-46`) | Client operations used only by the emulator rule suite; satisfies the rules-unit-testing peer range. |
| Gradle | — | 9.7.1 | `E0-06` | Required by AGP 9.x. |
| Android Gradle Plugin | `com.android.tools.build:gradle` | 9.3.1 | `E0-06` | Last stable 9.x at pinning time. AGP 9 carries built-in Kotlin support; see the AGP 9 build model group above. |
| Kotlin | `org.jetbrains.kotlin` | 2.4.10 | `E0-06` | Drives the whole Kotlin toolchain group. |
| KSP | `com.google.devtools.ksp` | 2.3.11 | `E0-06` | KSP versions its own line since 2.3.0 and is no longer `<kotlin>-<ksp>`. 2.3.10 and later support Kotlin 2.4.x. |
| Compose | Compose BOM | 2026.08.00 | `E0-06` | Android only. Requires `compileSdk 37` and AGP 9.1.0 or higher. |
| SQLDelight | `app.cash.sqldelight` | 2.3.2 | [ADR-0037](adr/0037-local-database-sqldelight-androidx-sqlite.md) (`D-36`) | Uses the SQLite 3.24 dialect, asynchronous generation and migration verification. |
| SQLDelight AndroidX driver | `com.eygraber:sqldelight-androidx-driver` | 0.2.1 | [ADR-0037](adr/0037-local-database-sqldelight-androidx-sqlite.md) (`D-36`) | Suspended adapter over AndroidX KMP SQLite; confined to `:core:database`. |
| SQLite | `androidx.sqlite:sqlite-bundled` | 2.7.0 | [ADR-0037](adr/0037-local-database-sqldelight-androidx-sqlite.md) (`D-36`) | Same bundled SQLite on both platforms and on Android API 26. |
| SKIE | `co.touchlab.skie` | 0.10.14 | [ADR-0059](adr/0059-ios-composition-owns-shared-framework.md) (`D-58`) | Applied only to `:composition:ios`, which processes exported `:shared` declarations. Supports Kotlin 2.4.10, which is what allows the Kotlin pin above. |
| Xcode | — | 26.6 | `E0-06` | Pinned on the macOS CI runner too. |
| Kotlin/Native iOS targets | — | `iosArm64`, `iosSimulatorArm64` | [ADR-0038](adr/0038-supported-ios-targets-are-arm64.md) (`D-37`) | `iosX64` is excluded because the accepted bundled-SQLite stack has no Intel-simulator artifacts and the application and CI already build ARM64 only. |
| Android `compileSdk` | — | 37 | `E0-06` | Floor imposed by the pinned Compose BOM. |
| Android `targetSdk` | — | 36 | `E0-06` | Deliberately one below `compileSdk`. `compileSdk` is forced by the Compose BOM and only decides which APIs compile; `targetSdk` is the Android runtime contract the app opts into, which is a behavioural decision and not a version pin. It matches the Android reference device below, and `E4-04` owns the move to a newer level before release. `minSdk` is fixed at 26 by `docs/SPECIFICATION.md §11`. |
| Firebase | Firebase BOM | 34.18.0 | [ADR-0001](adr/0001-backend-cloud-firestore.md) (`D-0`) | Governs the native Firebase artifact versions, including Crashlytics. |
| Firebase Android configuration | `com.google.gms.google-services` | 4.5.0 | [ADR-0058](adr/0058-pin-google-services-gradle-plugin.md) (`D-57`) | Processes the build-type-specific `google-services.json`; applied only to `:androidApp`. |
| GitLive | `dev.gitlive:firebase-*` | 2.6.0 | [ADR-0006](adr/0006-firestore-remote-sync-source.md), [ADR-0007](adr/0007-firebase-auth-gitlive.md) (`D-5`, `D-6`) | Latest 2.6.x. The 3.0 line is alpha and is out of scope for the MVP. |
| Firebase Apple SDK | `firebase-ios-sdk` Swift package | 11.8.0 | [ADR-0066](adr/0066-pin-firebase-apple-to-gitlive-bindings.md) (`D-65`) | Exact native SDK used to generate the GitLive 2.6.0 Apple cinterop bindings. It MUST move only with a GitLive release whose bindings target the selected replacement version. |
| Coroutines | `kotlinx-coroutines` | 1.11.0 | `E0-06` | Also determines the Native `Dispatchers.IO` source used by `DispatcherProvider`. |
| Serialization | `kotlinx-serialization-json` | 1.11.0 | `E0-06` | Outbox payloads and remote DTOs. |
| Date/time | `kotlinx-datetime` | 0.8.0 | `E0-06` | **The canonical `Instant` type is `kotlin.time.Instant`.** See the note below. |
| DI | `io.insert-koin` Koin KMP | 4.2.2 | [ADR-0004](adr/0004-koin-dependency-injection.md) (`D-3`) | Wiring only. |
| Logging | Kermit | 2.1.0 | [ADR-0016](adr/0016-logging-kermit.md) (`D-15`) | Behind `Logger`. |
| Crash reporting | Firebase Crashlytics | managed by the Firebase BOM | [ADR-0023](adr/0023-firebase-crashlytics.md) (`D-21`) | Behind `CrashReporter`, Phase 4. The BOM owns the version, so there is no separate pin. |
| Flow testing | Turbine | 1.2.1 | [ADR-0019](adr/0019-flow-testing-turbine.md) (`D-17`) | Compatibility with the pinned coroutines version is confirmed by `E0-05`, which is the first story that uses it. |
| Coverage | Kover | 0.9.9 | [ADR-0022](adr/0022-coverage-kover.md) (`D-18`) | |
| Architecture checks | Konsist | 0.17.3 | [ADR-0017](adr/0017-architecture-checks-konsist.md) (`D-16`) | Package-level rules. |
| Lint | ktlint Gradle plugin | 14.2.0 | `E0-05` | `org.jlleitschuh.gradle.ktlint`. Config files committed; no baseline suppression files. |
| Lint | ktlint engine | 1.8.0 | `E0-05` | The engine the plugin runs; pinned so the plugin cannot drift it. |
| Lint | detekt | 1.23.8 | `E0-05` | |

### The exact `Instant` package

`docs/CONTRACTS.md §2` refers to this cell, because kotlinx-datetime relocated the type.

The canonical timestamp type is **`kotlin.time.Instant`**, from the Kotlin standard library.
kotlinx-datetime 0.8.0 no longer declares its own `Instant`: it consumes the standard library type
and supplies the calendar operations around it, such as `toLocalDateTime(TimeZone)`.
`kotlinx.datetime.Instant` MUST NOT be used, and the 0.6.x compatibility artifacts that restore it
MUST NOT be added.

`shared/src/commonTest/kotlin/com/ruizurraca/carapp/PinnedInstantPackageTest.kt` enforces this: it
resolves a kotlinx-datetime extension declared on `kotlin.time.Instant`, so a relocation of the
type fails the build instead of silently changing the canonical timestamp of the domain model.

## Coverage thresholds

Enforced in CI by Kover (`D-18`). A module below its threshold fails the build.

| Module | Line coverage |
|--------|---------------|
| `:core:model` | 90% |
| `:core:common` | 90% |
| feature `domain` packages | 85% |
| `:core:sync` | 80% |
| Everything else | no threshold, but tests are still required by the Definition of Done |

## Performance measurement baselines

`docs/SPECIFICATION.md §11` states the targets. This section defines how they are measured, so that an acceptance criterion can actually pass or fail.

| Target | Reference environment | Method | Threshold |
|--------|----------------------|--------|-----------|
| Consumption for 1,000 entries | Shared test on the CI Linux runner (JVM), plus one manual run on the reference iPhone | 1,000 synthetic entries, release-mode build, discard 5 warm-up runs, take the median of 20 runs | < 100 ms |
| Cold start to first content | Reference devices below, release build, app force-stopped, device idle | Median of 10 runs, measured to the first frame that shows real content, not the splash | < 2 s |
| List smoothness | Reference devices below, release build, 1,000 fuel entries | Scripted fling across the full list; count frames over 32 ms | 0 frames over 32 ms |

Reference devices:

| Platform | Device | OS |
|----------|--------|-----|
| Android | Pixel 6a | Android 16 (API 36) |
| iOS | iPhone 12 | iOS 26 |

The reference OS is the baseline the thresholds were defined against, and it matches the pinned
`targetSdk`, so the app is measured on the runtime contract it actually opts into.

If a reference device is unavailable, the measurement is still run and the actual device is recorded in the handoff and in `docs/PROJECT_LOG.md`; it does not silently pass.

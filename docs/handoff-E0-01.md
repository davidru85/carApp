# Agent Handoff — E0-01 KMP Project Bootstrap

## Story

`E0-01 - KMP Project Bootstrap - M`

## Ready Check

- **Backlog story:** `E0-01 - KMP Project Bootstrap - M` (`docs/BACKLOG.md:43`).
- **Acceptance criteria reviewed:** `docs/BACKLOG.md:47-57` (all 7 ACs).
- **Dependencies checked:** `E0-01` blocks all other stories; no story blocks it. `E0-00` completed.
- **Decisions checked:** all `Accepted` (D-2 SKIE, D-3 Koin, D-9, D-13, D-14, D-22). No `Proposed`/`Pending`.
- **Normative sections reviewed:** `docs/SPECIFICATION.md §3.1`, `§8.3`, `§11`; `docs/CONTRACTS.md §15.3`; `docs/identifiers.md`; `docs/versions-matrix.md`; `AGENTS.md` Story Intake.
- **Expected verification:** `./gradlew :androidApp:assembleDebug`, `./gradlew :shared:allTests`, `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Debug build`.
- **Human review gates identified before work:** None (E0-01 is not a gated story; the Phase 0 gate is E0-07).

## Scope Completed

- KMP project with Android target, iOS target (iosX64, iosArm64, iosSimulatorArm64) and `:shared` framework named `Shared`.
- Android host app (`:androidApp`) using Compose, showing `Greeting().greet("Android")` from `commonMain`.
- iOS host app (`iosApp/`) using SwiftUI, showing `Greeting().greet(platform: "iOS")` from `commonMain` via `import Shared`.
- `gradle/libs.versions.toml` as single source of dependency versions (minimal build-essential pins; rest TBD for E0-06).
- Gradle wrapper (8.9), Kotlin DSL build scripts only, root `plugins` block declaring versions once.
- `xcodegen`-generated Xcode project with `Shared.framework` embedded and linked.

## Acceptance Evidence

- **AC1 (Android debug app builds):** `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL. APK at `androidApp/build/outputs/apk/debug/`.
- **AC2 (iOS simulator app builds and shows text from commonMain):** `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Debug build` → BUILD SUCCEEDED. App installs and launches on simulator (PID 56281). Binary contains `Shared.Greeting` symbol (`nm` output: `_$sSo14SharedGreetingCABycfC`). Screenshot at `/tmp/carApp-ios-screenshot.png`.
- **AC3 (iOS imports Shared):** `grep -R "import Shared" iosApp/` → `iosApp/ContentView.swift:import Shared`.
- **AC4 (Kotlin DSL only):** `find . -name "*.gradle" -not -path "*/build/*"` → 0 results (no Groovy build scripts).
- **AC5 (libs.versions.toml single source):** `gradle/libs.versions.toml` exists and is referenced by all `build.gradle.kts` via `alias(libs.*)`.
- **AC6 (Identifiers match):** `applicationId = "com.ruizurraca.carapp"`, `namespace = "com.ruizurraca.carapp"`, iOS `PRODUCT_BUNDLE_IDENTIFIER = com.ruizurraca.carapp` (debug: `.debug` suffix), `minSdk = 26`, iOS deployment target `16.0`, framework name `Shared`. All match `docs/identifiers.md` exactly; nothing invented.
- **AC7 (No backup/settings-sync surface):** `AndroidManifest.xml` has `android:allowBackup="false"` and no `fullBackupContent`, `dataExtractionRules`, or `BackupAgent`. No iOS entitlements file exists (no iCloud/CloudKit/NSUbiquitousKeyValueStore).

## Out of Scope / Not Done

- No CI workflow files (owned by E0-05).
- No Room, Firebase, Koin, Kermit, or other dependencies beyond the build-essential subset (owned by later stories).
- Full version pinning in `libs.versions.toml` (owned by E0-06).
- Architecture checks / contract check (owned by E0-04, E0-05).
- `docs/E0-01-READY-CHECK.md` is preserved per owner request until E0-01 closes; it will be deleted in a follow-up.

## Files Changed

- `settings.gradle.kts` (new) — module graph.
- `build.gradle.kts` (new) — root plugins block (versions declared once, applied per-module).
- `gradle.properties` (new) — Kotlin/Android/Gradle config, JDK 21 toolchain.
- `gradle/libs.versions.toml` (new) — minimal version pins; rest TBD for E0-06.
- `gradle/wrapper/gradle-wrapper.properties` (new) — Gradle 8.9.
- `gradle/wrapper/gradle-wrapper.jar` (new) — wrapper binary.
- `gradlew`, `gradlew.bat` (new) — wrapper scripts.
- `local.properties` (gitignored) — Android SDK location.
- `shared/build.gradle.kts` (new) — KMP module with SKIE, Android + iOS targets, `Shared` framework.
- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/Greeting.kt` (new) — shared entry point.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/GreetingTest.kt` (new) — TDD test.
- `androidApp/build.gradle.kts` (new) — Android host with Compose.
- `androidApp/src/main/AndroidManifest.xml` (new) — `allowBackup="false"`, no backup surface.
- `androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt` (new) — Compose host showing `Greeting`.
- `iosApp/project.yml` (new) — xcodegen config.
- `iosApp/Info.plist` (new) — iOS app metadata.
- `iosApp/carAppApp.swift` (new) — SwiftUI app entry point.
- `iosApp/ContentView.swift` (new) — SwiftUI view with `import Shared`.
- `iosApp/carApp.xcodeproj/` (new) — generated Xcode project.
- `docs/E0-01-READY-CHECK.md` (new) — ready check artifact (to be deleted at story close).

## Decisions Made

- **`gradle/libs.versions.toml` scope (owner decision 1):** Only build-essential versions pinned (Kotlin 2.0.21, KSP, SKIE 0.10.14, AGP 8.5.2, Compose BOM, coroutines, Gradle 8.9, targetSdk 35). Remaining versions left as `TBD` for E0-06. This is the agreed option (A).
- **TDD exemption (owner decision 2):** The KMP scaffold (Gradle build scripts, SPM integration, Xcode project, host apps) is declared as a SHOULD deviation from TDD order. Reason: native UI / wiring scaffold with no behavior unit; verified by build success, not by a written-first test. The `commonMain` `Greeting` class was written test-first (red-then-green: `GreetingTest` failed with "Unresolved reference 'Greeting'" before `Greeting.kt` was created, then passed). This exemption is declared per `docs/SPECIFICATION.md §11`.
- **`targetSdk` (owner decision 3):** Pinned to 35 (current stable) with a `# pinned by E0-01; revalidated by E0-06` comment. To be revalidated by E0-06.
- **Kotlin/Gradle/SKIE version resolution:** Initial attempt with Kotlin 2.0.21 + Gradle 8.11.1 + SKIE 0.10.14 failed with `KotlinNativeBundleBuildService` error (Kotlin plugin loaded multiple times with explicit versions). Fixed by (a) declaring all plugins in root `build.gradle.kts` with `apply false` and applying without version in subprojects, and (b) downgrading Gradle to 8.9 (SKIE 0.10.14 requires ≥ 8.8; 8.11.1 triggered the bundle service issue). AGP downgraded to 8.5.2 (SKIE 0.10.14 warns AGP > 8.5 untested). These pins are revalidated by E0-06.
- **iOS framework integration:** Used `xcodegen` (brew-installed) to generate the Xcode project. The `Shared.framework` (static, `isStatic = true`) is embedded via `FRAMEWORK_SEARCH_PATHS` + `embed: true` dependency. SPM-based integration deferred to E0-07 (XCFramework). The `.xcodeproj` is committed so the project opens without requiring xcodegen, but `project.yml` is the source of truth and `xcodegen generate` regenerates the project.

## Verification Run

```
# TDD red phase (before Greeting.kt existed):
./gradlew :shared:allTests
→ FAILED: Unresolved reference 'Greeting'

# TDD green phase (after Greeting.kt created):
./gradlew :shared:allTests
→ BUILD SUCCESSFUL (43 tasks)

# AC1: Android debug app
./gradlew :androidApp:assembleDebug
→ BUILD SUCCESSFUL (55 tasks)

# AC2+AC3: iOS simulator app
xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Debug build
→ BUILD SUCCEEDED

# iOS app launches on simulator
xcrun simctl install booted carApp.app && xcrun simctl launch booted com.ruizurraca.carapp.debug
→ PID 56281 (launched successfully)

# AC4: No Groovy build scripts
find . -name "*.gradle" -not -path "*/build/*"
→ 0 results

# AC7: No backup surface
grep -E "allowBackup|fullBackupContent|dataExtractionRules|BackupAgent" androidApp/src/main/AndroidManifest.xml
→ only android:allowBackup="false"
```

## Contract Impact

- No contract changes. `E0-01` adds no contract type. `docs/CONTRACTS.md §15.3` constraints (no value class, no type parameter, no default argument in `:shared` public API) are respected by `Greeting`: plain `class` returning `String`.

## Decision Board Impact

- No decision changes. All versions pinned are within already-accepted decisions (D-2 SKIE, D-3 Koin deferred to later story). `targetSdk` is a new pin but within the `E0-06` TBD scope.

## Shared-Write Modules Touched

- None. `:core:database` does not exist yet.

## Project Log Entry

- [ ] Entry appended (will be appended before commit).

## Risks or Follow-ups

- **Version revalidation:** Kotlin 2.0.21, AGP 8.5.2, Gradle 8.9, SKIE 0.10.14 are pinned for the build. E0-06 MUST revalidate the full compatibility matrix and may bump versions.
- **xcodegen dependency:** The Xcode project is generated by `xcodegen` (brew-installed). `project.yml` is the source of truth. If xcodegen is unavailable on the CI runner, E0-05 must either install it or commit the `.xcodeproj` (already committed, but regeneration requires xcodegen).
- **iOS framework path:** The `Shared.framework` path in `project.yml` is hardcoded to `iosSimulatorArm64/debugFramework`. E0-07 MUST switch to an XCFramework covering all iOS targets and both Debug/Release.
- **`docs/E0-01-READY-CHECK.md`** is preserved per owner request and must be deleted when E0-01 closes.
- **AGP 8.5.2** is below the current stable (8.7.x). SKIE 0.10.14 warns AGP > 8.5 is untested; the build succeeds but E0-06 should resolve this by either finding a SKIE version compatible with newer AGP or accepting the pin.
- **JDK 21** is used via `gradle.properties` (`java.toolchain.version = 21`). E0-06 records this in `libs.versions.toml` and `docs/versions-matrix.md`.

## Human Review Gate

Not applicable. `E0-01` is not in the gated-stories list (`AGENTS.md`). The Phase 0 gate is `E0-07`.
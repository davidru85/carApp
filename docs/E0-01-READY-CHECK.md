# Ready Check & Execution Plan — `E0-01` KMP Project Bootstrap

> Repository artifact (technical English). Records the ready check required by `AGENTS.md` Story Intake and the execution plan agreed with the owner. Appending this file is documentation, not a completed story; it does not touch a gated path and introduces no rule. It is a planning artifact for a single story and will be superseded by the handoff once `E0-01` is done.

## Ready Check — `E0-01` KMP Project Bootstrap

- **Backlog story:** `E0-01 - KMP Project Bootstrap - M` (`docs/BACKLOG.md:43`).
- **Acceptance criteria reviewed** (`docs/BACKLOG.md:47-57`):
  1. Android debug app builds.
  2. iOS simulator app builds and shows text coming from `commonMain`.
  3. iOS imports the shared framework as `import Shared` (canonical SPM module name from `docs/identifiers.md`).
  4. Build scripts use Kotlin DSL only.
  5. `gradle/libs.versions.toml` is the single source of dependency versions.
  6. Identifiers match `docs/identifiers.md` exactly; nothing is invented.
  7. Android `AndroidManifest.xml` and iOS entitlements contain no platform backup or settings-sync API surface.
- **Dependencies checked:** `E0-01` blocks all other stories; no story blocks it. `E0-00` (Owner Decision Closure) is completed — all decisions `D-0` through `D-23` are `Accepted`.
- **Decisions checked (all `Accepted`):** `D-2` SKIE on `:shared`; `D-3` Koin KMP (wiring only, n/a yet); `D-9` Firestore offline disabled (n/a in this story); `D-13` Firestore location (verified at `E0-07`); `D-14` dev Firebase project only (no Firebase in this story); `D-22` identifiers. No `Proposed` / `Pending`. No Ktor (`D-11` Deferred). No Room yet (arrives at `E1-01`). No Firebase yet (arrives at `E0-07` / `E2-02`).
- **Normative sections reviewed:**
  - `docs/SPECIFICATION.md §3.1` (in scope), `§8.3` (dependency rules — full enforcement at `E0-04`, but `:shared` rule 8 and Koin rule 10 apply), `§11` (platforms: Android `minSdk 26`, iOS 16+; CI on macOS runner from first PR; TDD compulsory for product code; offline-first launch — no product code in this story).
  - `docs/CONTRACTS.md §15.3` (Swift-facing surface constraints — full enforcement at `E0-07`; `:shared` public API already avoids value classes, type parameters, default arguments per `D-2` / §15.3).
  - `docs/identifiers.md` (all values fixed; nothing invented).
  - `docs/versions-matrix.md` (versions still `TBD` until `E0-06`; this story introduces `gradle/libs.versions.toml` and pins only the build-essential subset — full pinning belongs to `E0-06`).
  - `AGENTS.md` Story Intake fields (reply language = Spanish es-ES confirmed).
- **Expected verification:**
  - `./gradlew :androidApp:assembleDebug` — Android debug app builds.
  - `./gradlew :shared:assembleXCFramework` (or equivalent `linkDebugTestFatFramework`) — `:shared` links.
  - `xcodebuild -scheme <iosApp> -destination 'platform=iOS Simulator,name=<pinned>'` — iOS simulator app builds and shows text coming from `commonMain`.
  - `grep -R "import Shared" iosApp/` — `import Shared` is present.
  - `grep -RE "android:allowBackup|android:fullBackupContent|android:dataExtractionRules|BackupAgent" androidApp/src/main/AndroidManifest.xml` returns nothing; `grep -E "NSUbiquitousKeyValueStore|com.apple.developer.icloud" iosApp/**/*.entitlements` returns nothing.
  - CI workflow files are out of scope for `E0-01` (owned by `E0-05`); only local green build on macOS is required.
- **Human review gates:** **None** for `E0-01`. It is not in the gated-stories list in `AGENTS.md` (the Phase 0 gate is `E0-07`). `docs/identifiers.md` is a gated path, but `E0-01` only reads it — no change.
- **Reply language:** Spanish (es-ES) confirmed for this story.

### Verdict

Ready. One ambiguity (`E0-01` vs `E0-06` ownership of `libs.versions.toml` pinning) is resolved in the execution plan and will be restated in the handoff under **Decisions Made**.

## Resolved questions (owner decisions for this story)

1. **`gradle/libs.versions.toml` scope:** option (A) — pin only the build-essential subset needed to build the walking skeleton now; leave the remaining versions as `TBD` for `E0-06`. A comment in the file states `E0-06` owns the remaining pins.
2. **TDD exemption:** declare the KMP scaffold as a SHOULD deviation from TDD order in the handoff, with the reason "native UI / wiring scaffold, no behavior unit; verified by build success, not by a written-first test". The single `commonMain` `Greeting` is written test-first.
3. **`targetSdk`:** use the current stable Android `targetSdk` now and revalidate in `E0-06`. The chosen value is recorded in the handoff and in `gradle/libs.versions.toml` with a `# pinned by E0-01; revalidated by E0-06` comment.

## Execution Plan

### Branch & commits

- Branch: `story/E0-01-kmp-bootstrap` (`docs/CONTRIBUTING.md`).
- Conventional Commits with `E0-01` scope, technical English messages.

### TDD posture for `E0-01`

This is a scaffolding story, not product code. The closed TDD-exemption list in `docs/SPECIFICATION.md §11` does not literally include "KMP project bootstrap." Per the TDD rule, any exemption used MUST be declared in the handoff under **Decisions Made** with the reason. Plan:

- The `commonMain` `Greeting` is the only piece that approaches product code: written **test-first** (a `kotlin.test` asserting the greeting string is non-blank and contains a fixed token), so the bootstrap carries at least one red-then-green TDD cycle and `:shared` is exercised from `commonTest`.
- Everything else (Gradle convention, SPM integration, host apps) is declared in the handoff as a SHOULD deviation from TDD order, reason: "native UI / wiring scaffold, no behavior unit; verified by build success, not by a written-first test."

### Repo layout

```
settings.gradle.kts           KMP module graph + hosts
gradle/
  libs.versions.toml         single version source; minimal pins for build
build.gradle.kts            root, Kotlin DSL
gradle.properties           kotlin.code.style, android.useAndroidX, etc.
androidApp/                  Android host: applicationId com.ruizurraca.carapp (.debug suffix), namespace com.ruizurraca.carapp, minSdk 26, targetSdk pinned (stable)
shared/                      KMP module, package root com.ruizurraca.carapp, SKIE applied; exports a trivial Greeting
iosApp/                      Xcode project, bundle id com.ruizurraca.carapp (.debug suffix); consumes Shared via SPM as `import Shared`
```

### Versions — minimal pin set for `E0-01` (rest filled by `E0-06`)

Pinned now (build-essential):

- JDK toolchain, Gradle, AGP, Kotlin, KSP, SKIE (Kotlin-group coupled).
- Compose BOM (Android host UI).
- `kotlinx-coroutines` (minimal `commonMain` support).
- Android `targetSdk` (current stable, revalidated at `E0-06`).
- iOS deployment target = 16.0 (already fixed in `docs/identifiers.md` / `docs/SPECIFICATION.md §11`).

Left as `TBD` for `E0-06`: `Room`, `androidx.sqlite`, Firebase BOM, GitLive, `kotlinx-serialization`, `kotlinx-datetime`, Koin, Kermit, Turbine, Kover, Konsist, detekt, plus the full reference-device / measurement pins.

A comment in `gradle/libs.versions.toml` states `E0-06` owns the remaining pins.

### Identifier compliance (`docs/identifiers.md`)

- Android `applicationId` = `com.ruizurraca.carapp`; debug suffix `.debug` → `com.ruizurraca.carapp.debug`.
- Android `namespace` = `com.ruizurraca.carapp`.
- iOS bundle id = `com.ruizurraca.carapp`; debug suffix `.debug`.
- Shared module package root = `com.ruizurraca.carapp` (sub-packages follow module path).
- iOS framework name = `Shared` (SPM `import Shared`).
- `minSdk` = 26; iOS deployment target = 16.0.
- No invented values. Firestore project / Firebase config files are not added here — they arrive at `E0-07` / `E2-02`.

### Backup / settings-sync surface ban (AC#7)

- `AndroidManifest.xml`: no `android:allowBackup`, no `android:fullBackupContent`, no `android:dataExtractionRules`, no `BackupAgent`. Explicit `android:allowBackup="false"`.
- iOS entitlements: no `com.apple.developer.icloud-container-identifiers`, no `NSUbiquitousKeyValueStore`, no CloudKit entitlement. Minimal (or no) entitlements file for a debug simulator build.

### `:shared` public API discipline

Per `docs/CONTRACTS.md §15.3` and `D-2`: no value classes, no project-owned type parameters, no default arguments in the public API; SKIE applied only to `:shared`. The `Greeting` type is a plain `class` (or `object`) returning a `String` — no `value class`, no generics.

### CI

`E0-01` does not own CI setup (`E0-05` does). Only local green build on macOS is required; CI workflow files are out of scope.

### Definition of Done (to be filled in the handoff)

- All 7 ACs met with evidence (exact build commands + their result).
- One TDD cycle on `Greeting`; scaffolding TDD exemption declared in **Decisions Made**.
- `docs/PROJECT_LOG.md` entry appended (1 entry, milestone type, technical English).
- No contract / decision-board change (`E0-01` adds no contract type).
- No shared-write module touched (no `:core:database` yet).
- Residual risks recorded.
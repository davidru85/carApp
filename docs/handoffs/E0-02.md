# Agent Handoff - E0-02

> **Closure update (2026-08-27):** D-36 superseded the planned Room convention with the
> `carapp.sqldelight` convention, which E1-01 exercised on Android and Kotlin/Native. E0-04 and
> E0-05 added convention-plugin fixtures, architecture enforcement and CI. Only the feature
> package-level Konsist rules remain deferred to E1-07 under D-28.

## Story

`E0-02 - Gradle Convention Plugins - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E0-02`, unblocked by `E0-01`.
- [x] Acceptance criteria reviewed — the four criteria listed under `E0-02`.
- [x] Dependencies checked — stacked on the `E0-06` branch, because the convention plugins target the AGP 9 build model that `E0-06` pinned.
- [x] Required decisions are not `Proposed` or `Pending` — none pending; this story applies `D-1`, `D-2`, `D-24` and `D-25`.
- [x] Normative sections reviewed — `docs/TECHNICAL_PLAN.md §3` and `§4`, `docs/identifiers.md` "Module Android namespaces", `AGENTS.md` Technical Rules.
- [x] Expected verification identified — Android build, iOS simulator app build, host and Kotlin/Native tests.
- [x] Human review gates identified before work — none; no gated path or topic is touched.
- [x] Rule 0 acknowledged — chat replies in Spanish (es-ES), every artifact in technical English.

## Scope Completed

`build-logic` is an included build holding five class-based convention plugins, all reading `gradle/libs.versions.toml` so no version literal exists in build logic:

- **`carapp.kmp.library`** — Kotlin Multiplatform plus the AGP 9 `com.android.kotlin.multiplatform.library` plugin, the three iOS targets, the JDK toolchain, the host test runner and `kotlin-test` on `commonTest`. The Android namespace is **derived from the Gradle project path** per `D-24`, so no module carries a namespace literal.
- **`carapp.android.application`** — the Android host app: identifiers from `docs/identifiers.md`, SDK levels from the catalog, and `targetSdk` kept independent of `compileSdk` per `D-25`.
- **`carapp.compose`** — the Compose compiler plugin, the `buildFeatures.compose` flag and the Compose BOM platform. Split from the application plugin so a future Android module without Compose does not pay for it.
- **`carapp.skie`** — SKIE, and it **fails the build if applied to any module other than `:shared`**, turning the `D-2` rule from a review item into a build failure.
- **`carapp.room`** — Room 3 KMP with KSP on all four targets, the bundled SQLite and a fixed schema directory, so no story can quietly turn schema export off.

`:shared` and `:androidApp` were migrated onto them, and the root build file no longer configures modules.

## Acceptance Evidence

| Criterion | Evidence |
|-----------|----------|
| Creating a new module requires no more than five lines | The template is three lines: `plugins { id("carapp.kmp.library") }`. `:shared` keeps only its framework block, which is `:shared`-specific and not module wiring. **First true instances land in `E0-03`**, whose four `:core:*` modules each have a three-line build file; see "Out of Scope". |
| SKIE is applied only to `:shared` | `SkieConventionPlugin` throws with a rule-specific message naming `D-2` when `path != ":shared"`. |
| Test and Kotlin toolchain configuration is centralized | `jvmToolchain` and the `commonTest` `kotlin-test` dependency are set once in `carapp.kmp.library`; both module build files lost their copies. |
| Plugins make future feature splitting possible without redesign | Feature modules are plain `carapp.kmp.library` consumers; layer separation is by package analysis (`docs/TECHNICAL_PLAN.md §3`), so splitting a feature needs no new plugin. |

## Out of Scope / Not Done

- **The "no more than five lines" criterion has no instance inside this PR.** The repository has exactly two modules and both are special: `:shared` owns the iOS framework and `:androidApp` is the host app. The criterion is demonstrated by the template and verified for real by `E0-03`, which adds four ordinary modules. Reviewers who want that proof before merging should review `E0-02` and `E0-03` together.
- No convention plugin exists yet for feature modules or integrations; they are ordinary `carapp.kmp.library` consumers until a story needs more.
- `carapp.room` is written but applied to no module. `:core:database` is `E1-01`, so the Room plugin is unexercised.
- Java `compileOptions` are no longer set explicitly; the Kotlin JVM toolchain derives them. The AGP 9 `CompileOptions` DSL type is not on the convention plugin's compile classpath, and restating the value would have duplicated the toolchain pin.

## Files Changed

- `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts` (new).
- `build-logic/convention/src/main/kotlin/com/ruizurraca/carapp/buildlogic/` — `Catalog.kt`, `KmpLibraryConventionPlugin.kt`, `AndroidApplicationConventionPlugin.kt`, `ComposeConventionPlugin.kt`, `SkieConventionPlugin.kt`, `RoomConventionPlugin.kt` (new).
- `settings.gradle.kts` — `includeBuild("build-logic")`.
- `build.gradle.kts`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts` — migrated.
- `gradle/libs.versions.toml` — the five Gradle plugin artifacts the convention plugins compile against.

## Decisions Made

- **No `SHOULD` deviated from.** No new decision was taken, so no ADR was needed under the `AGENTS.md` rule.
- **Class-based plugins rather than precompiled script plugins.** Precompiled scripts cannot read the version catalog without a helper anyway, and class-based plugins let `carapp.skie` refuse to apply itself outside `:shared`.
- **The root build file keeps a `plugins { … apply false }` block.** It applies nothing; it exists so the plugins are on the build's plugin classpath, which is what lets the convention plugins compile against them with `compileOnly` and still be instantiated. Removing it produced `Could not generate a decorated class … ApplicationExtension`.

## Verification Run

- [x] Relevant tests pass
- [ ] Lint passes (ktlint, detekt) — not configured yet; `E0-05`
- [ ] Coverage thresholds hold — Kover not applied yet; `E0-05`
- [ ] Architecture checks pass — not implemented yet; `E0-04`
- [ ] Contract check passes — not implemented yet; `E0-05`
- [x] Relevant builds pass (Android, iOS simulator, `:shared` framework)
- [x] Documentation updated

```text
./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build
```

`BUILD SUCCESSFUL`, `GreetingTest` and `PinnedInstantPackageTest` pass on both the Android host and `iosSimulatorArm64`, and the iOS app returns `** BUILD SUCCEEDED **`.

## Contract Impact

- [x] No contract changes.

## Decision Board Impact

- [x] No decision changes.

## Shared-Write Modules Touched

- [x] None.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Risks or Follow-ups

- `carapp.room` is unverified until `E1-01` applies it. The Room 3 extension type is `androidx.room3.gradle.RoomExtension`, not the `androidx.room` one, and the KSP configuration names are hard-coded per target; both are first exercised there.
- `E0-04` must add the architecture check that no module other than `:shared` applies SKIE, so the rule holds even if someone bypasses the convention plugin.
- The convention plugins are not covered by tests. `E0-05` is the first story that could add a TestKit fixture for them.

## Human Review Gate

- [x] Not applicable — no gated path and no gated topic.

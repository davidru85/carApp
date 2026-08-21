# Agent Handoff - E0-06

## Story

`E0-06 - ADRs, Version Matrix and Decision Board Validation - S` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E0-06`, unblocked by `E0-01`; blocks `E0-07`.
- [x] Acceptance criteria reviewed — the eight criteria listed under `E0-06`.
- [x] Dependencies checked — `E0-01` is merged; no other story is in flight.
- [x] Required decisions are not `Proposed` or `Pending` — `docs/DECISION_BOARD.md` records no decision awaiting owner confirmation.
- [x] Normative sections reviewed — `docs/CONTRACTS.md §2` (the `Instant` reference), `docs/SPECIFICATION.md §11` (`minSdk`, iOS deployment target, TDD workflow), `docs/identifiers.md`.
- [x] Expected verification identified — Android debug build, iOS simulator app build, host and Kotlin/Native test execution, decision parity greps.
- [x] Human review gates identified before work — gated paths `docs/versions-matrix.md` and `docs/adr/**`; gated topic "technical stack or pinned versions".
- [x] Rule 0 acknowledged — chat replies in Spanish (es-ES), every artifact in technical English.

## Scope Completed

**Decision record validation (criteria 1-3).** Verified rather than rewritten: the four sources already agreed and needed no change.

- 24 ADRs exist, one per decision `D-0` to `D-23`, with no gap and no orphan.
- Every ADR `## Status` equals its `docs/DECISION_BOARD.md` status: 22 `Accepted`, and `D-11` and `D-12` `Deferred`.
- `docs/adr/README.md` maps every decision ID to its file, including the three cases where the ADR number and the decision number do not line up (`D-17`→ADR-0019, `D-18`→ADR-0022, `D-19`→ADR-0018, `D-22`→ADR-0021).
- The decision ID set and the status values are identical in `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.

**Toolchain pinning (criteria 4, 5, 8).** All 25 `TBD` cells of `docs/versions-matrix.md` are replaced by concrete versions, each with a "Backed by" citation naming the ADR, or the story where no decision exists. Every version lives in `gradle/libs.versions.toml`; the build scripts now read the SDK levels and the JDK toolchain from the catalog, so no version literal survives outside it.

**The `Instant` pin (criterion 6).** The canonical timestamp type is `kotlin.time.Instant`, recorded in its own subsection of the matrix, and guarded by `PinnedInstantPackageTest`.

**Performance baselines (criterion 7).** Reference OS versions pinned: Pixel 6a on Android 16 (API 36), iPhone 12 on iOS 26, one step below the pinned `targetSdk` so the measurement device is a realistic user device. The three measurement methods were already defined and were not changed.

## Acceptance Evidence

| Criterion | Evidence |
|-----------|----------|
| One ADR per `Accepted`/`Deferred` decision, status equal to the board | Extraction over `docs/adr/0*.md` comparing the `## Status` body with the board row: 24/24 match. |
| `docs/adr/README.md` maps every decision ID | 24 rows, `D-0` to `D-23`, no gap. |
| ID set and statuses identical across the four documents | Compared `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/README.md`. |
| Matrix pins every listed tool with the compatibility relation and the exact `Instant` package | `docs/versions-matrix.md`, "Pinned versions" plus "The exact `Instant` package". |
| Every `TBD` replaced with a citation | `grep -c TBD docs/versions-matrix.md` returns 1, the sentence stating that no `TBD` cells remain. |
| A unit test imports the pinned datetime package at build time | `PinnedInstantPackageTest` passes on `iosSimulatorArm64Test` and `testAndroidHostTest`. |
| Reference devices and measurement method fixed | "Performance measurement baselines". |
| Versions in `gradle/libs.versions.toml` and nowhere else | Build scripts read `libs.versions.*`; the previous literals for `compileSdk`, `minSdk`, `targetSdk` and the JDK are gone, as is the inert `java.toolchain.version` property. |

## Out of Scope / Not Done

- No CI runs any of this. `.github/` still has no `workflows/`; `E0-05` creates it. Every verification below was run locally.
- Room 3, Firebase, GitLive, Koin, Kermit, Turbine, Konsist, Kover, detekt and ktlint are pinned and declared in the catalog but not yet applied to any module. Their first real use is `E1-01`, `E0-07`, `E2-02` and `E0-05`; a pin can only be proven by the story that consumes it.
- `docs/handoff-E0-01.md` keeps its original version numbers. It is a historical record and is not rewritten.
- The `iosApp` Xcode project links the `iosSimulatorArm64` framework only, so an x86_64 simulator build still fails. That is pre-existing from `E0-01` and is not touched here.

## Files Changed

- `gradle/libs.versions.toml` — every `TBD` replaced; plugin aliases for Room, Kover, detekt, ktlint and serialization added; `kotlin-android` removed; `android-library` replaced by `android-kotlin-multiplatform-library`.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.9 → 9.7.1.
- `build.gradle.kts`, `androidApp/build.gradle.kts`, `shared/build.gradle.kts` — AGP 9 migration and catalog-driven SDK/JDK values.
- `gradle.properties` — removed the inert `java.toolchain.version` property.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/PinnedInstantPackageTest.kt` (new).
- `docs/versions-matrix.md`, `docs/PROJECT_LOG.md`, this handoff.

## Decisions Made

- **No `SHOULD` deviated from.** No decision status changed and no ADR was superseded.
- **The toolchain moved as one set rather than being pinned where `E0-01` left it.** `D-1` requires Room 3 KMP; the `androidx.room3` artifacts are 3.0.x, and the current Compose BOM requires `compileSdk 37` and AGP 9.1.0 or higher. Pinning AGP at 8.5.2 would have meant pinning a deliberately stale Compose for the whole MVP, and `E0-01` itself recorded AGP 8.5.2 as a workaround to revalidate here.
- **AGP 9 forced three build changes that are not stylistic.** Kotlin support is built into AGP 9, so `org.jetbrains.kotlin.android` is rejected outright; `com.android.library` is incompatible with the KMP plugin, so `:shared` uses `com.android.kotlin.multiplatform.library`; that plugin creates no host test runner, so `withHostTestBuilder` was added to keep the common tests running on the JVM as well as on Kotlin/Native.
- **`:shared` was given its own Android build namespace,** `com.ruizurraca.carapp.shared`, because AGP 9 rejects two modules sharing one. `docs/identifiers.md` fixes the `:androidApp` namespace and the Kotlin package root of shared code; it does not cover per-module Android build namespaces, and the Kotlin package root of shared code is unchanged. Flagged for review in case the owner would rather record it in `docs/identifiers.md`.
- **`kotlin.time.Instant` was chosen over the kotlinx-datetime 0.6.x compatibility artifact.** kotlinx-datetime 0.8.0 consumes the standard library type; the compat artifacts exist only to keep the old package alive and would pin the domain to a deprecated location.
- **Crashlytics has no separate version pin** because the Firebase BOM owns it. The matrix says so instead of inventing a number.

## Verification Run

- [x] Relevant tests pass
- [ ] Lint passes (ktlint, detekt) — not configured yet; `E0-05`
- [ ] Coverage thresholds hold — Kover not applied yet; `E0-05`
- [ ] Architecture checks pass — not implemented yet; `E0-04`
- [ ] Contract check passes — not implemented yet; `E0-05`
- [x] Relevant builds pass (Android, iOS simulator, `:shared` framework)
- [x] Documentation updated

```text
./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64   # baseline before the change, BUILD SUCCESSFUL
./gradlew :shared:compileTestKotlinIosSimulatorArm64                          # red: 7 unresolved-reference errors
./gradlew clean
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :androidApp:assembleDebug
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator -configuration Debug ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build
```

Results: `GreetingTest` 3 tests and `PinnedInstantPackageTest` 1 test, 0 failures and 0 errors on both `iosSimulatorArm64Test` and `testAndroidHostTest`; `:androidApp:assembleDebug` successful; `** BUILD SUCCEEDED **` for the iOS simulator app on Xcode 26.6. No Gradle deprecation warnings.

## Contract Impact

- [x] No contract changes. `docs/CONTRACTS.md §2` already delegates the exact `Instant` package to `docs/versions-matrix.md`, and that cell is now filled rather than redefined.

## Decision Board Impact

- [x] No decision changes. All 24 decisions keep their status; the story validated them rather than altering them.

## Shared-Write Modules Touched

- [x] None. `core/database` does not exist yet.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Risks or Follow-ups

- **The pins are proven only as far as the current code reaches.** Room 3, Firebase, GitLive, Koin, Kermit, Turbine, Konsist, Kover, detekt and ktlint are declared but unused; the first story to apply each one is the first real test of its pin. Turbine against coroutines 1.11.0 is called out explicitly by `D-17`.
- **The AGP 9 build model changes what `E0-02` inherits.** Convention plugins must be written against `com.android.kotlin.multiplatform.library` and AGP built-in Kotlin, not the AGP 8 model that `E0-01` used.
- **No CI enforces any of this.** Until `E0-05`, a version bump that breaks the build is caught only by whoever runs Gradle locally.
- **`targetSdk 37` was pinned as a build value, not as a behavioural review.** Opting into a new Android runtime behaviour normally deserves its own pass over the app's behaviour; there is almost no app yet, so there was nothing to review, and `E4-04` should revisit it before release.
- **The x86_64 simulator gap from `E0-01` is still open** and will matter when `E0-07` puts `iosSimulatorArm64` in CI.

## Human Review Gate

- [x] Gated path — `docs/versions-matrix.md`, `docs/adr/**` reviewed but unchanged.
- [x] Gated topic — technical stack and pinned versions.

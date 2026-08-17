# Version Matrix and Measurement Baselines

> Pinned toolchain versions and the compatibility relation between them, plus the reference devices and measurement methods for every performance target.
>
> **Versions are declared only in `gradle/libs.versions.toml`.** This file explains *why* each pin exists and which pins move together. Filling in the version column is `E0-06`; until then every cell marked `TBD` blocks the stories that depend on it.
>
> Changing any pinned version during the MVP is a human review gate (`AGENTS.md`).

## Compatibility sets

These groups move together. Bumping one member REQUIRES revalidating the whole group, because a mismatch inside a group typically fails at link time or at Objective-C export time rather than at compile time.

| Group | Members | Why they are coupled |
|-------|---------|----------------------|
| **Kotlin toolchain** | Kotlin, KSP, SKIE, `kotlinx-coroutines`, `kotlinx-serialization`, `kotlinx-datetime` | KSP and SKIE are published against exact Kotlin versions. The `kotlinx` libraries follow the Kotlin release train. |
| **Room KMP** | `androidx.room`, `androidx.sqlite:sqlite-bundled`, KSP | Room KMP code generation runs through KSP and the bundled SQLite must match the Room version. |
| **Apple toolchain** | Xcode, iOS deployment target, SKIE, macOS CI runner image | SKIE and the Kotlin/Native linker depend on the Xcode version available on the runner. |
| **Firebase** | Firebase BOM, GitLive Firebase SDK, Google Services plugin | GitLive 2.6.x wraps a specific Firebase SDK range; GitLive 3.0 alpha is out of scope. |
| **Android build** | AGP, Gradle, JDK toolchain, Compose BOM | AGP requires specific Gradle and JDK versions. |

## Pinned versions

| Area | Artifact | Version | Notes |
|------|----------|---------|-------|
| JDK toolchain | — | TBD | Same value for Gradle and for the Kotlin JVM toolchain. |
| Gradle | — | TBD | |
| Android Gradle Plugin | `com.android.tools.build:gradle` | TBD | |
| Kotlin | `org.jetbrains.kotlin` | TBD | Drives the whole Kotlin toolchain group. |
| KSP | `com.google.devtools.ksp` | TBD | Must match the Kotlin version exactly. |
| Compose | Compose BOM | TBD | Android only. |
| Room | `androidx.room` | TBD | Room 3.x KMP (`D-1`). |
| SQLite | `androidx.sqlite:sqlite-bundled` | TBD | Same bundled SQLite on both platforms. |
| SKIE | `co.touchlab.skie` | TBD | Applied only to `:shared` (`D-2`). |
| Xcode | — | TBD | Pinned on the macOS CI runner too. |
| Android `targetSdk` | — | TBD | `minSdk` is fixed at 26. |
| Firebase | Firebase BOM | TBD | |
| GitLive | `dev.gitlive:firebase-*` | 2.6.x | Not 3.0 alpha (`D-5`, `D-6`). |
| Coroutines | `kotlinx-coroutines` | TBD | Also determines the Native `Dispatchers.IO` source used by `DispatcherProvider`. |
| Serialization | `kotlinx-serialization-json` | TBD | Outbox payloads and remote DTOs. |
| Date/time | `kotlinx-datetime` | TBD | **Record the exact fully-qualified `Instant` package here**, because recent versions relocate it. `docs/CONTRACTS.md §2` refers to this cell. |
| DI | `io.insert-koin` Koin KMP | TBD | `D-3`. |
| Logging | Kermit | TBD | `D-15`, behind `Logger`. |
| Flow testing | Turbine | TBD | `D-17`; drop to hand-written helpers if incompatible. |
| Coverage | Kover | TBD | `D-18`. |
| Architecture checks | Konsist | TBD | `D-16`, package-level rules. |
| Lint | ktlint, detekt | TBD | Config files committed; no baseline suppression files. |

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
| Android | Pixel 6a | TBD, pinned in `E0-06` |
| iOS | iPhone 12 | TBD, pinned in `E0-06` |

If a reference device is unavailable, the measurement is still run and the actual device is recorded in the handoff and in `docs/PROJECT_LOG.md`; it does not silently pass.

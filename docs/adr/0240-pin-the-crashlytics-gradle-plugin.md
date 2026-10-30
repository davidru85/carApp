# ADR-0240 / D-239 - Pin the Crashlytics Gradle Plugin 3.0.8

## Status

Accepted

The owner selected option A on 2026-10-24.

## Context

`E4-04` binds Firebase Crashlytics behind `CrashReporter` (`D-21`, ADR-0023). GitLive
`firebase-crashlytics-android` 2.6.0 brings `com.google.firebase:firebase-crashlytics`, which the
project platform resolves to 20.1.0 through Firebase BOM 34.18.0. The Crashlytics Android SDK reads a
build ID that the Crashlytics Gradle plugin injects into the app's resources, and fails at app start
when it is missing unless `com.crashlytics.RequireBuildId=false` is set, which Firebase does not
support as a configuration.

The project pins every build and runtime version (`D-57` is the precedent for a Gradle plugin pin).
At the time of the decision the newest plugin on Google Maven is 3.0.8, published on 2026-08-19 in the
same Firebase update as BOM 34.18.0. Plugin v3 requires AGP 8.1 or later; no 3.0.x release note names
an incompatibility with AGP 9, and the one AGP 9 report (firebase-android-sdk issue #7652, "Could not
generate a decorated class for type CrashlyticsPlugin") was closed by its reporter as a classpath
issue. Minification stays off (`androidApp/build.gradle.kts`, `isMinifyEnabled = false`).

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Pin `com.google.firebase.crashlytics` 3.0.8 in the version catalog and apply it only to `:androidApp` | Supported configuration; the SDK gets its build ID; deobfuscation works if R8 is ever enabled; version policy unchanged | One more plugin on the build classpath; an upgrade needs a superseding decision |
| B. Omit the plugin and set `com.crashlytics.RequireBuildId=false` | No new plugin | Unsupported; reports cannot be matched to a build; mapping upload is impossible if R8 is enabled later |

## Decision

The selected option is **A**: `crashlyticsGradle = "3.0.8"` and the
`firebase-crashlytics` plugin alias live only in `gradle/libs.versions.toml`; the root build declares
it with `apply false`, so it shares the AGP class loader; `:androidApp` applies it after
`com.google.gms.google-services`.

## Consequences

### Positive

- The Crashlytics Android SDK starts with the build ID it requires.
- The plugin version follows the repository's single-source version policy.

### Negative

- A plugin upgrade requires coordinated catalog, matrix and decision-record review.
- The plugin adds Crashlytics tasks to every Android variant.

### Constraints Introduced

- The plugin version MUST live only in `gradle/libs.versions.toml`.
- The plugin MUST be applied only to `:androidApp`, after the Google Services plugin.
- Changing the pinned version requires a superseding owner decision.
- Enabling minification requires reviewing mapping-file upload under its own decision.

## Verification

- `FirebaseConfigurationTest.crashlyticsPluginIsPinnedAndAppliedOnlyToAndroidApp`.
- The debug APK's `resources.arsc` contains the Crashlytics build-ID resource.
- `:androidApp:connectedDebugAndroidTest` starts the app with the Crashlytics SDK on the `D-84`
  emulator.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-239`)
- `docs/adr/0058-pin-google-services-gradle-plugin.md` (`D-57`)
- `docs/adr/0023-firebase-crashlytics.md` (`D-21`)
- `docs/versions-matrix.md`
- [Firebase Android release notes](https://firebase.google.com/support/release-notes/android)

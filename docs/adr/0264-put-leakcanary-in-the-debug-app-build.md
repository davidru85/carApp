# ADR-0264 / D-263 - Put LeakCanary in the Debug App Build, Not Only in the Test APK

## Status

Accepted

The owner selected option A on 2026-11-02, after `E4-03` found the defect the original decision could
not run past. Amends `D-256` (ADR-0257).

## Context

`D-256` (ADR-0257) chose `com.squareup.leakcanary:leakcanary-android-instrumentation` 2.14 as an
`androidTestImplementation` dependency of `:androidApp`, and a `leakDetecting(...)` rule chain that calls
`AppWatcher.manualInstall(...)` on the application object.

`E4-03` implemented that decision literally and the instrumented suite failed: 153 of 154 tests threw
`android.content.res.Resources$NotFoundException: Resource ID #0x7f040003` from
`leakcanary.internal.InternalLeakCanary.addDynamicShortcut`, called by `AppWatcher.manualInstall`.

`aapt2 dump resources` explains it: `leak_canary_add_dynamic_shortcut` appears **zero** times in
`androidApp-debug.apk` and **once** in `androidApp-debug-androidTest.apk`. LeakCanary's resources, and the
`MainProcessAppWatcherInstaller` content provider of `leakcanary-object-watcher-android`, travel only with
a dependency the application classpath carries. `androidTestImplementation` puts them in the test APK,
whose content providers never start in the application process, which is where `AppWatcher` runs.
Passing `instrumentation.context` instead of `targetContext` does not help: it has no `Application`.

LeakCanary's own UI-test documentation prescribes the two-artifact arrangement this decision adopts.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `debugImplementation` of `leakcanary-android` 2.14 in `:androidApp`, keeping `androidTestImplementation` of `leakcanary-android-instrumentation` | LeakCanary's resources, provider and default `AppWatcher` reach the application process, so `DetectLeaksAfterTestSuccess` really detects leaks; release and `staging` artifacts are untouched | One development dependency in the debug artifact only; the debug APK grows |
| B. Keep `androidTestImplementation` only and drop the leak rule | No new dependency | Removes the "no memory leaks" acceptance criterion; `D-256` would be void |
| C. Upgrade to LeakCanary 3.x | Possibly a different packaging | 3.0 is alpha, and pinned versions are a gated topic; no reviewed reason exists |

## Decision

The selected option is **A**.

- `:androidApp` declares `debugImplementation(libs.leakcanary.android)` and keeps
  `androidTestImplementation(libs.leakcanary.android.instrumentation)`. `leakcanary-android` brings
  `leakcanary-android-core`, `leakcanary-object-watcher-android` and its
  `MainProcessAppWatcherInstaller` provider, so the application process has both the resources and an
  installed `AppWatcher`.
- `leakDetecting(...)` keeps installing `AppWatcher` only when it is not already installed, so the rule
  works with or without the provider, and it keeps running `DetectLeaksAfterTestSuccess` outside the
  Compose rule.
- Neither `release` nor `staging` may carry `leakcanary`. `PerformanceHarnessContractTest` asserts the
  exact two dependency lines, so a third one or a misplaced one fails the build.

## Consequences

### Positive

- The leak criterion of `E4-03` is executable and enforced on every instrumented run.
- The defect is fixed where it exists, without weakening the criterion.

### Negative

- The debug APK carries LeakCanary, which the owner installs on their own devices.

### Constraints Introduced

- `:androidApp` MUST declare LeakCanary as `debugImplementation` and as `androidTestImplementation`, and
  nowhere else.
- A test-only leak rule MUST NOT depend on resources that only the test APK carries.

## Verification

- `PerformanceHarnessContractTest.everyComposeInstrumentedTestDetectsLeaks` asserts the two dependency
  lines and the `leakDetecting(...)` chain of every Compose instrumented class.
- `aapt2 dump resources` finds `leak_canary_add_dynamic_shortcut` in `androidApp-debug.apk`.
- `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest` passes, and a deliberate
  activity leak makes it fail with `NoLeakAssertionFailedError`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-263`); `D-256` (ADR-0257); `D-84`
- LeakCanary, "Leak detection in UI tests" (`https://square.github.io/leakcanary/ui-tests/`)
- `docs/handoffs/E4-03.md` §Verification Run; `docs/handoffs/E4-B2.md`

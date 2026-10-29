# ADR-0241 / D-240 - Upload Crashlytics Symbols from iOS Release Archives

## Status

Accepted

The owner selected option A on 2026-10-24.

## Context

Firebase Crashlytics symbolicates iOS crashes only with the matching dSYM. The Release configuration
already produces `dwarf-with-dsym` and the project sets `ENABLE_USER_SCRIPT_SANDBOXING = NO`. The
pinned firebase-ios-sdk 11.8.0 (`D-65`) ships `Crashlytics/run`, which validates and uploads symbols;
with SwiftPM it lives at `${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A run-script phase that runs only when installing (archives) and only for Release | Every distributed build uploads its symbols with no manual step. | Archives need network for the background upload; the script path depends on the SwiftPM checkout layout. |
| B. Manual `upload-symbols` after each archive | No build-phase change. | A forgotten run leaves a release unreadable. |
| C. No symbol upload | Nothing to maintain. | Crash reports are unreadable. |

## Decision

The selected option is **A**. `iosApp/project.yml` declares a `postBuildScripts` phase named
`Upload Crashlytics symbols` with `runOnlyWhenInstalling: true`, the five Firebase input files, and a
script that exits unless `$CONFIGURATION` is `Release` before running the SwiftPM `Crashlytics/run`.

## Consequences

### Positive

- Archived Release builds always upload their dSYMs.
- Debug, simulator and test builds never run the phase.

### Negative

- A non-archive Release build (`xcodebuild build`) uploads nothing; only archives are distributed.

### Constraints Introduced

- The phase uses `$(…)` build-setting syntax in its input files, because XcodeGen expands defined
  `${…}` environment variables at generation time.
- Moving firebase-ios-sdk off SwiftPM or changing the derived-data layout requires revisiting the path.

## Verification

- `ReleaseAssetsContractTest.iosReleaseArchivesUploadCrashlyticsSymbolsOnlyWhenInstalling`.
- The owner's archive log shows the phase running (`docs/runbooks/production-firebase-release.md` O8, created by `E4-10`).

## References

- `docs/DECISION_BOARD.md` (decision ID `D-240`); `D-21`; `D-65`

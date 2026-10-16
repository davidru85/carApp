# ADR-0213 / D-212 - Read the App Version Natively in Each Host

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`docs/SPECIFICATION.md §3.1` lists the app version in Settings. No shared abstraction exposes it.
Android hard-codes `versionName = "0.0.1"` in `androidApp/build.gradle.kts`, with `BuildConfig`
disabled, and iOS hard-codes `CFBundleShortVersionString` `0.0.1` and `CFBundleVersion` `1` in
`iosApp/Info.plist`. `AppGraphDependencies` has sixteen fixed members (`docs/CONTRACTS.md §11.6`),
pinned by the `testAppGraphDependencies` parity check, and the shared layer has no use for the
version.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Each host reads its own version natively | No shared contract change; each platform's own version metadata is the source. | Two small host helpers, one per platform. |
| B. An app-information provider in `AppGraphDependencies` | One shared value. | Changes the sixteen fixed `§11.6` members, their `:shared:testing` parity and every provider composition, for data the shared layer never uses. Rejected. |

## Decision

The selected option is **A**. Android reads `PackageManager` `versionName` through
`internal fun Context.appVersionName(): String` in
`androidApp/src/main/java/com/ruizurraca/carapp/AppVersion.kt`. iOS reads `Bundle.main`
`CFBundleShortVersionString` and `CFBundleVersion` through
`enum AppVersion { static func displayString(bundle: Bundle = .main) -> String }` in
`iosApp/AppVersion.swift`. The version scheme itself is decided by `E4-04`.

## Consequences

### Positive

- No change to `§11.6`, `§20.10` or the golden header for the version.

### Negative

- The two hosts format the version independently; each formatting has a host test.

### Constraints Introduced

- The version string is not part of any `UiState`.

## Verification

- An Android component test asserts that the Settings version text equals `versionName`; an iOS
  unit test covers `AppVersion.displayString(bundle:)`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-212`)
- `docs/SPECIFICATION.md §3.1`; `docs/CONTRACTS.md §11.6`, `§14`
- `docs/BACKLOG.md` (`E4-01`, `E4-04`)

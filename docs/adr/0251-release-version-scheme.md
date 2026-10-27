# ADR-0251 / D-250 - Release Version Scheme

## Status

Accepted

The owner selected option A on 2026-10-24.

## Context

Both hosts shipped `0.0.1` / `1`, hard-coded in `androidApp/build.gradle.kts` and `iosApp/Info.plist`.
Store uploads need monotonically increasing build numbers.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `MAJOR.MINOR.PATCH` marketing version shared by both hosts; one integer build number shared by `versionCode` and `CFBundleVersion`, +1 per store upload | One version story for both stores; a test keeps the hosts equal. | A host-only upload still consumes a build number. |
| B. Independent per-host schemes | Flexible. | The two stores drift apart. |

## Decision

The selected option is **A**. The first release is `<O12_VERSION_NAME>` (`<O12_VERSION_CODE>`).
iOS reads `$(MARKETING_VERSION)` and `$(CURRENT_PROJECT_VERSION)` from `iosApp/project.yml`.

## Consequences

### Positive

- Equal versions on both platforms, enforced.

### Negative

- Every upload edits two files.

### Constraints Introduced

- `ReleaseAssetsContractTest.releaseVersionIsDeclaredOnceAndAgreesAcrossHosts`.

## Verification

- Release APK badging and the Release app's `Info.plist` show the same values.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-250`)

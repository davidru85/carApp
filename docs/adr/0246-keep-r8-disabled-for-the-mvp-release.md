# ADR-0246 / D-245 - Keep R8 Disabled for the MVP Release

## Status

Accepted

The owner selected option A on 2026-10-24.

## Context

`androidApp` builds release with `isMinifyEnabled = false`. Enabling R8 needs keep rules for GitLive,
kotlinx.serialization and SQLDelight-generated code, a Crashlytics mapping upload and release smoke
tests; none exists.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep R8 off for the MVP | No keep rules, no mapping upload, release behaviour equals tested behaviour. | Larger APK; code is not obfuscated. |
| B. Enable R8 with keep rules and release smoke tests | Smaller, obfuscated APK. | Reflection and serialization breakage risk discovered late; mapping upload to maintain. |

## Decision

The selected option is **A**: release keeps `isMinifyEnabled = false` and does not enable resource
shrinking.

## Consequences

### Positive

- Crash stack traces need no mapping file.

### Negative

- The APK is larger and readable; it contains no secret (`docs/SECURITY.md`).

### Constraints Introduced

- Enabling R8 later requires a superseding decision with keep rules, a mapping upload and release
  smoke tests.

## Verification

- `ReleaseAssetsContractTest.androidReleaseKeepsR8Disabled`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-245`); `docs/SECURITY.md`

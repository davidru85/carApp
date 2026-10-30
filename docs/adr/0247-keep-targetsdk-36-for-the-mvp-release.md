# ADR-0247 / D-246 - Keep targetSdk 36 for the MVP Release

## Status

Accepted

The owner selected option A on 2026-10-24. Discharges the `E4-04` obligation of ADR-0026 (`D-25`).

## Context

ADR-0026 pins `targetSdk 36` below `compileSdk 37` and says `E4-04` "owns the move to a newer level
before release and MUST review the runtime behaviour changes". On 2026-10-15 the Google Play target-API
page (developer.android.com/google/play/requirements/target-sdk, updated 2026-10-01) states: "Starting
August 31 2026: New apps and app updates must target Android 16 (API level 36) or higher".

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep `targetSdk 36` | Meets the Play requirement; it is the runtime contract every flow was built and tested against (the `D-84` API 36 emulator and the reference device). | The next Play raise needs a story with the ADR-0026 review. |
| B. Raise to 37 now | Ahead of the next requirement. | Needs the full behaviour-change review against F-1 to F-5 and the design assets inside a release story. |

## Decision

The selected option is **A**. `E4-04` reviewed the level against the Play requirement and keeps
`targetSdk 36`. This discharges ADR-0026's `E4-04` obligation; its policy (independent pins, review
before any raise) is unchanged.

## Consequences

### Positive

- The release runs on the reviewed runtime contract.

### Negative

- A later Play requirement (a 2027 date is expected but not verified) forces a raise with review.

### Constraints Introduced

- Raising `targetSdk` remains a gated change requiring the ADR-0026 behaviour review in its own story.

## Verification

- `ReleaseAssetsContractTest.androidTargetSdkStaysAtTheReviewedLevel`; the release APK badging reports
  `targetSdkVersion:'36'`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-246`); `D-25` (ADR-0026); `docs/versions-matrix.md`

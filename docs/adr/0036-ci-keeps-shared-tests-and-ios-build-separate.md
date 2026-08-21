# ADR-0036 / D-35 - `shared-tests` and `ios-simulator-build` Stay Separate CI Jobs

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

While the repository was private and Actions minutes were metered, the macOS jobs were the dominant cost: GitHub bills macOS at ten times wall-clock with a one-minute minimum, and the first CI run cost about 115 billed minutes, 100 of them macOS.

One proposed saving was to merge `shared-tests` into `ios-simulator-build`. `shared-tests` runs on macOS only because of `iosSimulatorArm64Test`; its other work, `testAndroidHostTest` and `koverVerify`, is JVM and runs on Linux. Folding the native test into the macOS job that already exists would have left a single macOS job and moved the rest to Ubuntu, saving roughly 47 billed minutes per run.

`D-34` then made the repository public, and standard runners became free. The saving that motivated the proposal disappeared, leaving only wall-clock time — and on that measure the merge is worse, not better.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Keep them separate | The two jobs run in parallel, so wall-clock is the slower of 4.2 and 3.6 minutes rather than their sum. A red job says which layer broke without reading logs. The `§18` check names keep matching what they run. | Two macOS jobs instead of one. Since `D-34` that costs nothing but runner time. |
| Merge them | One macOS job. Would have saved about 47 billed minutes per run while the repository was private. | Wall-clock gets worse, roughly 7 minutes chained against 4.2 in parallel. If `xcodebuild` fails, the native tests never run, exactly when it matters most to know whether the fault is the app or the shared logic. `shared-tests` would become a JVM-only Ubuntu job, so its `§18` name would no longer describe what it does, and the native tests would hide inside a check called `ios-simulator-build`. `koverVerify` would be separated from the tests it measures. |

## Decision

`shared-tests` and `ios-simulator-build` remain separate jobs, and `shared-tests` remains on `macos-latest` so it can run the Kotlin/Native tests.

The two jobs are complementary diagnostics and are kept apart for that reason, not by inertia:

- `shared-tests` answers "does the shared logic behave the same on the JVM and on Kotlin/Native?"
- `ios-simulator-build` answers "does the framework link and does the iOS app build against it?"

A single job cannot report both independently.

## Consequences

### Positive

- A failing check names the layer that broke.
- Wall-clock stays at the slower of the two rather than their sum.
- Every `§18` check name keeps describing what its job actually runs.

### Negative

- Two macOS jobs, so more runner time overall. Since `D-34` that is free, and the wall-clock cost is nil because they run in parallel.
- If the repository ever returns to private, the metered-minute argument returns with it and this decision MUST be revisited alongside `D-34`.

### Constraints Introduced

- Merging these two jobs REQUIRES superseding this decision.
- Moving `iosSimulatorArm64Test` out of `shared-tests` REQUIRES the same, because that is the only reason the job is on macOS.

## Verification

- `.github/workflows/ci.yml` declares both jobs, and the reasoning is stated inline so it is visible at the point where someone would be tempted to merge them.

## References

- `docs/DECISION_BOARD.md` (`D-35`)
- [ADR-0035](0035-repository-public-and-branch-protection-active.md) (`D-34`)
- `docs/CONTRACTS.md §18`

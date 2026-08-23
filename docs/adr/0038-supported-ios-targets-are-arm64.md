# ADR-0038 / D-37 - Support ARM64 iOS Targets Only

## Status

Accepted

Accepted by the owner on 2026-08-23.

## Context

The Kotlin Multiplatform convention previously declared `iosX64`, `iosArm64` and
`iosSimulatorArm64`. The application and CI already link and build only the Apple Silicon
simulator framework with `ARCHS=arm64`; the Intel simulator path has never been part of the
working application build.

The complete local-database stack accepted by `D-36` publishes `iosArm64` and
`iosSimulatorArm64` variants but no `iosX64` variants. Keeping `iosX64` would therefore require a
different SQLite driver for one target or private forks of the adapter and bundled SQLite
artifacts. Either choice would break the one-engine cross-platform guarantee that motivated
`D-36`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Remove `iosX64`; support `iosArm64` and `iosSimulatorArm64` | Preserves one accepted database stack, matches the application and CI paths already in use, and supports physical devices plus Apple Silicon simulators. | Intel Macs cannot build or run the simulator target. |
| Use the official SQLDelight Native driver only for `iosX64` | Retains Intel-simulator compilation without maintaining private artifacts. | One target uses the system SQLite instead of the accepted bundled engine, creating divergent behavior and verification. |
| Replace the accepted stack with the official Native driver for every iOS target | Keeps all three Kotlin/Native targets on an official driver. | Abandons the cross-platform bundled-SQLite consistency accepted by `D-36` and still leaves Android on a separate adapter path. |
| Maintain private `iosX64` forks | Could preserve the same architecture on Intel simulators. | Adds permanent release, security and compatibility maintenance for an application path that is not currently linked or tested. |

## Decision

Support the `iosArm64` device target and the `iosSimulatorArm64` Apple Silicon simulator target.
Remove `iosX64` from the shared KMP convention and from the `:shared` framework target list.

## Consequences

### Positive

- Every supported iOS target can use the complete `D-36` bundled-SQLite stack.
- The declared Kotlin/Native targets match the ARM64 application and CI paths that are actually
  built and verified.
- No target-specific database implementation or private dependency fork is required.

### Negative

- Intel Macs and x86_64 iOS simulators are unsupported.
- A future need for Intel-simulator support cannot be met by merely restoring one Gradle target.

### Constraints Introduced

- Shared KMP modules declare only `iosArm64` and `iosSimulatorArm64` until this decision is
  superseded.
- Reintroducing `iosX64` requires a complete compatible dependency set, application linking and CI
  verification, plus a decision that supersedes `D-37`.
- Historical handoffs and project-log entries remain unchanged when they describe the former
  target set or the earlier Intel-simulator limitation.

## Verification

- The KMP convention and `:shared` framework target list contain no `iosX64` declaration.
- `testAndroidHostTest` and `iosSimulatorArm64Test` exercise the supported host-test paths.
- The iOS application continues to build its ARM64 simulator framework with `ARCHS=arm64`.

## References

- `docs/DECISION_BOARD.md` (`D-37`)
- [ADR-0037](0037-local-database-sqldelight-androidx-sqlite.md) (`D-36`)
- `docs/versions-matrix.md`
- `docs/BACKLOG.md` (`E1-01`)

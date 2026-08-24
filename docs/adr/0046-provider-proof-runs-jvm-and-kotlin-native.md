# ADR-0046 - Provider Proof Runs JVM and Kotlin Native

## Status

Accepted

## Context

A Firebase dependency can leak through only one Kotlin target. Running the provider-free proof on
Android host alone would not demonstrate that the same common modules remain decoupled on the
supported iOS simulator target.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Android host and `iosSimulatorArm64` on macOS | Covers both supported shared execution families in one required check. | Slower and requires a macOS runner. |
| Android host only on Ubuntu | Fast and inexpensive. | Misses Kotlin/Native-only dependency leaks. |
| Separate platform jobs | Independent diagnostics. | Changes the fixed required check topology and branch protection. |

## Decision

The selected option is one macOS `provider-decoupling` job that compiles and tests the provider-free
`:core:*`, `:feature:*` and `:shared` graph on Android host and `iosSimulatorArm64`.

## Consequences

### Positive

- Provider decoupling is proved on JVM and Kotlin/Native.
- The branch-protection check name remains unchanged.

### Negative

- The check uses a macOS runner and takes longer than the previous placeholder.

### Constraints Introduced

- Both target test families are mandatory in the provider-free proof.
- The required check remains named `provider-decoupling`.
- Splitting or renaming the job requires a superseding decision and branch-protection update.

## Verification

- CI runs the provider-free Gradle task on `macos-latest`.
- The handoff records successful Android host and `iosSimulatorArm64` provider-free tests.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-45`)
- `docs/CONTRACTS.md §18`
- `docs/TECHNICAL_PLAN.md §5` and `§12`
- `docs/BACKLOG.md` (`E3-06`)

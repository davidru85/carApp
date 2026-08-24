# ADR-0057 / D-56 - Place the Test App Graph Factory in `:shared:testing`

## Status

Accepted

## Context

E0-07 must provide `testAppGraphDependencies(...)` with the same parameter order as
`AppGraphDependencies` and a deterministic default for every dependency. The original contract
assigned that factory to `:core:testing`.

`AppGraphDependencies` is owned by `:shared`. Implementing the original placement would therefore
require `:core:testing` to depend on `:shared`, reversing the application-to-core dependency
direction. That defect was detected only after the final graph types became implementable in
E0-07.

Kotlin Multiplatform consumers cannot depend on another module's `commonTest` source set. A
reusable test factory must consequently be published from a production source set of a module
that application test source sets depend on explicitly.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Create `:shared:testing`, depending on `:shared` and `:core:testing` | Preserves the layer direction, keeps generic fakes in core and exposes a reusable KMP test factory. | Adds one test-support production module whose consumers must constrain it to test configurations. |
| Allow `:core:testing` to depend on `:shared` | Requires no new module and preserves the original factory location. | Reverses the layer direction and gives a core module knowledge of the application graph. |
| Move `AppGraphDependencies` to a new central module | Keeps `:core:testing` independent of `:shared`. | Moves an established construction contract and introduces a broader application-graph module solely for test placement. |

## Decision

Create `:shared:testing`. Its `commonMain` source set exposes `testAppGraphDependencies(...)`,
depends on `:shared` as an API dependency because the return type is public, and depends on
`:core:testing` as an implementation dependency for generic deterministic fakes.

`AppGraphDependencies` remains in `:shared`. `:core:testing` remains generic and has no dependency
on `:shared`. Consumer modules may depend on `:shared:testing` only from `commonTest`.

This decision amends the module placement recorded for the E0-07 factory by D-27; D-27 continues
to govern when the complete factory is introduced.

## Consequences

### Positive

- No `:core:*` module knows about the application graph.
- Android-host and Kotlin/Native tests consume the same factory implementation.
- Adding an `AppGraphDependencies` member still requires one exact factory update.

### Negative

- Test support is compiled as production code inside its own module because KMP cannot consume
  another module's `commonTest` source set.
- Binary exclusion requires an explicit architecture and dependency-graph check.

### Constraints Introduced

- `:shared:testing` MUST declare the same Android, `iosArm64` and `iosSimulatorArm64` targets as
  `:shared`.
- `testAppGraphDependencies(...)` MUST live in `:shared:testing` `commonMain`.
- `:shared:testing` MUST depend on `:shared` with `api` and on `:core:testing` with
  `implementation`.
- Consumers MUST depend on `:shared:testing` only from `commonTest`.
- No module under `:core` may depend on `:shared` or `:shared:testing`.
- `:shared:testing` MUST NOT appear in Android application runtime artifacts or the iOS framework.
- The JVM-only `java-test-fixtures` plugin MUST NOT be used.

## Verification

- `architectureCheck` inspects the real Gradle graph and rejects every `:core:* -> :shared` edge.
- A failing build-logic fixture proves the core-to-shared rule fires.
- Android-host and `iosSimulatorArm64` tests construct the graph through the factory without Koin.
- Gradle dependency reports and framework inspection prove that app binaries exclude
  `:shared:testing`.
- Both `iosArm64` and `iosSimulatorArm64` compile successfully.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-56`)
- `docs/CONTRACTS.md §1.1`, `§11.6`, `§18`
- `docs/TECHNICAL_PLAN.md §3`, `§4`
- `docs/BACKLOG.md` (`E0-07`, `E3-08`)
- `D-16`
- `D-27`


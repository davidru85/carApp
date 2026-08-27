# ADR-0060 / D-59 - Use an Explicit AppProviders Port

## Status

Accepted

## Context

D-58 requires `:shared` to build the application graph without depending on Firebase wiring.
`AppGraphDependencies` already defines the complete injected dependency set, including the
build-mode flag, but the new `buildAppGraph(isDebugBuild, providers)` entry point needs a stable
provider port that can be implemented by wiring and by provider-free tests.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Explicit typed properties for every dependency except `isDebugBuild` | Makes the boundary compile-time visible, supports focused fakes and leaves the factory responsible for build mode. | Mirrors most fields of `AppGraphDependencies`. |
| One method returning `AppGraphDependencies` | Minimal implementation and no mirrored property list. | Makes the port opaque and moves graph-container construction into wiring. |
| Firebase-only properties | Produces the narrowest Firebase boundary. | Forces `:shared` to construct the remaining injected platform dependencies or add another factory parameter. |

## Decision

Define `AppProviders` in `:shared` with an explicit typed property for every
`AppGraphDependencies` member except `isDebugBuild`. `buildAppGraph(isDebugBuild, providers)` maps
those properties into `AppGraphDependencies` and supplies `isDebugBuild` itself.

Both `AppProviders` and `buildAppGraph` are Kotlin composition APIs and MUST be hidden from the
Objective-C header. `:shared:testing` exposes a fake implementation backed by its existing generic
fakes. `:wiring:firebase` supplies the production implementation without global state.

## Consequences

### Positive

- Missing or changed providers fail at compile time.
- Tests use the same graph factory as production without starting Koin or Firebase.
- The build-mode flag has one owner and cannot disagree with a prebuilt dependency container.
- No service-location API or global registration is introduced.

### Negative

- Provider additions require coordinated edits to `AppGraphDependencies`, `AppProviders`, wiring
  and the shared test factory.

### Constraints Introduced

- `AppProviders` property order MUST mirror `AppGraphDependencies` after excluding
  `isDebugBuild`.
- `buildAppGraph` is the only code that adds `isDebugBuild` to the complete dependency container.
- No provider SDK type may occur in `AppProviders`.

## Verification

- Common tests prove every property reaches graph construction and the explicit flag wins.
- Contract checks compare the provider and dependency-container member sets and order.
- Provider-free tests run on Android host and `iosSimulatorArm64`.

## References

- `docs/adr/0057-place-test-app-graph-factory-in-shared-testing.md` (`D-56`)
- `docs/adr/0059-ios-composition-owns-shared-framework.md` (`D-58`)
- `docs/CONTRACTS.md §11.6`
- `docs/BACKLOG.md` (`E0-07`)

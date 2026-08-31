# ADR-0087 / D-86 - Separate the Kotlin App Graph from the Swift Facade

## Status

Accepted

Accepted by the owner on 2026-08-30.

## Context

The contract defines two construction surfaces with different lifetime ownership. Kotlin callers
provide a `CoroutineScope` to state-holder factories. Swift callers cannot receive
`CoroutineScope`, so `SwiftAppGraph` creates and owns child scopes and caches exported holders.
The E0-07 implementation returned `SwiftAppGraph` directly from `buildAppGraph`, leaving Android
on the Swift lifetime model and omitting the declared Kotlin `AppGraph` interface.

Making `SwiftAppGraph` implement `AppGraph` would leak `AppGraph`, `SyncController` and
`CoroutineScope` into the Objective-C header, which contract assertion 7 forbids.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Return a hidden Kotlin `AppGraph` and let `SwiftAppGraph` wrap it by composition | Gives each platform the contracted lifetime model; keeps construction types out of Objective-C; avoids duplicated graphs. | Requires a construction API change and explicit wrapper caching. |
| Add a separate `buildAndroidAppGraph` while retaining the current factory | Minimises changes to the Swift path. | Creates two Kotlin factories for one dependency graph and ambiguous ownership. |
| Make `SwiftAppGraph` implement `AppGraph` | One concrete graph type. | Violates the Objective-C header allowlist by exporting Kotlin-only types. |

## Decision

`buildAppGraph(isDebugBuild, providers)` returns an `@HiddenFromObjC AppGraph`. The real Vehicle
factories are implemented on that graph and receive caller-owned scopes. `SwiftAppGraph` wraps an
`AppGraph` by composition, creates one owned child scope per cached state holder and never
implements `AppGraph`. `createSwiftAppGraph(isDebugBuild)` remains the sole Swift factory.

Fuel, Session and Sync factories retain their D-55 staged behavior. E3-08 still owns completing
the application graph.

## Consequences

### Positive

- Android can pass `viewModelScope` as required by the presentation contract.
- Swift keeps no-scope factories, idempotent caching and graph-owned cancellation.
- Kotlin-only graph contracts remain absent from the generated header.

### Negative

- The Swift facade maintains a cache keyed by state-holder factory arguments.
- Existing walking-skeleton graph tests must move to the Kotlin interface.

### Constraints Introduced

- `SwiftAppGraph` MUST wrap and MUST NOT implement `AppGraph`.
- Android obtains Vehicle holders only from `AppGraph` with caller-owned scopes.
- E3-08 completes the remaining staged graph factories without changing these surfaces.

## Verification

- Kotlin graph tests prove provider mapping and Vehicle factory behavior.
- Swift facade tests prove factory caching, close idempotence and post-close rejection.
- Objective-C golden-header assertion 7 proves the Kotlin graph remains hidden.

## References

- ADR-0056 / D-55
- ADR-0059 / D-58
- `docs/CONTRACTS.md §11.6`, `§14`, `§15.3`, `§20.10`

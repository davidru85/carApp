# ADR-0090 / D-89 - Own Database Lifetime with a Closeable Handle

## Status

Accepted

Accepted for the E1-07 owner-review correction on 2026-08-30.

## Context

`DefaultAppGraph` creates the SQLDelight database used by the Vehicle runtime, but the previous
`DatabaseFactory.create(): AppDatabase` contract discarded the `SqlDriver` that owns the database
connection. `AppGraph.close()` therefore had no resource it could release, and
`SwiftAppGraph.close()` only closed state holders before delegating to that ineffective method.

The lifetime type must remain in `:core:database`; exposing the driver to `:shared` would violate
the database-module boundary and risk leaking SQLDelight types into the Swift-facing framework.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Return a `DatabaseHandle` that owns one `AppDatabase` and its driver | Models per-graph ownership directly; keeps close logic in `:core:database`; supports observable and idempotent release. | Changes every `DatabaseFactory` implementation and test fake. |
| Add `DatabaseFactory.close(database)` | Avoids a new return type. | Makes the factory track or rediscover resources it no longer owns and permits mismatched factory/database pairs. |
| Expose the `SqlDriver` to `:shared` | Lets `DefaultAppGraph` close the connection directly. | Breaks the database boundary and expands the graph and Objective-C leakage risk. |

## Decision

`DatabaseFactory.create()` returns a `DatabaseHandle`. The handle exposes its `AppDatabase`, owns
the corresponding `SqlDriver` and closes that driver idempotently. `DefaultAppGraph` owns one
handle and releases it from its idempotent `close()`. `SwiftAppGraph.close()` closes its cached
holders and then the wrapped graph, releasing the same handle transitively.

## Consequences

### Positive

- Connection lifetime follows application-graph lifetime explicitly.
- Driver ownership and close behavior remain inside `:core:database`.
- Test factories can prove an observable release instead of inspecting graph flags.

### Negative

- Factory callers must access `handle.database` rather than receiving `AppDatabase` directly.
- Every production and test factory must return an owned handle.

### Constraints Introduced

- `DatabaseHandle` MUST live in `:core:database` and MUST NOT reach the Objective-C header.
- Each handle MUST close its driver at most once.
- `AppGraph.close()` and `SwiftAppGraph.close()` MUST remain idempotent.
- The D-55 staged factories MUST remain safe when the graph is closed before their behavior exists.

## Verification

- Android-host and iOS tests use an observable recording handle to prove one release after repeated
  direct and Swift-transitive graph close calls.
- The architecture fixture rejects `DatabaseHandle` outside the allowed database/test/graph boundary.
- `objc-header-golden-check` compares against the unchanged golden header.
- `contractCheck` verifies 90 decision/ADR mirrors.

## References

- ADR-0087 / D-86
- `docs/CONTRACTS.md §11.6`, `§20.3.2`, `§20.10`
- `docs/BACKLOG.md` E1-07

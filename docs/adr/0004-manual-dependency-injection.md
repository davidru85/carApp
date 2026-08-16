# ADR-0004 - Use Manual Composition Root for Dependency Injection

## Status

Accepted

## Context

The MVP dependency graph is small. Runtime DI failures would be especially costly when debugging across KMP and iOS.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Manual composition root | Explicit, simple, compile-time visible wiring. | More boilerplate as graph grows. |
| Koin | Familiar KMP DI option. | Runtime failures, hidden wiring, iOS debugging friction. |
| Code generation DI | Stronger checks. | Extra complexity for MVP. |

## Decision

Use constructor injection and manual composition roots.

## Consequences

### Positive

- Dependencies are explicit.
- Fewer runtime surprises.
- Easy provider replacement for tests and local-only wiring.

### Negative

- Large future graphs may need refactoring.

### Constraints Introduced

- No DI annotations in domain, data, or presentation.
- `:wiring:firebase` is the only module that creates Firebase implementations.

## Verification

- Architecture checks for integration dependencies.
- Provider decoupling build using fake wiring.

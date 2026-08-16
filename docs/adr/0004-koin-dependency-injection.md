# ADR-0004 / D-3 - Use Koin KMP for Dependency Injection

## Status

Accepted

## Context

The MVP dependency graph spans shared KMP modules, Android, iOS, Firebase integrations, local database construction, analytics, and test fakes. The project owner selected Koin for dependency injection. The architecture still requires implementation classes to use constructor injection and forbids service locator access from product logic.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Koin KMP | Owner-selected, KMP support, good fit for wiring platform and shared dependencies. | Runtime wiring must be guarded by tests and strict boundaries. |
| Manual composition root | Explicit, simple, compile-time visible wiring. | More boilerplate and less ergonomic for platform graph assembly. |
| Code generation DI | Stronger checks. | Extra complexity for MVP. |

## Decision

Use Koin KMP for dependency wiring and constructor injection for implementation classes.

## Consequences

### Positive

- Platform and shared dependencies can be wired consistently.
- Firebase implementations, fakes, and future API implementations can be swapped at module boundaries.
- Constructor injection keeps implementation classes testable without Koin.

### Negative

- Runtime wiring errors are possible if modules are not tested.
- Koin must be prevented from becoming a service locator inside product logic.

### Constraints Introduced

- Koin APIs are forbidden in feature `domain`, use cases, repository interfaces, repository implementations, and shared presentation business logic.
- `:wiring:firebase` is the only module that creates Firebase implementations.
- Domain tests must instantiate classes directly and must not require a Koin runtime.

## Verification

- Architecture checks for Koin usage boundaries.
- Koin module verification tests in Phase 0.
- Provider decoupling build using fake wiring.

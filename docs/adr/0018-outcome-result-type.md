# ADR-0018 / D-19 - Custom `Outcome<T, E>` as the Result Channel

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

Every public repository and use case signature in `docs/CONTRACTS.md` returns a two-parameter result type. The original documents wrote it as `Result<T, AppError>`, but `kotlin.Result` has a single type parameter, so that type did not exist. Left undecided, each agent would have invented its own, and the choice also affects the Swift-facing surface because generic hierarchies export poorly to Objective-C.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Custom `Outcome<T, E>` in `:core:common` | No dependency, exactly the shape needed, full control over the Swift surface. | A small amount of hand-written combinator code. |
| `kotlin.Result` | Standard library. | Only one type parameter; forces exceptions as the error channel, which the error taxonomy forbids. |
| Arrow `Either` | Rich combinator library. | A large dependency for one type, and it is not on the decision board. |
| Exceptions | Familiar. | Contradicts the whole typed error taxonomy and makes exhaustive handling impossible. |

## Decision

Declare `Outcome<T, E>` in `:core:common` exactly as specified in `docs/CONTRACTS.md §20.1`, with `map`, `mapError`, `flatMap`, `getOrNull` and `fold`.

The name deliberately avoids `Result` so that it can never be confused with `kotlin.Result` at a call site or in an import.

## Consequences

### Positive

- Every signature in `docs/CONTRACTS.md` becomes implementable as written.
- Expected failures are values, so exhaustive `when` over `AppError` is possible.

### Negative

- Combinators must be written and tested by hand.

### Constraints Introduced

- `kotlin.Result`, Arrow and exceptions-as-control-flow are FORBIDDEN in public signatures.
- `Outcome` is Kotlin-internal: it MUST NOT cross the Swift-facing surface, per `docs/CONTRACTS.md §15.3`. State holders expose resolved `UiState`, not `Outcome`.

## Verification

- `E0-03` implements and tests it.
- The architecture check forbids `kotlin.Result` and Arrow imports in feature and core modules.
- The `:shared` Objective-C header golden file shows no generic result type.

## References

- `docs/DECISION_BOARD.md` (`D-19`)
- `docs/CONTRACTS.md` §6, §15.3, §20.1

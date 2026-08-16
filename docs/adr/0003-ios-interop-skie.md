# ADR-0003 - Use SKIE for Kotlin-to-Swift Interop

## Status

Accepted

## Context

The project shares presentation state holders in KMP and consumes them from SwiftUI. Swift-facing APIs must be ergonomic enough to avoid duplicating business logic in Swift.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| SKIE | Better Swift interop for flows and common KMP patterns. | Additional toolchain dependency. |
| Raw KMP framework export | Fewer tools. | Worse Swift ergonomics and more wrapper code. |
| Manual wrappers only | Full control. | More boilerplate and higher risk of logic duplication. |

## Decision

Use SKIE, applied only in `:shared`.

## Consequences

### Positive

- SwiftUI can consume shared state holders more naturally.
- Reduces pressure to duplicate presentation logic on iOS.

### Negative

- Kotlin, SKIE, and Xcode versions must be pinned and treated as a compatibility set.

### Constraints Introduced

- SKIE is not applied to feature or core modules.
- Swift-facing APIs should remain small and flat.

## Verification

- Architecture/build checks ensure SKIE is applied only in `:shared`.
- iOS simulator build runs in CI.

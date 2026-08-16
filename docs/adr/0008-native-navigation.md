# ADR-0008 / D-7 - Use Native Navigation Per Platform

## Status

Accepted

## Context

The app uses native UI on Android and iOS. Navigation idioms differ between Compose and SwiftUI, while navigation itself does not contain core business logic.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Native navigation per platform | Best platform fit, simpler UI integration. | Some route structure duplicated. |
| Shared navigation model | Common routing logic. | Adds cross-platform abstraction with low MVP return. |
| Compose Multiplatform UI | More sharing. | Not aligned with native UI requirement. |

## Decision

Use native navigation per platform: Compose Navigation on Android and SwiftUI `NavigationStack` on iOS.

## Consequences

### Positive

- Platform-native navigation behavior.
- Keeps shared code focused on state and business rules.

### Negative

- Route definitions are duplicated at the UI host level.

### Constraints Introduced

- Do not share a common destination sealed class.
- Shared state holders should expose intent functions, not navigation framework types.

## Verification

- UI code review.
- Architecture checks preventing platform types in shared domain.

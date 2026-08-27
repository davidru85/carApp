# ADR-0003 / D-2 - Use SKIE for Kotlin-to-Swift Interop

## Status

Superseded

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

D-58 supersedes the module-ownership part of this decision. SKIE remains the selected interop
tool, but it is applied to `:composition:ios`, the module that now produces the exported
framework and processes `:shared` as an exported dependency.

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

- Architecture/build checks ensure SKIE is applied only in `:composition:ios` under D-58.
- iOS simulator build runs in CI.

## Superseded By

- `docs/adr/0059-ios-composition-owns-shared-framework.md` (`D-58`)

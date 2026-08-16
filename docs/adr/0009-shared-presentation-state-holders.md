# ADR-0009 / D-8 - Share Presentation State Holders in KMP

## Status

Accepted

## Context

The project uses native UI, but duplicating validation, orchestration, and UI state construction in Compose and SwiftUI would increase cost and risk.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Shared KMP state holders | Reuses validation and orchestration, keeps UI native. | Requires Swift interop care. |
| Fully native ViewModels | Native ergonomics. | Duplicates business-facing presentation logic. |
| Shared UI | Maximum sharing. | Not desired for MVP platform fit. |

## Decision

Use shared KMP state holders exposing `StateFlow<UiState>` and intent functions.

## Consequences

### Positive

- Business-adjacent presentation logic is implemented once.
- SwiftUI and Compose remain mostly rendering layers.

### Negative

- Swift-facing APIs must be kept simple.

### Constraints Introduced

- No business logic in SwiftUI or Compose.
- State holders live in feature `presentation` packages.
- Presentation does not depend on feature `data`.

## Verification

- Architecture checks.
- UI review for logic duplication.
- iOS build with SKIE in CI.

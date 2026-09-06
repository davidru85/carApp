# ADR-0115 / D-114 - Native-to-Shared Sign-In Handoff

## Status

Accepted

Selected by the owner for E2-03 on 2026-09-04.

## Context

F-1 uses native provider UI while shared presentation state owns the session phase, busy state and
recoverable failures. The boundary must preserve D-6 provider decoupling and the closed Swift ABI:
`NativeAuthCredential` is not on the `docs/CONTRACTS.md §15.3` export allowlist and must not become
public merely to carry native results back into shared code. Cancellation and failure must also
remain typed rather than crossing as provider text.

The owner requires `startPermanentSignIn(provider)` to record provider selection and busy state.
The native host then acquires a credential and returns only the primitive values required for the
existing provider-free exchange.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Provider-named primitive completion intents plus a closed exported failure enum (Selected) | Keeps native acquisition in native UI; preserves shared state ownership; avoids exporting credential wrappers or free text; explicit provider-specific inputs | Adds three intents to `SessionStateHolder` and requires the holder to retain the selected provider internally |
| Inject native credential acquirers into shared code | Lets one shared intent orchestrate the whole flow | Pulls native lifecycle and presentation concerns toward shared code; complicates Kotlin/Native callbacks and testing; blurs D-7 and D-8 boundaries |
| Expose a graph-level native credential exchanger | Keeps credential construction outside the state holder | Expands the public Swift facade, risks exporting provider-sensitive types and splits one presentation transaction across unrelated owners |

## Decision

`SessionStateHolder.startPermanentSignIn(provider)` records the selected provider and sets the busy
state. Native UI performs acquisition and completes the attempt through:

```kotlin
fun completeGoogleSignIn(idToken: String, accessToken: String?)
fun completeAppleSignIn(idToken: String, rawNonce: String)
fun failSignIn(reason: NativeSignInFailure)

enum class NativeSignInFailure { CANCELLED, NETWORK, CONFIGURATION, UNKNOWN }
```

The completion methods construct the internal `NativeAuthCredential` only after primitives cross
the presentation boundary. `NativeSignInFailure` is a closed exported enum with exact Objective-C
and Swift names governed by `docs/CONTRACTS.md §15.3`.

## Consequences

### Positive

- No Firebase, Google, Apple or native credential type enters the Shared framework API.
- Shared code remains the single owner of busy, retry and session state.
- Cancellation and native failures are exhaustive, localized by the host and independent of SDK
  error wording.

### Negative

- Provider completion calls are stateful: mismatched or duplicate completions must fail safely.
- Provider-specific primitive parameter lists are deliberately visible in the Swift ABI.

### Constraints Introduced

- `NativeAuthCredential` MUST NOT be exported or added to the `§15.3` allowlist.
- Provider tokens, Apple raw nonces and credentials MUST NOT enter `UiState`, analytics,
  `Logger` / Kermit or crash reporting.
- Native failure text MUST NOT cross the boundary. Hosts map it to `NativeSignInFailure` and
  localize the resulting shared message key.
- Completion and failure clear the selected attempt so retry cannot remain stuck in a busy state.

## Verification

- Shared state-holder tests prove provider recording, primitive credential construction, success,
  typed failure, cancellation and retry behavior.
- Objective-C golden-header verification pins the exact exported enum and method signatures and
  rejects `NativeAuthCredential` export.
- Android and iOS host tests prove SDK-result mapping without credential persistence or telemetry.
- `contractCheck` verifies the public boundary and the four decision mirrors.

## References

- `docs/DECISION_BOARD.md` (`D-114`)
- `docs/SPECIFICATION.md` sections 7 F-1, 11 and 12
- `docs/CONTRACTS.md` sections 11.1, 15.1, 15.3 and 20.10
- `docs/TECHNICAL_PLAN.md` sections 2 and 12
- [ADR-0007](0007-firebase-auth-gitlive.md) (`D-6`)
- [ADR-0087](0087-separate-kotlin-app-graph-from-swift-facade.md) (`D-86`)
- [ADR-0092](0092-pin-exported-common-enum-names.md) (`D-91`)

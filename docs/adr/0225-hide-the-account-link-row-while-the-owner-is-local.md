# ADR-0225 / D-224 - Hide the Account-Link Row While the Owner Is Local

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`D-210` adds an anonymous-only account-link row to Settings, delivered by `E4-05`. `SessionPhase.LOCAL`
is an offline first launch that has not yet been adopted into an anonymous Firebase identity
(`E2-06`), and `SessionStateHolder.startAccountConversion(provider)` publishes
`AuthError.ProviderUnavailable` unless the current session is anonymous. Nothing can be linked while
the owner is local.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Hide the row while `LOCAL`; it appears once adoption makes the session `ANONYMOUS` | No dead control; no new copy; derived on the host from `SessionUiState.phase`. | The user does not see the option until the device has connected once. |
| B. Show the row disabled, with copy saying a connection is needed | The option is discoverable offline. | New copy in two languages and one more disabled state to audit, for a transient phase. |
| C. Show the row enabled and make it trigger adoption first | One tap from offline. | New shared behaviour that cuts across the `E2-06` adoption sequencing. Rejected. |

## Decision

The selected option is **A**. The host shows the account-link row only when
`SessionUiState.phase == SessionPhase.ANONYMOUS`; it hides it for `LOCAL`, `PERMANENT` and every other
phase. No shared code changes for the visibility.

## Consequences

### Positive

- The row never offers an action that the shared layer would refuse.

### Negative

- An owner who never connects never sees the row; there is nothing to link in that state.

### Constraints Introduced

- The visibility is derived from `SessionUiState.phase` only.

## Verification

- An Android component test asserts the row is absent for `LOCAL` and `PERMANENT` and present for
  `ANONYMOUS`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-224`)
- `D-210` (ADR-0211); `docs/CONTRACTS.md §11.3`, `§11.4`, `§20.10`
- `docs/BACKLOG.md` (`E4-05`, `E2-06`)

# ADR-0228 / D-227 - Cancel the Credential Collision Through clearMessage

## Status

Accepted

The owner selected option A on 2026-10-16.

Correction (2026-10-23, PR #91 review round 2, chosen by the owner): the Decision below says
`confirmAccountConversion` acts "while that confirmation is the published collision message". The
`E2-04` code checks that the collision credential is still held, not that the message is published.
`docs/CONTRACTS.md §20.10` now states the checked condition; no code changes, as this decision intended.

## Context

`docs/CONTRACTS.md §11.3` requires that cancelling the credential collision leaves the anonymous
session and all local and remote data untouched. `§20.10` said that
`confirmAccountConversion(confirmation)` handles the collision "through
`Confirmation.AdoptExistingAccount` or cancellation", but no `Confirmation` value expresses a
cancellation (`§20.2`) and `confirmAccountConversion` ignores every confirmation other than
`AdoptExistingAccount`. Since `E2-04` the shared holder cancels the collision in `clearMessage()`:
while the displayed message carries `Confirmation.AdoptExistingAccount`, it discards the in-memory
collision credential (`D-154`) and reports `AccountConversionFailed(CANCELLED)`, and
`SessionStateHolderTest` pins that behaviour. `§20.10` described `clearMessage()` as withdrawing
departure confirmations only, so no normative intent defined the cancellation that the `E4-05`
host surface must call. `failSignIn(NativeSignInFailure.CANCELLED)` is not that intent: `§20.10`
scopes it to a native acquisition that has not completed, and when the collision is shown the
acquisition has already completed and its attempt has been cleared.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. State in `§20.10` that `clearMessage()` cancels a displayed collision and that `confirmAccountConversion` acts only on `AdoptExistingAccount`; no code change | Matches the behaviour `E2-04` ships and tests; no Swift-facing change; one paragraph. | Cancellation shares the generic dismissal intent, so a host must answer the collision only through its two actions. |
| B. Add a dedicated `cancelAccountConversion()` intent to `SessionStateHolder` | One single-purpose intent. | Changes the Swift-facing surface (golden header, assertions 34 and 35, `§20.10`), needs shared TDD in a gated area, and duplicates `clearMessage()`. |
| C. Add a cancellation value to `Confirmation` | Reuses `confirmAccountConversion`. | A `Confirmation` names consent to a consequence, not its absence; it changes the `§20.2` taxonomy and the Swift API. Rejected. |

## Decision

The selected option is **A**. `confirmAccountConversion(confirmation)` acts only on
`Confirmation.AdoptExistingAccount` while that confirmation is the published collision message,
and any other confirmation acts on nothing. The collision is cancelled with `clearMessage()` while
that confirmation is shown: it discards the in-memory collision credential, leaves the anonymous
session and all local and remote data untouched, and reports `AccountConversionFailed(CANCELLED)`.
`failSignIn(NativeSignInFailure.CANCELLED)` remains the cancellation of a native acquisition that
has not completed. No code changes.

The same `§20.10` sentence now states that `AuthError.UidWouldChange` aborts conversion, as
`§11.3` already does, instead of listing it with the collision flow.

## Consequences

### Positive

- The `E4-05` hosts call a cancellation the contract defines, and the behaviour they rely on is the
  one the shared tests already pin.
- No Swift-facing or shared change.

### Negative

- `clearMessage()` keeps two roles: dismissing a message and cancelling a displayed collision.

### Constraints Introduced

- A host cancels a displayed collision only through `clearMessage()` and confirms it only through
  `confirmAccountConversion(Confirmation.AdoptExistingAccount)`.
- A host answers an interrupted or cancelled native picker only through
  `failSignIn(NativeSignInFailure.CANCELLED)`.

## Verification

- `SessionStateHolderTest.cancellingTheCollisionLeavesTheAnonymousSessionAndCredentialUnused`,
  `dismissingACollisionTracksTheClosedCancelledFailure` and
  `aDifferentConfirmationCannotStartTheDestructiveCollisionFlow` (existing, `E2-04`).
- `E4-05`: `AccountConversionSurfaceTest.cancellingTheCollisionInvokesOnlyTheCancelCallback`,
  `SettingsAccountConversionTest.theSettingsScreenRoutesTheCollisionCancelToTheConversionCallback`,
  and owner-run item M5 in `docs/handoffs/E4-05.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-227`)
- `docs/CONTRACTS.md §11.3`, `§20.2`, `§20.10`
- `D-154` (ADR-0155), `D-117` (ADR-0118), `D-210` (ADR-0211)
- `docs/handoffs/E2-04.md`, `docs/handoffs/E4-05.md`
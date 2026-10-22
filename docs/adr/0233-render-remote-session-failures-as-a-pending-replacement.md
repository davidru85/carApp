# ADR-0233 / D-232 - Render Remote Session Failures as a Pending Account Replacement

## Status

Accepted

The owner selected option A on 2026-10-23, in the PR #91 review round 2.

## Context

`docs/CONTRACTS.md §14.1` makes `uiMessageStringResource` (Android) and `uiMessageKey` (iOS) the one
mapping source per host and assigns every `REMOTE.*` code a backup copy (`error_backup_offline`,
`error_backup_rejected`, `error_restore`). On the `SessionStateHolder` channel, a `REMOTE.*` code is
published only by a confirmed account replacement that failed after the session switch, which `E2-04`
resumes on its own; departures publish only `AUTH.*` and `PERSISTENCE.*` codes. The backup copy
("try again when you are online") would tell the owner to act when the app retries by itself. `E4-05`
therefore added `sessionMessageStringResource` and `sessionMessageKey`, which render every `REMOTE.*`
session message as `error_conversion_replacement_pending`; the choice was recorded only in the `E4-05`
handoff.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep the host override as the one documented exception to the single mapping source | Accurate copy for the resume behaviour `E4-05` must render; no shared change. | A second, narrow mapping function per host. |
| B. Publish a dedicated code or state from shared code | One mapping source. | Changes the error taxonomy and the Swift-facing surface; belongs with `E2-10`. |
| C. Render the `§14.1` backup copy | No exception. | Misleading instruction. Rejected. |

## Decision

The selected option is **A**. A `SessionUiState.message` that carries no confirmation is rendered
through `sessionMessageStringResource` (Android) and `sessionMessageKey` (iOS). Both map every
`REMOTE.*` code to `error_conversion_replacement_pending` and defer every other code to the `§14.1`
mapping. No other host function maps a `UiMessage.code`.

## Consequences

### Positive

- The account-replacement resume is described correctly on both hosts.

### Negative

- `§14.1` has one documented exception.

### Constraints Introduced

- The exception covers only `REMOTE.*` codes on the session channel; any other override needs a new
  decision.

## Verification

- `AccountConversionSurfaceRulesTest.aRemoteFailureOnTheSessionChannelSaysTheReplacementKeepsBeingRetried`
  and `AccountConversionSurfaceTest.aRemoteFailureOnTheSessionChannelRendersTheReplacementCopy`
  (Android); `AccountConversionSurfaceTests.testARemoteFailureOnTheSessionChannelSaysTheReplacementKeepsBeingRetried`
  (iOS).

## References

- `docs/DECISION_BOARD.md` (decision ID `D-232`)
- `D-220` (ADR-0221), `D-210` (ADR-0211), `D-228` (ADR-0229)
- `docs/CONTRACTS.md §14.1`, `§20.10`; `docs/handoffs/E4-05.md`

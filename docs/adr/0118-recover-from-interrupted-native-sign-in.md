# ADR-0118 / D-117 - Recover From an Interrupted Native Sign-In

## Status

Accepted

Selected by the owner on 2026-09-04, after the E2-03 review of pull request #54.

## Context

`D-114` splits an F-1 permanent sign-in in two: shared state records the provider and the busy state,
and the native host acquires the credential and reports the result through a completion or the closed
`NativeSignInFailure`.

On Android the acquisition ran in a scope tied to the activity, while `SessionStateHolder` lives in a
scope that survives configuration changes, and `MainActivity` declared no `android:configChanges`. A
rotation, dark-mode switch, font-size change or multi-window resize while the Credential Manager
sheet was open therefore cancelled the acquisition before any completion intent ran. The session was
left busy forever, both welcome actions stayed disabled, and no state change could clear it, because
the authoritative auth state had already emitted its current value and a `StateFlow` does not
re-emit an equal one.

The review also found that a deliberate cancellation surfaced a red error message, which would make
an automatic recovery look to the owner like a failure they caused.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Handle common configuration changes in place and abandon an orphaned acquisition (Selected) | Removes the frequent interruption and guarantees an exit from the busy state for every other destruction path | Requires declaring the correct qualifier set and carrying a marker across recreation |
| Handle configuration changes only | Keeps the sign-in alive across rotation | Any undeclared or unforeseen destruction path still strands the busy state |
| Abandon only, without configuration handling | Simplest change | Every rotation silently loses an in-progress sign-in and forces the owner to start again |
| Expire the busy state after a timeout | No host changes | An arbitrary deadline can cancel a healthy slow flow, and it hides the defect instead of fixing it |

## Decision

Both halves are applied.

`MainActivity` declares `orientation`, `screenSize`, `smallestScreenSize`, `screenLayout`,
`keyboardHidden`, `uiMode`, `density` and `fontScale`, so those changes are handled in place and no
longer destroy a host that is rendering native credential UI. `locale` is deliberately excluded and
still recreates the activity.

`AndroidGoogleSignInCoordinator` owns an in-flight marker. It sets the marker before the native call
and clears it immediately before the terminal completion or failure intent, so the terminal event
always clears it synchronously. The host stores the marker in recreation-surviving saved state; when
the UI is rebuilt with the marker still set, `abandonInterruptedAcquisition()` clears it and cancels
through `failSignIn(NativeSignInFailure.CANCELLED)`.

Abandonment reuses the existing closed failure intent. No new state-holder method is added, so
`docs/CONTRACTS.md §20.10`, the Swift-facing ABI and the generated header golden are unchanged.

Cancellation, whether owner-driven or from this recovery, leaves a retryable state with no
user-visible error message. Every other closed failure reason keeps reporting its message.

## Consequences

### Positive

- A configuration change during Google sign-in no longer strands the welcome screen.
- Recovery cannot report a failure the owner did not cause.
- The abandonment protocol is owned by one testable class rather than spread through composition.

### Negative

- An interruption that still destroys the host loses the attempt: the owner must start sign-in again.
- The declared qualifier set is a maintenance obligation; a new configuration dimension outside it
  falls back to the abandonment path rather than to an uninterrupted sign-in.

### Constraints Introduced

- Abandonment MUST reuse `failSignIn(NativeSignInFailure.CANCELLED)`; a dedicated intent would widen
  the exported Swift ABI for behaviour already expressible.
- Abandonment MUST NOT be keyed off `isBusy` alone, because `startAnonymousSignIn` also sets it and
  runs in a scope that survives recreation; cancelling that would break a healthy flow.
- The in-flight marker MUST survive host recreation and MUST be cleared by the terminal intent.

## Verification

- Host tests pin the marker protocol around a successful acquisition, around a failed acquisition,
  and for abandonment, which must cancel without starting or completing a sign-in.
- A shared state-holder test proves cancellation clears the busy state, publishes no message, and
  leaves a further attempt possible, while other reasons still publish their error code.
- The real configuration-change path on a device remains a manual acceptance item, because provider
  UI cannot be automated safely.

## References

- `docs/DECISION_BOARD.md` (`D-117`)
- `docs/CONTRACTS.md` sections 11.1 and 20.10
- `docs/SPECIFICATION.md` sections 7 F-1 and 12
- `docs/TECHNICAL_PLAN.md` section 2
- [ADR-0115](0115-native-to-shared-sign-in-handoff.md) (`D-114`)
- [ADR-0113](0113-android-google-credential-acquisition.md) (`D-112`)

# ADR-0123 / D-122 - Report a Device With No Account Available as Its Own Failure

## Status

Accepted

Selected by the owner on 2026-09-06, closing the request left open by the second and third E2-03
review rounds of pull request #54.

## Context

`D-114` closed `NativeSignInFailure` to `CANCELLED`, `NETWORK`, `CONFIGURATION` and `UNKNOWN`. The
second review round established that Android's `NoCredentialException` - the device has no account to
offer - must not be reported as `CONFIGURATION`, because having no account says nothing about how the
provider is configured. It was mapped to `UNKNOWN`, which resolves to `AUTH.UNKNOWN` and the generic
`error_unexpected` message.

That outcome is truthful and unusable. The owner is told that something went wrong and invited to
retry an action that will fail identically, while the two things that would resolve it - adding an
account, or taking the "continue without an account" action already on the same screen - are never
named.

Saying more requires a case the closed enum does not have. That widens the exported enum, the auth
error taxonomy and the Swift-facing ABI, and it changes the committed Objective-C golden header, so
it was escalated rather than taken on agent judgement.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A dedicated case across the shared taxonomy (Selected) | One actionable outcome, derived from the shared taxonomy exactly like every other auth message; both hosts gain it the moment either can detect the condition | Widens a closed exported enum, the error taxonomy and the Swift ABI; regenerates the golden header; only Android can produce it today |
| An Android-only host message | No shared surface changes | The welcome screen would carry two independent message sources that must be cleared together on retry and cancellation; iOS gains nothing; the divergence invites reintroduction |
| Keep `UNKNOWN` | Nothing to build | The owner is left without the one action that resolves the situation |

## Decision

`NativeSignInFailure` gains `NO_ACCOUNT_AVAILABLE`, mapped by `SessionStateHolder.failSignIn` to the
new `AuthError.NoAccountAvailable` leaf with the stable code `AUTH.NO_ACCOUNT_AVAILABLE`. Both hosts
resolve that code to a message that names both resolutions: adding an account, and continuing without
one.

Android produces the case from `NoCredentialException`. iOS keeps `UNKNOWN` for this condition:
Google sign-in on iOS is web-based and has no device accounts, and Apple sign-in exposes no reliable
"no Apple ID on this device" signal. The case is shared because the taxonomy is shared, not because
both hosts emit it today.

`AuthError.NoAccountAvailable` maps to `UNKNOWN` in both analytics failure reasons of `§20.9`. The
account-conversion and account-deletion flows never reach a native credential acquisition that can
report it, so neither reason set gains a member.

## Consequences

### Positive

- The most common first-run failure on a fresh Android device names its own resolutions.
- The message stays derived from the shared taxonomy, so nothing on the welcome screen needs a second
  message source.

### Negative

- The exported enum and the Swift ABI carry a case that iOS cannot currently produce.
- The golden Objective-C header changed, which is a review signal by design.

### Constraints Introduced

- `NO_ACCOUNT_AVAILABLE` MUST mean "this device has no account to offer", never a provider
  configuration problem and never an unclassified failure.
- The message for `AUTH.NO_ACCOUNT_AVAILABLE` MUST name the "continue without an account" path,
  because that path is always available and resolves the situation without an account.
- A host that gains a reliable no-account signal MUST use this case rather than adding another.

## Verification

- Android: `NoCredentialException` maps to the new case, and `authStringResource` resolves
  `AUTH.NO_ACCOUNT_AVAILABLE` away from `error_unexpected`.
- Shared: `failSignIn` with the new case publishes `AUTH.NO_ACCOUNT_AVAILABLE` and clears the busy
  state, and the existing closed-mapping test still covers every other case.
- iOS: `localizedUiMessage` resolves the new code to its own message rather than the generic one.
- Analytics: both normative `AuthError` mappings of `§20.9` stay exhaustive over eleven leaves.
- The regenerated Objective-C header differs from the previous golden only by the new enum case, and
  the committed golden is updated to it in the same change.

## References

- `docs/DECISION_BOARD.md` (`D-122`)
- `docs/CONTRACTS.md` sections 11.1, 15.3, 20.8, 20.9 and 20.10
- `docs/SPECIFICATION.md` sections 7 F-1 and 12
- [ADR-0115](0115-native-to-shared-sign-in-handoff.md) (`D-114`)

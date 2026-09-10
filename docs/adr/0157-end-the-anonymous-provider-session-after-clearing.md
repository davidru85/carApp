# ADR-0157 / D-156 - End the Anonymous Provider Session After Clearing

## Status

Accepted

## Context

`docs/SPECIFICATION.md §7 F-5` gives an anonymous owner "delete local data" rather than account
deletion, and states that the identity cannot be recovered. The first implementation cleared the
local tables and stopped there. That left the Firebase anonymous session in place, so recreating
`SessionStateHolder` read the same anonymous UID and routed the app back to `ANONYMOUS`. To the
owner the deletion looked as if it had not happened.

F-5 also says "for an anonymous session there is no sign-out". The question is whether that sentence
is about the action offered to the owner or about the internal provider call as well.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Clear local data, then end the provider session | The deletion is observable as done after a restart; nothing remote is called; the abandoned identity is an ordinary orphan that E3-11 already handles | Performs a provider sign-out in a flow whose user-facing action is not called "sign out", which has to be documented so nobody reads F-5 as forbidding it |
| B. Leave the anonymous session in place | The most literal reading of F-5's sentence | A recreated holder returns to `ANONYMOUS` on the same UID with empty data, so the destructive action the owner confirmed appears not to have taken effect |
| C. Delete the anonymous account through the E3-11 orphan callable | The identity is actually removed, not just abandoned | A local-data deletion would call a server operation, which is outside what F-5 describes and outside this story's scope |

## Decision

The selected option is **A**.

Local data is cleared first; only if that succeeds is the provider session ended. A clear that fails
therefore never leaves the owner signed out of an identity whose data is still on the device.

## Consequences

### Positive

- The deletion survives a restart, which is what makes it a deletion rather than a screen wipe.
- No server operation is introduced on a path F-5 describes as purely local.

### Negative

- The abandoned anonymous identity remains in Firebase until the existing `E3-11` cleanup removes it.
- A reader taking F-5's sentence literally may expect the session to survive; the contract now says
  otherwise explicitly.

### Constraints Introduced

- The `D-23` server operation MUST NOT be called on this path.
- The clear MUST precede the session end, so a failed clear cannot strand the owner.

## Verification

- `anonymousDeletionClearsLocalDataAndEndsTheSessionWithoutTheServerOperation` asserts the ordering
  and the absence of any server call.
- `anonymousDeletionSurvivesRecreatingTheStateHolder` asserts a recreated holder is `SIGNED_OUT`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-156`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E2-05.md`

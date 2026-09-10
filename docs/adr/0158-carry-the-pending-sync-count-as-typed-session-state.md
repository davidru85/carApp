# ADR-0158 / D-157 - Carry the Pending-Sync Count as Typed Session State

## Status

Accepted

## Context

F-5 refuses a sign-out with a non-empty outbox through
`Err(ValidationWarning.PendingSyncBeforeSignOut(pendingCount))`. The count is the whole point of the
warning: "you have unsynced changes" is not actionable, "you have 7 unsynced changes" is.

The warning reaches the host through `UiMessage`, which carries an id, a kind, a code and an optional
`Confirmation` — and nothing else. The count had nowhere to go and was being dropped.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A typed `SessionUiState.pendingSyncCount: Int?` | Follows the `D-145` precedent for `anonymousReminderIndex`: a typed value each host formats into its own §14 resources; the change is scoped to the session surface | Adds a member to the exported `SessionUiState` and therefore to the Objective-C golden header |
| B. Give `UiMessage` an argument list | One general mechanism for every future parameterised message | Changes a type shared by every feature and every state holder, for one warning; every existing consumer would have to consider it |
| C. Let each host count the outbox itself | No contract change at all | The UI observes only the local database through the shared layer; a host reaching for its own count would duplicate a query and could disagree with the warning it is rendering |

## Decision

The selected option is **A**.

`SessionUiState.pendingSyncCount` is non-null exactly while a pending-sync warning is being offered,
and `null` otherwise. It is a value, not copy: the host formats it.

## Consequences

### Positive

- The `pendingCount` the domain produced is the one the owner sees.
- `UiMessage` stays the narrow, shared channel it was designed to be.

### Negative

- One more exported member on the session surface, and one more line in the golden header.

### Constraints Introduced

- It MUST be cleared whenever the warning is no longer being offered.
- It is a typed value; it MUST NOT carry display copy.

## Verification

- `signOutWithPendingOutboxPublishesTheExactCountWithoutSigningOut` asserts the exact value 7.
- The regenerated Objective-C golden header pins the exported member.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-157`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E2-05.md`

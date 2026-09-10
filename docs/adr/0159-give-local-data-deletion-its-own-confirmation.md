# ADR-0159 / D-158 - Give Local-Data Deletion Its Own Confirmation

## Status

Accepted

## Context

`docs/CONTRACTS.md §20.2` assigns `Confirmation.DeleteAccount` to "account deletion" and
`Confirmation.DiscardPendingChanges` to "sign-out **or local-data deletion** with pending outbox
rows". The first implementation published `CONFIRMATION.DeleteAccount` for a local owner and for an
anonymous session, and never read the outbox on either path. Both halves of that table row were
therefore untrue.

F-5 independently requires that anonymous "delete local data" use the same two-step destructive
confirmation as account deletion, because the identity is unrecoverable.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Add `Confirmation.DeleteLocalData` and order the protocol | Each value means what §20.2 says it means; the pending-sync row becomes true because those paths now read the outbox; the destructive step F-5 requires is preserved | Changes the canonical enum and the Swift ABI, and both hosts must map a new code |
| B. Keep `DeleteAccount` as the destructive step for local data | No enum or ABI change | `DeleteAccount` would keep authorising something that is not account deletion, so §20.2's row would have to be rewritten to permit the confusion rather than remove it |
| C. Use `DiscardPendingChanges` alone | Matches the existing §20.2 row literally | With an empty outbox there is no confirmation at all, which breaks the two-step destructive confirmation F-5 requires for an unrecoverable identity |

## Decision

The selected option is **A**.

A local or anonymous deletion counts the outbox first. With pending rows it publishes
`WARNING.PENDING_SYNC` with `DiscardPendingChanges` and the exact count; once discarded, and directly
when the outbox is empty, it publishes `CONFIRMATION.DeleteLocalData` with `DeleteLocalData`.

## Consequences

### Positive

- A confirmation of the wrong kind authorises nothing, which is checkable rather than conventional.
- Both hosts give account deletion and local-data deletion their own copy, because the consequences
  differ.

### Negative

- A new canonical enum value and a new exported code, with the golden header and both hosts updated.

### Constraints Introduced

- `DeleteAccount` MUST NOT authorise a local-data deletion, and `DeleteLocalData` MUST NOT authorise
  an account deletion.
- The destructive step MUST be refused while a pending-sync warning is unanswered.

## Verification

- `localOwnerDeletionAsksForTheLocalDataConfirmation` and `anonymousDeletionAsksForTheLocalDataConfirmation`.
- `localDataDeletionWithPendingOutboxDiscardsFirstThenConfirmsDestructively` and
  `theDestructiveLocalDataConfirmationIsNotAcceptedBeforeTheDiscard`.
- `UiMessageMappingTests.testDepartureConfirmationsHaveTheirOwnDestructiveCopy`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-158`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E2-05.md`

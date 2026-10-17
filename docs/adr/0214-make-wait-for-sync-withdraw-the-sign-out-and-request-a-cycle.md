# ADR-0214 / D-213 - Make "Wait for Sync" Withdraw the Sign-Out and Request a Cycle

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`docs/SPECIFICATION.md §7 F-5`: when the outbox is non-empty, sign-out returns
`ValidationWarning.PendingSyncBeforeSignOut(pendingCount)`, and "the UI offers to wait for sync,
cancel, or discard pending changes after destructive confirmation". `SessionStateHolder` publishes
the warning as the `UiMessage` code `WARNING.PENDING_SYNC` with `Confirmation.DiscardPendingChanges`
and carries the count on `SessionUiState.pendingSyncCount` (`D-157`). Discarding is
`confirmSignOut(Confirmation.DiscardPendingChanges)`, and dismissing the prompt with
`clearMessage()` withdraws the sign-out request. `docs/CONTRACTS.md §20.10` defines no "wait"
intent.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. "Wait" calls `SessionStateHolder.clearMessage()` and `SyncStateHolder.requestSync(SyncTrigger.PullToRefresh)` | Existing intents only; no new departure state; the user decides again once the rows have synced. | The user taps "Sign out" again; the prompt reappears while rows remain. |
| B. A shared intent that syncs and resumes the sign-out once the outbox empties | One tap. | New departure state in gated `:shared` code with its own failure and cancellation cases; a sign-out completes without a fresh explicit gesture. |
| C. Offer only cancel and discard | Simplest. | Contradicts `F-5`. Rejected. |

## Decision

The selected option is **A**. On both hosts the pending-sync prompt shows `pendingSyncCount` and
three actions: wait (`SessionStateHolder.clearMessage()` followed by
`SyncStateHolder.requestSync(SyncTrigger.PullToRefresh)`), cancel (`SessionStateHolder.clearMessage()`)
and discard (`confirmSignOut(Confirmation.DiscardPendingChanges)`, the existing destructive path).
No shared intent is added.

## Consequences

### Positive

- No change to `AccountDepartureFlow` or `§20.10` for the wait action.

### Negative

- Waiting is a two-step user flow: sync, then sign out again.

### Constraints Introduced

- A host never signs out automatically after a wait.

## Verification

- An Android component test shows the prompt with its count and asserts that the wait action
  invokes both the message-clearing and the sync-request callbacks; the iOS host covers the same
  mapping.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-213`)
- `docs/SPECIFICATION.md §7 F-5`; `docs/CONTRACTS.md §9.8`, `§11.5`, `§20.10`
- `D-157`, `D-158`
- `docs/BACKLOG.md` (`E4-01`)

# ADR-0161 / D-160 - End the Provider Session as Its Own Deletion Step

## Status

Accepted

## Context

`FirebaseAuthClient.deleteAccount()` verifies token freshness and calls the `D-23` Admin operation.
It does not sign out and does not publish `AuthState.SignedOut`, so the persisted client session
outlives the account it belonged to. The shared flow published `SIGNED_OUT` from its own state
anyway, so the defect was invisible until a holder was recreated — and the test double hid it by
emitting `SignedOut` from `deleteAccount()`, which the real client never does.

The constraint that shapes the fix: once the server operation has succeeded it MUST NOT run again.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A separate flow step with its own flag | A failure in the cleanup or the clear cannot be confused with a failed server call, so the server operation is never repeated; the ordering is explicit in §11.5 and directly testable | `AuthClient.deleteAccount()` still leaves a live session, which has to be documented so no consumer assumes otherwise |
| B. Sign out inside `FirebaseAuthClient.deleteAccount()` | The adapter owns provider state, and no consumer can forget the cleanup | A sign-out failure after a successful deletion would have to surface as `Err`, which the flow reads as a failed server call and may repeat — or be swallowed, hiding a real failure |
| C. Both, with the flow verifying | Defence in depth against a future adapter that forgets | Two owners of one fact, free to diverge, and the flow needs its own flag regardless, so the adapter logic buys nothing |

## Decision

The selected option is **A**.

The flow runs `deleteAccount()`, then ends the provider session, then clears local data. Each step
records its own success, and a retry resumes from the first step that has not.

## Consequences

### Positive

- A recreated holder cannot return to `PERMANENT` after a successful deletion.
- The three failure points are distinguishable, which is what makes the no-repeat rule enforceable.

### Negative

- `AuthClient.deleteAccount()` has a contract a careless reader could misjudge; a regression test now
  pins it.

### Constraints Introduced

- A failure in the session cleanup or the local clear MUST NOT repeat the server operation.
- Once the server operation has succeeded, the session cleanup and the local clear form one tail
  that ordinary cancellation, including `SessionStateHolder.close()`, MUST NOT interrupt. This is
  about cancellation, not process death, which remains `E2-09`.
- Any test double for `AuthClient` MUST NOT publish `SignedOut` from `deleteAccount()`.

## Verification

- `permanentDeletionEndsTheProviderSessionBeforeClearingLocalData` asserts the observed order.
- `permanentDeletionSurvivesRecreatingTheStateHolder` asserts the recreated holder is not `PERMANENT`.
- `aProviderSignOutFailureAfterRemoteDeletionNeverRepeatsTheServerOperation`.
- `FirebaseAuthClientTest.deleteAccountLeavesTheClientSessionForTheCallerToEnd` pins the adapter.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-160`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E2-05.md`

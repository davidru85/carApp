# ADR-0162 / D-161 - Report Account-Deletion Analytics for the Permanent Path

## Status

Accepted

## Context

`AnalyticsEvent.AccountDeletionStarted` is declared in the closed hierarchy and in
`docs/CONTRACTS.md §17`, and was never emitted anywhere. `AccountDeletionCompleted` was emitted for
every departure kind, including a local owner clearing its device, which is not an account deletion
at all. Neither boundary was normative.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Emit `Started` when a permanent deletion is confirmed; restrict all three to that path | `started = completed + failed` becomes an identity a funnel can trust; the events describe what their names say | A dialog the owner opens and abandons is not counted |
| B. Emit `Started` when the confirmation is published | Measures dialog abandonment, which is useful product information | Breaks the identity, because a cancellation emits no closing event |
| C. Remove the event | No declared-but-unemitted event remains | Changes an accepted analytics contract and loses the funnel's start signal |

## Decision

The selected option is **A**.

The three account-deletion events describe the permanent `D-23` path only. A local or anonymous
local-data deletion reports none of them.

A `D-23` attempt refused with `AuthError.RequiresRecentLogin` is a failed attempt and reports
`AccountDeletionFailed`. Resuming after a successful re-authentication runs the `D-23` call again, so
it is a new attempt and reports its own `AccountDeletionStarted`. The full sequence for that path is
`Started`, `Failed(REQUIRES_RECENT_LOGIN)`, `Started`, then `Completed` or `Failed`. Without the
second `Started` the identity would not hold, which is what an earlier revision of this flow got
wrong. A retry that repeats only a session cleanup or a local clear does NOT report a start, because
it makes no new `D-23` attempt.

## Consequences

### Positive

- The event names match the events.
- The trio is internally consistent, so a discrepancy in the data is a real signal.

### Negative

- Abandonment at the confirmation dialog is not measured; a separate event would be needed for that.

### Constraints Introduced

- `AccountDeletionStarted`, `AccountDeletionCompleted` and `AccountDeletionFailed` MUST NOT be emitted
  for a local or anonymous local-data deletion.

## Verification

- `permanentDeletionEmitsTheAccountDeletionLifecycle` asserts the exact ordered pair.
- `aDeletionResumedAfterReauthenticationEmitsANewStarted` asserts the exact ordered four-event
  sequence of the stale-login path.
- `localOwnerDeletionEmitsNoAccountDeletionAnalytics` and `anonymousDeletionEmitsNoAccountDeletionAnalytics`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-161`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E2-05.md`

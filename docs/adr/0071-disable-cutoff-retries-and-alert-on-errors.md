# ADR-0071 / D-70 - Disable Cutoff Retries and Alert on Errors

## Status

Accepted

## Context

Firebase requires an explicit forced deployment when a Pub/Sub function enables retry because a
failed event can repeat for up to seven days. `maxInstances = 1` limits concurrency, not duration.
A persistent permissions or API fault would therefore create recurring billable work inside the
control whose purpose is to stop recurring spend.

With retry disabled, a failed invocation is not automatically replayed. Silence is the resulting
risk: a configured cutoff could fail without an operator seeing it. Cloud Billing publishes budget
status updates repeatedly, but the actual cadence for this budget must be observed rather than
assumed.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Disable retry, alert on every execution error and measure recurring publication | A persistent fault produces a visible single failure instead of up to seven days of executions. | A dropped event waits until the next observed budget publication before a second attempt. |
| Enable retry with `--force` | Retries transient errors without waiting for the next budget update. | A persistent fault creates the recurring billable consumption the cutoff exists to prevent. |

## Decision

Deploy `stopBilling` with `retry: false`. Create a Cloud Monitoring alert that notifies
`davidru85@gmail.com` whenever the function reports an execution error. The budget acceptance test
records consecutive Pub/Sub publication timestamps and their interval; that observed interval is
the worst-case second-attempt assumption after a dropped event. A materially lower frequency than
the documented multiple-times-per-day cadence requires immediate owner review of D-70.

The handler checks the project's current billing state before attempting an update. When billing
is already disabled it returns `ALREADY_DISABLED` without a write, making repeated budget updates
idempotent and cheap.

## Consequences

### Positive

- Persistent failure cannot become a seven-day function retry loop.
- A failed cutoff attempt is visible through an independent monitoring path.
- Later budget publications use a trivial no-op after successful shutdown.

### Negative

- Recovery from one dropped event is bounded by the measured next publication, not an immediate
  automatic retry.

### Constraints Introduced

- `retry: true` and forced failure-policy deployment are forbidden for `stopBilling`.
- The error alert and cadence record are part of the control and cannot be omitted from acceptance.

## Verification

- The exported endpoint contract asserts `retry: false` and the dedicated service account.
- Unit tests prove `ALREADY_DISABLED` performs no billing update.
- Monitoring inspection proves the execution-error policy and notification channel are enabled.
- The runbook records at least two consecutive real budget publication timestamps.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-70`)
- `docs/runbooks/development-firebase-cost-controls.md`
- [Programmatic budget notifications](https://cloud.google.com/billing/docs/how-to/budgets-programmatic-notifications)

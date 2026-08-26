# ADR-0073 / D-72 - Exercise the Cutoff with Controlled Threshold Events

## Status

Accepted

## Context

The temporary one-nano-EUR budget has published genuine zero-cost notifications, which proves the
budget publisher and establishes its real cadence but cannot cross an actual-cost threshold while
the project remains within free tiers. Deliberately generating chargeable usage would spend money
to test the mechanism intended to prevent spending, while waiting for organic spend would leave
E0-07 completion open-ended.

The cutoff is not accepted until its deployed Pub/Sub, Eventarc, Cloud Function and Cloud Billing
API path has actually disabled billing and the owner has performed recovery. Its destructive
effect is the test subject, so expected and actual service behavior must be recorded separately.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Publish two controlled schema-valid threshold events to the real budget topic | Deterministically exercises the deployed path, the destructive result and the idempotent no-op without creating spend. | The trigger event is controlled rather than emitted by the Billing Budget service. |
| Generate billable usage until the budget crosses its threshold | Uses a completely natural budget event. | Spends money and introduces reporting delay specifically to test cost containment. |
| Wait for organic spend | Requires no test event. | Has no bounded completion time and may never exercise the control. |

## Decision

Independently retain evidence of genuine recurring publications from the real budget, then publish
two back-to-back schema-valid over-threshold messages to its real Pub/Sub topic. The first message
must execute the deployed function and remove the development project's billing association. The
second delivery must exercise the handler's `ALREADY_DISABLED` early return without another
Billing API write.

Before publication, the runbook records expected outcomes for Firestore data, Authentication, the
deployed function, Pub/Sub and the budget publisher. It then records the actual result beside each
expectation. Recovery owner David Ruiz performs and times the complete relink procedure, verifies
service restoration and restores the final EUR 10 budget.

## Consequences

### Positive

- The emergency control is proven rather than inferred from configuration.
- The test has no intentional cloud-consumption cost.
- Differences between expected and actual failure behavior become durable operational evidence.

### Negative

- Controlled messages do not prove that a future non-zero cost update will be published; the
  separately observed genuine budget cadence remains the evidence for that component.
- The development project intentionally stops serving during the test.

### Constraints Introduced

- Controlled acceptance messages MUST use the production schema and real topic.
- The runbook MUST preserve expected and actual columns, message identifiers, audit evidence,
  owner, timing and the end-to-end recovery result.
- Acceptance MUST prove `ALREADY_DISABLED` on the second delivery.

## Verification

- Genuine budget messages establish the observed publication cadence.
- Function logs identify one `BILLING_DISABLED` result and one `ALREADY_DISABLED` result.
- Cloud Audit Logs identify the function identity as the billing-disable actor.
- Authentication, Firestore, function and Pub/Sub behavior are checked while billing is disabled
  and again after the owner completes the timed recovery.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-72`)
- `docs/SPECIFICATION.md`
- `docs/TECHNICAL_PLAN.md`
- `docs/adr/0067-contain-development-cloud-costs.md`
- `docs/adr/0071-disable-cutoff-retries-and-alert-on-errors.md`
- `docs/runbooks/development-firebase-cost-controls.md`


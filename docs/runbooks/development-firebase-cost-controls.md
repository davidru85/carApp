# Development Firebase Cost-Control Runbook

## Scope and Ownership

This runbook applies only to `davidruiz-carapp-dev`. Recovery owner: **David Ruiz**
(`davidru85@gmail.com`). Production MUST use aggressive alerts and manual intervention and MUST
NOT copy the automatic billing cutoff.

The EUR 10 monthly budget is notification-only. It does not cap or stop spending, and delayed cost
reporting can produce charges above EUR 10. `stopBilling` is the separate destructive response.

## Deliberate Billing-State History

Cloud Audit Logs are the timestamp source. Every state change appends a row before the task is
considered complete.

| Audit timestamp (UTC) | Actor | Action | Result and reason |
|-----------------------|-------|--------|-------------------|
| 2026-08-26T12:45:57.806803Z | `davidru85@gmail.com` | Manual `gcloud billing projects link` (`AssignResourceToBillingAccount`) | Billing enabled to deploy the approved D-66 cutoff controls. |
| 2026-08-26T12:47:41.293609Z | `davidru85@gmail.com` | Manual `gcloud billing projects unlink` (`DisableResourceBilling`) | Billing deliberately disabled after Firebase rejected the first deployment for `retry: true`; no function had deployed, so this was manual containment and not the cutoff firing. |
| 2026-08-26T12:54:55.587060Z | `davidru85@gmail.com` | Manual `gcloud billing projects link` (`AssignResourceToBillingAccount`) | Billing deliberately restored only after D-70 selected `retry: false`, so the approved function could be deployed and tested. |

## Fixed Controls

- Budget: EUR 10 per calendar month, project-scoped, actual-cost thresholds 50%, 90% and 100%.
- Budget email recipient: `davidru85@gmail.com` through default billing/project recipients.
- Programmatic topic: `projects/davidruiz-carapp-dev/topics/carapp-development-billing-alerts`.
- Function: `stopBilling`, 2nd gen, `europe-west1`, runtime sourced from
  `docs/versions-matrix.md`, `retry: false`, concurrency 1 and maximum instances 1.
- Function error notification: Cloud Monitoring email to `davidru85@gmail.com`.
- Billing administration notification: Cloud Monitoring email to `davidru85@gmail.com`.
- Function-error policy: `projects/davidruiz-carapp-dev/alertPolicies/16829276630868734195`.
- Billing-administration policy:
  `projects/davidruiz-carapp-dev/alertPolicies/16719603493370820812`.
- Runtime identity: keyless `development-billing-cutoff`; its broad billing-account role and
  minimal project role are governed by D-69.

The deployed function was `ACTIVE` at 2026-08-26T12:56:47.496721996Z with revision
`stopbilling-00001-kem`, Node.js 22, `RETRY_POLICY_DO_NOT_RETRY` and the dedicated runtime
identity. An intentional severity-ERROR acceptance event was ingested at
2026-08-26T17:11:59.221985794Z and matched the function-error policy filter. Owner confirmation
of email delivery remains part of the destructive acceptance evidence.

The temporary acceptance budget is deliberately one nano-EUR, not EUR 0.01. This exact amount is
used only to force the real cutoff. After recovery it MUST be changed to the final EUR 10 amount
and verified through the Billing Budgets API.

## Budget Publication Cadence

Google documents budget Pub/Sub updates as occurring multiple times per day, including when usage
is zero, and warns that the first publication can take several hours. That documentation is not
the acceptance measurement. Consecutive messages from this exact budget are recorded below.

| Publication timestamp (UTC) | Previous interval | Budget display name | Cost / budget |
|-----------------------------|-------------------|---------------------|---------------|
| Pending real notification | — | — | — |

The observed maximum interval is pending. If notifications arrive materially less often than
multiple times per day, D-70 is reopened before the cutoff is accepted.

## Manual Relink Procedure

Only recovery owner David Ruiz performs this procedure after identifying and stopping the cause:

1. Confirm `stopBilling` is no longer receiving an over-threshold test message or runaway source.
2. Inspect function errors and billing-administration audit alerts.
3. Relink with
   `gcloud billing projects link davidruiz-carapp-dev --billing-account=01F6AF-2A3D04-00546B`.
4. Wait for `gcloud billing projects describe davidruiz-carapp-dev` to report
   `billingEnabled: true`.
5. Verify Authentication, Firestore, existing documents and `stopBilling` separately; service
   restoration is not guaranteed to be immediate.
6. Append the audit timestamp, actor, observed service state and recovery result to this runbook.

## Destructive Acceptance Evidence

Pending the real temporary-budget trigger. This section will record:

- the budget publication that caused the cutoff;
- the function log and billing audit event;
- observed Authentication and Firestore failures;
- the state of existing Firestore data before, during and after the outage;
- the manual relink audit event and time to recovery;
- the notification-channel delivery evidence.

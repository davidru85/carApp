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
| 2026-08-26T17:36:01.190Z | — | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T18:00:27.489Z | 24m 26.299s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T18:25:34.617Z | 25m 07.128s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T18:50:55.915Z | 25m 21.298s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T19:15:25.580Z | 24m 29.665s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T19:39:32.108Z | 24m 06.528s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T20:24:29.433Z | 44m 57.325s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T21:26:45.131Z | 1h 02m 15.698s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T22:12:50.206Z | 46m 05.075s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T22:38:35.956Z | 25m 45.750s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |
| 2026-08-26T23:00:35.342Z | 21m 59.386s | Development Firebase cost containment | EUR 0 / temporary one-nano-EUR budget |

The observed maximum interval in this ten-interval sample is **1h 02m 15.698s**. The real publisher
therefore delivered multiple zero-cost updates per day and did not require reopening D-70. This
measured maximum is the current worst-case second-attempt assumption after a dropped event; it is
evidence, not a guaranteed service-level objective.

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

The controlled D-72 test records expectations before publishing either threshold message. Actual
results remain `Pending` until the destructive test has run; replacing an expectation with the
observed outcome is forbidden because discrepancies are acceptance evidence.

| Surface | Expected outcome while billing is disabled | Actual outcome |
|---------|--------------------------------------------|----------------|
| Firestore data | The acceptance document remains stored and unchanged. Firestore data-plane access stops while billing is detached, then the same document becomes readable after relink. | Pending controlled test. |
| Firebase Authentication | Backend Authentication stops: new anonymous sign-in and token refresh fail. Any Keychain credential remains only as local persisted state and cannot make the backend operational. | Pending controlled test. |
| Deployed `stopBilling` function | The first delivery completes and the deployed function resource and revision remain recorded. The immediately queued second delivery reaches the idempotent handler and returns `ALREADY_DISABLED`; later execution is unavailable until billing is restored. | Pending controlled test. |
| Pub/Sub and budget publisher | Topic, subscription and budget configuration survive. The billing-account publisher remains configured, but project-local message delivery is expected to stop until relink because the development project deliberately stops serving. | Pending controlled test. |
| Owner recovery | David Ruiz relinks the known billing account, waits for `billingEnabled: true`, verifies Auth, Firestore, Pub/Sub and the deployed function end to end, restores the EUR 10 budget and records total elapsed time. | Pending controlled test. |

The completed evidence will also record:

- the budget publication that caused the cutoff;
- the function log and billing audit event;
- observed Authentication and Firestore failures;
- the state of existing Firestore data before, during and after the outage;
- the manual relink audit event and time to recovery;
- the notification-channel delivery evidence.

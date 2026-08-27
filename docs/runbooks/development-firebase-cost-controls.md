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
| 2026-08-26T23:56:09.839657Z | `development-billing-cutoff@davidruiz-carapp-dev.iam.gserviceaccount.com` | Deployed `stopBilling` (`DisableResourceBilling`) | D-72 controlled threshold event removed the billing association. The Billing API request time was 23:56:11.119930Z and the function logged `BILLING_DISABLED` at 23:56:11.181333Z. |
| 2026-08-27T00:01:20.012443Z | `davidru85@gmail.com` | Manual `gcloud billing projects link` (`AssignResourceToBillingAccount`) | Recovery owner deliberately relinked after the complete disabled-state observation; `billingEnabled: true` was observed at 00:01:22Z. |

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
- Eventarc invocation: that identity holds `roles/run.invoker` only on the `stopbilling` Cloud Run
  service. It MUST NOT hold a project-level Cloud Run role. Verify this service policy after every
  deployment with `gcloud run services get-iam-policy stopbilling --region=europe-west1`.

The deployed function was `ACTIVE` at 2026-08-26T12:56:47.496721996Z with revision
`stopbilling-00001-kem`, Node.js 22, `RETRY_POLICY_DO_NOT_RETRY` and the dedicated runtime
identity. An intentional severity-ERROR acceptance event was ingested at
2026-08-26T17:11:59.221985794Z and matched the function-error policy filter. Owner confirmation
of email delivery remains part of the destructive acceptance evidence.

The first D-72 publication attempt exposed a missing Eventarc transport permission: messages
`21346724746516520` and `21347059695302127`, published at 2026-08-26T23:51:42Z, reached Cloud Run
but received HTTP 403 because the configured delivery identity lacked `run.routes.invoke`.
Billing remained enabled. At 2026-08-26T23:54:36.808902Z, David Ruiz granted
`roles/run.invoker` to that identity on the exact `stopbilling` service, with no project-level
Cloud Run role. The repeated attempt then exercised the complete path successfully.

The temporary acceptance budget was deliberately one nano-EUR, not EUR 0.01. It established the
real publisher cadence but did not naturally cross its threshold because reported cost stayed at
zero. D-72 therefore used controlled threshold messages. Recovery restored and API-verified the
final EUR 10 amount.

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
| Firestore data | The acceptance document remains stored and unchanged. Firestore data-plane access stops while billing is detached, then the same document becomes readable after relink. | **Different.** Admin data-plane reads returned HTTP 200 continuously through 00:00:44Z, more than four minutes after cutoff. The document field hash remained `6044d3434902d38180dbaf09818e4ea23201c04c3c19ff5f3fb6306d3fa18a0a`; no interruption or mutation was observed. The same hash was returned after relink. |
| Firebase Authentication | Backend Authentication stops: new anonymous sign-in and token refresh fail. Any Keychain credential remains only as local persisted state and cannot make the backend operational. | **Different.** A new App-Check-protected anonymous sign-in returned HTTP 200 after billing reported disabled, and authenticated account lookups continued returning HTTP 200 through 00:00:44Z. Auth did not stop during the observed window. |
| Deployed `stopBilling` function | The first delivery completes and the deployed function resource and revision remain recorded. The immediately queued second delivery reaches the idempotent handler and returns `ALREADY_DISABLED`; later execution is unavailable until billing is restored. | **Partly different.** Messages `21341922908322796` and `21341340469343468` were published back to back at 23:56:03Z. Logs record `BILLING_DISABLED` at 23:56:11.181333Z and `ALREADY_DISABLED` at 23:56:11.408501Z. Later redeliveries also returned `ALREADY_DISABLED` while detached. The Functions management API returned 403, but after relink the function remained `ACTIVE` on unchanged revision `stopbilling-00001-kem`. |
| Pub/Sub and budget publisher | Topic, subscription and budget configuration survive. The billing-account publisher remains configured, but project-local message delivery is expected to stop until relink because the development project deliberately stops serving. | **Different.** The topic and pull subscription remained readable and `ACTIVE`; the budget remained readable with its topic and thresholds; Eventarc continued delivering queued messages to the warm function while billing was detached. No publisher or delivery interruption was observed. |
| Owner recovery | David Ruiz relinks the known billing account, waits for `billingEnabled: true`, verifies Auth, Firestore, Pub/Sub and the deployed function end to end, restores the EUR 10 budget and records total elapsed time. | **Matched.** David Ruiz started relink at 00:01:19Z; the audit event is 00:01:20.012443Z and `billingEnabled: true` was observed at 00:01:22Z, three seconds after the command started. Auth and Firestore returned 200, the witness hash matched, Pub/Sub was `ACTIVE`, and the function remained `ACTIVE` on the same revision. The final EUR 10 amount was restored at 00:02:24.021839Z with 50/90/100% actual-cost thresholds and both notification paths intact; all end-to-end checks completed by 00:03:00Z, at most 101 seconds after recovery began. |

The project was deliberately detached for 5m 10.173s between the disable and relink audit events.
The test proves the billing-association removal and recovery path, but it disproves the prior claim
that Authentication and Firestore necessarily stop immediately. In the observed free-tier state,
the only confirmed outage was the Cloud Functions management read, which returned 403; the
product data planes and already deployed event delivery continued serving. Future readers MUST
not describe this control as an immediate universal service shutdown.

Completed evidence also records:

- the controlled topic publication that caused the cutoff;
- the function log and billing audit event;
- observed Authentication and Firestore responses, including discrepancies from expected outage;
- the state of existing Firestore data before, during and after the outage;
- the manual relink audit event and time to recovery;
- the notification-channel delivery evidence.

The schema-valid witness document, both temporary anonymous users and the temporary iOS App Check
debug token were deleted after recovery; every cleanup API returned HTTP 200.

## Cloud Functions Artifact Retention

The deployed function's `buildConfig.dockerRepository` resolves to
`projects/davidruiz-carapp-dev/locations/europe-west1/repositories/gcf-artifacts`. This is the
repository governed by D-73; a repository name inferred from convention is not sufficient.

At 2026-08-27T00:10:51.326151Z, the Firebase CLI applied cleanup policy
`firebase-functions-cleanup` to that exact repository:

- action: `DELETE`;
- age: `86400s` (one day);
- tag state: `ANY`.

The pre-policy inventory was 110.685 MB and contained:

| Created (UTC) | Package | Digest | Size | Tags |
|---------------|---------|--------|------|------|
| 2026-08-26T12:56:00.847439Z | `davidruiz--carapp--dev__europe--west1__stop_billing` | `sha256:31597e8d83c80b092fbea18d89ee8d6d12e0b22b4fa6baef66699237a855bf25` | 22,388,333 bytes | `latest`, `version_1` |
| 2026-08-26T12:56:13.130863Z | `davidruiz--carapp--dev__europe--west1__stop_billing/cache` | `sha256:2ab9110b5cca334a8972927a9f31a2dc2e85da5d7a7a4cb1145c0561d4ef386f` | 90,966,053 bytes | `latest` |

The images were not yet one day old when the policy was created. D-73 acceptance remains open
until Artifact Registry's asynchronous cleanup removes an eligible image without manual deletion.
The after-inventory and audit timestamp are appended here when that occurs.

# ADR-0070 / D-69 - Isolate the Required Billing Admin Privilege

## Status

Accepted

## Context

`stopBilling` must remove the development project's billing-account association. That operation
requires `billing.resourceAssociations.delete` on billing account `01F6AF-2A3D04-00546B` and
`resourcemanager.projects.deleteBillingAssignment` on the project.

The billing account is personal and has no Google Cloud organization parent. Google Cloud custom
billing roles can be created only at organization level, and among predefined billing-account
roles only `roles/billing.admin` contains `billing.resourceAssociations.delete`. The standard role
contains substantially more account-administration permissions than the function needs.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Grant Billing Admin only to a dedicated keyless cutoff identity and alert on administrative changes | Implements the official automatic-cutoff pattern without mixing billing privilege into product functions. | The identity still has broader billing-account permissions than its handler needs. |
| Keep billing disabled | Avoids the broad role and all billed-project risk. | Blocks Identity Platform, native acceptance and E0-07. |
| Create a Google Cloud organization solely for a custom billing role | Permits an exact billing-account permission set. | Requires a managed domain and permanent organization infrastructure disproportionate to one development project. |

## Decision

Grant `roles/billing.admin` on billing account `01F6AF-2A3D04-00546B` only to the keyless
`development-billing-cutoff@davidruiz-carapp-dev.iam.gserviceaccount.com` identity.

Its project custom role contains exactly:

- `resourcemanager.projects.get`;
- `resourcemanager.projects.deleteBillingAssignment`;
- `serviceusage.services.use`.

Eventarc delivers the Pub/Sub CloudEvent as the same identity. The identity additionally holds
`roles/run.invoker` only on the Cloud Run service
`projects/davidruiz-carapp-dev/locations/europe-west1/services/stopbilling`; it holds no Cloud Run
role at project level. This service-scoped binding is transport permission, not product-data or
billing-account authority.

The identity has no Firebase Authentication, Firestore, Storage or other product-data role. A
Cloud Monitoring log-match alert sends every Cloud Billing administrative change to
`davidru85@gmail.com`. Every deliberate billing state transition is also appended to
`docs/runbooks/development-firebase-cost-controls.md` with its audit timestamp and actor.

## Consequences

### Positive

- Billing privilege is absent from the future D-63 product functions and their runtime identity.
- The function has no key and cannot access product data.
- Administrative use of the broad role is visible outside the function.

### Negative

- Compromise of the runtime identity could exercise wider billing-account administration than
  billing disassociation alone.

### Constraints Introduced

- No other function may use the cutoff service account.
- No service-account key may be created for it.
- `roles/run.invoker` MUST remain scoped to `stopbilling`; project-level Cloud Run invocation is
  forbidden.
- Removing or weakening the billing-administration alert requires a superseding owner decision.

## Verification

- IAM inspection proves the account role, exact project role and absence of service-account keys.
- Cloud Run IAM inspection proves the service-scoped invoker binding and absence of a project-level
  Cloud Run role.
- The deployed endpoint names the dedicated runtime identity.
- The billing-administration alert is enabled and its notification channel is verified.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-69`)
- `docs/SECURITY.md`
- `docs/runbooks/development-firebase-cost-controls.md`
- [Cloud Billing custom roles](https://cloud.google.com/billing/docs/how-to/custom-roles)

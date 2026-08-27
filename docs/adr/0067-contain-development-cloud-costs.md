# ADR-0067 / D-66 - Contain Development Cloud Costs

## Status

Accepted

## Context

E0-07 requires Firebase Authentication with Identity Platform, which requires Cloud Billing on
the development project. Legitimate spend for one developer is expected to remain effectively
zero under the free tiers. The selected EUR 10 monthly amount is therefore an accident ceiling,
not growth allowance: reaching it indicates runaway usage or an implementation fault.

Cloud Billing budgets are notification-only. They neither cap spending nor stop services, and
cost reporting can arrive after the underlying usage. Anonymous Authentication is not the likely
marginal cost driver while native 30-day cleanup is enabled because those accounts do not count
towards Auth usage limits or billing quotas. The realistic risks are runaway Firestore reads or
writes and recursive Cloud Function invocations.

The project also needs CI evidence that the runtime actually deployed for the billing cutoff has
not drifted from the normative runtime pin. CI may not store a long-lived project credential.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| EUR 10 budget, actual-cost alerts at 50%, 90% and 100%, then disable development billing at 100% | Makes accidental spend visible and shuts down the development project after the selected ceiling is reported. | Notifications are delayed, so final spend can exceed EUR 10; disabling billing stops the project. |
| Disable development billing at 90% | Leaves one euro of nominal margin. | The margin is immaterial compared with reporting delay and stops development earlier without materially bounding overshoot. |
| Disable on forecasted 100% spend | Can react before actual cost reaches the budget. | A burst of legitimate testing extrapolated from a near-zero baseline can predict an absurd month and cause an unjustified shutdown. |
| Host the cutoff in a separate FinOps project | Keeps the control plane alive after the target project stops. | Adds another project, billing link and maintenance surface; the function has already completed its only job when it stops itself. |
| Python billing function | Uses an official client and supported runtime. | Adds a second runtime, dependency pipeline and deployment path to a Node-based Functions package. |
| Node.js 24 in a separate billing codebase and Node.js 22 for the 1st gen Auth trigger | Uses the newest runtime for the 2nd gen Pub/Sub function. | Splits the one Functions package and deployment path required by D-63. |
| Node.js 24 now, followed by a downgrade for E3-10 and a later upgrade | Avoids selecting Node.js 22 before the Auth trigger exists. | Deliberately schedules a downgrade and later re-upgrade, producing three runtime migrations where none are needed. |
| GitHub OIDC through Workload Identity Federation | Lets CI read the deployed function without a stored key. | Adds narrowly scoped IAM and GitHub environment configuration that must remain audited. |
| Store a service-account key in GitHub Secrets | Simple CI authentication. | Violates the repository prohibition on real-project credentials in CI and creates a long-lived secret. |

## Decision

The development project `davidruiz-carapp-dev` uses the following controls:

- one EUR 10 calendar-month Cloud Billing budget scoped only to the development project;
- actual-cost email alerts at 50%, 90% and 100% to the billing account administrators/users and
  project owners;
- programmatic notifications to a project-local Pub/Sub topic;
- `stopBilling`, a 2nd gen Pub/Sub function in `europe-west1`, which removes the development
  project's billing-account association when actual cost is greater than or equal to 100%;
- an idempotent check that does nothing when billing is already disabled;
- no automatic Pub/Sub retry; every execution error produces a Cloud Monitoring notification and
  the observed recurring budget publication cadence is recorded as the next-attempt bound;
- a dedicated keyless execution identity. The personal billing account requires the standard
  `roles/billing.admin` role, while its project role is minimal and billing administrative changes
  alert the owner;
- Node.js 22, Firebase Functions 7.3.2, Firebase Admin 14.3.0 and
  `@google-cloud/billing` 6.0.0 in the single `functions/` package;
- a real destructive acceptance exercise using a temporary trivial budget, followed by manual
  relinking to billing account `01F6AF-2A3D04-00546B` by the recovery owner, David Ruiz;
- a runbook that states observed Authentication, Firestore and stored-data behavior during that
  exercise and gives the exact recovery procedure.

Node.js 22 is not independent technical debt. It is coupled to the sole 1st gen Auth trigger
accepted by D-63: it is the current runtime that supports both 1st and 2nd generation functions in
one package. The existing TD-01 review and migration owns both constraints. Node.js 22 deprecates
on 2027-04-30 and is decommissioned on 2027-10-31; the latter is a hard migration deadline.

N3 is explicitly rejected. Choosing a temporary Node.js 24 pin would force a deliberate downgrade
for the 1st gen Auth trigger and a later upgrade after that trigger moves to 2nd gen.

Production MUST NOT inherit the automatic billing cutoff. Before E4-04 creates the production
project, it must define its own billing account, budget and aggressive alerts. Production cost
response is manual because an automatic outage can cost more than the bill it prevents.

CI authenticates with GitHub OIDC and Google Workload Identity Federation. The provider and IAM
binding both restrict admission to immutable repository `1336269502` owned by `472324`. The
provider additionally requires the `cloud-runtime-verification` environment and admits only a
push of `main` or an internal pull request targeting `main` and initiated by the owner. The service
account receives a custom project role containing exactly `cloudfunctions.functions.get`; it has
no broader inherited project role. Google authentication and Cloud SDK actions are pinned by
commit SHA.

## Consequences

- The EUR 10 budget is notification-only and is not a hard spending cap.
- Delayed cost reporting can make the final charge exceed the budget, potentially by more than a
  threshold percentage.
- When the cutoff fires, the development project stops serving, including free-tier resources;
  recovery is manual and service restoration is not guaranteed to be immediate.
- The same-project function stops after removing billing, which is intended because its operation
  is complete.
- Development availability is deliberately subordinate to limiting accidental spend.
- Production uses alerts and manual intervention, never this automatic cutoff.
- Node.js 22 and the 1st gen Auth exception move together under TD-01 rather than creating a second
  debt review.
- CI receives a short-lived, read-only identity and no service-account key.
- The mandatory Firebase Admin peer carries the separately governed D-68 moderate production
  transitive residual; its acceptance is reachability-tested and expires into the TD-01 review.
- The broad billing-account role and no-retry delivery policy are governed separately by D-69 and
  D-70, including their compensating monitoring controls.

## Verification

- Unit tests cover below-threshold, threshold, malformed-event, already-disabled and disable paths.
- A temporary real budget notification fires `stopBilling`; the runbook records observed impact
  and successful relinking.
- `docs/versions-matrix.md` is the normative runtime source.
- `contractCheck` fails when the Functions manifest, CI runtime assertion or matrix disagree.
- CI reads the deployed function and fails unless its runtime equals the matrix value.
- IAM inspection proves the provider condition, repository-scoped `principalSet`, one-permission
  custom role and absence of broader service-account roles.

## References

- [Cloud Billing budgets](https://cloud.google.com/billing/docs/how-to/budgets)
- [Disable billing with notifications](https://cloud.google.com/billing/docs/how-to/disable-billing-with-notifications)
- [Cloud Run functions runtime support](https://cloud.google.com/functions/docs/runtime-support)
- [Google GitHub Actions authentication](https://github.com/google-github-actions/auth)
- `docs/runbooks/development-firebase-cost-controls.md`
- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
- `D-14`, `D-60`, `D-63`, `D-68`, `D-69`, `D-70`

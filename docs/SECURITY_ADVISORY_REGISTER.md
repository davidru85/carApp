# Security Advisory Acceptance Register

This register records dependency advisories that remain present by an explicit owner decision.
An entry is never an audit suppression: the normal audit output stays visible, the configured
severity gate remains active and every acceptance has a dated re-evaluation path.

## GHSA-w5hq-g745-h8pq - Functions Production Graph

| Field | Recorded value |
|-------|----------------|
| Decision | D-68 / ADR-0069 |
| Accepted | 2026-08-26 |
| Severity | Moderate |
| Affected package | `uuid@9.0.1`; v3, v5 and v6 with an external output buffer |
| Production audit | Seven moderate entries remain with development dependencies excluded |
| Dependency path | `firebase-functions` requires `firebase-admin` as a mandatory peer; Firebase Admin includes Cloud Storage, which includes the affected UUID version |
| Reachability | `functions/test/dependencyReachability.test.mjs` invokes the exported `stopBilling` CloudEvent path through `CloudBillingGateway` and the Billing disassociation request, while recording loaded modules. Cloud Storage and `uuid` are not loaded; the affected variants are therefore unreachable. |
| CI policy | Report moderate findings; fail on high and critical with `npm audit --omit=dev --audit-level=high` |
| Review owner | David Ruiz |
| Next review | 2026-12-01, as part of the existing TD-01 quarterly review |

Immediate re-evaluation triggers are recorded verbatim:

- any high or critical finding;
- any expansion of this function into Storage;
- the availability of a compatible official update.

## State and Review History

Each row reassesses the risk against current reality. A review MUST NOT merely repeat the original
decision after its billing, deployment or user assumptions have changed.

| Date | Billing enabled | Function deployed | Users | Evidence and outcome | Next review |
|------|-----------------|-------------------|-------|----------------------|-------------|
| 2026-08-26 (acceptance) | No; Cloud Billing API returned `billingEnabled: false` | No; billing is disabled and the Cloud Run Admin API has never been enabled | No; Firebase Auth account export returned `CONFIGURATION_NOT_FOUND`, confirming Identity Platform is not initialized | Production audit reports seven moderate entries. Mandatory peer metadata prevents removing Firebase Admin. The full-trigger reachability test reaches billing disassociation without loading Cloud Storage or `uuid`. Residual accepted before deployment under D-68. | 2026-12-01 |
| 2026-08-27 (post-deployment) | Yes; development billing was relinked after the controlled D-72 cutoff test | Yes; Gen 2 `stopBilling` is `ACTIVE` on Node.js 22 | No persistent product users; E0-07 created only temporary acceptance identities and removed them after verification | The production-only audit still reports seven moderate entries and no high or critical finding. The exported full-trigger test still reaches billing disassociation without loading Cloud Storage or `uuid`. Billing and deployment remove two original mitigating assumptions, but the affected call path remains dynamically unreachable, the function has no Storage behavior and the project has no product users. Residual remains accepted under D-68. | 2026-12-01 |
| 2026-10-13 (high and critical trigger) | Yes; `gcloud billing projects describe` returned `billingEnabled: true` | Yes; only Gen 2 `stopBilling` is deployed, `ACTIVE` on `nodejs22` since 2026-08-27 | Not re-verified; this review changes no D-68 user assumption | The CI production audit failed on an unchanged lockfile with one critical (`proxy-addr`) and two high (`@grpc/grpc-js`, `brace-expansion`) findings. They are fixed, not accepted, by D-200 within the existing parent ranges. The production-only audit now reports six moderate entries, all on the GHSA-w5hq-g745-h8pq `uuid` path, and no high or critical finding. A complete non-forced `npm audit fix` would also have downgraded `gaxios` to remove one moderate dependent; that downgrade was rejected. The reachability test still passes. Residual remains accepted under D-68. | 2026-12-01 |
| 2026-10-24 (high trigger) | Not re-verified; this review changes no D-68 assumption | Not re-verified; this review changes no D-68 assumption | Not re-verified; this review changes no D-68 assumption | The CI production audit failed on an unchanged lockfile with one high finding, `body-parser` GHSA-27r3-jxmh-p828 (denial of service via trailing bytes after a compressed request body, fixed in 2.4.0). It is fixed, not accepted, by raising the already-pinned transitive `body-parser` to 2.4.0 inside `express`'s `^2.0.0` range and `qs` to 6.16.1, with no change to a declared dependency. The downgrade of `gaxios` to 6.3.0 that a non-forced `npm audit fix` also proposes was rejected again, as the 2026-10-13 row records. The production-only audit now reports six moderate entries, all on the GHSA-w5hq-g745-h8pq `uuid` path, and no high or critical finding. The reachability test still passes. Residual remains accepted under D-68. | 2026-12-01 |

## Rejected Remediations

- Replacing Firebase Functions with Functions Framework is disproportionate: it creates a second,
  divergent deployment path that must be migrated again for D-63.
- Forcing transitive versions or downgrading official SDKs exchanges a known moderate risk in an
  unreachable path for an unsupported dependency combination and contradicts standing policy.

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

## Rejected Remediations

- Replacing Firebase Functions with Functions Framework is disproportionate: it creates a second,
  divergent deployment path that must be migrated again for D-63.
- Forcing transitive versions or downgrading official SDKs exchanges a known moderate risk in an
  unreachable path for an unsupported dependency combination and contradicts standing policy.

# ADR-0201 / D-200 - Resolve `@grpc/grpc-js`, `brace-expansion` and `proxy-addr` Advisories Within Existing Ranges

## Status

Accepted

## Context

On 2026-10-13 the `contract-check` job's production dependency audit
(`npm audit --omit=dev --audit-level=high`) failed on an unchanged `functions/package-lock.json`.
The previous CI run, on 2026-09-29, passed with the same lockfile. The audit surfaced three new
packages above the configured gate in the accepted Firebase Functions stack:

| Package and locked version | Severity | Advisories | Parent in the production graph |
|----------------------------|----------|------------|--------------------------------|
| `proxy-addr@2.0.7` | Critical | GHSA-jqcg-44mw-7w3h | `express`, required by `firebase-functions` |
| `@grpc/grpc-js@1.14.4` | High | GHSA-m9gg-hp2v-232j, GHSA-f596-whhp-79r4 | `google-gax`, required by `@google-cloud/billing` and `@google-cloud/firestore` |
| `brace-expansion@2.1.4` | High | GHSA-q2hr-2g5m-vwhr, GHSA-qhr7-859c-m2p7, GHSA-6j4f-fj2g-mc7p | `minimatch`, required through `glob` |

Patched versions `proxy-addr@2.0.8`, `@grpc/grpc-js@1.14.6` and `brace-expansion@2.1.7` are
inside the existing parent ranges. `docs/SECURITY_ADVISORY_REGISTER.md` names any high or critical
finding, and the availability of a compatible update, as immediate re-evaluation triggers. The
failure blocks every pull request, because the audit gate runs in a required check.

A non-forced `npm audit fix` also downgraded the transitive `gaxios` from 6.7.1 to 6.3.0. That
downgrade only moves a parent out of the range that depends on the moderate `uuid` advisory already
accepted under `D-68`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Resolve only the three affected packages to their patched versions in the lockfile, within the existing parent ranges | Clears every high and critical finding without a direct dependency, a top-level pin change, an override or a downgrade. | Makes the three safe transitive resolutions an explicit lockfile responsibility. |
| Apply the complete non-forced `npm audit fix` result | Also removes one moderate `uuid` dependent from the report. | Downgrades `gaxios` from 6.7.1 to 6.3.0, which the register's rejected remediations exclude, to remove a residual that `D-68` already accepts. |
| Accept the three findings as new register residuals | No lockfile change. | Retains a critical and two high findings although compatible fixes exist, and keeps the required audit gate failing. |
| Force an override, a top-level upgrade or a downgrade of the Functions stack | Controls the graph explicitly. | Broader, unsupported stack change that duplicates transitive dependencies in the manifest. |

## Decision

Resolve `proxy-addr` to 2.0.8, `@grpc/grpc-js` to 1.14.6 and `brace-expansion` to 2.1.7 in
`functions/package-lock.json` with a targeted `npm update` of exactly those packages. Do not add any
of them to `functions/package.json`. Keep every accepted top-level Functions pin, the transitive
`gaxios` resolution and the repository-wide install-script block unchanged.

## Consequences

### Positive

- The production audit reports no high or critical finding, and the required gate passes again.
- No new residual is accepted: the three findings are fixed, not registered.
- A clean `npm ci` reproduces the patched versions, none of which declares an install script.

### Negative

- The six moderate `uuid` entries accepted under `D-68` remain visible in the audit output.
- The deployed `stopBilling` revision keeps the earlier resolution until its next deployment.
  Redeployment is an owner-run operation and is not part of this decision.

### Constraints Introduced

- `functions/package-lock.json` MUST resolve `proxy-addr` at 2.0.8 or later, `@grpc/grpc-js` at
  1.14.6 or later and `brace-expansion` at 2.1.7 or later, each within its existing parent range.
- A remediation for a register re-evaluation trigger MUST NOT downgrade an unrelated transitive
  package to remove a residual that another decision already accepts.

## Verification

- `npm ls @grpc/grpc-js brace-expansion proxy-addr --all` after a clean `npm ci` resolves only
  `@grpc/grpc-js@1.14.6`, `brace-expansion@2.1.7` and `proxy-addr@2.0.8`.
- `npm run audit` exits 0 and reports only the six `D-68` moderate entries.
- `npm test` passes all Cloud Functions tests, including the `D-68` dependency reachability test,
  and `npm run test:emulator` passes the Firestore emulator integration tests.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-200`)
- `docs/SECURITY_ADVISORY_REGISTER.md`
- `docs/versions-matrix.md`
- `functions/package-lock.json`
- `D-51`, `D-68`, `D-130` ([ADR-0131](0131-resolve-qs-advisories-within-existing-ranges.md))
- [GHSA-jqcg-44mw-7w3h](https://github.com/advisories/GHSA-jqcg-44mw-7w3h)
- [GHSA-m9gg-hp2v-232j](https://github.com/advisories/GHSA-m9gg-hp2v-232j)
- [GHSA-f596-whhp-79r4](https://github.com/advisories/GHSA-f596-whhp-79r4)
- [GHSA-q2hr-2g5m-vwhr](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr)
- [GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7)
- [GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p)

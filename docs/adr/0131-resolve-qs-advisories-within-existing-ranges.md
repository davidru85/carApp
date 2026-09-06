# ADR-0131 / D-130 - Resolve `qs` Advisories Within Existing Dependency Ranges

## Status

Accepted

## Context

The E3-10 production audit surfaced GHSA-x5fp-wj9c-mxmx and GHSA-4mjr-xmp4-gh2g in
`qs@6.15.3`, pulled by the accepted Firebase Functions stack through Express and Body Parser. Both
are network availability risks and both are fixed by `qs@6.16.0`. The existing parents already
allow that version.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Resolve `qs` 6.16.0 in the lockfile within the existing parent ranges | Removes both advisories without a direct dependency, SDK downgrade or unsupported override. | Makes the safe transitive resolution an explicit lockfile responsibility. |
| Retain 6.15.3 and accept a new residual | No lockfile change. | Adds two remotely triggerable HTTP-parser advisories to a package now exposing a callable. |
| Force a direct override or downgrade the Firebase Functions stack | Can control the complete dependency graph explicitly. | Creates an unsupported or broader stack change and duplicates a transitive dependency in the manifest. |

## Decision

Resolve `qs` to 6.16.0 in `functions/package-lock.json` without adding it to `package.json`. Keep
the repository-wide install-script block and every accepted top-level Functions pin unchanged.

## Consequences

- The two new `qs` advisories are absent from the production audit.
- The seven moderate entries already accepted for GHSA-w5hq-g745-h8pq under D-68 remain visible.
- A clean `npm ci` reproduces the patched transitive version.

## Verification

- `npm ls qs --all` resolves only `qs@6.16.0`.
- `npm test` passes all Cloud Functions tests.
- `npm audit --omit=dev --audit-level=high` reports only the seven D-68 moderate entries and no
  high or critical finding.

## References

- `functions/package-lock.json`
- `docs/SECURITY_ADVISORY_REGISTER.md`
- [GHSA-x5fp-wj9c-mxmx](https://github.com/advisories/GHSA-x5fp-wj9c-mxmx)
- [GHSA-4mjr-xmp4-gh2g](https://github.com/advisories/GHSA-4mjr-xmp4-gh2g)
- `D-51`, `D-68`

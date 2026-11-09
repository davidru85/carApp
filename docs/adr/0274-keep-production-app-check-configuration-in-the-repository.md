# ADR-0274 / D-273 - Keep Production App Check Configuration in the Repository

## Status

Accepted

The owner selected option A on 2026-11-09, at the `E4-10` intake.

## Context

The development App Check configuration lives in `infra/google-cloud/app-check/` as four JSON resources:
the Play Integrity and App Attest app configurations, and the Authentication and Firestore enforcement
modes (`docs/runbooks/development-firebase-app-check.md`). Each names the development project number.
`E4-10` applies the same profile to production (`D-67`, `D-249`).

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Production JSON resources in `infra/google-cloud/app-check/production/` | Reviewed in the pull request; the live state can be compared with a source, as in development | Four more files |
| B. Values only inside the runbook commands | Fewer files | Production has no source to compare the live state with |

## Decision

The selected option is **A**.

- `infra/google-cloud/app-check/production/` holds `android-play-integrity.json`, `ios-app-attest.json`,
  `auth-enforcement.json` and `firestore-enforcement.json`. They name the production project number and
  app IDs, which `E4-10` writes after the apps are registered.
- Their values are the `D-249` profile: `allowUnrecognizedVersion: true`, `MEETS_DEVICE_INTEGRITY`, a
  `3600s` token TTL on both platforms, and `ENFORCED` for both services (`D-67`).
- The owner applies them with the commands of `docs/runbooks/production-firebase-release.md` P11.
- The development resources stay where they are.

## Consequences

### Positive

- A production App Check change is a reviewed file change.

### Negative

- The files describe the intended state only; nothing applies them automatically, so they can drift
  from the live state until the next read-back.

### Constraints Introduced

- A production App Check change updates these files in the same change.
- `E4-09`'s switch to `allowUnrecognizedVersion: false` (`D-249`) edits
  `android-play-integrity.json`.

## Verification

- The P11 read-back of each resource matches its file.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-273`); `D-67` (ADR-0068); `D-249` (ADR-0250); `D-258`
  (ADR-0259)
- `docs/runbooks/development-firebase-app-check.md`

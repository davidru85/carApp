# ADR-0250 / D-249 - Production App Check Uses the Off-Play Play Integrity Profile

## Status

Accepted

The owner selected option B on 2026-10-24. Applied by `E4-10` (`D-258`), not by `E4-04`.

## Context

Development allows unrecognized Play versions because it is installed outside Google Play
(`docs/runbooks/development-firebase-app-check.md`). Under `D-236` the app is not distributed through a
store during the MVP, and under `D-258` the production project itself arrives only with `E4-10`. A
release build is therefore sideloaded for every test the MVP performs, so requiring Play-recognized
versions would make the production project unusable for its own acceptance run.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `allowUnrecognizedVersion: false`, `MEETS_DEVICE_INTEGRITY`, 1-hour TTL | Only Play-distributed builds pass App Check. | A sideloaded release APK cannot sign in or back up, so `E4-10` could not verify the production project before `E4-09` publishes the app. |
| B. `allowUnrecognizedVersion: true` with `MEETS_DEVICE_INTEGRITY`, 1-hour TTL, as in development | Sideloaded release builds work, so `E4-10` can prove the production project end to end. | Repackaged builds that still pass device integrity can call Authentication and Firestore; the protection is weaker than Play recognition. |

## Decision

The selected option is **B**: production keeps the off-Play Play Integrity profile that development
uses, with `allowUnrecognizedVersion: true` and `MEETS_DEVICE_INTEGRITY` still required. iOS uses App
Attest with a 1-hour TTL. Authentication and Firestore stay enforced (`D-67`). `E4-09` switches to
option A when Google Play app signing exists, because that is when a Play-recognized build becomes the
only one that should pass.

## Consequences

### Positive

- `E4-10` can verify the production project with sideloaded release builds on physical devices.

### Negative

- Until `E4-09` switches the profile, a repackaged client that passes device integrity is not blocked by
  App Check. `docs/SECURITY.md` records it as an accepted residual risk of the unpublished period.

### Constraints Introduced

- `E4-09` MUST set `allowUnrecognizedVersion: false` once Play app signing is enrolled, and its
  acceptance criteria say so.
- Production App Check changes repeat the read-back of the runbook O6.

## Verification

- `E4-10`'s runbook read-back shows `"allowUnrecognizedVersion": true`, both services `ENFORCED`, and a
  sideloaded release build obtaining a token.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-249`); `D-67` (ADR-0068), `D-236` (ADR-0237),
  `D-258` (ADR-0259), `D-259` (ADR-0260)
- `docs/runbooks/development-firebase-app-check.md`

# ADR-0271 / D-270 - Sign carApp with a Personal Apple Developer Account

## Status

Accepted

The owner selected option A on 2026-11-09, at the `E4-10` intake.

## Context

`docs/BACKLOG.md` §`E4-10` needs the Apple Developer account kind before its intake. The production
Firebase iOS app stores the account's Team ID for App Attest, and Sign in with Apple binds to the same
team. `docs/BACKLOG.md` §`E4-09` also requires the kind before the App Store Connect record exists,
because the bundle identifier binds to the account there. Moving to another account later means
registering a new Team ID in Firebase and transferring the app.

carApp is a portfolio project, and no store submission is planned during the MVP (`D-236`).

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Personal (individual) account | No legal entity or D-U-N-S number; the account already signs the device builds | The store lists the owner's personal name as the seller |
| B. Organization account | The store lists a company as the seller | Needs a legal entity and a D-U-N-S number; a new Team ID to register |

## Decision

The selected option is **A**: carApp is signed and, when `E4-09` runs, published with the owner's
personal Apple Developer account.

- `E4-10` enters that account's Team ID on the production Firebase iOS app. The Team ID is read from
  `iosApp/Local.xcconfig` without printing it, and no repository artifact records it.
- This answers the `E4-09` criterion on the account kind; `E4-09` creates the App Store Connect record
  under the same account.

## Consequences

### Positive

- `E4-10` needs no new Apple account and no new Team ID.
- `E4-09` starts with the account question answered.

### Negative

- A later move to an organization account needs an app transfer, a new Team ID on the Firebase iOS app
  and new Sign in with Apple and App Attest registrations.

### Constraints Introduced

- The production Firebase iOS app carries the Team ID of the personal account.
- A change of account kind needs a new decision.

## Verification

- The production Firebase iOS app reports a Team ID (`docs/runbooks/production-firebase-release.md`
  P6); the value is checked against `iosApp/Local.xcconfig` without printing either.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-270`); `D-118` (ADR-0119); `D-236` (ADR-0237); `D-258`
  (ADR-0259)
- `docs/BACKLOG.md` §`E4-09` and §`E4-10`

# ADR-0273 / D-272 - Configure Production Sign-In Providers in the Console

## Status

Accepted

The owner selected option A on 2026-11-09, at the `E4-10` intake.

## Context

`firebase.json` holds an `auth` section with anonymous sign-in and Google sign-in, whose
`oAuthBrandDisplayName` is `carApp`, and the Firebase CLI applies it to whichever project a deploy
targets. The production consent screen must name `CarApp` (`D-237`), and `docs/BACKLOG.md` §`E4-10`
requires that the development project stay unchanged.

The pinned Firebase CLI 15.28.1 provisions the `auth` section through a web app. When the project has no
web app, `lib/deploy/auth/prepare.js` creates one named "Default Web App". The production project
registers only the Android and iOS apps.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Enable the production providers in the Firebase console, and never deploy `auth` to production | Development is unchanged; production gets no web app; one shared `firebase.json` | The production providers are not in the repository |
| B. A separate `firebase.production.json` deployed with `--config` | The production providers are in the repository | Duplicates the functions and Firestore configuration; deploying `auth` creates the web app |
| C. Change `firebase.json` to `CarApp` for every project | One edit | Changes development, against the backlog criterion, and still creates the web app |

## Decision

The selected option is **A**.

- The owner enables the `google.com` and `apple.com` providers on `davidruiz-carapp` in the Firebase
  console, with the public-facing name `CarApp` and the owner's support email
  (`docs/runbooks/production-firebase-release.md` P8).
- Every Firebase CLI deploy to `davidruiz-carapp` names its targets with `--only`, and the list never
  contains `auth`. The allowed targets are `firestore:rules`, `firestore:indexes` and
  `functions:default` (`D-247`).
- The `auth` section of `firebase.json` applies to the development project only.

## Consequences

### Positive

- The development brand and providers do not change.
- The production project holds only the two native app registrations.

### Negative

- The production providers are configured by hand, so a read-back is their only record.
- A bare `firebase deploy` to production would apply the development `auth` section; the runbook's
  `--only` lists are the only guard.

### Constraints Introduced

- No command deploys `auth` to `davidruiz-carapp`.
- No production deploy runs without `--only`.

## Verification

- The P8 read-back lists `google.com` and `apple.com` as enabled and shows the brand title `CarApp`.
- The P5 read-back of `firebase apps:list` shows no web app.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-272`); `D-237` (ADR-0238, reserved); `D-247` (ADR-0248);
  `D-258` (ADR-0259)
- `firebase.json`; `docs/runbooks/production-firebase-release.md`

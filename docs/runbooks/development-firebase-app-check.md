# Development Firebase App Check Runbook

## Scope

This runbook applies only to `davidruiz-carapp-dev` (project number `330616809208`). App Check is
an additional caller-integrity control; it never replaces Firebase Authentication or Firestore
Security Rules. D-67 requires both protected services to reject requests without a valid token.

## Registered Native Apps

| Platform | Firebase app | Runtime provider | Local acceptance provider |
|----------|--------------|------------------|---------------------------|
| Android | `1:330616809208:android:fa70311338f5e88e373d0d` | Play Integrity | Debug provider on an emulator only |
| iOS | `1:330616809208:ios:fa5cdbe548e91b1d373d0d` | App Attest | Debug provider on a simulator-only Debug build |

The Android app has the owner's D-41 local debug SHA-256 registered in Firebase. The fingerprint
MUST NOT be copied into documentation. The iOS Firebase app is associated with the owner's Apple
Developer team; query Firebase Management when verification needs the exact external value rather
than duplicating it in the repository.

The development Android app is installed outside Google Play. Its Play Integrity configuration
therefore allows an unrecognized Play version but requires `MEETS_DEVICE_INTEGRITY`, following the
Firebase off-Play profile. App Check tokens have a one-hour TTL on both platforms.

## Enforcement State

Baseline protection was changed to `ENFORCED` on 2026-08-26:

- Firebase Authentication (`identitytoolkit.googleapis.com`) at 17:31:49.027887Z;
- Cloud Firestore (`firestore.googleapis.com`) at 17:31:51.147423Z.

The configuration source is under `infra/google-cloud/app-check/`. Read the live service resources
through the Firebase App Check API after every change; enforcement can take up to 15 minutes to
propagate.

Identity Platform was initialized after App Check was configured. The project config reports
`IDENTITY_PLATFORM`, anonymous sign-in enabled and `autodeleteAnonymousUsers: true`.

## Debug Tokens

Debug tokens are secrets. Never commit them, print them in CI or include them in a distributed
artifact. Register only a named local emulator/simulator token through the App Check API and revoke
it when that local environment is retired. The API list response intentionally cannot recover a
token value.

Android selects the debug provider only when its Debug build detects an emulator; a physical
device selects Play Integrity. iOS selects the debug provider only for a Debug simulator build;
every physical build selects App Attest.

## Acceptance

The story is not accepted until all of the following are recorded in `docs/handoff-E0-07.md`:

1. an anonymous Authentication request without App Check is rejected after propagation;
2. an equivalent request with a registered token succeeds;
3. Android and iOS local native flows sign in anonymously and exercise Firestore;
4. Authentication and Firestore service resources still report `ENFORCED`;
5. Release dependency/configuration checks prove no debug provider or development plist ships.

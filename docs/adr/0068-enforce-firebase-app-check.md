# ADR-0068 / D-67 - Enforce Firebase App Check

## Status

Accepted

## Context

The MVP originally excluded Firebase App Check. Enabling billing changes the threat model:
anonymous Authentication permits frictionless identity creation, so abuse of a publicly readable
development configuration becomes a direct cost vector instead of only console noise. Firestore
rules still authorize every operation and validate the closed schema, but they do not prove that a
request came from an authentic build of the application.

No public build has been distributed, so enforcement can be introduced without retaining support
for an installed client that lacks App Check.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| App Attest on iOS, Play Integrity on Android, debug providers only in local/CI builds, enforced for Authentication and Firestore | Reduces automated abuse before a billed build is distributed and uses platform-native attestation. | Adds platform SDK configuration, debug-token handling and attestation quotas. |
| Keep App Check outside the MVP | Preserves the original smaller dependency surface. | Leaves anonymous identity creation and Firestore traffic exposed as direct billed abuse vectors. |
| Integrate now but defer enforcement | Allows metrics collection before rejection. | Leaves the billed development project unprotected during the exact acceptance period that motivated the change. |

## Decision

App Check is part of the MVP because billing turns anonymous-authentication abuse into a direct cost
vector. The development project enforces baseline App Check protection for Firebase
Authentication and Cloud Firestore before any build is distributed beyond local development.

- iOS physical builds use App Attest.
- Android physical builds use Play Integrity.
- Simulator, emulator and CI-only builds use the Firebase App Check debug provider.
- Debug-provider dependencies and factories MUST NOT appear in a release build.
- Debug tokens are secrets: they are never committed, printed in CI logs or embedded in an
  artifact intended for distribution.
- CI continues to use local emulators and holds no Firebase client or administrator credentials.
- Firestore Security Rules and Firebase Authentication remain mandatory. App Check is an
  additional caller-integrity layer, not authorization.
- Production must receive separate app registrations, attestation-provider configuration and
  enforcement under D-14 before release; development App Check configuration is never copied as a
  production credential set.

## Consequences

- Unverified requests to Authentication and Firestore are rejected after enforcement propagates.
- Local native acceptance requires explicitly registered debug tokens.
- App Attest is unavailable in the iOS Simulator and Play Integrity is unsuitable for Android
  emulators, so build-time provider selection is mandatory.
- Provider attestation has quota, latency and platform-account prerequisites that must be verified
  again during release preparation.
- Closed Firestore rules remain load-bearing even after App Check is enforced.

## Verification

- Android source-set tests prove debug and release dependencies cannot cross.
- Xcode build settings and source guards select debug only for the simulator/local Debug build and
  App Attest for physical non-Debug builds.
- Firebase App Check service configuration reports `ENFORCED` for Authentication and Firestore.
- Real Android and iOS acceptance succeeds with registered local debug tokens.
- A request without a valid App Check token is rejected after enforcement propagates.
- Contract and security documentation no longer describe App Check as out of MVP.

## References

- [Enable App Check enforcement](https://firebase.google.com/docs/app-check/enable-enforcement)
- [Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [App Attest provider](https://firebase.google.com/docs/app-check/ios/app-attest-provider)
- [Debug providers](https://firebase.google.com/docs/app-check/android/debug-provider)
- `docs/SECURITY.md`
- `D-14`, `D-54`, `D-60`, `D-66`

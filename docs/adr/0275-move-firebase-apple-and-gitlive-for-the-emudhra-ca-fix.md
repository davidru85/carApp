# ADR-0275 / D-274 - Move Firebase Apple and GitLive for the eMudhra CA Fix

## Status

Accepted

The owner accepted superseding `D-65` on 2026-11-12, when scheduling `E4-11`.

## Context

The Firebase Apple SDK for Cloud Firestore compiles a TLS root certificate bundle into the app binary. In
the affected versions that bundle still trusts intermediate CA certificates issued by eMudhra, whose private
keys were compromised on 2026-10-17 and later revoked. A network attacker could forge a Firestore server
certificate and intercept traffic. An operating system update or a server-side change cannot repair an
already built app: the SDK must be upgraded and the app rebuilt. Google announced this in mandatory Firebase
notices on 2026-11-03 and 2026-11-09 (https://firebase.google.com/support/guides/compromised-emudhra-intermediate-ca).

`D-65` (ADR-0066) pins `firebase-ios-sdk` to exactly 11.8.0 because GitLive 2.6.0 generated its Apple cinterop
bindings against that version. The iOS code of this repository links 11.8.0 through SwiftPM, including
`FirebaseFirestore`, so it is affected. No 11.x release is patched; the fixed versions are 12.19.3 and 13.1.1.
The Android SDK, the Node server SDKs and the Gradle plugins are not affected.

GitLive 2.8.0, released on 2026-11-05 on the 2.x line, bumps `firebase-ios-sdk` to 12.19.3 for this fix. Its
pull request (GitLiveApp/firebase-kotlin-sdk#939) ports the two source changes Firebase 12 needs and raises the
Apple deployment target of the GitLive klibs to iOS 15.0; this app targets iOS 16.0.

`D-65` states that the two pins move together once GitLive publishes bindings for a newer Firebase Apple
release, and that the move needs an owner-reviewed decision and full native-path verification. This is that
decision.

## Options Considered

| Option | Benefits | Costs and risks |
|--------|----------|-----------------|
| A. GitLive 2.8.0 with Firebase Apple 12.19.3 | The version GitLive built and tested for the fix; stays on the 2.x line; smallest jump that is patched | A Firebase major version change, so the native paths need runtime proof; GitLive verifies it by compilation and its own CI |
| B. GitLive 2.8.0 with Firebase Apple 13.1.1 or later | Patched and newer | A larger jump with no benefit for this app; needs a newer Xcode than the pin allows |
| C. Stay on 11.8.0 and accept the risk | No change | Leaves a known man-in-the-middle exposure in every iOS build, and no 11.x patch will arrive |
| D. GitLive 3.0.x | Newest line | Forbidden by `D-5` and `D-6`; an alpha |

## Decision

The selected option is **A**: GitLive 2.8.0 and `firebase-ios-sdk` 12.19.3, moved together.

- `gradle/libs.versions.toml` pins `firebaseApple = "12.19.3"` and `gitlive = "2.8.0"`.
- This **supersedes `D-65`** (ADR-0066). The compatibility rule of `D-65` survives: GitLive and Firebase Apple
  are one set, and neither moves alone.
- `D-113` (GoogleSignIn-iOS 9.2.0) is revalidated against the new SwiftPM graph and stays as it is.
- `D-75` (ADR-0076) is evaluated together with this decision, as ADR-0066 requires: the standalone Native test
  exception is re-checked, not assumed.
- `firebaseBom`, `googleSignInIos`, `googleServices` and `crashlyticsGradle` do not change.

## Consequences

### Positive

- The iOS build no longer embeds the compromised intermediate certificates.
- The compatibility set is again the one GitLive built and tested.

### Negative

- Firebase Apple 12 removes some 11.x API; the compile check of the `:integration:*` modules and the runtime
  check prove no call site depends on it.
- GitLive 2.8.0 changes two behaviours that this project does not use: `FirebaseOptions.gaTrackingId` is always
  `null` on Apple, and Realtime Database writes suspend while offline.

### Constraints Introduced

- A build that links a Firebase Apple version other than 12.19.3 with GitLive 2.8.0 is non-compliant, as under
  `D-65`.
- A later move of either pin needs a new decision and the same native-path proof.

## Verification

- `FirebaseConfigurationTest` fails before the catalog change and passes after it.
- The Xcode project and `Package.resolved` select 12.19.3.
- The `staging` variant signs in anonymously, creates a vehicle and a fuel entry, syncs, and shows a successful
  backup on a simulator.
- `contractCheck` keeps `D-274` and ADR-0275 aligned across all decision mirrors.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-274`); `D-65` (ADR-0066, superseded); `D-75` (ADR-0076); `D-113`
  (ADR-0114); `D-5`; `D-6`
- `docs/BACKLOG.md` §`E4-11`; `docs/handoffs/E4-11.md`

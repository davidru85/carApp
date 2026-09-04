# ADR-0113 / D-112 - Android Google Credential Acquisition

## Status

Accepted

Selected by the owner for E2-03 on 2026-09-04.

## Context

F-1 requires exactly Google and guest actions on Android. `AuthClient` already exchanges a
provider-free Google credential, but E2-03 must select the native Android API that acquires the
Google ID token. This is an authentication-path dependency choice, so the version line must be
stable, compatible with the project's fixed minSdk 26 and free from an avoidable in-MVP migration.

Credential Manager 1.6.0 is the current stable line, released on 2026-04-08, and has minSdk 23.
Its floor is therefore below the project's minSdk 26. Google ID 1.2.0 is the compatible stable
Google credential implementation selected for that stack.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Credential Manager 1.6.0, Credential Manager Play Services Auth 1.6.0 and Google ID 1.2.0 (Selected) | Current stable Android authentication path; minSdk 23 remains below the project floor; supported migration destination for Google identity | Adds three Android-only dependencies and requires native credential-result mapping |
| Credential Manager 1.7 alpha | May expose newer API work earlier | Places an alpha dependency in the authentication path; contradicts the stable-line precedent of D-5 / D-6, which rejected GitLive 3.0 alpha for the same reason |
| Legacy Google Sign-In | Familiar API with extensive historical examples | Deprecated; creates a mandatory migration to Credential Manager inside the MVP and increases delivery and regression risk |

## Decision

Use `androidx.credentials:credentials` 1.6.0,
`androidx.credentials:credentials-play-services-auth` 1.6.0 and
`com.google.android.libraries.identity.googleid:googleid` 1.2.0. Declare their versions only in
`gradle/libs.versions.toml` and consume them only from the Android host.

## Consequences

### Positive

- Android follows the supported stable credential-acquisition path.
- The selected stack preserves the fixed minSdk 26.
- E2-03 avoids a known mandatory migration away from a deprecated API.

### Negative

- The Android host must map Credential Manager cancellations and failures into the closed shared
  `NativeSignInFailure` enum.
- The Google server client identifier must be present in the debug Firebase configuration.

### Constraints Introduced

- The three dependency pins live only in `gradle/libs.versions.toml`.
- The three artifacts belong to the **Android native authentication** compatibility set in
  `docs/versions-matrix.md` and must be revalidated together.
- Credentials and tokens never enter `UiState`, analytics, logging or crash reporting.

## Verification

- Version-catalog tests and `contractCheck` pin the selected versions and decision mirrors.
- Android unit tests cover result and failure mapping; instrumented tests cover the onboarding
  surface without persisting credentials.
- The standard Android build and lint tasks prove minSdk and dependency compatibility.

## References

- `docs/DECISION_BOARD.md` (`D-112`)
- `docs/SPECIFICATION.md` sections 7 F-1, 11 and 12
- `docs/CONTRACTS.md` sections 11.1, 15.1 and 20.10
- `docs/TECHNICAL_PLAN.md` sections 2 and 12
- `docs/versions-matrix.md`

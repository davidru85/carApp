# ADR-0114 / D-113 - iOS Native Provider Acquisition

## Status

Accepted

Selected by the owner for E2-03 on 2026-09-04.

## Context

F-1 requires Apple, Google and guest actions on iOS, and requires Apple whenever Google is
available. The iOS host must acquire provider credentials without exporting Apple, Google or
Firebase SDK types through the Shared framework. The project has an iOS 16 deployment target,
Xcode 26.6 and an exact Firebase Apple SDK 11.8.0 pin under D-65.

Apple supplies the first-party `AuthenticationServices` API. Firebase's Apple exchange requires a
SHA-256 hash of a per-request nonce while retaining the raw nonce until credential exchange.
Google supplies the official GoogleSignIn-iOS Swift package.

GoogleSignIn-iOS 10.0.0 exists and is newer than the selected 9.2.0. It was deliberately rejected:
it raises the deployment floor to iOS 15 and moves to AppAuth 3.0.0, GTMAppAuth 6.0.0 and
GTMSessionFetcher 4.x-5.x without adding behavior required by F-1. Exact 9.2.0 preserves the current
iOS 16 floor and was resolved against D-65 before product code was changed.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Native `AuthenticationServices` plus `CryptoKit` for Apple; exact GoogleSignIn-iOS 9.2.0 for Google (Selected) | First-party Apple flow; official Google SDK; preserves the current deployment and Firebase compatibility envelope; smallest provider-specific host surface | Requires two native flows and explicit nonce, URL-scheme and lifecycle handling |
| Native Apple flow plus GoogleSignIn-iOS 10.0.0 | Uses the newer Google SDK line | Raises the Google SDK floor to iOS 15 and changes AppAuth, GTMAppAuth and GTMSessionFetcher major lines without supplying any F-1 capability; increases risk against Firebase Apple 11.8.0 |
| Native Apple flow plus manual Google OAuth | Gives complete control over the Google authorization exchange | Reimplements security-sensitive SDK behavior, redirect handling and token parsing; creates a larger authentication maintenance surface |
| FirebaseUI for both providers | Provides prebuilt provider UI and orchestration | Conflicts with the native platform design, adds a broad UI dependency and weakens control over the exact F-1 action set |

## Decision

Use `AuthenticationServices` and `CryptoKit` for Sign in with Apple. Generate a cryptographically
secure raw nonce per attempt, send its SHA-256 hash to Apple and retain the raw nonce only until the
Firebase exchange completes. Use the official GoogleSignIn-iOS package pinned with an exact SwiftPM
requirement at 9.2.0, not a range.

The clean E2-03 SwiftPM resolution of exact Firebase Apple SDK 11.8.0 plus exact
GoogleSignIn-iOS 9.2.0 produced:

| Package | Resolved version | Firebase 11.8.0 constraint check |
|---------|------------------|----------------------------------|
| GTMSessionFetcher | 3.5.0 | Inside `3.4.1..<5.0.0` |
| GTMAppAuth | 5.0.0 | Resolved compatibly |
| AppAuth | 2.1.0 | Resolved compatibly |
| GoogleUtilities | 8.1.2 | Inside `8.0.0..<9.0.0` |
| app-check | 11.2.0 | Inside `11.0.1..<12.0.0` |

No Firebase 11.8.0 constraint is relaxed.

## Consequences

### Positive

- Apple credential acquisition remains fully native and follows the Firebase nonce protocol.
- Google uses its official SDK while retaining the existing iOS deployment target.
- The transitive graph is known before implementation and remains compatible with D-65.

### Negative

- GoogleSignIn-iOS 9.2.0 is not the newest release and requires deliberate future review.
- Apple Developer and Google/Firebase console provisioning must remain synchronized with the
  committed debug configuration.

### Constraints Introduced

- GoogleSignIn-iOS is exact-pinned at 9.2.0.
- GoogleSignIn-iOS joins the **Firebase** compatibility set in `docs/versions-matrix.md` and MUST be
  revalidated whenever D-65 moves.
- If a future resolution forces any Firebase dependency outside its declared range, work stops for
  owner review; the D-65 pin is not relaxed implicitly.
- The Apple raw nonce and all provider tokens are ephemeral and never enter `UiState`, analytics,
  logging or crash reporting.

## Verification

- Commit `iosApp/Package.resolved` and verify its exact graph against this ADR.
- Build the signed iOS Debug app and run its unit and UI test targets.
- Manually accept Apple and Google provider flows on the configured development environment.
- `contractCheck` verifies decision and pin documentation alignment.

## References

- `docs/DECISION_BOARD.md` (`D-113`)
- `docs/SPECIFICATION.md` sections 7 F-1, 11 and 12
- `docs/CONTRACTS.md` sections 11.1, 15.1 and 20.10
- `docs/TECHNICAL_PLAN.md` sections 2 and 12
- `docs/versions-matrix.md`
- [ADR-0066](0066-pin-firebase-apple-to-gitlive-bindings.md) (`D-65`)

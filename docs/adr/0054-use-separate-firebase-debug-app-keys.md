# ADR-0054 - Use Separate Firebase Debug App Keys

## Status

Accepted

## Context

E0-07 introduces the first real Firebase clients and commits the public development configuration
files. The development Firebase project exists, but it has no registered Android or iOS app.
Android application restrictions require an application ID and SHA-1 signing-certificate
fingerprint, while iOS application restrictions require a bundle identifier. A Google Cloud API
key can carry only one application-restriction type.

The fixed debug identifiers are `com.ruizurraca.carapp.debug` on both platforms. D-41 requires the
Android key to use the owner's current local debug certificate and forbids recording that
fingerprint in the repository.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Register separate debug apps and restrict their Firebase-provisioned keys | Uses the official Firebase flow, isolates each platform and keeps configuration retrieval direct. | Maintains two client keys and two cloud app records. |
| Create custom restricted keys before registering the apps | Applies restrictions before app registration. | Adds manual key-association complexity and may leave unused auto-provisioned keys. |
| Share one key across Android and iOS | Keeps one key record. | Cannot express both Android and iOS application restrictions and therefore violates the public-repository precondition. |

## Decision

Register `carApp Android Debug` and `carApp iOS Debug` as separate Firebase apps. Use the distinct
platform keys provisioned or selected by Firebase, restrict the Android key to the fixed debug
application ID plus the owner's current local debug certificate, and restrict the iOS key to the
fixed debug bundle identifier. Verify both application restrictions before committing either
configuration file.

## Consequences

### Positive

- A leaked public configuration key is usable only from the registered platform application.
- Android and iOS restrictions can evolve independently without widening the other platform.
- Firebase CLI remains the source for app registration and configuration retrieval.

### Negative

- The development project has two client keys and two app registrations to maintain.

### Constraints Introduced

- `google-services.json` MUST NOT be committed until its selected key has the Android application
  restriction.
- `GoogleService-Info.plist` MUST NOT be committed until its selected key has the iOS application
  restriction.
- The debug certificate fingerprint remains external state and MUST NOT appear in repository
  artifacts.
- Replacing either app registration or widening either application restriction requires a
  superseding owner decision.

## Verification

- Firebase CLI lists one Android and one iOS debug app with the fixed identifiers.
- Google Cloud API Keys metadata reports the exact platform application restriction for each key.
- The downloaded configuration files select those restricted keys.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-53`)
- `docs/SECURITY.md`
- `docs/identifiers.md`
- `docs/BACKLOG.md` (`E0-07`)
- `D-41`

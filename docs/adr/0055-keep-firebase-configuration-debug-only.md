# ADR-0055 - Keep Firebase Configuration Debug-Only

## Status

Accepted

## Context

E0-07 commits restricted configuration for the development Firebase project. Production uses a
separate project whose identifier and app registrations are deliberately deferred until release
preparation. A configuration stored at the Android module root or bundled unconditionally in the
iOS target would allow a release build to connect to the development backend.

Android's Google Services plugin supports build-type-specific configuration under `src/debug`.
The single iOS target can bundle a uniquely named debug plist and select it explicitly only for a
Debug build. Keeping configuration untracked would avoid public files but contradict the E0-07
acceptance criterion and make local and CI setup dependent on undocumented state.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Commit debug-only platform configuration and fail release closed | Prevents cross-environment traffic and keeps debug setup reproducible. | Requires explicit Xcode resource selection and later production configuration. |
| Share development configuration with debug and release | Uses default file locations with minimal build configuration. | A release can silently read or write development data. |
| Inject untracked configuration locally or through CI | Keeps client keys out of the public repository. | Contradicts the accepted repository policy and creates machine-specific setup. |

## Decision

Store Android development configuration at `androidApp/src/debug/google-services.json`. Store iOS
development configuration at `iosApp/Config/Debug/GoogleService-Info-Debug.plist`, include it only
in Debug artifacts and initialise Firebase from that explicitly named plist. A release build MUST
fail before packaging while no reviewed production configuration exists.

## Consequences

### Positive

- No release artifact can silently connect to `davidruiz-carapp-dev`.
- Debug builds and CI use committed, reproducible configuration whose keys are already restricted.
- Production provisioning remains an explicit E4-04 review event.

### Negative

- iOS requires explicit configuration-file loading instead of the default plist lookup.
- Release builds intentionally remain unavailable until production Firebase provisioning lands.

### Constraints Introduced

- The development JSON and plist MUST NOT appear in a release artifact.
- The iOS Debug build MUST contain exactly one Firebase app identifier.
- E4-04 MUST add separately registered and restricted production apps before enabling release
  packaging.
- Moving either file or changing selection behavior requires a superseding owner decision.

## Verification

- Android Debug processes the file from `src/debug`; a release configuration task fails because
  no release file exists.
- The iOS Debug app bundle contains the named development plist and initialises the expected app.
- A release archive check fails before packaging until production configuration is added.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-54`)
- `docs/SECURITY.md`
- `docs/identifiers.md`
- `docs/BACKLOG.md` (`E0-07`)
- `D-34`
- `D-41`
- `D-53`

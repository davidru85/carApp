# ADR-0058 / D-57 - Pin Google Services Gradle Plugin 4.5.0

## Status

Accepted

## Context

E0-07 introduces Android's first executable Firebase client configuration. D-54 already requires
the official Google Services Gradle plugin to process `androidApp/src/debug/google-services.json`,
but the plugin version was absent from both the Gradle version catalog and the version matrix.

The project pins every build and runtime version. Leaving the plugin unversioned would either make
the build invalid or allow an implicit version source outside the catalog. At the time of the
decision, Google's Android Firebase setup guide and Google Play services release notes identify
4.5.0 as the current stable version. The preceding stable version is 4.4.4.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Pin 4.5.0 | Matches the current official Firebase setup guidance and includes the latest stable fixes. | Has less project-specific soak time than the preceding stable release. |
| Pin 4.4.4 | Has a longer adoption history. | Starts E0-07 on a superseded release without a known compatibility reason. |

## Decision

Pin `com.google.gms.google-services` 4.5.0 in `gradle/libs.versions.toml` and apply it only to
`:androidApp`. The plugin processes the debug configuration selected by D-54. No provider module
or shared KMP module applies this Android application plugin.

## Consequences

### Positive

- Android uses the current stable configuration processor documented by Firebase.
- The plugin version follows the repository's single-source version policy.
- Build-type-specific processing preserves the debug-only configuration boundary.

### Negative

- A plugin upgrade requires coordinated catalog, matrix and decision-record review.

### Constraints Introduced

- The plugin version MUST live only in `gradle/libs.versions.toml`.
- The plugin MUST be applied only to `:androidApp`.
- Release Firebase processing MUST fail while no reviewed release configuration exists.
- Changing the pinned version requires a superseding owner decision.

## Verification

- `contractCheck` reports 58 aligned decisions and ADRs.
- The debug Google Services task generates resources from `androidApp/src/debug/google-services.json`.
- The release Google Services task fails because no release configuration exists.
- `architectureCheck` and the complete Android/iOS verification command still pass.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-57`)
- [Add Firebase to your Android project](https://firebase.google.com/docs/android/setup)
- [Google Play services release notes](https://developers.google.com/android/guides/releases)
- `docs/adr/0055-keep-firebase-configuration-debug-only.md` (`D-54`)
- `docs/BACKLOG.md` (`E0-07`)

# ADR-0262 / D-261 - Give Each iOS Configuration Its Own Firebase Plist Name

## Status

Accepted

The owner selected option A on 2026-10-29, in the review of pull request #94. Amends `D-238`
(ADR-0239) and `D-259` (ADR-0260).

## Context

`D-238` requires each build type to carry only its own Firebase configuration, and `iosApp/project.yml`
to exclude each plist from the other configurations with `EXCLUDED_SOURCE_FILE_NAMES`. `D-259` added the
`Staging` configuration with the development plist at `iosApp/Config/Staging/GoogleService-Info.plist`.

`EXCLUDED_SOURCE_FILE_NAMES` matches file names, not paths. The Staging plist carried the file name of the
production plist that `E4-10` adds at `iosApp/Config/Release/GoogleService-Info.plist`, which is also the
resource `carAppApp.swift` loads for Release. As delivered:

- Release excluded only `GoogleService-Info-Debug.plist`, so a Release bundle would carry the development
  configuration as `GoogleService-Info.plist`. Only the pre-build verification script, which fails first
  on the empty `GOOGLE_REVERSED_CLIENT_ID`, kept a Release artifact from existing.
- Debug excluded nothing, so a Debug bundle carried both development plists.
- Staging excluded its own plist and loaded `GoogleService-Info-Debug.plist`, while the verification
  script validated the Staging file that Staging never bundled.
- Once `E4-10` added the production plist as a resource, two resources would share one product name and
  Debug and Release would fail with a duplicate-output error; had it not been added as a resource, the
  script would pass on the file on disk and Release would ship the development configuration.

`D-238` requires a superseding decision to move a configuration file.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Rename the Staging plist to `iosApp/Config/Staging/GoogleService-Info-Staging.plist`; Staging loads it; each configuration excludes every plist but its own | Each configuration carries exactly its own plist under a unique name, as each Android build type does; Staging keeps a file of its own until `E4-10`; the production plist needs no exclusion change when it arrives. | Two identical development plists exist on iOS. |
| B. Defer the fix to `E4-10` and record the defect as a residual risk | No file changes now. | `E4-04` closes with one pre-build script as the only barrier, and `E4-10` inherits a defect it must rediscover. |
| C. Delete the Staging plist; Staging loads and validates the Debug plist | Smallest change; one development plist on iOS. | Staging no longer has a configuration file of its own. |

## Decision

The selected option is **A**.

- `iosApp/Config/Staging/GoogleService-Info.plist` is renamed, with its content unchanged, to
  `iosApp/Config/Staging/GoogleService-Info-Staging.plist`, and `.gitignore` negates the new path.
- `firebaseConfigurationResourceName` in `iosApp/carAppApp.swift` maps `Debug` to
  `GoogleService-Info-Debug`, `Staging` to `GoogleService-Info-Staging` and every other configuration to
  `GoogleService-Info`.
- `iosApp/project.yml` excludes, per configuration, every Firebase plist but its own: Debug excludes
  `GoogleService-Info-Staging.plist GoogleService-Info.plist`, Staging excludes
  `GoogleService-Info-Debug.plist GoogleService-Info.plist`, and Release excludes
  `GoogleService-Info-Debug.plist GoogleService-Info-Staging.plist`.
- The pre-build verification script validates, for each configuration, the plist that configuration
  bundles.
- `E4-10` adds `iosApp/Config/Release/GoogleService-Info.plist` as a resource; the exclusions above already
  keep it out of Debug and Staging.

## Consequences

### Positive

- No configuration can carry another configuration's plist, and no two Firebase resources share a product
  name.
- The Release fail-closed state no longer rests on the verification script alone.

### Negative

- Two identical development plists exist on iOS; a change to the development iOS app registration
  updates both.

### Constraints Introduced

- Every iOS Firebase plist has a unique file name, and each configuration excludes every plist but its
  own.
- `FirebaseConfigurationTest.eachIosConfigurationBundlesOnlyItsOwnFirebasePlist` enforces it on
  `iosApp/project.yml` and on the generated Xcode project.

## Verification

- `./gradlew --no-watch-fs :build-logic:convention:test` runs the guard.
- An unsigned `xcodebuild archive -configuration Staging` bundles only `GoogleService-Info-Staging.plist`;
  a Debug simulator build bundles only `GoogleService-Info-Debug.plist`; `xcodebuild -configuration
  Release` still fails before packaging.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-261`); `D-238` (ADR-0239), `D-259` (ADR-0260), `D-258`
  (ADR-0259)
- `docs/SECURITY.md`

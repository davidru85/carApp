# ADR-0239 / D-238 - Commit Restricted Firebase Configuration per Build Type and Fail Closed Without It

## Status

Accepted

The owner selected option A on 2026-10-24; `D-258` (ADR-0259) moved the production files themselves to
`E4-10`. Amends `D-54` (ADR-0055); amended in turn by `D-259` (ADR-0260) and by `D-261` (ADR-0262),
which gives each iOS configuration a Firebase plist with a file name of its own.

## Context

`D-54` kept Firebase configuration debug-only and made release builds fail closed until reviewed
production configuration existed: Android has `google-services.json` only under `src/debug`, and the
iOS Release configuration excludes the debug plist and calls `FirebaseApp.configure()` with no plist to
find. Two further defects block release:

- `iosApp/Info.plist` hard-codes the development reversed client ID as the Google Sign-In URL scheme
  for every configuration, so Google sign-in cannot return to a Release build.
- `.gitignore` ignores `google-services.json` and `GoogleService-Info.plist` everywhere, negating only
  the two debug files, so a committed production configuration would be silently untracked.

The owner postponed the production project to the last steps before the MVP closes (`D-258`), so this
decision has to hold in two halves: the **mechanism** in `E4-04`, the **production files** in `E4-10`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Commit the restricted production configuration per build type, select the iOS URL scheme from per-configuration xcconfig files, guard both directions with tests | Reproducible builds from a clean clone, as `D-54` required for debug; the build type alone selects the backend; tests make a cross-environment release unrepresentable. | Production client keys are public, like the development keys; they are safe only while restricted (`docs/SECURITY.md`). |
| B. Keep production configuration untracked and inject it locally | Keys never appear in the public repository. | Contradicts `D-54`'s reproducibility rationale; release builds depend on undocumented local state; the guard tests cannot see the files. |

## Decision

The selected option is **A**, in two halves.

**`E4-04` delivers the mechanism and the fail-closed state.**

- Android release reads `androidApp/src/release/google-services.json`; debug keeps
  `androidApp/src/debug/google-services.json`, and staging keeps its own development copy (`D-259`). No
  configuration exists at the module root or in `src/main`.
- iOS Release bundles `iosApp/Config/Release/GoogleService-Info.plist`; Debug keeps
  `iosApp/Config/Debug/GoogleService-Info-Debug.plist`; Staging keeps the development plist (`D-259`).
  `iosApp/project.yml` excludes each plist from the other configurations with
  `EXCLUDED_SOURCE_FILE_NAMES`.
- `iosApp/Info.plist` names the URL scheme `$(GOOGLE_REVERSED_CLIENT_ID)`. The value comes from the
  per-configuration xcconfig, `iosApp/Config/Debug/Firebase.xcconfig`,
  `iosApp/Config/Release/Firebase.xcconfig` and, for Staging, its own file; the target keeps
  `Signing.xcconfig` (`D-118`).
- **Absent release configuration is the correct fail-closed state.** A release build MUST fail
  *before packaging* when the release plist, the `google-services.json` of the release source set or
  the release URL-scheme value is missing. Falling back to a development value is forbidden.
  `carAppApp.swift`'s `FirebaseApp.configure()` outside `#if DEBUG` fails only at runtime, which
  ADR-0055 does not accept, so the check moves into the build.
- The guard tests hold in both directions: an **absent** release configuration passes and is the
  expected state until `E4-10`; a **present** one MUST name only the production project and MUST NOT
  contain `davidruiz-carapp-dev`; a debug or staging configuration MUST never reach a release
  artifact.

**`E4-10` delivers the production files.**

- It adds `androidApp/src/release/google-services.json` and
  `iosApp/Config/Release/GoogleService-Info.plist` with keys already restricted, and only then adds the
  `.gitignore` negations for those two paths, so nothing can be staged at them earlier.
- It sets the real `GOOGLE_REVERSED_CLIENT_ID` in `iosApp/Config/Release/Firebase.xcconfig`.
- It moves the presence assertion of the guard tests from "absent is correct" to "present and
  production-only".

## Consequences

### Positive

- `E4-04` closes with a mechanism that cannot silently point a release build at the development
  project, and `E4-10` only has to add two reviewed files.
- No release artifact can name `davidruiz-carapp-dev`, and no debug or staging artifact can name
  production.

### Negative

- Until `E4-10`, `./gradlew :androidApp:assembleRelease` fails by design; the locally testable build is
  `staging` (`D-259`).
- The production client keys will be publicly readable; their protection is the key restriction plus
  App Check (`D-67`) and the Firestore rules.

### Constraints Introduced

- `FirebaseConfigurationTest` asserts: the exact allowed locations of development configuration (debug
  and staging) and none for release; a present release configuration names only the production project
  recorded in `docs/identifiers.md` and registers `com.ruizurraca.carapp`; each build type excludes the
  other's plist; each configuration's URL scheme equals the `REVERSED_CLIENT_ID` of its plist; the
  `.gitignore` negations match the reviewed paths once `E4-10` adds them.
- A new build type resolves to one of the reviewed configurations through its own source set and
  updates the guard's expected set in the same change (`D-259` is the first one).
- Moving a configuration file or changing the selection mechanism requires a superseding decision.

## Verification

- `./gradlew :build-logic:convention:test` runs the guards.
- `./gradlew :androidApp:assembleRelease` fails on the missing release configuration, and
  `xcodebuild -configuration Release -sdk iphoneos CODE_SIGNING_ALLOWED=NO` fails before packaging.
- `aapt2 dump resources` on the staging APK and `PlistBuddy` on the Staging app bundle show the
  development project ID and the development reversed client ID (`docs/handoffs/E4-04.md`).

## References

- `docs/DECISION_BOARD.md` (decision ID `D-238`); `D-54` (ADR-0055), `D-118` (ADR-0119), `D-258`
  (ADR-0259), `D-259` (ADR-0260), `D-261` (ADR-0262), `D-237` (reserved; its ADR-0238 is written by `E4-10`)
- `docs/SECURITY.md`; `docs/identifiers.md`

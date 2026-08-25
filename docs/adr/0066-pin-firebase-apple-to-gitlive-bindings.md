# ADR-0066 / D-65 - Pin Firebase Apple to the GitLive Cinterop Version

## Status

Accepted.

## Context

E0-07 introduces direct Firebase Authentication and Cloud Firestore integration on iOS through
GitLive 2.6.0. GitLive's Apple cinterop bindings are generated against Firebase Apple SDK 11.8.0.
The application must add the native Firebase products to the Xcode project because the exported
Kotlin framework does not supply them transitively.

Using a newer native Firebase Apple SDK is not a compatible version-range upgrade. A mismatched
SDK can satisfy compilation and linking while failing later with native runtime exceptions. The
native SDK pin is therefore part of the GitLive compatibility set, not an independent platform
choice.

## Options Considered

| Option | Benefits | Costs and risks |
|--------|----------|-----------------|
| Firebase Apple 11.8.0 | Matches the exact SDK used to generate GitLive 2.6.0 bindings and minimizes cinterop and runtime mismatch risk. | Retains an older native SDK until GitLive publishes regenerated bindings. |
| Firebase Apple 12.18.0 | Uses the current stable Apple SDK and its recent fixes. | Does not match GitLive 2.6.0 bindings; successful compilation would not rule out native runtime failure. |
| Firebase Apple 11.15.0 | Avoids the Firebase 12 major-version removals while being newer than 11.8.0. | Still differs from the binding-generation version and retains both mismatch risk and a later migration. |

## Decision

Pin the Firebase Apple SDK to 11.8.0 exactly while the project uses GitLive 2.6.0.

GitLive and Firebase Apple are one compatibility set. Neither version may be changed independently.
When GitLive publishes Apple bindings generated against a supported newer Firebase Apple release,
the coordinated upgrade requires a new owner-reviewed decision and full native-path verification.

## Consequences

- The Xcode Swift package dependency uses an exact 11.8.0 requirement.
- The iOS application links only the Firebase products required by the E0-07 path.
- The older Apple SDK is explicit migration debt, not an accidental transitive version.
- Android remains governed independently by the Firebase BOM, but any GitLive upgrade still
  requires revalidating both platforms.
- A build that happens to link with another Firebase Apple version is non-compliant even if tests
  appear to pass.

## Verification

- `docs/versions-matrix.md` records Firebase Apple 11.8.0 in the Firebase compatibility set.
- The Xcode project and resolved package state select Firebase Apple 11.8.0.
- The complete E0-07 Apple build links the `Shared` framework with Firebase Auth and Firestore.
- Native-path tests exercise anonymous authentication and Vehicle remote backup under the selected
  SDK.
- `contractCheck` keeps D-65 and ADR-0066 aligned across all decision mirrors.

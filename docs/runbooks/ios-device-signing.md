# iOS Device Signing Runbook

## Scope

This runbook covers building and running the application on a **physical iPhone**. Simulator builds,
the `ios-simulator-build` CI job and every automated test need none of it: Xcode signs the simulator
application locally, which is what `D-71` / [ADR-0072](../adr/0072-use-normal-ios-simulator-signing.md)
require.

`D-71` forbids committing a developer team, certificate or provisioning profile. `D-118` /
[ADR-0119](../adr/0119-supply-ios-device-signing-locally.md) defines how a device build gets the team
without putting it in the repository.

## How it works

`iosApp/Signing.xcconfig` is committed and is the base configuration of the `carApp` target for both
Debug and Release. Its only content is an optional include:

```text
#include? "Local.xcconfig"
```

The `#include?` form is a no-op when the file is absent, which is the state of a fresh clone and of
every CI runner. `iosApp/Local.xcconfig` is git-ignored and holds the developer team of whoever is
building.

A build setting written into the Xcode project would override an xcconfig value, so
`DEVELOPMENT_TEAM` MUST NOT appear in `iosApp/project.yml` or in the generated project. The
`FirebaseConfigurationTest` guards enforce both halves: no committed team, and the local mechanism
still in place.

## One-time setup on a development machine

1. Find the team identifier: Xcode → Settings → Accounts → select the Apple ID → the team row shows
   it, or run `security find-identity -p codesigning -v` and read it from the certificate name. It is
   a ten-character identifier such as `ABCDE12345`.
2. Create `iosApp/Local.xcconfig`:

   ```text
   DEVELOPMENT_TEAM = ABCDE12345
   ```

3. Confirm the file is ignored. `git status --porcelain iosApp/Local.xcconfig` MUST print nothing.
4. Confirm Xcode reads it:

   ```bash
   cd iosApp
   xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphoneos -showBuildSettings \
     | grep DEVELOPMENT_TEAM
   ```

## Building for a device

```bash
cd iosApp
xcodebuild -project carApp.xcodeproj -scheme carApp \
  -sdk iphoneos -destination 'generic/platform=iOS' build
```

Add `-allowProvisioningUpdates` only when the profile has to be created or refreshed, because it
contacts Apple and can register new provisioning state on the account. A build that only reuses an
existing profile does not need it.

Xcode's Run button works the same way: the target already resolves its team through the xcconfig, so
there is nothing to select in Signing & Capabilities.

## If the signing guard fails

`FirebaseConfigurationTest.iosSimulatorUsesNormalXcodeSigningWithoutACommittedIdentity` fails when a
team identifier reaches `iosApp/project.yml` or the generated project. The usual cause is selecting a
team in the Signing & Capabilities editor, which writes `DEVELOPMENT_TEAM` into the project file.

Recovery:

1. Remove `DEVELOPMENT_TEAM` from `iosApp/project.yml` if it is there.
2. Regenerate the project: `cd iosApp && ./generate-project.sh`.
3. Confirm: `grep -r 'DEVELOPMENT_TEAM\|DevelopmentTeam' iosApp` returns only `Local.xcconfig`.
4. Re-run the guard: `./gradlew :build-logic:convention:test`.

## Sign in with Apple

The `com.apple.developer.applesignin` entitlement is committed in `iosApp/carApp.entitlements`, but
the capability must also be enabled on the App ID in the Apple Developer account, which requires
Account Holder or Admin access. That provisioning state lives in the Apple account, not in this
repository.

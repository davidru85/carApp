# ADR-0119 / D-118 - Supply the iOS Developer Team From an Untracked Local Configuration

## Status

Accepted

Selected by the owner on 2026-09-05.

## Context

`D-71` / [ADR-0072](0072-use-normal-ios-simulator-signing.md) states that no development team,
account-specific certificate or provisioning profile is committed. Simulator builds and the
`ios-simulator-build` CI job need none of them, because Xcode signs the simulator application
locally.

While provisioning Sign in with Apple for E2-03, selecting the team in Xcode wrote `DEVELOPMENT_TEAM`
into `iosApp/project.yml` and into the generated project. That violated `D-71`, and the guard test
`FirebaseConfigurationTest.iosSimulatorUsesNormalXcodeSigningWithoutACommittedIdentity` kept the
protected `architecture-check` job red until the values were removed on 2026-09-05.

Removing them restored the decision but left nothing in its place: a signed device build then failed
with `Signing for "carApp" requires a development team`. The E2-03 acceptance evidence for the Apple
entitlement had been produced by exactly such a build, so it was no longer reproducible, and no
document explained how to build for a device.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Committed `Signing.xcconfig` with an optional include of an untracked `Local.xcconfig` (Selected) | Works from the command line and from Xcode's Run button, so selecting a team in the UI is no longer necessary and cannot rewrite the project; a no-op on CI, where the local file never exists | One file has to be created once per development machine |
| Pass the team as an argument or environment variable on each build command | Nothing to create; zero repository footprint | Xcode's Run button still has no team, so a developer using the UI is pushed back into the Signing editor, which is exactly how the violation happened |
| Commit the team and supersede `D-71` | Simplest daily workflow | Reverses an accepted decision and re-couples the project to one person's Apple account for a value that only that person can use |
| Defer device signing to the release phase | No work now | Leaves an acceptance evidence path that cannot be reproduced, and surfaces the problem during release work instead of now |

## Decision

`iosApp/Signing.xcconfig` is committed and is the base configuration of the `carApp` target for the
Debug and Release configurations. Its only functional content is `#include? "Local.xcconfig"`, which
is a no-op when that file is absent.

`iosApp/Local.xcconfig` is git-ignored and holds `DEVELOPMENT_TEAM` for the machine that builds.

A build setting written into the Xcode project overrides an xcconfig value, so `DEVELOPMENT_TEAM`
MUST NOT appear in `iosApp/project.yml` or in the generated project. Both halves are guarded:
`iosSimulatorUsesNormalXcodeSigningWithoutACommittedIdentity` rejects a committed team, and
`iosDeveloperTeamIsSuppliedByAnUntrackedLocalConfiguration` rejects the removal of the local
mechanism or of its ignore rule.

`docs/runbooks/ios-device-signing.md` documents the one-time setup, the build commands and the
recovery path when the guard fires.

## Consequences

### Positive

- Device builds are reproducible from a documented procedure without committing account state.
- Xcode's Run button resolves the team through the xcconfig, removing the interaction that caused
  the original violation.
- CI is unaffected: the local file never exists there and the optional include does nothing.

### Negative

- Every development machine needs a one-time manual step before its first device build.
- The mechanism is invisible in the Xcode UI, so a developer who does not read the runbook may still
  reach for the Signing editor. The guard catches that, and the runbook documents the recovery.

### Constraints Introduced

- `DEVELOPMENT_TEAM` MUST NOT be committed in any form.
- `Signing.xcconfig` MUST stay functional when `Local.xcconfig` is absent.
- `iosApp/Local.xcconfig` MUST stay in `.gitignore`.

## Verification

- `iosDeveloperTeamIsSuppliedByAnUntrackedLocalConfiguration` pins the committed xcconfig, its
  optional include, the project reference and the ignore rule.
- `xcodebuild -showBuildSettings -sdk iphoneos` reports the team supplied by the local file.
- A signed device build, `xcodebuild -sdk iphoneos -destination 'generic/platform=iOS' build`,
  succeeds without `-allowProvisioningUpdates`, so it reuses the existing profile and registers no
  new provisioning state.

## References

- `docs/DECISION_BOARD.md` (`D-118`)
- `docs/runbooks/ios-device-signing.md`
- [ADR-0072](0072-use-normal-ios-simulator-signing.md) (`D-71`)
- [ADR-0075](0075-use-xcuitest-for-keychain-persistence-acceptance.md) (`D-74`)
- [ADR-0120](0120-declare-guarded-repository-inputs.md) (`D-119`)

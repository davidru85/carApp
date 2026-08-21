# ADR-0026 / D-25 - Pin `targetSdk` Separately from `compileSdk`

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

`E0-06` pinned the toolchain. The pinned Compose BOM requires `compileSdk 37`: the AAR metadata check fails the build below it, so `compileSdk` is not a free choice.

`targetSdk` is a different thing. `compileSdk` decides which APIs the code may compile against; `targetSdk` declares which Android runtime contract the app opts into, which changes how the platform treats the running app — background execution, permissions, window insets and similar behaviour.

The first pinning pass set both to 37 by symmetry. That silently opted the MVP into every API 37 runtime behaviour change as a side effect of a version-pinning story, with nobody having reviewed those changes against the flows `F-1` to `F-5` or the design assets. It was also inconsistent with the performance baselines, whose Android reference device was pinned at Android 16 (API 36).

No normative document constrains `targetSdk`: `docs/SPECIFICATION.md §11` fixes `minSdk` at 26, and `docs/identifiers.md` delegates `targetSdk` to `docs/versions-matrix.md`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| `compileSdk 37`, `targetSdk 36` | Compile against the newest APIs while the runtime opt-in stays a deliberate, reviewed act. Matches the Android reference device, so the app is measured on the contract it opts into. | A bump is owed before release; two values to explain instead of one. |
| `targetSdk 37` | The app is built from day one under the rules it will ship under; no migration owed. | Opts into every API 37 behaviour change with no review. New platform behaviour is less battle-tested across AndroidX and third-party libraries. |
| `targetSdk 37` plus a review criterion in `E4-04` | Keeps the previous benefits and names an owner for the review. | The review lands late, when changing behaviour costs more. |

## Decision

Pin `compileSdk` and `targetSdk` independently. For the MVP: `compileSdk 37`, forced by the Compose BOM, and `targetSdk 36`.

Raising `targetSdk` is a behavioural change, not a version bump. `E4-04` owns the move to a newer level before release and MUST review the runtime behaviour changes of the target level against the functional flows and the design assets.

## Consequences

### Positive

- A Compose BOM bump can raise `compileSdk` without silently changing the app's runtime contract.
- The performance reference device and the pinned `targetSdk` agree, so the measurement baselines describe the shipped configuration.
- The runtime opt-in has a named owner and a story instead of happening by symmetry.

### Negative

- The MVP ships one API level below the newest available until `E4-04` raises it.
- Two SDK values must be kept distinct in every build script and convention plugin.

### Constraints Introduced

- `compileSdk` and `targetSdk` are separate entries in `gradle/libs.versions.toml` and MUST NOT be collapsed into one.
- Changing `targetSdk` is a human review gate under the "technical stack or pinned versions" gated topic and requires a behavioural review, not just a build check.

## Verification

- `E0-06` pins both values and the merged Android manifest reports `targetSdkVersion="36"` with `minSdkVersion="26"`.
- `E4-04` reviews and raises `targetSdk` before release.

## References

- `docs/DECISION_BOARD.md` (`D-25`)
- `docs/versions-matrix.md`, "Pinned versions"
- `docs/SPECIFICATION.md §11`

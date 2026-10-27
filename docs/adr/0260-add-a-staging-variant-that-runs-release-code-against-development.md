# ADR-0260 / D-259 - Add a `staging` Variant That Runs Release Code against Development

## Status

Accepted

The owner selected option A on 2026-10-27.

## Context

`D-238` makes release builds fail closed until `E4-10` supplies the production configuration, and
`D-258` moves that cutover to the last story of the MVP. Between them, nothing would exercise the code
that only ships in a release build: `D-245` keeps R8 off, but the release source set already supplies
the Play Integrity App Check provider (never the debug provider), and `E4-03` must measure a build with
release settings. A debug build cannot substitute: it is debuggable, it uses the debug provider and it
compiles the `#if DEBUG` seams.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A `staging` build type on Android and a `Staging` configuration on iOS: release code and settings, the `.debug` identity, debug signing, the development Firebase configuration | Release code runs today against the project that exists; `E4-03` measures something real; the store-only project stays out of Phase 4. | Two more build configurations to keep honest; the same application ID as Debug, so they cannot coexist on one device. |
| B. Measure debug builds until `E4-10` | No new configuration. | `E4-03` would measure a debuggable app with the debug App Check provider and `#if DEBUG` seams compiled in: the numbers would not describe the shipped product. |
| C. Create the production project now | One configuration to reason about. | Exactly what `D-258` postponed; it needs billing, identity and restricted keys that serve no purpose while unpublished. |

## Decision

The selected option is **A**.

**Android.** A build type `staging`:

- `initWith(release)`, so minification follows `D-245` (off) and the build is not debuggable;
- `applicationIdSuffix ".debug"` and the debug signing config, so it installs next to nothing else and
  needs no keystore;
- `matchingFallbacks += "release"` where a dependency resolves per build type;
- it uses the **release** source set code, so `AppCheckProvider` there provides Play Integrity and never
  the debug provider;
- it uses the development `google-services.json`, committed under `src/staging/`, and the guards name
  the exact allowed locations of development configuration (debug and staging) while asserting none for
  release.

**iOS.** A configuration `Staging` in `iosApp/project.yml`:

- Release build settings with no `DEBUG` compilation condition;
- bundle identifier `com.ruizurraca.carapp.debug`;
- `configFiles` includes `Signing.xcconfig`, so the untracked `Local.xcconfig` applies;
- the development plist and the development URL scheme;
- App Attest as the App Check provider;
- `KOTLIN_FRAMEWORK_BUILD_TYPE=release`, because the configuration name is neither `Debug` nor
  `Release`.

**Firebase plist selection becomes configuration-driven**, because `carAppApp.swift` selects it with
`#if DEBUG`, which cannot distinguish Staging from Release. The UI-test hooks and the App Check debug
provider stay `#if DEBUG`, and the Debug-only hook rule keeps holding for Staging. A scheme or a
documented `xcodebuild -configuration Staging` invocation is provided.

**`D-54` (ADR-0055) is amended**: development configuration is allowed in Debug **and** Staging, never
in Release. Staging is never distributed.

**Documented limits.** The staging and debug application IDs are equal, so the two cannot coexist on one
device. Backend calls pass App Check only on physical devices (Play Integrity, App Attest); emulators and
simulators keep using the debug provider through a Debug build.

**Measurement.** `E4-03` measures on `staging`. The "release build" wording of
`docs/versions-matrix.md` (rows "Consumption for 1,000 entries", "Cold start to first content", "List
smoothness") is clarified to name the `staging` build type, and the `E4-03` intake references this
decision.

## Consequences

### Positive

- Release code, the release App Check provider and release settings are exercised on every local run and
  by `E4-03`, without a production project.
- A future production build differs from `staging` only in its configuration files.

### Negative

- A third configuration must be kept in step with two others; the guards cover the Firebase side, and
  `ReleaseAssetsContractTest` covers the resource side.
- Staging cannot be installed beside Debug on one device.

### Constraints Introduced

- No development configuration may reach `release`; the guards enforce the allowed locations by name.
- Staging MUST NOT be distributed, and its identity MUST stay `com.ruizurraca.carapp.debug`.
- A new build type or configuration MUST declare its Firebase source explicitly and update the guard's
  expected set in the same change (`D-238`).

## Verification

- `:androidApp:assembleStaging` succeeds; `aapt2 dump badging` shows `com.ruizurraca.carapp.debug`, not
  debuggable, `versionName 1.0.0`, `versionCode 1`, and the icon resources; the dex contains no
  `DebugAppCheckProviderFactory`.
- `xcodebuild -configuration Staging -sdk iphoneos` builds; the bundle carries the development plist, the
  development URL scheme, `1.0.0` / `1`, `UILaunchScreen` and `CFBundleIconName`.
- `:androidApp:assembleRelease` and `xcodebuild -configuration Release` still fail closed.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-259`); `D-54` (ADR-0055), `D-238` (ADR-0239), `D-245`
  (ADR-0246), `D-247` (ADR-0248), `D-249` (ADR-0250), `D-258` (ADR-0259)
- `docs/versions-matrix.md`, `docs/handoffs/E4-04.md`
